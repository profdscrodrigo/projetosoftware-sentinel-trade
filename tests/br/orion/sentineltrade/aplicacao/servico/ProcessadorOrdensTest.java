package br.orion.sentineltrade.aplicacao.servico;

import static org.junit.jupiter.api.Assertions.*;

import br.orion.sentineltrade.Cenario;
import br.orion.sentineltrade.aplicacao.porta.OrdemParaEnvio;
import br.orion.sentineltrade.aplicacao.porta.RelatorioExecucao;
import br.orion.sentineltrade.dominio.*;
import br.orion.sentineltrade.dominio.Excecoes.RespostaInvalidaException;
import br.orion.sentineltrade.infraestrutura.integracao.CorretoraResiliente;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/** Motor assíncrono: transmissão, execuções, liquidação, retentativa e recuperação de falhas. */
class ProcessadorOrdensTest {
    private final Cenario c = new Cenario();

    private Dinheiro disponivel(String t) { return c.app.carteira.consultarSaldo(t).disponivel(); }
    private Dinheiro bloqueado(String t) { return c.app.carteira.consultarSaldo(t).bloqueado(); }
    private Ordem ordem(String t, Ordem o) { return c.app.ordens.consultarOrdem(t, o.id().toString()); }
    private long eventos(TipoEvento tipo) {
        return c.app.persistencia.auditoria().listar(0, 10_000).stream().filter(e -> e.tipo().equals(tipo.name())).count();
    }

    @Test void cicloCompletoDaCompraAteAExecucaoELiquidacao() {          // RF09 / RF17
        String t = c.ana();
        Ordem o = c.app.ordens.enviarOrdem(t, Cenario.compra("PETR4", 100, "38.50")).ordem();
        c.app.processador.ciclo();
        assertEquals(StatusOrdem.EXECUTADA, ordem(t, o).status());
        assertEquals(Dinheiro.de("96150"), disponivel(t));
        assertEquals(Dinheiro.ZERO, bloqueado(t));
        var item = c.app.carteira.consultarCarteira(t).itens().stream().filter(i -> i.ticker().codigo().equals("PETR4")).findFirst().orElseThrow();
        assertEquals(100, item.quantidade());
        assertEquals(Dinheiro.de("38.50"), item.precoMedio());
        assertTrue(c.app.enviador.enviadas().stream().anyMatch(n -> n.tipo() == TipoNotificacao.EXECUCAO && n.investidorId().equals(c.anaId())),
                "investidor notificado da execução");                    // RF11
        assertEquals(1, c.app.ordens.execucoesDaOrdem(t, o.id().toString()).size());
    }

    @Test void precoMelhorQueOLimiteDevolveADiferenca() {
        c.app.cotacoesSimuladas.definir("PETR4", "38.00", "38.10");
        String t = c.ana();
        Ordem o = c.app.ordens.enviarOrdem(t, Cenario.compra("PETR4", 100, "38.50")).ordem();
        c.app.processador.ciclo();
        assertEquals(StatusOrdem.EXECUTADA, ordem(t, o).status());
        assertEquals(Dinheiro.de("96200"), disponivel(t));                // gastou 3.800, não 3.850
        assertEquals(Dinheiro.ZERO, bloqueado(t));
    }

    @Test void vendaExecutadaCreditaSaldoEBaixaAPosicao() {
        String t = c.ana();
        Ordem o = c.app.ordens.enviarOrdem(t, Cenario.venda("VALE3", 100, "62.40")).ordem();
        c.app.processador.ciclo();
        assertEquals(StatusOrdem.EXECUTADA, ordem(t, o).status());
        assertEquals(Dinheiro.de("106240"), disponivel(t));
        var item = c.app.carteira.consultarCarteira(t).itens().get(0);
        assertEquals(100, item.quantidade());
        assertEquals(0, item.quantidadeBloqueada());
        assertEquals(Dinheiro.de("60.00"), item.precoMedio());            // custo médio preservado: 12.000 / 200
    }

    @Test void execucoesParciaisAteCompletarAOrdem() {
        c.app.corretoraSimulada.setLoteMaximo(40);
        String t = c.ana();
        Ordem o = c.app.ordens.enviarOrdem(t, Cenario.compra("PETR4", 100, "38.50")).ordem();
        c.app.processador.ciclo();
        assertEquals(StatusOrdem.PARCIALMENTE_EXECUTADA, ordem(t, o).status());
        assertEquals(40, ordem(t, o).quantidadeExecutada());
        c.app.processador.ciclo();
        assertEquals(80, ordem(t, o).quantidadeExecutada());
        c.app.processador.ciclo();
        assertEquals(StatusOrdem.EXECUTADA, ordem(t, o).status());
        assertEquals(Dinheiro.de("96150"), disponivel(t));
        assertEquals(3, c.app.ordens.execucoesDaOrdem(t, o.id().toString()).size());
        assertEquals(2, eventos(TipoEvento.ORDEM_EXECUTADA_PARCIAL));
    }

