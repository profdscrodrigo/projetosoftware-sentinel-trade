package br.mackenzie.sentineltrade.model;

import java.io.Serializable;

/** Classe ABSTRATA: não existe "usuário genérico", só Investidor, Administrador ou Analista de Risco. Mostra abstração, encapsulamento (atributos privados) e herança. */
public abstract class Usuario implements Serializable {

    private static final long serialVersionUID = 1L;

    private final long id;
    private final String nome;
    private final String email;

    protected Usuario(long id, String nome, String email) {
        this.id = id;
        this.nome = nome;
        this.email = email;
    }

    public long getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public String getEmail() {
        return email;
    }

    /** Cada subclasse informa o próprio perfil (polimorfismo). */
    public abstract Perfil getPerfil();

    /** Página que o usuário vê logo depois de entrar. */
    public abstract String getPaginaInicial();
}
