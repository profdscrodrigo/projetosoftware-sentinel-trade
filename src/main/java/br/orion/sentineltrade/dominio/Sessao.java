package br.orion.sentineltrade.dominio;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Sessão de acesso. Nasce PENDENTE_MFA (senha validada) e só vira ATIVA após o 2º fator.
 * O token em claro nunca é armazenado: apenas o hash SHA-256.
 */
public final class Sessao {
    public enum Estado { PENDENTE_MFA, ATIVA, REVOGADA }

    private final UUID id;
    private final UUID usuarioId;
    private final Papel papel;
    private Estado estado;
    private final String tokenHash;
    private final Instant criadaEm;
    private final Instant expiraEm;
    private int tentativasMfa;

    public Sessao(UUID id, UUID usuarioId, Papel papel, Estado estado, String tokenHash,
                  Instant criadaEm, Instant expiraEm, int tentativasMfa) {
        this.id = id; this.usuarioId = usuarioId; this.papel = papel; this.estado = estado;
        this.tokenHash = tokenHash; this.criadaEm = criadaEm; this.expiraEm = expiraEm; this.tentativasMfa = tentativasMfa;
    }

    public static Sessao nova(UUID usuarioId, Papel papel, Estado estado, String tokenHash, Instant agora, Duration ttl) {
        return new Sessao(UUID.randomUUID(), usuarioId, papel, estado, tokenHash, agora, agora.plus(ttl), 0);
    }

    public boolean valida(Instant agora, Estado esperado) { return estado == esperado && agora.isBefore(expiraEm); }
    public int registrarFalhaMfa() { return ++tentativasMfa; }
    public void revogar() { this.estado = Estado.REVOGADA; }

    public UUID id() { return id; }
    public UUID usuarioId() { return usuarioId; }
    public Papel papel() { return papel; }
    public Estado estado() { return estado; }
    public String tokenHash() { return tokenHash; }
    public Instant criadaEm() { return criadaEm; }
    public Instant expiraEm() { return expiraEm; }
    public int tentativasMfa() { return tentativasMfa; }

    @Override public String toString() { return "Sessao[" + id + ", " + estado + "]"; }
}
