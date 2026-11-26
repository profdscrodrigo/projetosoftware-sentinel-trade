package br.orion.sentineltrade.aplicacao.servico;

import static org.junit.jupiter.api.Assertions.*;

import br.orion.sentineltrade.Cenario;
import br.orion.sentineltrade.aplicacao.servico.OrdemService.ResultadoAlteracao;
import br.orion.sentineltrade.dominio.*;
import br.orion.sentineltrade.dominio.Excecoes.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Ordem a mercado (preço de proteção) e alteração de ordem (cancelar e substituir). */
class OrdemMercadoEAlteracaoTest {
    private final Cenario c = new Cenario();

    private static SolicitacaoOrdem mercado(String lado, String ticker, int qtd) {
        return new SolicitacaoOrdem(lado, "MERCADO", ticker, String.valueOf(qtd), null, Cenario.chave(), false);
    }
    private Dinheiro disponivel(String t) { return c.app.carteira.consultarSaldo(t).disponivel(); }
    private Dinheiro bloqueado(String t) { return c.app.carteira.consultarSaldo(t).bloqueado(); }
    private Ordem ordem(String t, Ordem o) { return c.app.ordens.consultarOrdem(t, o.id().toString()); }
    private static String chave() { return UUID.randomUUID().toString(); }

    // ------------------------------------------------------------------ ordem a mercado
    @Test void compraAMercadoUsaPrecoDeProtecaoDentroDaFaixaEExecutaAoPrecoDeMercado() {
        String t = c.ana();
        Ordem o = c.app.ordens.enviarOrdem(t, mercado("COMPRA", "PETR4", 100)).ordem();
        assertEquals(TipoOrdem.MERCADO, o.tipo());
        assertEquals(Dinheiro.de("42.35"), o.precoLimite(), "cotação 38,50 + 10%");
        assertEquals(StatusOrdem.VALIDADA, o.status());
        assertEquals(Dinheiro.de("4235"), bloqueado(t), "reserva calculada sobre o preço de proteção");
        c.app.processador.ciclo();
        assertEquals(StatusOrdem.EXECUTADA, ordem(t, o).status());
        assertEquals(Dinheiro.de("96150"), disponivel(t), "executou a 38,50; a diferença da reserva foi devolvida");
        assertEquals(Dinheiro.ZERO, bloqueado(t));
    }

    @Test void vendaAMercadoUsaPrecoDeProtecaoParaBaixo() {
        String t = c.ana();
        Ordem o = c.app.ordens.enviarOrdem(t, mercado("VENDA", "VALE3", 100)).ordem();
        assertEquals(Dinheiro.de("56.16"), o.precoLimite(), "cotação 62,40 − 10%");
        c.app.processador.ciclo();
        assertEquals(StatusOrdem.EXECUTADA, ordem(t, o).status());
        assertEquals(Dinheiro.de("106240"), disponivel(t), "vendeu ao preço de mercado, não ao de proteção");
    }

    @Test void precoDeProtecaoSempreFicaDentroDaFaixaPermitida() {
        for (String preco : new String[]{"33.33", "10.07", "128.71", "0.99", "7.77"}) {
            c.app.cotacoesSimuladas.definir("PETR4", preco, preco);
            c.app.cotacoes.obter(Ticker.de("PETR4"));
            String t = c.ana();
            Ordem o = c.app.ordens.enviarOrdem(t, mercado("COMPRA", "PETR4", 1)).ordem();
            assertEquals(StatusOrdem.VALIDADA, o.status(), "cotação " + preco + " → " + o.motivoRejeicao());
        }
    }

    @Test void ordemAMercadoNaoAceitaPrecoEExigeTipoValido() {
        String t = c.ana();
        assertThrows(ValidacaoException.class, () -> c.app.ordens.enviarOrdem(t,
                new SolicitacaoOrdem("COMPRA", "MERCADO", "PETR4", "10", "38.50", Cenario.chave(), false)));
        assertThrows(ValidacaoException.class, () -> c.app.ordens.enviarOrdem(t,
                new SolicitacaoOrdem("COMPRA", "STOP", "PETR4", "10", "38.50", Cenario.chave(), false)));
        assertThrows(ValidacaoException.class, () -> c.app.ordens.enviarOrdem(t,
                new SolicitacaoOrdem("COMPRA", "LIMITE", "PETR4", "10", null, Cenario.chave(), false)));
    }

