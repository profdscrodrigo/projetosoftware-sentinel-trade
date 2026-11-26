package br.mackenzie.sentineltrade.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Testa as transições do diagrama de estados da ordem. */
class StatusOrdemTest {

    @Test
    @DisplayName("Caminho feliz: CRIADA -> VALIDADA -> ENVIADA -> EXECUTADA")
    void caminhoFelizPermitido() {
        assertTrue(StatusOrdem.CRIADA.podeMudarPara(StatusOrdem.VALIDADA));
        assertTrue(StatusOrdem.VALIDADA.podeMudarPara(StatusOrdem.ENVIADA));
        assertTrue(StatusOrdem.ENVIADA.podeMudarPara(StatusOrdem.EXECUTADA));
    }

    @Test
    @DisplayName("Estados finais nunca mudam (RN-04)")
    void estadosFinaisNaoMudam() {
        for (StatusOrdem destino : StatusOrdem.values()) {
            assertFalse(StatusOrdem.EXECUTADA.podeMudarPara(destino));
            assertFalse(StatusOrdem.REJEITADA.podeMudarPara(destino));
            assertFalse(StatusOrdem.CANCELADA.podeMudarPara(destino));
        }
    }

    @Test
    @DisplayName("Ordem não pode pular a validação")
    void naoPulaValidacao() {
        assertFalse(StatusOrdem.CRIADA.podeMudarPara(StatusOrdem.ENVIADA));
        assertFalse(StatusOrdem.CRIADA.podeMudarPara(StatusOrdem.EXECUTADA));
    }

    @Test
    @DisplayName("Ordem com FALHA pode ser reenviada ou cancelada")
    void falhaPodeSerRecuperada() {
        assertTrue(StatusOrdem.FALHA.podeMudarPara(StatusOrdem.ENVIADA));
        assertTrue(StatusOrdem.FALHA.podeMudarPara(StatusOrdem.CANCELADA));
        assertFalse(StatusOrdem.FALHA.podeMudarPara(StatusOrdem.EXECUTADA));
    }

    @Test
    @DisplayName("Texto desconhecido não vira status (allowlist)")
    void textoDesconhecidoNaoViraStatus() {
        assertTrue(StatusOrdem.deTexto("EXECUTADA").isPresent());
        assertTrue(StatusOrdem.deTexto("'; DROP TABLE ordem; --").isEmpty());
        assertTrue(StatusOrdem.deTexto(null).isEmpty());
    }
}
