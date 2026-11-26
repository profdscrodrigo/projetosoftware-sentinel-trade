package br.orion.sentineltrade.dominio;

import java.time.Instant;
import java.util.UUID;

public final class Notificacao {
    private final UUID id;
    private final UUID investidorId;
    private final TipoNotificacao tipo;
    private final String mensagem;
    private final Instant criadaEm;
    private Instant enviadaEm;

    public Notificacao(UUID id, UUID investidorId, TipoNotificacao tipo, String mensagem, Instant criadaEm, Instant enviadaEm) {
        this.id = id; this.investidorId = investidorId; this.tipo = tipo;
        this.mensagem = mensagem; this.criadaEm = criadaEm; this.enviadaEm = enviadaEm;
    }
    public static Notificacao nova(UUID investidorId, TipoNotificacao tipo, String mensagem, Instant agora) {
        return new Notificacao(UUID.randomUUID(), investidorId, tipo, mensagem, agora, null);
    }
    public void marcarEnviada(Instant agora) { this.enviadaEm = agora; }

    public UUID id() { return id; }
    public UUID investidorId() { return investidorId; }
    public TipoNotificacao tipo() { return tipo; }
    public String mensagem() { return mensagem; }
    public Instant criadaEm() { return criadaEm; }
    public Instant enviadaEm() { return enviadaEm; }
}
