package br.mackenzie.sentineltrade.model;

import java.math.BigDecimal;

/* Ativo negociável (fictício) com a cotação mais recente. */
public class Ativo {

    private final long id;
    private final String codigo;
    private final String nome;
    private final String tipo;
    private final boolean negociavel;
    private final BigDecimal cotacao;
    private final BigDecimal variacao;

    public Ativo(long id, String codigo, String nome, String tipo, boolean negociavel,
                 BigDecimal cotacao, BigDecimal variacao) {
        this.id = id;
        this.codigo = codigo;
        this.nome = nome;
        this.tipo = tipo;
        this.negociavel = negociavel;
        this.cotacao = cotacao == null ? BigDecimal.ZERO : cotacao;
        this.variacao = variacao == null ? BigDecimal.ZERO : variacao;
    }

    public long getId() {
        return id;
    }

    public String getCodigo() {
        return codigo;
    }

    public String getNome() {
        return nome;
    }

    public String getTipo() {
        return tipo;
    }

    public boolean isNegociavel() {
        return negociavel;
    }

    public BigDecimal getCotacao() {
        return cotacao;
    }

    public BigDecimal getVariacao() {
        return variacao;
    }

    public boolean isEmAlta() {
        return variacao.signum() >= 0;
    }
}
