package br.orion.sentineltrade.dominio;

import static br.orion.sentineltrade.dominio.StatusOrdem.*;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class StatusOrdemTest {
    @Test void fluxoNormalDaOrdem() {
        assertTrue(CRIADA.podeTransitarPara(VALIDADA));
        assertTrue(VALIDADA.podeTransitarPara(ENVIADA));
        assertTrue(ENVIADA.podeTransitarPara(PARCIALMENTE_EXECUTADA));
        assertTrue(PARCIALMENTE_EXECUTADA.podeTransitarPara(PARCIALMENTE_EXECUTADA));
        assertTrue(PARCIALMENTE_EXECUTADA.podeTransitarPara(EXECUTADA));
        assertTrue(ENVIADA.podeTransitarPara(EXECUTADA));
    }

    @Test void rejeicaoECancelamentoPermitidosApenasEmAberto() {
        assertTrue(CRIADA.podeTransitarPara(REJEITADA));
        assertTrue(VALIDADA.podeTransitarPara(CANCELADA));
        assertTrue(ENVIADA.podeTransitarPara(CANCELADA));
        assertTrue(PARCIALMENTE_EXECUTADA.podeTransitarPara(CANCELADA));
        assertFalse(PARCIALMENTE_EXECUTADA.podeTransitarPara(REJEITADA));
    }

    @Test void estadosTerminaisNuncaVoltam() {                       // RN04
        for (StatusOrdem terminal : new StatusOrdem[]{EXECUTADA, REJEITADA, CANCELADA}) {
            assertTrue(terminal.terminal());
            for (StatusOrdem destino : StatusOrdem.values())
                assertFalse(terminal.podeTransitarPara(destino), terminal + " -> " + destino + " deveria ser proibida");
        }
    }

    @Test void naoHaSaltosNemRetrocessos() {
        assertFalse(CRIADA.podeTransitarPara(ENVIADA));
        assertFalse(CRIADA.podeTransitarPara(EXECUTADA));
        assertFalse(VALIDADA.podeTransitarPara(EXECUTADA));
        assertFalse(ENVIADA.podeTransitarPara(CRIADA));
        assertFalse(ENVIADA.podeTransitarPara(VALIDADA));
        assertFalse(PARCIALMENTE_EXECUTADA.podeTransitarPara(ENVIADA));
    }

    @Test void reservaMantidaSomenteEmEstadosAbertosPosValidacao() {
        assertFalse(CRIADA.mantemReserva());
        assertTrue(VALIDADA.mantemReserva());
        assertTrue(ENVIADA.mantemReserva());
        assertTrue(PARCIALMENTE_EXECUTADA.mantemReserva());
        assertFalse(EXECUTADA.mantemReserva());
        assertFalse(CANCELADA.mantemReserva());
    }
}
