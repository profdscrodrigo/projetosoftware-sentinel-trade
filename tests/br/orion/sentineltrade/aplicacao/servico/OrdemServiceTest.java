package br.orion.sentineltrade.aplicacao.servico;

import static org.junit.jupiter.api.Assertions.*;

import br.orion.sentineltrade.Cenario;
import br.orion.sentineltrade.aplicacao.servico.CarteiraService.SaldoConta;
import br.orion.sentineltrade.aplicacao.servico.OrdemService.ResultadoEnvio;
import br.orion.sentineltrade.dominio.*;
import br.orion.sentineltrade.dominio.Excecoes.*;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;

/** Casos de uso críticos: enviar compra, enviar venda e cancelar (RF05–RF09, RN01–RN10). */
class OrdemServiceTest {
    private final Cenario c = new Cenario();

    private SaldoConta saldo(String token) { return c.app.carteira.consultarSaldo(token); }
    private void definirLimites(String max, String diario, String conf) {
        c.app.risco.definirLimites(c.risco(), c.anaId().toString(), max, diario, conf);
    }

    // ------------------------------------------------------------------ compra
    @Test void compraValidaReservaSaldoEEnfileiraParaACorretora() {
        String t = c.ana();
        ResultadoEnvio r = c.app.ordens.enviarOrdem(t, Cenario.compra("PETR4", 100, "38.50"));
        assertFalse(r.duplicada());
        assertEquals(StatusOrdem.VALIDADA, r.ordem().status());
        assertEquals(Dinheiro.de("96150"), saldo(t).disponivel());
        assertEquals(Dinheiro.de("3850"), saldo(t).bloqueado());
        assertEquals(1, c.app.persistencia.fila().disponiveis(c.relogio.instant(), 10).size());
    }

    @Test void compraSemSaldoEhRejeitadaERegistrada() {                  // RN01
        definirLimites("500000", "1000000", "500000");
        String t = c.ana();
        Ordem o = c.app.ordens.enviarOrdem(t, Cenario.compra("MXRF11", 10_000, "10.35")).ordem();   // 103.500 > 100.000
        assertEquals(StatusOrdem.REJEITADA, o.status());
        assertEquals(CodigoRegra.SALDO_INSUFICIENTE, o.motivoRejeicao());
        assertEquals(Dinheiro.de("100000"), saldo(t).disponivel());
        assertEquals(Dinheiro.ZERO, saldo(t).bloqueado());
        assertEquals(0, c.app.persistencia.fila().disponiveis(c.relogio.instant(), 10).size(), "ordem rejeitada não vai à corretora");
        assertEquals(1, c.app.ordens.historico(t).size(), "a rejeição fica no histórico");
    }

    @Test void limitePorOrdemEhAplicado() {
        String t = c.ana();
        Ordem o = c.app.ordens.enviarOrdem(t, Cenario.compra("BOVA11", 500, "128.70")).ordem();      // 64.350 > 50.000
        assertEquals(CodigoRegra.LIMITE_POR_ORDEM_EXCEDIDO, o.motivoRejeicao());
        assertEquals(Dinheiro.de("100000"), saldo(t).disponivel());
    }

    @Test void limiteDiarioConsideraAsOrdensDoDia() {
        definirLimites("50000", "60000", "50000");
        String t = c.ana();
        assertEquals(StatusOrdem.VALIDADA, c.app.ordens.enviarOrdem(t, Cenario.compra("PETR4", 1000, "38.50")).ordem().status());   // 38.500
        Ordem segunda = c.app.ordens.enviarOrdem(t, Cenario.compra("PETR4", 1000, "38.50")).ordem();                                  // 77.000 > 60.000
        assertEquals(CodigoRegra.LIMITE_DIARIO_EXCEDIDO, segunda.motivoRejeicao());
    }

