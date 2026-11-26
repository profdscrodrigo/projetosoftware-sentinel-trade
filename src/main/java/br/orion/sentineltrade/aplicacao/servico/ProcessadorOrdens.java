package br.orion.sentineltrade.aplicacao.servico;

import br.orion.sentineltrade.aplicacao.porta.*;
import br.orion.sentineltrade.aplicacao.porta.Repositorios.*;
import br.orion.sentineltrade.dominio.*;
import br.orion.sentineltrade.dominio.Excecoes.IntegracaoException;
import br.orion.sentineltrade.dominio.Excecoes.RespostaInvalidaException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Motor de ordens (processamento ASSÍNCRONO). Três responsabilidades:
 *  1. transmitir à corretora as ordens VALIDADAS da fila, com retentativa e recuo exponencial;
 *  2. consumir relatórios de execução (entrega "pelo menos uma vez" + consumidor IDEMPOTENTE);
 *  3. liquidar saldo e carteira de forma atômica a cada execução.
 *
 * Princípio: nenhuma chamada externa dentro de transação de banco; qualquer falha externa deixa
 * ordens, saldo e posições intactos (a ordem continua na fila ou é rejeitada com reserva devolvida).
 */
public final class ProcessadorOrdens {
    public static final int MAX_TENTATIVAS_ENVIO = 5;
    private static final Duration BACKOFF_BASE = Duration.ofSeconds(2);
    private static final Duration BACKOFF_MAX = Duration.ofSeconds(60);

    private final FilaOrdens fila;
    private final OrdemRepositorio ordens;
    private final ContaRepositorio contas;
    private final PosicaoRepositorio posicoes;
    private final ExecucaoRepositorio execucoes;
    private final CorretoraGateway corretora;
    private final AuditoriaService auditoria;
    private final NotificacaoService notificacoes;
    private final GerenciadorTransacao tx;
    private final Clock clock;

    public ProcessadorOrdens(FilaOrdens fila, OrdemRepositorio ordens, ContaRepositorio contas, PosicaoRepositorio posicoes,
                             ExecucaoRepositorio execucoes, CorretoraGateway corretora, AuditoriaService auditoria,
                             NotificacaoService notificacoes, GerenciadorTransacao tx, Clock clock) {
        this.fila = fila; this.ordens = ordens; this.contas = contas; this.posicoes = posicoes; this.execucoes = execucoes;
        this.corretora = corretora; this.auditoria = auditoria; this.notificacoes = notificacoes; this.tx = tx; this.clock = clock;
    }

    /** Um ciclo do worker. Devolve a quantidade de itens tratados. */
    public int ciclo() {
        int n = processarFila();
        n += coletarExecucoes();
        n += notificacoes.despacharPendentes();
        return n;
    }

    // ---------------------------------------------------------------- 1. transmissão
    public int processarFila() {
        int n = 0;
        for (MensagemFila m : fila.disponiveis(clock.instant(), 50)) {
            try { processarMensagem(m); n++; }
            catch (RuntimeException e) { Log.erro("Falha inesperada ao processar mensagem " + m.id(), e); }
        }
        return n;
    }

    private void processarMensagem(MensagemFila m) {
        Ordem o = ordens.buscarPorId(m.ordemId()).orElse(null);
        if (o == null || o.status() != StatusOrdem.VALIDADA) {          // cancelada/rejeitada enquanto esperava
            tx.executarSemRetorno(() -> fila.concluir(m.id()));
            return;
        }
        AckCorretora ack;
        try {
            ack = corretora.enviar(new OrdemParaEnvio(o.id(), o.ticker(), o.lado(), o.quantidade(), o.precoLimite()));
        } catch (IntegracaoException e) {
            tratarFalhaEnvio(m, o.id(), e);
            return;
        }
        boolean[] compensar = {false};
        Retentativa.comConflito(3, () -> tx.executar(() -> {
            Ordem atual = ordens.buscarPorId(o.id()).orElseThrow();
            Instant agora = clock.instant();
            if (atual.status() == StatusOrdem.VALIDADA) {
                if (ack.aceita()) {
                    atual.marcarEnviada(agora);
                    ordens.atualizar(atual);
                    auditoria.registrar(TipoEvento.ORDEM_ENVIADA, atual.investidorId(), "ORDEM", atual.id().toString(), "aceita pela corretora");
                } else {
                    rejeitar(atual, CodigoRegra.REJEITADA_PELA_CORRETORA, ack.motivo());
                }
            } else if (atual.status() == StatusOrdem.CANCELADA && ack.aceita()) {
                compensar[0] = true;        // cancelada localmente durante o envio: desfaz na corretora
            }
            fila.concluir(m.id());
            return null;
        }));
        if (compensar[0]) {
            try { corretora.cancelar(o.id()); }
            catch (IntegracaoException e) { Log.aviso("Compensação de cancelamento pendente para ordem " + o.id()); }
        }
    }

