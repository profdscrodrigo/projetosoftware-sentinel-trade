package br.mackenzie.sentineltrade.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OrdemTest {

    private Ordem novaOrdem() {
        return new Ordem("chave-1", 1, 1, "ORCP3", TipoOrdem.COMPRA, 10, new BigDecimal("25.40"));
    }

    @Test
    @DisplayName("Toda ordem nova começa como CRIADA e calcula o total")
    void comecaCriada() {
        Ordem ordem = novaOrdem();
        assertEquals(StatusOrdem.CRIADA, ordem.getStatus());
        assertEquals(new BigDecimal("254.00"), ordem.getValorTotal());
    }

    @Test
    @DisplayName("Dados inválidos são recusados ao criar a ordem")
    void dadosInvalidos() {
        assertThrows(IllegalArgumentException.class,
                () -> new Ordem("c", 1, 1, "ORCP3", TipoOrdem.COMPRA, 0, BigDecimal.TEN));
        assertThrows(IllegalArgumentException.class,
                () -> new Ordem("c", 1, 1, "ORCP3", TipoOrdem.COMPRA, 5, new BigDecimal("-1")));
        assertThrows(IllegalArgumentException.class,
                () -> new Ordem("c", 1, 1, "ORCP3", null, 5, BigDecimal.TEN));
    }

    @Test
    @DisplayName("Mudança de status proibida lança erro")
    void transicaoProibida() {
        Ordem ordem = novaOrdem();
        assertThrows(IllegalStateException.class, () -> ordem.mudarStatus(StatusOrdem.EXECUTADA, null));
    }

    @Test
    @DisplayName("Ordem executada não pode ser cancelada nem ter o preço alterado")
    void executadaNaoCancela() {
        Ordem ordem = novaOrdem();
        ordem.mudarStatus(StatusOrdem.VALIDADA, null);
        ordem.mudarStatus(StatusOrdem.ENVIADA, null);
        assertTrue(ordem.isCancelavel());
        assertTrue(ordem.isPrecoAlteravel());
        ordem.mudarStatus(StatusOrdem.EXECUTADA, null);
        assertFalse(ordem.isCancelavel());
        assertThrows(IllegalStateException.class, () -> ordem.alterarPreco(BigDecimal.ONE));
    }
}
