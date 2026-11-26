package br.orion.sentineltrade.infraestrutura.seguranca;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Cifra segredos em repouso (ex.: segredo MFA) com AES-256-GCM: confidencialidade + integridade (qualquer alteração do
 * texto cifrado é detectada). IV aleatório de 12 bytes por operação. Formato armazenado: {@code v1:base64(iv ‖ cifrado+tag)};
 * o prefixo de versão permite trocar algoritmo/chave no futuro (rotação) sem ambiguidade.
 *
 * A chave (32 bytes, Base64) vem da variável de ambiente MFA_ENCRYPTION_KEY — nunca do código nem do repositório.
 * Em produção deve ficar em um cofre (KMS/HSM).
 */
public final class CifradorSegredo {
    private static final String PREFIXO = "v1:";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private final SecretKeySpec chave;
    private final SecureRandom random = new SecureRandom();

    public CifradorSegredo(byte[] chave32) {
        if (chave32 == null || chave32.length != 32) throw new IllegalArgumentException("A chave AES-256 deve ter exatamente 32 bytes");
        this.chave = new SecretKeySpec(chave32, "AES");
    }

    public static CifradorSegredo deBase64(String base64) {
        try { return new CifradorSegredo(Base64.getDecoder().decode(base64.trim())); }
        catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException("MFA_ENCRYPTION_KEY inválida: use 32 bytes em Base64 (ex.: openssl rand -base64 32)");
        }
    }

    /** Chave aleatória descartável: serve ao modo em memória, em que nada sobrevive ao processo. */
    public static CifradorSegredo efemero() {
        byte[] k = new byte[32];
        new SecureRandom().nextBytes(k);
        return new CifradorSegredo(k);
    }

    public static String gerarChaveBase64() {
        byte[] k = new byte[32];
        new SecureRandom().nextBytes(k);
        return Base64.getEncoder().encodeToString(k);
    }

    public String cifrar(String texto) {
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, chave, new GCMParameterSpec(TAG_BITS, iv));
            byte[] cifrado = c.doFinal(texto.getBytes(StandardCharsets.UTF_8));
            return PREFIXO + Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + cifrado.length).put(iv).put(cifrado).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Falha ao cifrar segredo", e);
        }
    }

    /** @throws IllegalStateException se o valor foi adulterado, está mal formado ou foi cifrado com outra chave. */
    public String decifrar(String armazenado) {
        try {
            if (armazenado == null || !armazenado.startsWith(PREFIXO)) throw new IllegalStateException("Formato de segredo desconhecido");
            byte[] tudo = Base64.getDecoder().decode(armazenado.substring(PREFIXO.length()));
            if (tudo.length <= IV_BYTES) throw new IllegalStateException("Segredo cifrado truncado");
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, chave, new GCMParameterSpec(TAG_BITS, tudo, 0, IV_BYTES));
            return new String(c.doFinal(tudo, IV_BYTES, tudo.length - IV_BYTES), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("Segredo cifrado inválido (adulterado ou chave incorreta)");
        }
    }
}
