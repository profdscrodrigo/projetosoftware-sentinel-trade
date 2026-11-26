package br.orion.sentineltrade.infraestrutura.integracao;

import br.orion.sentineltrade.aplicacao.porta.*;
import br.orion.sentineltrade.dominio.Cotacao;
import br.orion.sentineltrade.dominio.Excecoes.IntegracaoIndisponivelException;
import br.orion.sentineltrade.dominio.Lado;
import java.time.Clock;
import java.util.*;

/**
 * Bolsa/Corretora simulada. Aceita ordens (idempotente por idCliente), executa-as contra a cotação
 * (compra se cotação ≤ limite; venda se ≥ limite), em lotes (execuções parciais), e entrega relatórios
 * "pelo menos uma vez" até serem confirmados. Possui chaves para simular indisponibilidade, falhas
 * transitórias, lentidão e entrega duplicada.
 */
public final class CorretoraSimulada implements CorretoraGateway {
    private static final class OrdemCorretora {
        final OrdemParaEnvio ordem;
        int executado;
        boolean cancelada;
        OrdemCorretora(OrdemParaEnvio o) { this.ordem = o; }
    }

    private final Clock clock;
    private final ProvedorCotacoes mercado;
    private final Map<UUID, OrdemCorretora> livro = new LinkedHashMap<>();
    private final Map<UUID, AckCorretora> acks = new HashMap<>();
    private final Map<String, RelatorioExecucao> naoConfirmados = new LinkedHashMap<>();
    private final String prefixoExecucao = UUID.randomUUID().toString();
    private int sequencia;

    private volatile boolean indisponivel;
    private volatile int falhasRestantes;
    private volatile long latenciaMs;
    private volatile int loteMaximo = Integer.MAX_VALUE;
    private volatile boolean duplicarRelatorios;

    public CorretoraSimulada(Clock clock, ProvedorCotacoes mercado) { this.clock = clock; this.mercado = mercado; }

    // ---- chaves de simulação
    public void setIndisponivel(boolean v) { this.indisponivel = v; }
    public void falharProximas(int n) { this.falhasRestantes = n; }
    public void setLatenciaMs(long ms) { this.latenciaMs = ms; }
    public void setLoteMaximo(int lote) { this.loteMaximo = lote; }
    public void setDuplicarRelatorios(boolean v) { this.duplicarRelatorios = v; }
    /** Quantas ordens DISTINTAS a corretora possui (prova de que reenvios não duplicam). */
    public synchronized int ordensDistintas() { return livro.size(); }

    private void simularFalhas() {
        if (latenciaMs > 0) {
            try { Thread.sleep(latenciaMs); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
        if (indisponivel) throw new IntegracaoIndisponivelException("Corretora simulada indisponível");
        if (falhasRestantes > 0) { falhasRestantes--; throw new IntegracaoIndisponivelException("Falha transitória da corretora simulada"); }
    }

    @Override public synchronized AckCorretora enviar(OrdemParaEnvio o) {
        simularFalhas();
        AckCorretora existente = acks.get(o.idCliente());
        if (existente != null) return existente;                         // idempotência: não cria segunda ordem
        AckCorretora ack;
        if (o.quantidade() <= 0 || !o.precoLimite().positivo()) ack = new AckCorretora(false, "parâmetros inválidos");
        else if (mercado.obter(o.ticker()).isEmpty()) ack = new AckCorretora(false, "ativo sem negociação");
        else { livro.put(o.idCliente(), new OrdemCorretora(o)); ack = new AckCorretora(true, null); }
        acks.put(o.idCliente(), ack);
        return ack;
    }

    @Override public synchronized boolean cancelar(UUID idCliente) {
        simularFalhas();
        OrdemCorretora oc = livro.get(idCliente);
        if (oc == null) return false;
        if (oc.executado >= oc.ordem.quantidade() && !oc.cancelada) return false;   // já totalmente executada
        oc.cancelada = true;
        return true;
    }

    @Override public synchronized List<RelatorioExecucao> consultarRelatorios() {
        simularFalhas();
        for (OrdemCorretora oc : livro.values()) {
            if (oc.cancelada || oc.executado >= oc.ordem.quantidade()) continue;
            Optional<Cotacao> cot = mercado.obter(oc.ordem.ticker());
            if (cot.isEmpty()) continue;
            var preco = cot.get().preco();
            boolean cruza = oc.ordem.lado() == Lado.COMPRA ? preco.compareTo(oc.ordem.precoLimite()) <= 0
                                                           : preco.compareTo(oc.ordem.precoLimite()) >= 0;
            if (!cruza) continue;                                         // ordem "repousa" no livro
            int q = Math.min(oc.ordem.quantidade() - oc.executado, loteMaximo);
            oc.executado += q;
            String id = prefixoExecucao + "-EXE-" + (++sequencia);
            naoConfirmados.put(id, new RelatorioExecucao(id, oc.ordem.idCliente(), RelatorioExecucao.Tipo.EXECUCAO, q, preco, null, clock.instant()));
        }
        List<RelatorioExecucao> saida = new ArrayList<>(naoConfirmados.values());
        if (duplicarRelatorios) saida.addAll(new ArrayList<>(naoConfirmados.values()));
        return saida;
    }

    @Override public synchronized void confirmarRelatorios(Collection<String> ids) {
        simularFalhas();
        ids.forEach(naoConfirmados::remove);
    }
}