    @Test void ordemAMercadoSemCotacaoValidaEhRecusadaNaEntrada() {
        String t = c.ana();
        c.app.cotacoesSimuladas.setIndisponivel(true);
        RegraNegocioException e = assertThrows(RegraNegocioException.class, () -> c.app.ordens.enviarOrdem(t, mercado("COMPRA", "PETR4", 10)));
        assertEquals(CodigoRegra.COTACAO_INDISPONIVEL, e.codigo());
        assertEquals(0, c.app.ordens.historico(t).size(), "sem preço de referência não há o que registrar");
    }

    @Test void ordemAMercadoMantemOsControlesDeRisco() {
        String t = c.ana();
        Ordem grande = c.app.ordens.enviarOrdem(t, new SolicitacaoOrdem("COMPRA", "MERCADO", "BOVA11", "500", null, Cenario.chave(), true)).ordem();
        assertEquals(CodigoRegra.LIMITE_POR_ORDEM_EXCEDIDO, grande.motivoRejeicao());
        c.relogio.definir(java.time.Instant.parse("2026-11-24T22:00:00Z"));      // 19:00: mercado fechado
        String t2 = c.ana();
        c.app.cotacoesSimuladas.atualizarInstantes();
        assertEquals(CodigoRegra.MERCADO_FECHADO, c.app.ordens.enviarOrdem(t2, mercado("COMPRA", "PETR4", 10)).ordem().motivoRejeicao());
    }

    @Test void reenvioDeOrdemAMercadoComAMesmaChaveNaoDuplica() {
        String t = c.ana();
        SolicitacaoOrdem s = mercado("COMPRA", "PETR4", 10);
        var a = c.app.ordens.enviarOrdem(t, s);
        c.app.cotacoesSimuladas.definir("PETR4", "39.00", "38.10");               // a cotação mudou entre os envios
        var b = c.app.ordens.enviarOrdem(t, s);
        assertTrue(b.duplicada());
        assertEquals(a.ordem().id(), b.ordem().id());
        assertEquals(1, c.app.ordens.historico(t).size());
    }

    // ------------------------------------------------------------------ alteração
    @Test void alterarOrdemValidadaSubstituiPorNovaEAjustaAReserva() {
        String t = c.ana();
        Ordem o = c.app.ordens.enviarOrdem(t, Cenario.compra("PETR4", 100, "38.50")).ordem();
        assertEquals(Dinheiro.de("3850"), bloqueado(t));
        ResultadoAlteracao r = c.app.ordens.alterarOrdem(t, o.id().toString(), "200", "38.00", chave(), false);
        assertEquals(StatusOrdem.CANCELADA, r.substituida().status());
        assertEquals(StatusOrdem.VALIDADA, r.nova().status());
        assertEquals(o.id(), r.nova().substituiOrdemId(), "a nova ordem fica ligada à original");
        assertEquals(200, r.nova().quantidade());
        assertEquals(Dinheiro.de("38.00"), r.nova().precoLimite());
        assertEquals(Dinheiro.de("7600"), bloqueado(t), "reserva da antiga devolvida e da nova feita");
        assertEquals(Dinheiro.de("92400"), disponivel(t));
        c.app.processador.ciclo();
        assertEquals(0, c.app.corretoraSimulada.ordensDistintas() - 1, "só a nova ordem chegou à corretora");
        assertEquals(StatusOrdem.CANCELADA, ordem(t, o).status());
    }

    @Test void alterarOrdemJaEnviadaCancelaNaCorretoraESubstitui() {
        String t = c.ana();
        Ordem o = c.app.ordens.enviarOrdem(t, Cenario.compra("PETR4", 100, "36.00")).ordem();     // não cruza: fica no livro
        c.app.processador.ciclo();
        assertEquals(StatusOrdem.ENVIADA, ordem(t, o).status());
        ResultadoAlteracao r = c.app.ordens.alterarOrdem(t, o.id().toString(), "100", "35.00", chave(), false);
        assertEquals(StatusOrdem.CANCELADA, ordem(t, o).status());
        assertEquals(Dinheiro.de("3500"), bloqueado(t));
        c.app.cotacoesSimuladas.definir("PETR4", "34.80", "38.10");                                // agora cruza o novo limite
        c.app.processador.ciclo();
        assertEquals(StatusOrdem.EXECUTADA, ordem(t, r.nova()).status());
        assertEquals(Dinheiro.de("96520"), disponivel(t), "100 × 34,80 gastos");
        assertEquals(StatusOrdem.CANCELADA, ordem(t, o).status(), "a antiga nunca executa");
        assertTrue(c.app.auditoria.verificarIntegridade().integra());
    }

