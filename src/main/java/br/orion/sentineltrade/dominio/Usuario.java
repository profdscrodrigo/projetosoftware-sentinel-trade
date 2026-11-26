package br.orion.sentineltrade.dominio;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/** Usuário do sistema. Atributos privados; hash de senha e segredo MFA nunca aparecem em toString. */
public final class Usuario {
    private final UUID id;
    private final String nome;
    private final String email;
    private final Papel papel;
    private String senhaHash;
    private final String segredoMfa;
    private boolean ativo;
    private int falhasLogin;
    private Instant bloqueadoAte;
    private final Instant criadoEm;
    private long versao;

    public Usuario(UUID id, String nome, String email, Papel papel, String senhaHash, String segredoMfa,
                   boolean ativo, int falhasLogin, Instant bloqueadoAte, Instant criadoEm, long versao) {
        this.id = id; this.nome = nome; this.email = email; this.papel = papel;
        this.senhaHash = senhaHash; this.segredoMfa = segredoMfa; this.ativo = ativo;
        this.falhasLogin = falhasLogin; this.bloqueadoAte = bloqueadoAte; this.criadoEm = criadoEm; this.versao = versao;
    }

    public static Usuario novo(String nome, String email, Papel papel, String senhaHash, String segredoMfa, Instant agora) {
        return new Usuario(UUID.randomUUID(), nome, email, papel, senhaHash, segredoMfa, true, 0, null, agora, 0);
    }

    public boolean bloqueado(Instant agora) { return bloqueadoAte != null && agora.isBefore(bloqueadoAte); }

    public void registrarFalhaLogin(Instant agora, int maxFalhas, Duration bloqueio) {
        falhasLogin++;
        if (falhasLogin >= maxFalhas) { bloqueadoAte = agora.plus(bloqueio); falhasLogin = 0; }
    }
    public void registrarSucessoLogin() { falhasLogin = 0; bloqueadoAte = null; }

    public UUID id() { return id; }
    public String nome() { return nome; }
    public String email() { return email; }
    public Papel papel() { return papel; }
    public String senhaHash() { return senhaHash; }
    public String segredoMfa() { return segredoMfa; }
    public boolean ativo() { return ativo; }
    public int falhasLogin() { return falhasLogin; }
    public Instant bloqueadoAte() { return bloqueadoAte; }
    public Instant criadoEm() { return criadoEm; }
    public long versao() { return versao; }
    public void definirSenhaHash(String hash) { this.senhaHash = hash; }
    /** Uso exclusivo da camada de persistência. */
    public void versaoPersistida(long v) { this.versao = v; }

    @Override public String toString() { return "Usuario[" + id + ", " + papel + "]"; }
}
