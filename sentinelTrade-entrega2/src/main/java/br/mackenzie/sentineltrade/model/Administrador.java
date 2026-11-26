package br.mackenzie.sentineltrade.model;

/* Administrador: gerencia ativos, cotações, parâmetros e a recuperação de ordens. */
public class Administrador extends Usuario {

    private static final long serialVersionUID = 1L;

    public Administrador(long id, String nome, String email) {
        super(id, nome, email);
    }

    @Override
    public Perfil getPerfil() {
        return Perfil.ADMINISTRADOR;
    }

    @Override
    public String getPaginaInicial() {
        return "/admin";
    }
}
