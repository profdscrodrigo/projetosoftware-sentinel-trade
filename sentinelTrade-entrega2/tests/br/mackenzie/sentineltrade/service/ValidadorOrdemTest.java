package br.mackenzie.sentineltrade.service;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import br.mackenzie.sentineltrade.model.LimiteRisco;
import br.mackenzie.sentineltrade.model.Ordem;
import br.mackenzie.sentineltrade.model.TipoOrdem;
import br.mackenzie.sentineltrade.service.regras.ContextoValidacao;
import br.mackenzie.sentineltrade.service.regras.ValidadorOrdem;

/** Testa cada regra de validação de ordem (RF-08). */
class ValidadorOrdemTest {

    private final ValidadorOrdem validador = new ValidadorOrdem();
    private final LimiteRisco limite = new LimiteRisco(new BigDecimal("5000"), new BigDecimal("20000"),
            new BigDecimal("3000"));

    private Ordem ordem(TipoOrdem tipo, int quantidade, String preco) {
        return new Ordem("chave", 1, 1, "ORCP3", tipo, quantidade, new BigDecimal(preco));
    }

    /** Contexto "tudo certo": saldo 10.000, 100 ativos na carteira, nada gasto hoje, mercado aberto. */
    private ContextoValidacao contexto() {
        return new ContextoValidacao(new BigDecimal("10000"), 100, limite, BigDecimal.ZERO, true, true);
    }

    private void assertRecusa(Optional<String> motivo, String trecho) {
        assertTrue(motivo.isPresent(), "A ordem deveria ser recusada");
        assertTrue(motivo.get().contains(trecho), "Motivo inesperado: " + motivo.get());
    }

    @Test
    @DisplayName("Ordem dentro de todas as regras é aceita")
    void ordemValida() {
        assertTrue(validador.validar(ordem(TipoOrdem.COMPRA, 10, "25.40"), contexto()).isEmpty());
    }

    @Test
    @DisplayName("RN-01: compra sem saldo é recusada")
    void semSaldo() {
        ContextoValidacao pouco = new ContextoValidacao(new BigDecimal("100"), 100, limite, BigDecimal.ZERO, true, true);
        assertRecusa(validador.validar(ordem(TipoOrdem.COMPRA, 10, "25.40"), pouco), "Saldo insuficiente");
    }

    @Test
    @DisplayName("RN-02: venda maior que a carteira é recusada")
    void semPosicao() {
        assertRecusa(validador.validar(ordem(TipoOrdem.VENDA, 101, "25.40"), contexto()), "Quantidade maior");
    }

    @Test
    @DisplayName("RN-09: valor acima do limite por ordem é recusado")
    void acimaDoLimitePorOrdem() {
        assertRecusa(validador.validar(ordem(TipoOrdem.VENDA, 100, "60.00"), contexto()), "limite por ordem");
    }

    @Test
    @DisplayName("RN-09: limite diário estourado é recusado")
    void acimaDoLimiteDiario() {
        ContextoValidacao gastouMuito = new ContextoValidacao(new BigDecimal("10000"), 100, limite,
                new BigDecimal("19900"), true, true);
        assertRecusa(validador.validar(ordem(TipoOrdem.COMPRA, 10, "25.40"), gastouMuito), "Limite diário");
    }

    @Test
    @DisplayName("RN-10: mercado fechado ou ativo suspenso recusam a ordem")
    void mercadoFechadoOuAtivoSuspenso() {
        ContextoValidacao fechado = new ContextoValidacao(new BigDecimal("10000"), 100, limite, BigDecimal.ZERO, true, false);
        ContextoValidacao suspenso = new ContextoValidacao(new BigDecimal("10000"), 100, limite, BigDecimal.ZERO, false, true);
        assertRecusa(validador.validar(ordem(TipoOrdem.COMPRA, 1, "10"), fechado), "Mercado fechado");
        assertRecusa(validador.validar(ordem(TipoOrdem.COMPRA, 1, "10"), suspenso), "suspenso");
    }
}
