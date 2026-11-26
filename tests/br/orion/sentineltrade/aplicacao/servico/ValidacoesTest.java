package br.orion.sentineltrade.aplicacao.servico;

import static org.junit.jupiter.api.Assertions.*;

import br.orion.sentineltrade.dominio.Dinheiro;
import br.orion.sentineltrade.dominio.Excecoes.ValidacaoException;
import br.orion.sentineltrade.dominio.Lado;
import br.orion.sentineltrade.dominio.Ticker;
import org.junit.jupiter.api.Test;

/** Validação por allowlist no servidor: o que não é explicitamente permitido é recusado. */
class ValidacoesTest {
    /** Senha de teste gerada a cada execução: nenhum segredo literal no repositório. */
    private static final String SENHA_TESTE = "Aa1!" + java.util.UUID.randomUUID();
    @Test void tickerAceitaApenasOFormatoPrevisto() {
        assertEquals("PETR4", Ticker.de(" petr4 ").codigo());
        assertEquals("MXRF11", Ticker.de("MXRF11").codigo());
        for (String ruim : new String[]{"PETR", "PETR444", "P3TR4", "PETR4;", "PETR4' OR '1'='1", "PETR4; DROP TABLE ordens;--",
                "../../etc/passwd", "<script>alert(1)</script>", "", "   ", "PETR 4", "PÉTR4"})
            assertThrows(ValidacaoException.class, () -> Ticker.de(ruim), "deveria rejeitar: " + ruim);
        assertThrows(ValidacaoException.class, () -> Ticker.de(null));
        assertThrows(ValidacaoException.class, () -> new Ticker("PETR4\n"), "o construtor não normaliza: rejeita quebra de linha");
    }

    @Test void caracteresDeControleNasPontasSaoRecusadosEmVezDeNormalizados() {
        for (String ruim : new String[]{"PETR4\u0000", "\u0000PETR4", "PETR4\u001b", "\u0007PETR4"})
            assertThrows(ValidacaoException.class, () -> Ticker.de(ruim), "deveria rejeitar controle em: " + ruim.length());
        assertThrows(ValidacaoException.class, () -> Validacoes.quantidade("10\u0000"));
        assertThrows(ValidacaoException.class, () -> Validacoes.preco("\u00001.00"));
        assertThrows(ValidacaoException.class, () -> Validacoes.lado("COMPRA\u0000"));
        assertEquals("PETR4", Ticker.de("  PETR4 \t").codigo(), "espaço em branco comum continua sendo aceito nas pontas");
    }

    @Test void quantidadeInteiraPositivaNoIntervalo() {
        assertEquals(100, Validacoes.quantidade("100"));
        assertEquals(1_000_000, Validacoes.quantidade("1000000"));
        for (String ruim : new String[]{"0", "-5", "1000001", "10.5", "1e3", "abc", "", " ", "99999999", "1 0", "0x10", null})
            assertThrows(ValidacaoException.class, () -> Validacoes.quantidade(ruim), "deveria rejeitar: " + ruim);
    }

    @Test void precoComAteDuasCasasEDentroDoIntervalo() {
        assertEquals(Dinheiro.de("38.50"), Validacoes.preco("38,50"));
        assertEquals(Dinheiro.de("38.5"), Validacoes.preco("38.5"));
        assertEquals(Dinheiro.de("0.01"), Validacoes.preco("0.01"));
        for (String ruim : new String[]{"0", "0.00", "-1", "abc", "1.234", "1000000.01", "1e5", "NaN", "", "10,", ",5", "1,2,3", null})
            assertThrows(ValidacaoException.class, () -> Validacoes.preco(ruim), "deveria rejeitar: " + ruim);
    }

    @Test void ladoSomenteCompraOuVenda() {
        assertEquals(Lado.COMPRA, Validacoes.lado("compra"));
        assertEquals(Lado.VENDA, Validacoes.lado(" VENDA "));
        for (String ruim : new String[]{"TROCA", "", "COMPRA;", "BUY", null})
            assertThrows(ValidacaoException.class, () -> Validacoes.lado(ruim));
    }

    @Test void chaveDeIdempotenciaTemFormatoControlado() {
        assertNotNull(Validacoes.chaveIdempotencia("550e8400-e29b-41d4-a716-446655440000"));
        for (String ruim : new String[]{"curta", "com espaco 1234567890", "x".repeat(65), "chave'; DROP--0123456", "", null})
            assertThrows(ValidacaoException.class, () -> Validacoes.chaveIdempotencia(ruim), "deveria rejeitar: " + ruim);
    }

    @Test void emailENomeValidados() {
        assertEquals("ana@orion.example", Validacoes.email("  Ana@Orion.Example "));
        for (String ruim : new String[]{"sem-arroba", "a@b", "a b@c.com", "<x>@c.com", "a@c.com\nBcc: x@y.com", "", null, "a@".repeat(3) + "c.com"})
            assertThrows(ValidacaoException.class, () -> Validacoes.email(ruim), "deveria rejeitar: " + ruim);
        assertEquals("Ana D'Ávila-Silva", Validacoes.nome("Ana D'Ávila-Silva"));
        for (String ruim : new String[]{"", "A", "<b>Ana</b>", "Ana\u001b[31m", "Robert'); DROP TABLE--", "x".repeat(101), "123"})
            assertThrows(ValidacaoException.class, () -> Validacoes.nome(ruim), "deveria rejeitar: " + ruim);
    }

    @Test void politicaDeSenha() {
        assertDoesNotThrow(() -> Validacoes.senha(SENHA_TESTE, "ana@orion.example"));
        for (String ruim : new String[]{"curta1A#", "semmaiuscula123#", "SEMMINUSCULA123#", "SemNumeroNemSimbolo", "SemSimbolo12345Ab",
                "Senha@123456", "Ana-Orion#2026Xx"})
            assertThrows(ValidacaoException.class, () -> Validacoes.senha(ruim, "ana@orion.example"), "deveria rejeitar: " + ruim);
        assertThrows(ValidacaoException.class, () -> Validacoes.senha(null, null));
    }

    @Test void codigoMfaTemSeisDigitos() {
        assertTrue(Validacoes.codigoMfaValido("012345"));
        for (String ruim : new String[]{"12345", "1234567", "12345a", " 12345", "", null})
            assertFalse(Validacoes.codigoMfaValido(ruim));
    }

    @Test void impressaoDigitalIdentificaOConteudoDaOrdem() {
        var a = Validacoes.ordem(new SolicitacaoOrdem("COMPRA", "PETR4", "10", "38,50", "chave-0123456789ab", false));
        var b = Validacoes.ordem(new SolicitacaoOrdem("compra", "petr4", "10", "38.50", "chave-0123456789ab", true));
        var c = Validacoes.ordem(new SolicitacaoOrdem("COMPRA", "PETR4", "11", "38.50", "chave-0123456789ab", false));
        assertEquals(a.impressaoDigital(), b.impressaoDigital());     // mesma ordem, mesmo conteúdo (confirmação não conta)
        assertNotEquals(a.impressaoDigital(), c.impressaoDigital());
    }

    @Test void sanitizadorRemoveControlesEQuebrasDeLinha() {
        String s = Sanitizador.paraLog("ok\r\nFAKE LOG ENTRY\u001b[31m\t fim");
        assertFalse(s.contains("\n") || s.contains("\r") || s.contains("\u001b") || s.contains("\t"));
        assertTrue(Sanitizador.paraLog("x".repeat(500), 50).length() <= 51);
    }
}