    @Test void ordemDeAltoValorExigeConfirmacaoAdicional() {              // «extend»
        String t = c.ana();
        SolicitacaoOrdem s = Cenario.compra("PETR4", 700, "38.50");                                   // 26.950 > 20.000
        assertThrows(ConfirmacaoNecessariaException.class, () -> c.app.ordens.enviarOrdem(t, s));
        assertEquals(0, c.app.ordens.historico(t).size(), "nada é criado sem a confirmação");
        ResultadoEnvio r = c.app.ordens.enviarOrdem(t, Cenario.confirmada(s));
        assertEquals(StatusOrdem.VALIDADA, r.ordem().status());
    }

    @Test void mercadoFechadoRejeitaAOrdem() {                           // regra de negócio: situação do mercado
        c.relogio.definir(Instant.parse("2026-11-24T22:00:00Z"));          // terça 19:00 em Brasília
        String t = c.ana();
        assertEquals(CodigoRegra.MERCADO_FECHADO, c.app.ordens.enviarOrdem(t, Cenario.compra("PETR4", 10, "38.50")).ordem().motivoRejeicao());
        c.relogio.definir(Instant.parse("2026-11-21T14:00:00Z"));          // sábado
        String t2 = c.ana();
        assertEquals(CodigoRegra.MERCADO_FECHADO, c.app.ordens.enviarOrdem(t2, Cenario.compra("PETR4", 10, "38.50")).ordem().motivoRejeicao());
    }

    @Test void ativoSuspensoOuDesconhecidoEhIndisponivel() {
        c.app.admin.alterarStatusAtivo(c.admin(), "PETR4", "SUSPENSO");
        String t = c.ana();
        assertEquals(CodigoRegra.ATIVO_INDISPONIVEL, c.app.ordens.enviarOrdem(t, Cenario.compra("PETR4", 10, "38.50")).ordem().motivoRejeicao());
        assertEquals(CodigoRegra.ATIVO_INDISPONIVEL, c.app.ordens.enviarOrdem(t, Cenario.compra("ZZZZ9", 10, "10.00")).ordem().motivoRejeicao());
    }

    @Test void cotacaoDesatualizadaBloqueiaANegociacao() {
        String t = c.ana();
        c.relogio.avancar(Duration.ofMinutes(2));                          // provedor não atualizou
        assertEquals(CodigoRegra.COTACAO_DESATUALIZADA, c.app.ordens.enviarOrdem(t, Cenario.compra("PETR4", 10, "38.50")).ordem().motivoRejeicao());
        c.app.cotacoesSimuladas.atualizarInstantes();
        assertEquals(StatusOrdem.VALIDADA, c.app.ordens.enviarOrdem(t, Cenario.compra("PETR4", 10, "38.50")).ordem().status());
    }

    @Test void precoForaDaFaixaDaCotacaoEhRejeitado() {                  // proteção contra erro de digitação
        String t = c.ana();
        assertEquals(CodigoRegra.PRECO_FORA_DA_FAIXA, c.app.ordens.enviarOrdem(t, Cenario.compra("PETR4", 10, "50.00")).ordem().motivoRejeicao());
        assertEquals(CodigoRegra.PRECO_FORA_DA_FAIXA, c.app.ordens.enviarOrdem(t, Cenario.compra("PETR4", 10, "3.85")).ordem().motivoRejeicao());
    }

    @Test void provedorDeCotacoesForaDoArSemCacheRejeitaMasComCacheRecenteContinua() {
        String t = c.ana();
        c.app.cotacoesSimuladas.setIndisponivel(true);
        assertEquals(CodigoRegra.COTACAO_INDISPONIVEL, c.app.ordens.enviarOrdem(t, Cenario.compra("PETR4", 10, "38.50")).ordem().motivoRejeicao());
        c.app.cotacoesSimuladas.setIndisponivel(false);
        c.app.cotacoes.consultar(t, "PETR4");                              // popula o cache com cotação válida
        c.app.cotacoesSimuladas.setIndisponivel(true);
        assertEquals(StatusOrdem.VALIDADA, c.app.ordens.enviarOrdem(t, Cenario.compra("PETR4", 10, "38.50")).ordem().status(),
                "degradação controlada: usa a última cotação válida enquanto recente");
    }

