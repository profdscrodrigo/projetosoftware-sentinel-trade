package br.orion.sentineltrade.dominio;

import static org.junit.jupiter.api.Assertions.*;

import br.orion.sentineltrade.dominio.Excecoes.*;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OrdemTest {
    private static final Instant T0 = Instant.parse("2026-11-24T14:00:00Z");
    private final UUID inv = UUID.randomUUID();
    private Conta conta;
    private Posicao posicao;

    @BeforeEach void preparar() {
        conta = new Conta(UUID.randomUUID(), inv, Dinheiro.de("10000"), Dinheiro.ZERO, 1);
        posicao = Posicao.vazia(inv, Ticker.de("PETR4"));
    }

    private Ordem compra(int q, String preco) {
        return Ordem.nova(Lado.COMPRA, inv, Ticker.de("PETR4"), q, Dinheiro.de(preco), "chave-0123456789ab", "x", T0);
    }
    private Ordem venda(int q, String preco) {
        return Ordem.nova(Lado.VENDA, inv, Ticker.de("PETR4"), q, Dinheiro.de(preco), "chave-0123456789ab", "x", T0);
    }

    @Test void ordemExigeQuantidadeEPrecoPositivos() {            // RNF02
        assertThrows(ValidacaoException.class, () -> compra(0, "10"));
        assertThrows(ValidacaoException.class, () -> compra(-5, "10"));
        assertThrows(ValidacaoException.class, () -> compra(1, "0"));
        assertThrows(ValidacaoException.class, () -> compra(1, "-3.50"));
    }

    @Test void compraReservaSaldoEFicaValidada() {                // RN01
        Ordem o = compra(100, "10.00");
        o.reservarEValidar(conta, posicao, T0);
        assertEquals(StatusOrdem.VALIDADA, o.status());
        assertEquals(Dinheiro.de("9000"), conta.saldoDisponivel());
        assertEquals(Dinheiro.de("1000"), conta.saldoBloqueado());
    }

    @Test void compraSemSaldoNaoAlteraNada() {
        Ordem o = compra(2000, "10.00");                          // 20.000 > 10.000
        RegraNegocioException e = assertThrows(RegraNegocioException.class, () -> o.reservarEValidar(conta, posicao, T0));
        assertEquals(CodigoRegra.SALDO_INSUFICIENTE, e.codigo());
        assertEquals(StatusOrdem.CRIADA, o.status());
        assertEquals(Dinheiro.de("10000"), conta.saldoDisponivel());
        assertEquals(Dinheiro.ZERO, conta.saldoBloqueado());
    }

    @Test void vendaExigePosicaoDisponivel() {                    // RN02
        Ordem o = venda(10, "10.00");
        RegraNegocioException e = assertThrows(RegraNegocioException.class, () -> o.reservarEValidar(conta, posicao, T0));
        assertEquals(CodigoRegra.POSICAO_INSUFICIENTE, e.codigo());
        assertEquals(StatusOrdem.CRIADA, o.status());
    }

    @Test void vendaBloqueiaAQuantidadeEImpedeVendaEmDobro() {
        posicao.adicionar(100, Dinheiro.de("1000"));
        venda(100, "10").reservarEValidar(conta, posicao, T0);
        assertEquals(100, posicao.quantidadeBloqueada());
        assertEquals(0, posicao.quantidadeDisponivel());
        Ordem outra = venda(1, "10");
        assertThrows(RegraNegocioException.class, () -> outra.reservarEValidar(conta, posicao, T0));
    }

    @Test void execucoesParciaisDevolvemMelhoriaDePrecoELiquidam() {
        Ordem o = compra(100, "10.00");
        o.reservarEValidar(conta, posicao, T0);
        o.marcarEnviada(T0);
        o.aplicarExecucao(40, Dinheiro.de("9.50"), T0, conta, posicao);
        assertEquals(StatusOrdem.PARCIALMENTE_EXECUTADA, o.status());
        assertEquals(60, o.quantidadeRestante());
        assertEquals(Dinheiro.de("600"), conta.saldoBloqueado());
        assertEquals(Dinheiro.de("9020"), conta.saldoDisponivel());   // 20 devolvidos (40 × 0,50)
        o.aplicarExecucao(60, Dinheiro.de("9.00"), T0, conta, posicao);
        assertEquals(StatusOrdem.EXECUTADA, o.status());
        assertEquals(Dinheiro.ZERO, conta.saldoBloqueado());
        assertEquals(Dinheiro.de("9080"), conta.saldoDisponivel());
        assertEquals(100, posicao.quantidade());
        assertEquals(Dinheiro.de("9.20"), posicao.precoMedio());      // (380 + 540) / 100
    }

    @Test void execucaoComPrecoPiorQueOLimiteEhRecusadaSemEfeito() {
        Ordem o = compra(10, "10.00");
        o.reservarEValidar(conta, posicao, T0);
        o.marcarEnviada(T0);
        assertThrows(RespostaInvalidaException.class, () -> o.aplicarExecucao(10, Dinheiro.de("10.01"), T0, conta, posicao));
        assertThrows(RespostaInvalidaException.class, () -> o.aplicarExecucao(11, Dinheiro.de("9.00"), T0, conta, posicao));
        assertEquals(StatusOrdem.ENVIADA, o.status());
        assertEquals(0, posicao.quantidade());
        assertEquals(Dinheiro.de("100"), conta.saldoBloqueado());
    }

    @Test void execucaoDeVendaCreditaSaldoEBaixaPosicaoEcusto() {
        posicao.adicionar(100, Dinheiro.de("1000"));
        Ordem o = venda(50, "12.00");
        o.reservarEValidar(conta, posicao, T0);
        o.marcarEnviada(T0);
        o.aplicarExecucao(50, Dinheiro.de("12.50"), T0, conta, posicao);
        assertEquals(StatusOrdem.EXECUTADA, o.status());
        assertEquals(Dinheiro.de("10625"), conta.saldoDisponivel()); // 10.000 + 50 × 12,50
        assertEquals(50, posicao.quantidade());
        assertEquals(0, posicao.quantidadeBloqueada());
        assertEquals(Dinheiro.de("500"), posicao.custoTotal());
    }

    @Test void cancelarOrdemParcialDevolveSomenteORestante() {
        Ordem o = compra(100, "10.00");
        o.reservarEValidar(conta, posicao, T0);
        o.marcarEnviada(T0);
        o.aplicarExecucao(40, Dinheiro.de("10.00"), T0, conta, posicao);
        o.cancelar(T0, conta, posicao);
        assertEquals(StatusOrdem.CANCELADA, o.status());
        assertEquals(40, o.quantidadeExecutada());
        assertEquals(Dinheiro.ZERO, conta.saldoBloqueado());
        assertEquals(Dinheiro.de("9600"), conta.saldoDisponivel());  // 10.000 − 400 gastos
    }

    @Test void ordemExecutadaNaoPodeSerCanceladaNemRejeitada() {  // RF07 / RN04
        Ordem o = compra(10, "10.00");
        o.reservarEValidar(conta, posicao, T0);
        o.marcarEnviada(T0);
        o.aplicarExecucao(10, Dinheiro.de("10.00"), T0, conta, posicao);
        RegraNegocioException e = assertThrows(RegraNegocioException.class, () -> o.cancelar(T0, conta, posicao));
        assertEquals(CodigoRegra.ORDEM_NAO_CANCELAVEL, e.codigo());
        assertThrows(RegraNegocioException.class, () -> o.rejeitar(CodigoRegra.FALHA_INTEGRACAO, T0, conta, posicao));
        assertEquals(StatusOrdem.EXECUTADA, o.status());
    }

    @Test void ordemRejeitadaOuCanceladaNaoVoltaAoInicio() {
        Ordem o = compra(10, "10.00");
        o.rejeitar(CodigoRegra.MERCADO_FECHADO, T0, conta, posicao);
        assertEquals(StatusOrdem.REJEITADA, o.status());
        assertEquals(CodigoRegra.MERCADO_FECHADO, o.motivoRejeicao());
        assertThrows(RegraNegocioException.class, () -> o.reservarEValidar(conta, posicao, T0));
        assertThrows(RegraNegocioException.class, () -> o.marcarEnviada(T0));
        assertThrows(RegraNegocioException.class, () -> o.cancelar(T0, conta, posicao));
    }

    @Test void rejeitarOrdemEnviadaDevolveAReserva() {
        Ordem o = compra(100, "10.00");
        o.reservarEValidar(conta, posicao, T0);
        o.marcarEnviada(T0);
        o.rejeitar(CodigoRegra.REJEITADA_PELA_CORRETORA, T0, conta, posicao);
        assertEquals(Dinheiro.de("10000"), conta.saldoDisponivel());
        assertEquals(Dinheiro.ZERO, conta.saldoBloqueado());
    }
}