    @Test void alterarSomenteAQuantidadeMantemOPrecoLimite() {
        String t = c.ana();
        Ordem o = c.app.ordens.enviarOrdem(t, Cenario.compra("PETR4", 100, "38.50")).ordem();
        ResultadoAlteracao r = c.app.ordens.alterarOrdem(t, o.id().toString(), "50", "", chave(), false);
        assertEquals(Dinheiro.de("38.50"), r.nova().precoLimite());
        assertEquals(Dinheiro.de("1925"), bloqueado(t));
    }

    @Test void alterarOrdemAMercadoRecalculaOPrecoDeProtecao() {
        String t = c.ana();
        Ordem o = c.app.ordens.enviarOrdem(t, mercado("COMPRA", "PETR4", 100)).ordem();
        c.app.cotacoesSimuladas.definir("PETR4", "40.00", "38.10");
        ResultadoAlteracao r = c.app.ordens.alterarOrdem(t, o.id().toString(), "100", null, chave(), false);
        assertEquals(TipoOrdem.MERCADO, r.nova().tipo());
        assertEquals(Dinheiro.de("44.00"), r.nova().precoLimite());
        assertThrows(ValidacaoException.class, () -> c.app.ordens.alterarOrdem(t, r.nova().id().toString(), "100", "40.00", chave(), false));
    }

    @Test void alteracaoImpossivelDeixaAOrdemOriginalIntacta() {
        String t = c.ana();
        Ordem o = c.app.ordens.enviarOrdem(t, Cenario.compra("PETR4", 100, "38.50")).ordem();
        // saldo insuficiente para a nova ordem
        c.app.risco.definirLimites(c.risco(), c.anaId().toString(), "500000", "1000000", "500000");
        String t2 = c.ana();
        RegraNegocioException e1 = assertThrows(RegraNegocioException.class,
                () -> c.app.ordens.alterarOrdem(t2, o.id().toString(), "10000", "38.50", chave(), true));
        assertEquals(CodigoRegra.SALDO_INSUFICIENTE, e1.codigo());
        // preço fora da faixa
        RegraNegocioException e2 = assertThrows(RegraNegocioException.class,
                () -> c.app.ordens.alterarOrdem(t2, o.id().toString(), "100", "60.00", chave(), false));
        assertEquals(CodigoRegra.PRECO_FORA_DA_FAIXA, e2.codigo());
        assertEquals(StatusOrdem.VALIDADA, ordem(t2, o).status());
        assertEquals(Dinheiro.de("3850"), bloqueado(t2));
        assertEquals(1, c.app.ordens.historico(t2).size(), "nenhuma ordem nova foi criada");
    }

    @Test void naoAlteraOrdemExecutadaParcialmenteExecutadaRejeitadaOuCancelada() {
        c.app.corretoraSimulada.setLoteMaximo(40);
        String t = c.ana();
        Ordem parcial = c.app.ordens.enviarOrdem(t, Cenario.compra("PETR4", 100, "38.50")).ordem();
        c.app.processador.ciclo();
        assertEquals(StatusOrdem.PARCIALMENTE_EXECUTADA, ordem(t, parcial).status());
        assertEquals(CodigoRegra.ORDEM_NAO_ALTERAVEL, assertThrows(RegraNegocioException.class,
                () -> c.app.ordens.alterarOrdem(t, parcial.id().toString(), "200", "38.50", chave(), false)).codigo());
        Ordem rejeitada = c.app.ordens.enviarOrdem(t, Cenario.venda("ITUB4", 10, "33.20")).ordem();
        assertThrows(RegraNegocioException.class, () -> c.app.ordens.alterarOrdem(t, rejeitada.id().toString(), "5", "33.20", chave(), false));
        Ordem cancelada = c.app.ordens.enviarOrdem(t, Cenario.compra("ITUB4", 10, "33.20")).ordem();
        c.app.ordens.cancelarOrdem(t, cancelada.id().toString());
        assertThrows(RegraNegocioException.class, () -> c.app.ordens.alterarOrdem(t, cancelada.id().toString(), "5", "33.20", chave(), false));
    }

    @Test void corretoraForaDoArImpedeAlteracaoDeOrdemEnviadaSemMexerNela() {
        String t = c.ana();
        Ordem o = c.app.ordens.enviarOrdem(t, Cenario.compra("PETR4", 100, "36.00")).ordem();
        c.app.processador.ciclo();
        c.app.corretoraSimulada.setIndisponivel(true);
        assertThrows(IntegracaoIndisponivelException.class, () -> c.app.ordens.alterarOrdem(t, o.id().toString(), "50", "36.00", chave(), false));
        assertEquals(StatusOrdem.ENVIADA, ordem(t, o).status());
        assertEquals(Dinheiro.de("3600"), bloqueado(t));
    }

