package br.orion.sentineltrade.aplicacao.servico;

import br.orion.sentineltrade.aplicacao.porta.*;
import br.orion.sentineltrade.aplicacao.porta.Repositorios.*;
import br.orion.sentineltrade.aplicacao.servico.Validacoes.OrdemValidada;
import br.orion.sentineltrade.dominio.*;
import br.orion.sentineltrade.dominio.Excecoes.*;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Casos de uso do investidor sobre ordens: enviar compra/venda, cancelar, consultar status e histórico.
 *
 * Garantias: validação no servidor; idempotência por chave (reenvio não duplica); reserva atômica de
 * saldo/posição; auditoria na mesma transação; o investidor só enxerga as próprias ordens.
 */
public final class OrdemService {
    public record ResultadoEnvio(Ordem ordem, boolean duplicada) {}

    private final AutenticacaoService auth;
    private final GerenciadorTransacao tx;
    private final OrdemRepositorio ordens;
    private final ContaRepositorio contas;
    private final PosicaoRepositorio posicoes;
    private final LimiteRiscoRepositorio limites;
    private final ExecucaoRepositorio execucoes;
    private final FilaOrdens fila;
    private final ValidadorOrdem validador;
    private final AuditoriaService auditoria;
    private final NotificacaoService notificacoes;
    private final CorretoraGateway corretora;
    private final ProcessadorOrdens processador;
    private final LimitadorDeTaxa limitador;
    private final ParametrosSistema params;
    private final Clock clock;
    private final ZoneId zona;

    public OrdemService(AutenticacaoService auth, GerenciadorTransacao tx, OrdemRepositorio ordens, ContaRepositorio contas,
                        PosicaoRepositorio posicoes, LimiteRiscoRepositorio limites, ExecucaoRepositorio execucoes,
                        FilaOrdens fila, ValidadorOrdem validador, AuditoriaService auditoria, NotificacaoService notificacoes,
                        CorretoraGateway corretora, ProcessadorOrdens processador, LimitadorDeTaxa limitador,
                        ParametrosSistema params, Clock clock, ZoneId zona) {
        this.auth = auth; this.tx = tx; this.ordens = ordens; this.contas = contas; this.posicoes = posicoes;
        this.limites = limites; this.execucoes = execucoes; this.fila = fila; this.validador = validador;
        this.auditoria = auditoria; this.notificacoes = notificacoes; this.corretora = corretora;
        this.processador = processador; this.limitador = limitador; this.params = params; this.clock = clock; this.zona = zona;
    }

    // ------------------------------------------------------------------ enviar
    public ResultadoEnvio enviarOrdem(String token, SolicitacaoOrdem solicitacao) {
        Sessao sessao = auth.exigir(token, Papel.INVESTIDOR);
        UUID inv = sessao.usuarioId();
        limitador.registrar("ordem:" + inv, params.inteiro(ParametrosSistema.ORDEM_MAX_POR_MINUTO), Duration.ofMinutes(1));

        OrdemValidada validada = Validacoes.ordem(solicitacao);                // 1) allowlist, no servidor
        String impressao = validada.impressaoDigital();                        //    (ordem a mercado: independe do preço de proteção)

        Optional<Ordem> existente = ordens.buscarPorChave(inv, validada.chave());   // 2) idempotência (leitura rápida)
        if (existente.isPresent()) return duplicada(inv, existente.get(), impressao);

        LimiteRisco limite = limites.buscar(inv).orElseThrow(() -> new RegraNegocioException(CodigoRegra.LIMITE_NAO_CONFIGURADO));
        if (validada.tipo() == TipoOrdem.MERCADO)                              // preço de proteção = cotação ± faixa
            validada = validada.comPreco(validador.precoDeProtecao(validada.ticker(), validada.lado()));
        final OrdemValidada p = validada;
        exigirConfirmacaoSeAltoValor(p, limite);

        Instant agora = clock.instant();
        RegraNegocioException falhaMercado = null;                            // 3) validações externas FORA da transação
        try { validador.validarMercado(p.ticker(), p.preco(), agora); }
        catch (RegraNegocioException e) { falhaMercado = e; }
        final RegraNegocioException falha = falhaMercado;

        return Retentativa.comConflito(3, () -> tx.executar(() -> registrar(inv, p, impressao, falha, null)));
    }