    @Test void ordemQueNaoCruzaCotacaoFicaEnviadaEExecutaQuandoOPrecoChega() {
        String t = c.ana();
        Ordem o = c.app.ordens.enviarOrdem(t, Cenario.compra("PETR4", 100, "36.00")).ordem();
        c.app.processador.ciclo();
        assertEquals(StatusOrdem.ENVIADA, ordem(t, o).status());
        c.app.cotacoesSimuladas.definir("PETR4", "35.50", "38.10");
        c.app.processador.ciclo();
        assertEquals(StatusOrdem.EXECUTADA, ordem(t, o).status());
        assertEquals(Dinheiro.de("96450"), disponivel(t));                // 3.550 gastos; 50 devolvidos
    }

    // ------------------------------------------------------------------ idempotência no consumo
    @Test void relatorioDuplicadoDaCorretoraNaoLiquidaDuasVezes() {      // entrega "pelo menos uma vez"
        c.app.corretoraSimulada.setDuplicarRelatorios(true);
        String t = c.ana();
        Ordem o = c.app.ordens.enviarOrdem(t, Cenario.compra("PETR4", 100, "38.50")).ordem();
        c.app.processador.ciclo();
        c.app.processador.ciclo();
        assertEquals(StatusOrdem.EXECUTADA, ordem(t, o).status());
        assertEquals(Dinheiro.de("96150"), disponivel(t));
        assertEquals(1, c.app.ordens.execucoesDaOrdem(t, o.id().toString()).size());
        assertTrue(eventos(TipoEvento.EXECUCAO_DUPLICADA_IGNORADA) >= 1);
        assertEquals(100, c.app.carteira.consultarCarteira(t).itens().stream().filter(i -> i.ticker().codigo().equals("PETR4")).findFirst().orElseThrow().quantidade());
    }

    @Test void reenvioParaACorretoraNaoCriaSegundaOrdemLa() {             // falha entre o ack e o commit
        String t = c.ana();
        Ordem o = c.app.ordens.enviarOrdem(t, Cenario.compra("PETR4", 100, "36.00")).ordem();
        c.app.corretora.enviar(new OrdemParaEnvio(o.id(), o.ticker(), o.lado(), o.quantidade(), o.precoLimite()));   // "queda" após o ack
        c.app.processador.ciclo();                                         // a fila reenvia
        assertEquals(1, c.app.corretoraSimulada.ordensDistintas());
        assertEquals(StatusOrdem.ENVIADA, ordem(t, o).status());
    }

    // ------------------------------------------------------------------ falhas da corretora
    @Test void corretoraIndisponivelNaoApagaAOrdemNemMexeNaCarteiraAteRejeitarPorFalha() {   // RN07
        c.app.corretoraSimulada.setIndisponivel(true);
        String t = c.ana();
        Ordem o = c.app.ordens.enviarOrdem(t, Cenario.compra("PETR4", 100, "38.50")).ordem();

        for (int tentativa = 1; tentativa <= 3; tentativa++) {
            c.app.processador.ciclo();
            assertEquals(StatusOrdem.VALIDADA, ordem(t, o).status(), "continua na fila, intacta");
            assertEquals(Dinheiro.de("3850"), bloqueado(t));
            assertEquals(0, c.app.carteira.consultarCarteira(t).itens().stream().filter(it -> it.ticker().codigo().equals("PETR4")).count(),
                    "carteira inalterada: nenhuma posição em PETR4 foi criada");
            c.relogio.avancar(Duration.ofSeconds(61));
        }
        assertEquals(CorretoraResiliente.Estado.ABERTO, c.app.corretoraResiliente.estado(), "circuit breaker abriu");
        c.app.processador.ciclo(); c.relogio.avancar(Duration.ofSeconds(61));
        c.app.processador.ciclo();

        Ordem final_ = ordem(t, o);
        assertEquals(StatusOrdem.REJEITADA, final_.status());
        assertEquals(CodigoRegra.FALHA_INTEGRACAO, final_.motivoRejeicao());
        assertEquals(Dinheiro.de("100000"), disponivel(t), "reserva devolvida");
        assertEquals(Dinheiro.ZERO, bloqueado(t));
        assertEquals(ProcessadorOrdens.MAX_TENTATIVAS_ENVIO, eventos(TipoEvento.ENVIO_CORRETORA_FALHOU));
        assertTrue(c.app.enviador.enviadas().stream().anyMatch(n -> n.tipo() == TipoNotificacao.FALHA), "investidor notificado da falha");
    }

