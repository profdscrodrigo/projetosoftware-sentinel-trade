package br.orion.sentineltrade.infraestrutura.seguranca;

import static org.junit.jupiter.api.Assertions.*;

import br.orion.sentineltrade.Cenario;
import br.orion.sentineltrade.DadosDemonstracao;
import br.orion.sentineltrade.dominio.Excecoes.AutenticacaoException;
import br.orion.sentineltrade.dominio.Usuario;
import java.time.Instant;
import java.util.Base64;
import org.junit.jupiter.api.Test;

/** Segredo MFA protegido em repouso com AES-256-GCM. */
class CifradorSegredoTest {
    @Test void cifraEDecifraSemPerdas() {
        CifradorSegredo c = CifradorSegredo.efemero();
        String segredo = "JBSWY3DPEHPK3PXP-segredo-de-teste";
        String guardado = c.cifrar(segredo);
        assertTrue(guardado.startsWith("v1:"));
        assertFalse(guardado.contains(segredo));
        assertEquals(segredo, c.decifrar(guardado));
    }

    @Test void cadaCifraUsaUmIvDiferente() {
        CifradorSegredo c = CifradorSegredo.efemero();
        assertNotEquals(c.cifrar("mesmo texto"), c.cifrar("mesmo texto"), "IV aleatório: textos iguais geram cifras distintas");
    }

    @Test void adulteracaoDoValorArmazenadoEhDetectada() {
        CifradorSegredo c = CifradorSegredo.efemero();
        String guardado = c.cifrar("segredo");
        byte[] bytes = Base64.getDecoder().decode(guardado.substring(3));
        bytes[bytes.length - 1] ^= 0x01;                                          // vira um único bit
        String adulterado = "v1:" + Base64.getEncoder().encodeToString(bytes);
        assertThrows(IllegalStateException.class, () -> c.decifrar(adulterado));
    }

    @Test void chaveErradaOuFormatoInvalidoNaoDecifra() {
        String guardado = CifradorSegredo.efemero().cifrar("segredo");
        assertThrows(IllegalStateException.class, () -> CifradorSegredo.efemero().decifrar(guardado));
        for (String ruim : new String[]{"", "texto-puro", "v1:", "v1:AAAA", "v2:" + guardado.substring(3), null})
            assertThrows(IllegalStateException.class, () -> CifradorSegredo.efemero().decifrar(ruim), "não deveria decifrar: " + ruim);
    }

    @Test void chaveDeveTer32BytesEmBase64() {
        assertDoesNotThrow(() -> CifradorSegredo.deBase64(CifradorSegredo.gerarChaveBase64()));
        for (String ruim : new String[]{"", "curta", "AAAA", Base64.getEncoder().encodeToString(new byte[16]), "!!!não-é-base64!!!", null})
            assertThrows(IllegalArgumentException.class, () -> CifradorSegredo.deBase64(ruim), "não deveria aceitar: " + ruim);
        assertThrows(IllegalArgumentException.class, () -> new CifradorSegredo(new byte[31]));
    }

    @Test void totpComSegredoAdulteradoOuDeOutraChaveNuncaAutentica() {
        CifradorSegredo a = CifradorSegredo.efemero(), b = CifradorSegredo.efemero();
        TotpMfa mfaA = new TotpMfa(a), mfaB = new TotpMfa(b);
        String segredo = mfaA.gerarSegredo();
        Instant agora = Instant.parse("2026-11-24T14:00:00Z");
        String codigo = mfaA.codigoAtual(segredo, agora);
        assertTrue(mfaA.verificar(segredo, codigo, agora));
        assertFalse(mfaB.verificar(segredo, codigo, agora), "outra chave de cifra");
        assertFalse(mfaA.verificar("v1:lixo", codigo, agora));
        assertFalse(mfaA.verificar(null, codigo, agora));
    }

    @Test void segredoMfaGravadoNoBancoNuncaEstaEmTextoPuro() {
        Cenario c = new Cenario();
        Usuario u = c.app.persistencia.usuarios().buscarPorEmail("ana@orion.example").orElseThrow();
        assertTrue(u.segredoMfa().startsWith("v1:"), "armazenado cifrado e versionado");
        // um segredo TOTP em claro seria Base64 de 20 bytes; o valor cifrado não é decodificável como tal
        assertThrows(IllegalArgumentException.class, () -> Base64.getDecoder().decode(u.segredoMfa()));
        // e o login continua funcionando de ponta a ponta
        assertNotNull(c.ana());
    }

    @Test void valorAdulteradoNoBancoImpedeOLogin() {
        Cenario c = new Cenario();
        Usuario u = c.app.persistencia.usuarios().buscarPorEmail("ana@orion.example").orElseThrow();
        String pendente = c.app.auth.iniciarLogin("ana@orion.example", c.cred("ana@orion.example").senha());
        String codigoValido = DadosDemonstracao.codigoMfaAtual(c.app, "ana@orion.example");
        Usuario adulterado = new Usuario(u.id(), u.nome(), u.email(), u.papel(), u.senhaHash(),
                u.segredoMfa().substring(0, u.segredoMfa().length() - 2) + "AA", true, 0, null, u.criadoEm(), u.versao());
        c.app.persistencia.usuarios().atualizar(adulterado);
        assertThrows(AutenticacaoException.class, () -> c.app.auth.confirmarMfa(pendente, codigoValido));
    }
}