    private void tratarFalhaEnvio(MensagemFila m, UUID ordemId, IntegracaoException e) {
        int tentativas = m.tentativas() + 1;
        Retentativa.comConflito(3, () -> tx.executar(() -> {
            Ordem o = ordens.buscarPorId(ordemId).orElseThrow();
            auditoria.registrar(TipoEvento.ENVIO_CORRETORA_FALHOU, o.investidorId(), "ORDEM", o.id().toString(),
                    "tentativa " + tentativas + "/" + MAX_TENTATIVAS_ENVIO + ": " + e.getClass().getSimpleName());
            if (o.status() != StatusOrdem.VALIDADA) { fila.concluir(m.id()); return null; }
            if (tentativas >= MAX_TENTATIVAS_ENVIO) {
                rejeitar(o, CodigoRegra.FALHA_INTEGRACAO, "esgotadas as tentativas de envio");
                fila.concluir(m.id());
            } else {
                fila.reagendar(m.id(), tentativas, clock.instant().plus(backoff(tentativas)));
            }
            return null;
        }));
    }

    static Duration backoff(int tentativa) {
        long seg = BACKOFF_BASE.getSeconds() << Math.min(tentativa - 1, 10);
        return Duration.ofSeconds(Math.min(seg, BACKOFF_MAX.getSeconds()));
    }

    // ---------------------------------------------------------------- 2. execuções
    public int coletarExecucoes() {
        List<RelatorioExecucao> relatorios;
        try { relatorios = corretora.consultarRelatorios(); }
        catch (IntegracaoException e) {
            Log.aviso("Corretora indisponível ao consultar relatórios: " + e.getClass().getSimpleName());
            return 0;                                   // nada muda; tenta no próximo ciclo
        }
        List<String> confirmados = new ArrayList<>();
        for (RelatorioExecucao r : relatorios) {
            try { aplicarRelatorio(r); confirmados.add(r.idExecucao()); }
            catch (RespostaInvalidaException e) { registrarAnomalia(r, e.getMessage()); confirmados.add(r.idExecucao()); }
            catch (RuntimeException e) { Log.erro("Falha ao aplicar relatório " + r.idExecucao() + " (será reentregue)", e); }
        }
        if (!confirmados.isEmpty()) {
            try { corretora.confirmarRelatorios(confirmados); }
            catch (IntegracaoException e) { Log.aviso("Confirmação falhou; reentrega será ignorada por idempotência"); }
        }
        return confirmados.size();
    }

    /** Idempotente: o mesmo relatório aplicado duas vezes tem o efeito de uma. */
    public void aplicarRelatorio(RelatorioExecucao r) {
        Retentativa.comConflito(3, () -> tx.executar(() -> { aplicarRelatorioNaTransacao(r); return null; }));
    }

