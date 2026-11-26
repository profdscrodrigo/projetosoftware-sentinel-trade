package br.mackenzie.sentineltrade.integracao;

import java.util.Set;

/* Resposta da Bolsa. Dados de sistemas externos não são confiáveis: por isso a resposta é validada antes de ser usada */
public record RespostaBolsa(String situacao, String mensagem) {

    private static final Set<String> SITUACOES_PERMITIDAS = Set.of("EXECUTADA", "PENDENTE");

    public boolean isValida() {
        return situacao != null && SITUACOES_PERMITIDAS.contains(situacao);
    }

    public boolean isExecutada() {
        return "EXECUTADA".equals(situacao);
    }
}
