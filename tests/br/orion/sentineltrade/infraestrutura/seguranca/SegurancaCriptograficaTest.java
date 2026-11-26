package br.orion.sentineltrade.infraestrutura.seguranca;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class SegurancaCriptograficaTest {
    /** Senha de teste gerada a cada execução: nenhum segredo literal no repositório. */
    private static final String SENHA_TESTE = "Aa1!" + java.util.UUID.randomUUID();
    private static final byte[] CHAVE_RFC = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

    @Test void totpConfereComOsVetoresOficiaisDaRfc6238() {
        // RFC 6238, Apêndice B (SHA-1, 8 dígitos): T = tempo / 30 s
        assertEquals("94287082", TotpMfa.hotp(CHAVE_RFC, 59L / 30, 8));
        assertEquals("07081804", TotpMfa.hotp(CHAVE_RFC, 1111111109L / 30, 8));
        assertEquals("14050471", TotpMfa.hotp(CHAVE_RFC, 1111111111L / 30, 8));
        assertEquals("89005924", TotpMfa.hotp(CHAVE_RFC, 1234567890L / 30, 8));
        assertEquals("69279037", TotpMfa.hotp(CHAVE_RFC, 2000000000L / 30, 8));
    }

    @Test void verificacaoAceitaJanelaAtualEVizinhasERecusaCodigoErrado() {
        CifradorSegredo cifrador = CifradorSegredo.efemero();
        TotpMfa mfa = new TotpMfa(cifrador);
        String segredo = cifrador.cifrar(Base64.getEncoder().encodeToString(CHAVE_RFC));   // o segredo circula sempre cifrado
        Instant t = Instant.ofEpochSecond(1234567890L);
        String certo = mfa.codigoAtual(segredo, t);
        assertEquals("005924", certo);
        assertTrue(mfa.verificar(segredo, certo, t));
        assertTrue(mfa.verificar(segredo, certo, t.plusSeconds(30)), "tolera uma janela de atraso");
        assertFalse(mfa.verificar(segredo, certo, t.plusSeconds(120)), "código expirado");
        assertFalse(mfa.verificar(segredo, "000000", t));
        assertFalse(mfa.verificar(segredo, null, t));
        assertFalse(mfa.verificar(segredo, "12345", t));
    }

    @Test void segredosMfaSaoUnicos() {
        TotpMfa mfa = new TotpMfa(CifradorSegredo.efemero());
        assertNotEquals(mfa.gerarSegredo(), mfa.gerarSegredo());
    }

    @Test void senhaFicaProtegidaPorHashComSaltUnico() {
        Pbkdf2Hasher h = new Pbkdf2Hasher(1000);
        char[] senha = SENHA_TESTE.toCharArray();
        String h1 = h.hash(senha), h2 = h.hash(senha);
        assertNotEquals(h1, h2, "salt aleatório por senha");
        assertFalse(h1.contains(SENHA_TESTE), "a senha nunca aparece no hash");
        assertTrue(h1.startsWith("pbkdf2_sha256$1000$"));
        assertTrue(h.verifica(senha, h1));
        assertFalse(h.verifica("outra-senha".toCharArray(), h1));
    }

    @Test void hashMalformadoOuAdulteradoNuncaValida() {
        Pbkdf2Hasher h = new Pbkdf2Hasher(1000);
        char[] s = SENHA_TESTE.toCharArray();
        String ok = h.hash(s);
        for (String ruim : new String[]{"", "lixo", "a$b$c$d", "pbkdf2_sha256$x$y$z", "pbkdf2_sha256$1000$AAAA", ok.substring(0, ok.length() - 4) + "AAAA"})
            assertFalse(h.verifica(s, ruim), "não deveria validar: " + ruim);
    }

    @Test void iteracoesFicamNoHashPermitindoEvolucaoDoCusto() {
        String antigo = new Pbkdf2Hasher(1000).hash(SENHA_TESTE.toCharArray());
        assertTrue(new Pbkdf2Hasher(2000).verifica(SENHA_TESTE.toCharArray(), antigo));
    }
}