    private void aplicarRelatorioNaTransacao(RelatorioExecucao r) {
        Instant agora = clock.instant();
        Ordem o = ordens.buscarPorId(r.ordemId()).orElse(null);
        if (o == null) {
            auditoria.registrar(TipoEvento.ANOMALIA_EXECUCAO, null, "ORDEM", r.ordemId().toString(), "relatório para ordem desconhecida");
            return;
        }
        if (r.tipo() == RelatorioExecucao.Tipo.REJEICAO) {
            if (!o.status().terminal()) rejeitar(o, CodigoRegra.REJEITADA_PELA_CORRETORA, r.motivo());
            return;
        }
        if (!execucoes.registrarSeNova(new Execucao(r.idExecucao(), o.id(), r.quantidade(), r.preco(), r.em()))) {
            auditoria.registrar(TipoEvento.EXECUCAO_DUPLICADA_IGNORADA, o.investidorId(), "ORDEM", o.id().toString(), "execução " + r.idExecucao());
            return;
        }
        if (o.status().terminal()) {
            auditoria.registrar(TipoEvento.ANOMALIA_EXECUCAO, o.investidorId(), "ORDEM", o.id().toString(),
                    "execução recebida com ordem em estado " + o.status() + " (conciliação manual)");
            return;
        }
        if (o.status() == StatusOrdem.VALIDADA) {       // relatório chegou antes da confirmação de envio
            o.marcarEnviada(agora);
            auditoria.registrar(TipoEvento.ORDEM_ENVIADA, o.investidorId(), "ORDEM", o.id().toString(), "confirmada implicitamente por execução");
        }
        Conta conta = conta(o.investidorId());
        Posicao pos = posicao(o.investidorId(), o.ticker());
        o.aplicarExecucao(r.quantidade(), r.preco(), agora, conta, pos);
        contas.atualizar(conta);
        posicoes.salvar(pos);
        ordens.atualizar(o);
        boolean total = o.status() == StatusOrdem.EXECUTADA;
        String det = r.quantidade() + " @ " + r.preco() + " (acumulado " + o.quantidadeExecutada() + "/" + o.quantidade() + ")";
        auditoria.registrar(total ? TipoEvento.ORDEM_EXECUTADA : TipoEvento.ORDEM_EXECUTADA_PARCIAL,
                o.investidorId(), "ORDEM", o.id().toString(), det);
        notificacoes.criar(o.investidorId(), total ? TipoNotificacao.EXECUCAO : TipoNotificacao.EXECUCAO_PARCIAL,
                (total ? "Ordem executada: " : "Ordem parcialmente executada: ") + o.lado() + " " + o.ticker() + " " + det);
    }

    private void registrarAnomalia(RelatorioExecucao r, String motivo) {
        tx.executarSemRetorno(() -> auditoria.registrar(TipoEvento.ANOMALIA_EXECUCAO, null, "ORDEM", r.ordemId().toString(),
                "relatório descartado (dado inválido): " + motivo));
    }

    // ---------------------------------------------------------------- comum
    /** Rejeita a ordem, devolve a reserva, audita e notifica. Deve rodar dentro de transação. */
    private void rejeitar(Ordem o, CodigoRegra motivo, String detalhe) {
        Conta conta = conta(o.investidorId());
        Posicao pos = posicao(o.investidorId(), o.ticker());
        o.rejeitar(motivo, clock.instant(), conta, pos);
        if (o.lado() == Lado.COMPRA) contas.atualizar(conta); else posicoes.salvar(pos);
        ordens.atualizar(o);
        auditoria.registrar(TipoEvento.ORDEM_REJEITADA, o.investidorId(), "ORDEM", o.id().toString(), motivo + ": " + detalhe);
        notificacoes.criar(o.investidorId(), motivo == CodigoRegra.FALHA_INTEGRACAO ? TipoNotificacao.FALHA : TipoNotificacao.REJEICAO,
                "Ordem " + o.lado() + " " + o.ticker() + " rejeitada: " + motivo.descricao());
    }

    private Conta conta(UUID investidorId) {
        return contas.buscarPorInvestidor(investidorId).orElseThrow(() -> new IllegalStateException("Conta inexistente"));
    }
    private Posicao posicao(UUID investidorId, Ticker t) {
        return posicoes.buscar(investidorId, t).orElseGet(() -> Posicao.vazia(investidorId, t));
    }
}