    // ------------------------------------------------------------------ venda
    @Test void vendaExigePosicaoDisponivelENaoPermiteVenderEmDobro() {   // RN02
        String t = c.ana();
        assertEquals(CodigoRegra.POSICAO_INSUFICIENTE, c.app.ordens.enviarOrdem(t, Cenario.venda("PETR4", 10, "38.50")).ordem().motivoRejeicao());
        Ordem ok = c.app.ordens.enviarOrdem(t, Cenario.venda("VALE3", 200, "62.40")).ordem();
        assertEquals(StatusOrdem.VALIDADA, ok.status());
        assertEquals(200, c.app.carteira.consultarCarteira(t).itens().get(0).quantidadeBloqueada());
        assertEquals(CodigoRegra.POSICAO_INSUFICIENTE, c.app.ordens.enviarOrdem(t, Cenario.venda("VALE3", 1, "62.40")).ordem().motivoRejeicao());
    }

    // ------------------------------------------------------------------ idempotência
    @Test void reenvioComAMesmaChaveNaoDuplicaOrdemNemReserva() {         // RN03 / RNF06
        String t = c.ana();
        SolicitacaoOrdem s = Cenario.compra("PETR4", 100, "38.50");
        ResultadoEnvio a = c.app.ordens.enviarOrdem(t, s);
        ResultadoEnvio b = c.app.ordens.enviarOrdem(t, s);
        ResultadoEnvio d = c.app.ordens.enviarOrdem(t, s);
        assertFalse(a.duplicada());
        assertTrue(b.duplicada() && d.duplicada());
        assertEquals(a.ordem().id(), b.ordem().id());
        assertEquals(1, c.app.ordens.historico(t).size());
        assertEquals(Dinheiro.de("3850"), saldo(t).bloqueado(), "reservado uma única vez");
        assertEquals(1, c.app.persistencia.fila().disponiveis(c.relogio.instant(), 10).size());
    }

    @Test void mesmaChaveComConteudoDiferenteEhConflito() {
        String t = c.ana();
        SolicitacaoOrdem s = Cenario.compra("PETR4", 100, "38.50");
        c.app.ordens.enviarOrdem(t, s);
        SolicitacaoOrdem outra = new SolicitacaoOrdem("COMPRA", "PETR4", "101", "38.50", s.chaveIdempotencia(), false);
        assertThrows(ConflitoException.class, () -> c.app.ordens.enviarOrdem(t, outra));
        assertEquals(1, c.app.ordens.historico(t).size());
    }

    @Test void envioSimultaneoComAMesmaChaveCriaUmaUnicaOrdem() throws Exception {
        String t = c.ana();
        SolicitacaoOrdem s = Cenario.compra("PETR4", 100, "38.50");
        ExecutorService pool = Executors.newFixedThreadPool(8);
        List<Future<ResultadoEnvio>> fs = new ArrayList<>();
        for (int i = 0; i < 8; i++) fs.add(pool.submit(() -> c.app.ordens.enviarOrdem(t, s)));
        List<UUID> ids = new ArrayList<>();
        for (Future<ResultadoEnvio> f : fs) ids.add(f.get(10, TimeUnit.SECONDS).ordem().id());
        pool.shutdown();
        assertEquals(1, ids.stream().distinct().count());
        assertEquals(1, c.app.ordens.historico(t).size());
        assertEquals(Dinheiro.de("3850"), saldo(t).bloqueado());
    }