    private void exigirConfirmacaoSeAltoValor(OrdemValidada p, LimiteRisco limite) {
        Dinheiro valor = p.preco().vezes(p.quantidade());
        if (!p.confirmada() && valor.compareTo(limite.valorConfirmacao()) > 0 && valor.compareTo(limite.valorMaximoPorOrdem()) <= 0)
            throw new ConfirmacaoNecessariaException("Ordem de " + valor.formatado() + " exige confirmação adicional (limite sem confirmação: "
                    + limite.valorConfirmacao().formatado() + ")");               // «extend»
    }

    private ResultadoEnvio registrar(UUID inv, OrdemValidada p, String impressao, RegraNegocioException falhaMercado, UUID substitui) {
        Optional<Ordem> dup = ordens.buscarPorChave(inv, p.chave());
        if (dup.isPresent()) return duplicada(inv, dup.get(), impressao);

        Instant agora = clock.instant();
        Conta conta = contas.buscarPorInvestidor(inv).orElseThrow(() -> new IllegalStateException("Conta inexistente"));
        Posicao pos = posicoes.buscar(inv, p.ticker()).orElseGet(() -> Posicao.vazia(inv, p.ticker()));
        LimiteRisco limite = limites.buscar(inv).orElseThrow(() -> new RegraNegocioException(CodigoRegra.LIMITE_NAO_CONFIGURADO));

        Ordem o = Ordem.nova(p.lado(), p.tipo(), inv, p.ticker(), p.quantidade(), p.preco(), p.chave(), impressao, agora, substitui);
        try { ordens.inserir(o); }
        catch (ChaveDuplicadaException e) { return duplicada(inv, ordens.buscarPorChave(inv, p.chave()).orElseThrow(), impressao); }
        auditoria.registrar(TipoEvento.ORDEM_CRIADA, inv, "ORDEM", o.id().toString(), descricao(o));

        try {
            if (falhaMercado != null) throw falhaMercado;
            Dinheiro negociado = ordens.valorNegociadoNoPeriodo(inv, inicioDoDia(agora), fimDoDia(agora));
            validador.validarRisco(o, limite, negociado);
            o.reservarEValidar(conta, pos, agora);                              // saldo/posição: tudo ou nada
            if (o.lado() == Lado.COMPRA) contas.atualizar(conta); else posicoes.salvar(pos);
            ordens.atualizar(o);
            fila.publicar(new MensagemFila(UUID.randomUUID(), o.id(), 0, agora));
            auditoria.registrar(TipoEvento.ORDEM_VALIDADA, inv, "ORDEM", o.id().toString(), "reserva efetuada; enfileirada para a corretora");
        } catch (RegraNegocioException e) {
            o.rejeitar(e.codigo(), agora, conta, pos);                          // CRIADA → REJEITADA (sem reserva a devolver)
            ordens.atualizar(o);
            auditoria.registrar(TipoEvento.ORDEM_REJEITADA, inv, "ORDEM", o.id().toString(), e.getMessage());
            notificacoes.criar(inv, TipoNotificacao.REJEICAO, "Ordem " + o.lado() + " " + o.ticker() + " rejeitada: " + e.getMessage());
        }
        return new ResultadoEnvio(o, false);
    }

    private Instant inicioDoDia(Instant agora) { return LocalDate.ofInstant(agora, zona).atStartOfDay(zona).toInstant(); }
    private Instant fimDoDia(Instant agora) { return LocalDate.ofInstant(agora, zona).plusDays(1).atStartOfDay(zona).toInstant(); }

