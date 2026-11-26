package br.mackenzie.sentineltrade.seguranca;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Testa o segundo fator (MFA) e a limpeza de textos para o log. */
class SegurancaBasicaTest {

    @Test
    @DisplayName("MFA: código certo dentro do prazo libera o acesso")
    void codigoCerto() {
        DesafioMfa desafio = new DesafioMfa(1, "123456", Instant.now());
        assertEquals(DesafioMfa.Resultado.OK, desafio.verificar("123456", Instant.now()));
    }

    @Test
    @DisplayName("MFA: depois de 3 erros o código não vale mais")
    void tresErros() {
        DesafioMfa desafio = new DesafioMfa(1, "123456", Instant.now());
        assertEquals(DesafioMfa.Resultado.CODIGO_ERRADO, desafio.verificar("000000", Instant.now()));
        assertEquals(DesafioMfa.Resultado.CODIGO_ERRADO, desafio.verificar("111111", Instant.now()));
        assertEquals(DesafioMfa.Resultado.TENTATIVAS_ESGOTADAS, desafio.verificar("222222", Instant.now()));
        assertEquals(DesafioMfa.Resultado.TENTATIVAS_ESGOTADAS, desafio.verificar("123456", Instant.now()));
    }

    @Test
    @DisplayName("MFA: código expira depois de 5 minutos")
    void expira() {
        Instant criado = Instant.parse("2026-01-01T10:00:00Z");
        DesafioMfa desafio = new DesafioMfa(1, "123456", criado);
        assertEquals(DesafioMfa.Resultado.EXPIRADO, desafio.verificar("123456", criado.plusSeconds(301)));
    }

    @Test
    @DisplayName("MFA: o código gerado sempre tem 6 dígitos")
    void codigoComSeisDigitos() {
        assertTrue(DesafioMfa.novo(1).getCodigo().matches("^[0-9]{6}$"));
    }

    @Test
    @DisplayName("Log: quebras de linha são removidas (evita falsificar linhas do log)")
    void limpaLog() {
        String limpo = Sanitizador.paraLog("ok\nAUDITORIA acao=LOGIN_SUCESSO");
        assertFalse(limpo.contains("\n"));
        assertTrue(Sanitizador.paraLog("x".repeat(500)).length() <= 203);
    }
}
