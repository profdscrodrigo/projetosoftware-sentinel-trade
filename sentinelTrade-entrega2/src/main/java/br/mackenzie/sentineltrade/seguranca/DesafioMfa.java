package br.mackenzie.sentineltrade.seguranca;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;

/** Segundo fator de autenticação SIMULADO (RF-01). Gera um código de 6 dígitos que vale por 5 minutos e aceita no máximo 3 tentativas. */
public class DesafioMfa implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final SecureRandom ALEATORIO = new SecureRandom();
    public static final int MAXIMO_TENTATIVAS = 3;
    public static final Duration VALIDADE = Duration.ofMinutes(5);

    public enum Resultado { OK, CODIGO_ERRADO, EXPIRADO, TENTATIVAS_ESGOTADAS }

    private final long usuarioId;
    private final String codigo;
    private final Instant expiraEm;
    private int tentativas;

    public DesafioMfa(long usuarioId, String codigo, Instant criadoEm) {
        this.usuarioId = usuarioId;
        this.codigo = codigo;
        this.expiraEm = criadoEm.plus(VALIDADE);
    }

    /** Cria um desafio com código aleatório seguro (SecureRandom). */
    public static DesafioMfa novo(long usuarioId) {
        String codigo = String.format("%06d", ALEATORIO.nextInt(1_000_000));
        return new DesafioMfa(usuarioId, codigo, Instant.now());
    }

    public Resultado verificar(String codigoDigitado, Instant agora) {
        if (tentativas >= MAXIMO_TENTATIVAS) {
            return Resultado.TENTATIVAS_ESGOTADAS;
        }
        if (agora.isAfter(expiraEm)) {
            return Resultado.EXPIRADO;
        }
        tentativas++;
        boolean igual = codigoDigitado != null && MessageDigest.isEqual(
                codigo.getBytes(StandardCharsets.UTF_8), codigoDigitado.getBytes(StandardCharsets.UTF_8));
        if (igual) {
            return Resultado.OK;
        }
        return tentativas >= MAXIMO_TENTATIVAS ? Resultado.TENTATIVAS_ESGOTADAS : Resultado.CODIGO_ERRADO;
    }

    public long getUsuarioId() {
        return usuarioId;
    }

    /** Usado só para mostrar o código no "celular simulado". */
    public String getCodigo() {
        return codigo;
    }

    public int getTentativasRestantes() {
        return Math.max(0, MAXIMO_TENTATIVAS - tentativas);
    }
}
