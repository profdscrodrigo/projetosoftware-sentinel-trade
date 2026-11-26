package br.mackenzie.sentineltrade.model;

import java.math.BigDecimal;

/** Quantidade que o investidor tem de um ativo na carteira. */
public class Posicao {

    private final String codigoAtivo;
    private final String nomeAtivo;
    private final int quantidade;
    private final BigDecimal precoMedio;
    private final BigDecimal cotacao;

    public Posicao(String codigoAtivo, String nomeAtivo, int quantidade, BigDecimal precoMedio, BigDecimal cotacao) {
        this.codigoAtivo = codigoAtivo;
        this.nomeAtivo = nomeAtivo;
        this.quantidade = quantidade;
        this.precoMedio = precoMedio;
        this.cotacao = cotacao == null ? BigDecimal.ZERO : cotacao;
    }

    /** Valor estimado = quantidade x cotação atual. */
    public BigDecimal getValorEstimado() {
        return cotacao.multiply(BigDecimal.valueOf(quantidade));
    }

    public String getCodigoAtivo() {
        return codigoAtivo;
    }

    public String getNomeAtivo() {
        return nomeAtivo;
    }

    public int getQuantidade() {
        return quantidade;
    }

    public BigDecimal getPrecoMedio() {
        return precoMedio;
    }

    public BigDecimal getCotacao() {
        return cotacao;
    }
}