    @Test void corretoraQueVoltaAntesDoLimiteDeTentativasRecebeAOrdem() {
        c.app.corretoraSimulada.falharProximas(2);
        String t = c.ana();
        Ordem o = c.app.ordens.enviarOrdem(t, Cenario.compra("PETR4", 100, "38.50")).ordem();
        c.app.processador.ciclo();
        assertEquals(StatusOrdem.VALIDADA, ordem(t, o).status());
        c.relogio.avancar(Duration.ofSeconds(61));
        c.app.processador.ciclo();
        assertEquals(StatusOrdem.EXECUTADA, ordem(t, o).status());
        assertEquals(Dinheiro.de("96150"), disponivel(t));
    }

    @Test void backoffExponencialLimitado() {
        assertEquals(Duration.ofSeconds(2), ProcessadorOrdens.backoff(1));
        assertEquals(Duration.ofSeconds(4), ProcessadorOrdens.backoff(2));
        assertEquals(Duration.ofSeconds(16), ProcessadorOrdens.backoff(4));
        assertEquals(Duration.ofSeconds(60), ProcessadorOrdens.backoff(20));
    }

    // ------------------------------------------------------------------ relatórios anômalos
    @Test void relatorioDeRejeicaoTardiaDevolveAReservaEEhIdempotente() {
        String t = c.ana();
        Ordem o = c.app.ordens.enviarOrdem(t, Cenario.compra("PETR4", 100, "36.00")).ordem();
        c.app.processador.ciclo();
        RelatorioExecucao rej = new RelatorioExecucao("REJ-1", o.id(), RelatorioExecucao.Tipo.REJEICAO, 0, null, "limite da bolsa", c.relogio.instant());
        c.app.processador.aplicarRelatorio(rej);
        c.app.processador.aplicarRelatorio(rej);
        assertEquals(StatusOrdem.REJEITADA, ordem(t, o).status());
        assertEquals(CodigoRegra.REJEITADA_PELA_CORRETORA, ordem(t, o).motivoRejeicao());
        assertEquals(Dinheiro.de("100000"), disponivel(t));
        assertEquals(1, eventos(TipoEvento.ORDEM_REJEITADA));
    }

    @Test void execucaoInconsistenteComALimiteDaOrdemEhRecusadaSemAlterarSaldo() {   // dado externo não confiável
        String t = c.ana();
        Ordem o = c.app.ordens.enviarOrdem(t, Cenario.compra("PETR4", 100, "36.00")).ordem();
        c.app.processador.ciclo();
        RelatorioExecucao ruim = new RelatorioExecucao("EXE-X", o.id(), RelatorioExecucao.Tipo.EXECUCAO, 100, Dinheiro.de("99.00"), null, c.relogio.instant());
        assertThrows(RespostaInvalidaException.class, () -> c.app.processador.aplicarRelatorio(ruim));
        assertEquals(StatusOrdem.ENVIADA, ordem(t, o).status());
        assertEquals(Dinheiro.de("3600"), bloqueado(t));
        assertEquals(Dinheiro.de("96400"), disponivel(t));
    }

    @Test void execucaoParaOrdemJaCanceladaViraAnomaliaAuditadaSemMexerNoSaldo() {
        String t = c.ana();
        Ordem o = c.app.ordens.enviarOrdem(t, Cenario.compra("PETR4", 100, "36.00")).ordem();
        c.app.processador.ciclo();
        c.app.ordens.cancelarOrdem(t, o.id().toString());
        c.app.processador.aplicarRelatorio(new RelatorioExecucao("EXE-TARDIA", o.id(), RelatorioExecucao.Tipo.EXECUCAO, 100,
                Dinheiro.de("35.00"), null, c.relogio.instant()));
        assertEquals(StatusOrdem.CANCELADA, ordem(t, o).status());
        assertEquals(Dinheiro.de("100000"), disponivel(t));
        assertEquals(1, eventos(TipoEvento.ANOMALIA_EXECUCAO));
    }

    @Test void relatorioDeOrdemDesconhecidaEhAuditadoEDescartado() {
        c.app.processador.aplicarRelatorio(new RelatorioExecucao("EXE-?", java.util.UUID.randomUUID(), RelatorioExecucao.Tipo.EXECUCAO, 1,
                Dinheiro.de("1.00"), null, c.relogio.instant()));
        assertEquals(1, eventos(TipoEvento.ANOMALIA_EXECUCAO));
    }

    @Test void trilhaDeAuditoriaPermaneceIntegraAposTodoOCiclo() {
        String t = c.ana();
        c.app.ordens.enviarOrdem(t, Cenario.compra("PETR4", 100, "38.50"));
        c.app.ordens.enviarOrdem(t, Cenario.venda("VALE3", 50, "62.40"));
        c.app.processador.ciclo();
        assertTrue(c.app.auditoria.verificarIntegridade().integra());
    }
}