    private ResultadoEnvio duplicada(UUID inv, Ordem existente, String impressao) {
        if (!existente.impressaoDigital().equals(impressao))
            throw new ConflitoException("Chave de idempotência já utilizada em outra ordem com conteúdo diferente");
        tx.executarSemRetorno(() -> auditoria.registrar(TipoEvento.ORDEM_DUPLICADA_IGNORADA, inv, "ORDEM",
                existente.id().toString(), "reenvio com a mesma chave; nenhuma nova ordem criada"));
        return new ResultadoEnvio(existente, true);
    }

    // ------------------------------------------------------------------ cancelar
    public Ordem cancelarOrdem(String token, String idOrdem) {
        Sessao sessao = auth.exigir(token, Papel.INVESTIDOR);
        UUID id = Validacoes.uuid(idOrdem);
        Ordem o = doInvestidor(sessao.usuarioId(), id);
        if (o.status().terminal() && o.status() != StatusOrdem.CANCELADA)
            throw new RegraNegocioException(CodigoRegra.ORDEM_NAO_CANCELAVEL, "estado " + o.status());

        if (o.status() == StatusOrdem.ENVIADA || o.status() == StatusOrdem.PARCIALMENTE_EXECUTADA) {
            boolean aceito;
            try { aceito = corretora.cancelar(o.id()); }
            catch (IntegracaoException e) {
                throw new IntegracaoIndisponivelException("Corretora indisponível: a ordem NÃO foi cancelada. Tente novamente em instantes.", e);
            }
            processador.coletarExecucoes();                                     // aplica execuções em trânsito antes de finalizar
            if (!aceito) throw new RegraNegocioException(CodigoRegra.ORDEM_NAO_CANCELAVEL, "a corretora informou que a ordem já foi executada");
        }
        return Retentativa.comConflito(3, () -> tx.executar(() -> finalizarCancelamento(sessao.usuarioId(), id)));
    }

    private Ordem finalizarCancelamento(UUID inv, UUID id) {
        Ordem o = doInvestidor(inv, id);
        if (o.status() == StatusOrdem.CANCELADA) return o;                      // cancelamento idempotente
        Conta conta = contas.buscarPorInvestidor(inv).orElseThrow(() -> new IllegalStateException("Conta inexistente"));
        Posicao pos = posicoes.buscar(inv, o.ticker()).orElseGet(() -> Posicao.vazia(inv, o.ticker()));
        o.cancelar(clock.instant(), conta, pos);                                // lança ORDEM_NAO_CANCELAVEL se terminal
        if (o.lado() == Lado.COMPRA) contas.atualizar(conta); else posicoes.salvar(pos);
        ordens.atualizar(o);
        auditoria.registrar(TipoEvento.ORDEM_CANCELADA, inv, "ORDEM", o.id().toString(),
                "cancelada pelo investidor; executado " + o.quantidadeExecutada() + "/" + o.quantidade());
        notificacoes.criar(inv, TipoNotificacao.CANCELAMENTO, "Ordem " + o.lado() + " " + o.ticker() + " cancelada");
        return o;
    }

    // ------------------------------------------------------------------ alterar (cancelar e substituir)
    public record ResultadoAlteracao(Ordem substituida, Ordem nova, boolean duplicada) {}

