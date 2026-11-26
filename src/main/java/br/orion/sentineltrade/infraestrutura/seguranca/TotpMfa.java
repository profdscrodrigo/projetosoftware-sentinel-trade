package br.orion.sentineltrade.infraestrutura.seguranca;

import br.orion.sentineltrade.aplicacao.porta.ServicoMfa;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Segundo fator simulado por TOTP (RFC 6238, HMAC-SHA1, 30 s, 6 dígitos), com tolerância de ±1 janela.
 * O "segredo" que circula pelo sistema e vai ao banco é um valor OPACO: o segredo real cifrado com AES-256-GCM
 * ({@link CifradorSegredo}). Só esta classe o decifra, no instante da verificação.
 */
public final class TotpMfa implements ServicoMfa {
    private static final int PASSO_SEGUNDOS = 30;
    private final SecureRandom random = new SecureRandom();
    private final CifradorSegredo cifrador;

    public TotpMfa(CifradorSegredo cifrador) { this.cifrador = cifrador; }

    @Override public String gerarSegredo() {
        byte[] b = new byte[20];
        random.nextBytes(b);
        return cifrador.cifrar(Base64.getEncoder().encodeToString(b));
    }

    @Override public String codigoAtual(String segredo, Instant agora) {
        return hotp(Base64.getDecoder().decode(cifrador.decifrar(segredo)), agora.getEpochSecond() / PASSO_SEGUNDOS, 6);
    }

    @Override public boolean verificar(String segredo, String codigo, Instant agora) {
        if (codigo == null || codigo.length() != 6) return false;
        byte[] chave;
        try { chave = Base64.getDecoder().decode(cifrador.decifrar(segredo)); }
        catch (IllegalStateException e) { return false; }                      // segredo adulterado ou chave errada: nunca autentica
        long contador = agora.getEpochSecond() / PASSO_SEGUNDOS;
        boolean ok = false;
        for (long c = contador - 1; c <= contador + 1; c++) {               // sem "return antecipado": tempo uniforme
            ok |= MessageDigest.isEqual(hotp(chave, c, 6).getBytes(StandardCharsets.UTF_8), codigo.getBytes(StandardCharsets.UTF_8));
        }
        return ok;
    }

    /** HOTP (RFC 4226). Público para permitir teste com os vetores oficiais da RFC 6238. */
    public static String hotp(byte[] chave, long contador, int digitos) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(chave, "HmacSHA1"));
            byte[] h = mac.doFinal(ByteBuffer.allocate(8).putLong(contador).array());
            int off = h[h.length - 1] & 0x0F;
            int bin = ((h[off] & 0x7F) << 24) | ((h[off + 1] & 0xFF) << 16) | ((h[off + 2] & 0xFF) << 8) | (h[off + 3] & 0xFF);
            int mod = 1;
            for (int i = 0; i < digitos; i++) mod *= 10;
            return String.format("%0" + digitos + "d", bin % mod);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA1 indisponível", e);
        }
    }
}
