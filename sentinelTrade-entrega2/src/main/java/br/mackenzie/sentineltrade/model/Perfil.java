package br.mackenzie.sentineltrade.model;

/* Perfis de acesso. Cada perfil só enxerga as próprias telas (menor privilégio - RN-06) */
public enum Perfil {
    INVESTIDOR("Investidor"),
    ADMINISTRADOR("Administrador"),
    ANALISTA_RISCO("Analista de risco");

    private final String descricao;

    Perfil(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}