    /**
     * Altera quantidade e/ou preço de uma ordem VALIDADA ou ENVIADA que ainda não teve nenhuma execução.
     * Como nas bolsas, a alteração é "cancelar e substituir": a ordem original é cancelada (devolvendo a reserva) e nasce uma
     * NOVA ordem, com id próprio, ligada à original. Isso evita que execuções em trânsito da ordem antiga se misturem aos novos
     * parâmetros. Tudo que pode recusar a alteração é verificado ANTES de cancelar: se algo falhar, a ordem original fica intacta.
     * Em ordem a mercado, informe só a quantidade (o preço de proteção é recalculado). Preço em branco mantém o limite atual.
     */
    public ResultadoAlteracao alterarOrdem(String token, String idOrdem, String quantidade, String preco, String chaveNova, boolean confirmada) {
        Sessao sessao = auth.exigir(token, Papel.INVESTIDOR);
        UUID inv = sessao.usuarioId();
        limitador.registrar("ordem:" + inv, params.inteiro(ParametrosSistema.ORDEM_MAX_POR_MINUTO), Duration.ofMinutes(1));
        UUID id = Validacoes.uuid(idOrdem);
        String chave = Validacoes.chaveIdempotencia(chaveNova);
        Ordem atual = doInvestidor(inv, id);

        Optional<Ordem> ja = ordens.buscarPorChave(inv, chave);                // reenvio da mesma alteração
        if (ja.isPresent()) {
            if (id.equals(ja.get().substituiOrdemId())) return new ResultadoAlteracao(atual, ja.get(), true);
            throw new ConflitoException("Chave de idempotência já utilizada em outra ordem");
        }
        if (!atual.alteravel())
            throw new RegraNegocioException(CodigoRegra.ORDEM_NAO_ALTERAVEL, "estado " + atual.status()
                    + (atual.quantidadeExecutada() > 0 ? ", com execuções" : ""));

        int qtd = Validacoes.quantidade(quantidade);
        boolean semPreco = preco == null || preco.isBlank();
        Dinheiro novoPreco;
        if (atual.tipo() == TipoOrdem.MERCADO) {
            if (!semPreco) throw new ValidacaoException("Ordem a mercado não aceita preço");
            novoPreco = validador.precoDeProtecao(atual.ticker(), atual.lado());
        } else {
            novoPreco = semPreco ? atual.precoLimite() : Validacoes.preco(preco);
            if (qtd == atual.quantidade() && novoPreco.equals(atual.precoLimite())) throw new ValidacaoException("Nenhuma alteração informada");
        }
        OrdemValidada nova = new OrdemValidada(atual.lado(), atual.tipo(), atual.ticker(), qtd, novoPreco, chave, confirmada);
        String impressao = (atual.tipo() == TipoOrdem.MERCADO ? nova.comPreco(null) : nova).impressaoDigital();

        LimiteRisco limite = limites.buscar(inv).orElseThrow(() -> new RegraNegocioException(CodigoRegra.LIMITE_NAO_CONFIGURADO));
        exigirConfirmacaoSeAltoValor(nova, limite);
        Instant agora = clock.instant();
        validador.validarMercado(atual.ticker(), novoPreco, agora);            // recusa sem efeito colateral
        preChecar(inv, atual, nova, limite, agora);                            // limites, saldo e posição (somente leitura)

        if (atual.status() == StatusOrdem.ENVIADA) {                           // já está na corretora: cancela lá primeiro
            boolean aceito;
            try { aceito = corretora.cancelar(atual.id()); }
            catch (IntegracaoException e) {
                throw new IntegracaoIndisponivelException("Corretora indisponível: a ordem NÃO foi alterada. Tente novamente em instantes.", e);
            }
            processador.coletarExecucoes();                                     // aplica execuções em trânsito
            if (!aceito) throw new RegraNegocioException(CodigoRegra.ORDEM_NAO_ALTERAVEL, "a corretora informou que a ordem já foi executada");
        }
        return Retentativa.comConflito(3, () -> tx.executar(() -> substituir(inv, id, nova, impressao)));
    }