    // ------------------------------------------------------------------ validação de entrada
    @Test void entradasMaliciosasOuInvalidasNaoCriamNada() {
        String t = c.ana();
        String k = Cenario.chave();
        SolicitacaoOrdem[] ruins = {
                new SolicitacaoOrdem("COMPRA", "PETR4'; DROP TABLE ordens;--", "1", "10", k, false),
                new SolicitacaoOrdem("COMPRA", "PETR4", "-5", "10", k, false),
                new SolicitacaoOrdem("COMPRA", "PETR4", "0", "10", k, false),
                new SolicitacaoOrdem("COMPRA", "PETR4", "1e3", "10", k, false),
                new SolicitacaoOrdem("COMPRA", "PETR4", "10", "abc", k, false),
                new SolicitacaoOrdem("COMPRA", "PETR4", "10", "-1", k, false),
                new SolicitacaoOrdem("COMPRA", "PETR4", "10", "1000000.01", k, false),
                new SolicitacaoOrdem("TROCA", "PETR4", "10", "10", k, false),
                new SolicitacaoOrdem("COMPRA", "PETR4", "10", "10", "curta", false),
                new SolicitacaoOrdem(null, null, null, null, null, false)};
        for (SolicitacaoOrdem s : ruins) assertThrows(ValidacaoException.class, () -> c.app.ordens.enviarOrdem(t, s));
        assertThrows(ValidacaoException.class, () -> c.app.ordens.enviarOrdem(t, null));
        assertEquals(0, c.app.ordens.historico(t).size());
        assertEquals(Dinheiro.de("100000"), saldo(t).disponivel());
    }

    @Test void limiteDeFrequenciaDeEnvioDeOrdens() {
        c.app.admin.definirParametro(c.admin(), ParametrosSistema.ORDEM_MAX_POR_MINUTO, "3");
        String t = c.ana();
        for (int i = 0; i < 3; i++) c.app.ordens.enviarOrdem(t, Cenario.compra("PETR4", 1, "38.50"));
        assertThrows(LimiteTaxaException.class, () -> c.app.ordens.enviarOrdem(t, Cenario.compra("PETR4", 1, "38.50")));
    }

    // ------------------------------------------------------------------ cancelamento
    @Test void cancelarOrdemAindaNaoTransmitidaDevolveSaldo() {           // RF07
        String t = c.ana();
        Ordem o = c.app.ordens.enviarOrdem(t, Cenario.compra("PETR4", 100, "38.50")).ordem();
        Ordem canc = c.app.ordens.cancelarOrdem(t, o.id().toString());
        assertEquals(StatusOrdem.CANCELADA, canc.status());
        assertEquals(Dinheiro.de("100000"), saldo(t).disponivel());
        assertEquals(Dinheiro.ZERO, saldo(t).bloqueado());
        c.app.processador.ciclo();                                         // a mensagem da fila é descartada
        assertEquals(0, c.app.corretoraSimulada.ordensDistintas(), "ordem cancelada nunca chega à corretora");
        assertEquals(StatusOrdem.CANCELADA, c.app.ordens.consultarOrdem(t, o.id().toString()).status());
    }

    @Test void cancelarOrdemJaEnviadaPassaPelaCorretoraEDevolveSaldo() {
        String t = c.ana();
        Ordem o = c.app.ordens.enviarOrdem(t, Cenario.compra("PETR4", 100, "36.00")).ordem();   // não cruza a cotação
        c.app.processador.ciclo();
        assertEquals(StatusOrdem.ENVIADA, c.app.ordens.consultarOrdem(t, o.id().toString()).status());
        assertEquals(StatusOrdem.CANCELADA, c.app.ordens.cancelarOrdem(t, o.id().toString()).status());
        assertEquals(Dinheiro.de("100000"), saldo(t).disponivel());
        c.app.processador.ciclo();
        assertEquals(StatusOrdem.CANCELADA, c.app.ordens.consultarOrdem(t, o.id().toString()).status(), "nenhuma execução tardia");
    }