    @Test void reenvioDaMesmaAlteracaoNaoCriaOutraOrdem() {
        String t = c.ana();
        Ordem o = c.app.ordens.enviarOrdem(t, Cenario.compra("PETR4", 100, "38.50")).ordem();
        String k = chave();
        ResultadoAlteracao a = c.app.ordens.alterarOrdem(t, o.id().toString(), "80", "38.50", k, false);
        ResultadoAlteracao b = c.app.ordens.alterarOrdem(t, o.id().toString(), "80", "38.50", k, false);
        assertTrue(b.duplicada());
        assertEquals(a.nova().id(), b.nova().id());
        assertEquals(2, c.app.ordens.historico(t).size());
        assertEquals(Dinheiro.de("3080"), bloqueado(t));
    }

    @Test void alteracaoValidaEntradaEExigeConfirmacaoDeAltoValor() {
        String t = c.ana();
        Ordem o = c.app.ordens.enviarOrdem(t, Cenario.compra("PETR4", 10, "38.50")).ordem();
        assertThrows(ValidacaoException.class, () -> c.app.ordens.alterarOrdem(t, o.id().toString(), "-1", "38.50", chave(), false));
        assertThrows(ValidacaoException.class, () -> c.app.ordens.alterarOrdem(t, o.id().toString(), "10", "abc", chave(), false));
        assertThrows(ValidacaoException.class, () -> c.app.ordens.alterarOrdem(t, o.id().toString(), "10", "38.50", chave(), false), "nenhuma mudança");
        assertThrows(ValidacaoException.class, () -> c.app.ordens.alterarOrdem(t, "nao-e-uuid", "10", "38.00", chave(), false));
        String k = chave();
        assertThrows(ConfirmacaoNecessariaException.class, () -> c.app.ordens.alterarOrdem(t, o.id().toString(), "700", "38.50", k, false));
        assertEquals(StatusOrdem.VALIDADA, ordem(t, o).status(), "sem confirmação nada muda");
        assertEquals(StatusOrdem.VALIDADA, c.app.ordens.alterarOrdem(t, o.id().toString(), "700", "38.50", k, true).nova().status());
    }

    @Test void investidorNaoAlteraOrdemDeOutroENemOutrosPerfis() {
        String ana = c.ana(), bruno = c.bruno();
        Ordem o = c.app.ordens.enviarOrdem(ana, Cenario.compra("PETR4", 10, "38.50")).ordem();
        assertThrows(NaoEncontradoException.class, () -> c.app.ordens.alterarOrdem(bruno, o.id().toString(), "5", "38.00", chave(), false));
        assertThrows(AcessoNegadoException.class, () -> c.app.ordens.alterarOrdem(c.admin(), o.id().toString(), "5", "38.00", chave(), false));
        assertEquals(StatusOrdem.VALIDADA, ordem(ana, o).status());
    }

    @Test void alteracaoDeVendaAjustaQuantidadeBloqueadaNaPosicao() {
        String t = c.ana();
        Ordem o = c.app.ordens.enviarOrdem(t, Cenario.venda("VALE3", 100, "62.40")).ordem();
        assertEquals(100, c.app.carteira.consultarCarteira(t).itens().get(0).quantidadeBloqueada());
        c.app.ordens.alterarOrdem(t, o.id().toString(), "150", "62.00", chave(), false);
        assertEquals(150, c.app.carteira.consultarCarteira(t).itens().get(0).quantidadeBloqueada());
        assertEquals(CodigoRegra.POSICAO_INSUFICIENTE, assertThrows(RegraNegocioException.class,
                () -> c.app.ordens.alterarOrdem(t, c.app.ordens.historico(t).get(0).id().toString(), "250", "62.00", chave(), false)).codigo());
    }

    @Test void alteracaoGeraAuditoriaENotificacaoEATrilhaContinuaIntegra() {
        String t = c.ana();
        Ordem o = c.app.ordens.enviarOrdem(t, Cenario.compra("PETR4", 10, "38.50")).ordem();
        c.app.ordens.alterarOrdem(t, o.id().toString(), "20", "38.50", chave(), false);
        var tipos = c.app.persistencia.auditoria().listar(0, 1000).stream().map(EventoAuditoria::tipo).toList();
        assertTrue(tipos.contains("ORDEM_ALTERADA"));
        assertEquals(1, tipos.stream().filter(x -> x.equals("ORDEM_CANCELADA")).count());
        assertTrue(c.app.notificacoes.minhasNotificacoes(t).stream().anyMatch(n -> n.tipo() == TipoNotificacao.ALTERACAO));
        assertTrue(c.app.auditoria.verificarIntegridade().integra());
    }
}