    private void preChecar(UUID inv, Ordem atual, OrdemValidada nova, LimiteRisco limite, Instant agora) {
        Dinheiro novoValor = nova.preco().vezes(nova.quantidade());
        if (novoValor.compareTo(limite.valorMaximoPorOrdem()) > 0)
            throw new RegraNegocioException(CodigoRegra.LIMITE_POR_ORDEM_EXCEDIDO, "limite " + limite.valorMaximoPorOrdem());
        Dinheiro negociado = ordens.valorNegociadoNoPeriodo(inv, inicioDoDia(agora), fimDoDia(agora));
        if (!atual.criadaEm().isBefore(inicioDoDia(agora)) && atual.criadaEm().isBefore(fimDoDia(agora)))
            negociado = negociado.menos(atual.valorTotal());                    // a ordem antiga deixará de consumir o limite
        if (negociado.mais(novoValor).compareTo(limite.limiteDiario()) > 0)
            throw new RegraNegocioException(CodigoRegra.LIMITE_DIARIO_EXCEDIDO, "limite diário " + limite.limiteDiario());
        if (atual.lado() == Lado.COMPRA) {
            Conta c = contas.buscarPorInvestidor(inv).orElseThrow(() -> new IllegalStateException("Conta inexistente"));
            if (c.saldoDisponivel().mais(atual.valorTotal()).compareTo(novoValor) < 0) throw new RegraNegocioException(CodigoRegra.SALDO_INSUFICIENTE);
        } else {
            Posicao p = posicoes.buscar(inv, atual.ticker()).orElseGet(() -> Posicao.vazia(inv, atual.ticker()));
            if (p.quantidadeDisponivel() + atual.quantidade() < nova.quantidade()) throw new RegraNegocioException(CodigoRegra.POSICAO_INSUFICIENTE);
        }
    }

    private ResultadoAlteracao substituir(UUID inv, UUID idAntiga, OrdemValidada nova, String impressao) {
        Ordem velha = doInvestidor(inv, idAntiga);
        if (!velha.alteravel())
            throw new RegraNegocioException(CodigoRegra.ORDEM_NAO_ALTERAVEL, "a ordem mudou de estado durante a alteração");
        Instant agora = clock.instant();
        Conta conta = contas.buscarPorInvestidor(inv).orElseThrow(() -> new IllegalStateException("Conta inexistente"));
        Posicao pos = posicoes.buscar(inv, velha.ticker()).orElseGet(() -> Posicao.vazia(inv, velha.ticker()));
        velha.cancelar(agora, conta, pos);                                      // devolve a reserva da ordem antiga
        if (velha.lado() == Lado.COMPRA) contas.atualizar(conta); else posicoes.salvar(pos);
        ordens.atualizar(velha);
        auditoria.registrar(TipoEvento.ORDEM_CANCELADA, inv, "ORDEM", velha.id().toString(), "cancelada para substituição");
        ResultadoEnvio r = registrar(inv, nova, impressao, null, velha.id());   // valida e reserva a nova, na mesma transação
        auditoria.registrar(TipoEvento.ORDEM_ALTERADA, inv, "ORDEM", r.ordem().id().toString(),
                "substitui " + velha.id() + ": " + descricao(velha) + " => " + descricao(r.ordem()));
        notificacoes.criar(inv, TipoNotificacao.ALTERACAO, "Ordem " + velha.lado() + " " + velha.ticker() + " alterada; nova ordem "
                + r.ordem().status());
        return new ResultadoAlteracao(velha, r.ordem(), false);
    }

    // ------------------------------------------------------------------ consultas
    public Ordem consultarOrdem(String token, String idOrdem) {
        Sessao s = auth.exigir(token, Papel.INVESTIDOR);
        return doInvestidor(s.usuarioId(), Validacoes.uuid(idOrdem));
    }

    /** Histórico de operações do próprio investidor (mais recentes primeiro). */
    public List<Ordem> historico(String token) {
        return ordens.listarPorInvestidor(auth.exigir(token, Papel.INVESTIDOR).usuarioId());
    }

    public List<Execucao> execucoesDaOrdem(String token, String idOrdem) {
        Sessao s = auth.exigir(token, Papel.INVESTIDOR);
        Ordem o = doInvestidor(s.usuarioId(), Validacoes.uuid(idOrdem));
        return execucoes.listarPorOrdem(o.id());
    }

    /** Ordem de outro investidor é tratada como inexistente (não revela que o id existe). */
    private Ordem doInvestidor(UUID investidorId, UUID ordemId) {
        return ordens.buscarPorId(ordemId).filter(o -> o.investidorId().equals(investidorId))
                .orElseThrow(() -> new NaoEncontradoException("Ordem não encontrada"));
    }

    private static String descricao(Ordem o) {
        return o.lado() + " " + o.tipo() + " " + o.ticker() + " " + o.quantidade() + " @ " + o.precoLimite();
    }
}