    @Test void cancelarOrdemParcialmenteExecutadaPreservaOExecutado() {
        c.app.corretoraSimulada.setLoteMaximo(40);
        String t = c.ana();
        Ordem o = c.app.ordens.enviarOrdem(t, Cenario.compra("PETR4", 100, "38.50")).ordem();
        c.app.processador.ciclo();
        assertEquals(StatusOrdem.PARCIALMENTE_EXECUTADA, c.app.ordens.consultarOrdem(t, o.id().toString()).status());
        Ordem canc = c.app.ordens.cancelarOrdem(t, o.id().toString());
        assertEquals(StatusOrdem.CANCELADA, canc.status());
        assertEquals(40, canc.quantidadeExecutada());
        assertEquals(Dinheiro.de("98460"), saldo(t).disponivel());          // 100.000 − 40 × 38,50
        assertEquals(Dinheiro.ZERO, saldo(t).bloqueado());
        assertEquals(40, c.app.carteira.consultarCarteira(t).itens().get(0).quantidade());
    }

    @Test void ordemExecutadaNaoPodeSerCancelada() {                     // RF07 / RN04
        String t = c.ana();
        Ordem o = c.app.ordens.enviarOrdem(t, Cenario.compra("PETR4", 10, "38.50")).ordem();
        c.app.processador.ciclo();
        assertEquals(StatusOrdem.EXECUTADA, c.app.ordens.consultarOrdem(t, o.id().toString()).status());
        RegraNegocioException e = assertThrows(RegraNegocioException.class, () -> c.app.ordens.cancelarOrdem(t, o.id().toString()));
        assertEquals(CodigoRegra.ORDEM_NAO_CANCELAVEL, e.codigo());
    }

    @Test void ordemRejeitadaNaoPodeSerCancelada() {
        String t = c.ana();
        Ordem o = c.app.ordens.enviarOrdem(t, Cenario.venda("PETR4", 10, "38.50")).ordem();      // rejeitada (sem posição)
        assertThrows(RegraNegocioException.class, () -> c.app.ordens.cancelarOrdem(t, o.id().toString()));
    }

    @Test void cancelamentoEhIdempotente() {
        String t = c.ana();
        Ordem o = c.app.ordens.enviarOrdem(t, Cenario.compra("PETR4", 100, "38.50")).ordem();
        c.app.ordens.cancelarOrdem(t, o.id().toString());
        assertEquals(StatusOrdem.CANCELADA, c.app.ordens.cancelarOrdem(t, o.id().toString()).status());
        assertEquals(Dinheiro.de("100000"), saldo(t).disponivel(), "devolvido uma única vez");
    }

    @Test void corretoraForaDoArNaoCancelaMasNaoCorrompeAOrdem() {        // RN07
        String t = c.ana();
        Ordem o = c.app.ordens.enviarOrdem(t, Cenario.compra("PETR4", 100, "36.00")).ordem();
        c.app.processador.ciclo();
        c.app.corretoraSimulada.setIndisponivel(true);
        assertThrows(IntegracaoIndisponivelException.class, () -> c.app.ordens.cancelarOrdem(t, o.id().toString()));
        assertEquals(StatusOrdem.ENVIADA, c.app.ordens.consultarOrdem(t, o.id().toString()).status());
        assertEquals(Dinheiro.de("3600"), saldo(t).bloqueado(), "reserva mantida");
    }

    // ------------------------------------------------------------------ consultas
    @Test void historicoListaMaisRecentesPrimeiroEComTodosOsStatus() {    // RF14
        String t = c.ana();
        Ordem a = c.app.ordens.enviarOrdem(t, Cenario.compra("PETR4", 10, "38.50")).ordem();
        Ordem b = c.app.ordens.enviarOrdem(t, Cenario.venda("PETR4", 10, "38.50")).ordem();      // rejeitada
        List<Ordem> h = c.app.ordens.historico(t);
        assertEquals(2, h.size());
        assertEquals(b.id(), h.get(0).id());
        assertEquals(a.id(), h.get(1).id());
    }
}
