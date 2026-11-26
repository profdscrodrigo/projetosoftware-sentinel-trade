package br.mackenzie.sentineltrade.model;

/* Analista de Risco: acompanha ordens rejeitadas e ajusta limites de risco. */
public class AnalistaRisco extends Usuario {

    private static final long serialVersionUID = 1L;

    public AnalistaRisco(long id, String nome, String email) {
        super(id, nome, email);
    }

    @Override
    public Perfil getPerfil() {
        return Perfil.ANALISTA_RISCO;
    }

    @Override
    public String getPaginaInicial() {
        return "/risco";
    }
}
