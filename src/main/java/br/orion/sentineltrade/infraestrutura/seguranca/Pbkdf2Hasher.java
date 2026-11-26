package br.orion.sentineltrade.infraestrutura.seguranca;

import br.orion.sentineltrade.aplicacao.porta.HasherSenha;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Hash de senha com PBKDF2-HMAC-SHA256, salt aleatório de 16 bytes por senha e iterações configuráveis
 * (OWASP 2023: ≥ 600.000). Formato: pbkdf2_sha256$iteracoes$salt$hash (as iterações ficam no próprio hash,
 * permitindo aumentar o custo no futuro sem invalidar senhas antigas).
 */
public final class Pbkdf2Hasher implements HasherSenha {
    public static final int ITERACOES_PADRAO = 600_000;
    private final int iteracoes;
    private final SecureRandom random = new SecureRandom();

    public Pbkdf2Hasher(int iteracoes) { this.iteracoes = iteracoes; }
    public Pbkdf2Hasher() { this(ITERACOES_PADRAO); }

    @Override public String hash(char[] senha) {
        byte[] salt = new byte[16];
        random.nextBytes(salt);
        byte[] h = derivar(senha, salt, iteracoes);
        return "pbkdf2_sha256$" + iteracoes + "$" + Base64.getEncoder().encodeToString(salt) + "$" + Base64.getEncoder().encodeToString(h);
    }

    @Override public boolean verifica(char[] senha, String armazenado) {
        try {
            String[] p = armazenado.split("\\$");
            if (p.length != 4 || !p[0].equals("pbkdf2_sha256")) return false;
            int it = Integer.parseInt(p[1]);
            byte[] esperado = Base64.getDecoder().decode(p[3]);
            byte[] calculado = derivar(senha, Base64.getDecoder().decode(p[2]), it);
            return MessageDigest.isEqual(esperado, calculado);               // comparação em tempo constante
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static byte[] derivar(char[] senha, byte[] salt, int it) {
        PBEKeySpec spec = new PBEKeySpec(senha, salt, it, 256);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("PBKDF2 indisponível", e);
        } finally {
            spec.clearPassword();
        }
    }
}
