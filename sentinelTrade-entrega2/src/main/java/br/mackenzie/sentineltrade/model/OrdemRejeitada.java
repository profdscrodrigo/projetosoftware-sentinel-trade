package br.mackenzie.sentineltrade.model;

import java.math.BigDecimal;

/* Linha da tela do analista de risco: ordem rejeitada e o motivo */
public class OrdemRejeitada {

    private final String dataHora;
    private final String conta;
    private final String investidor;
    private final String codigoAtivo;
    private final String tipo;
    private final BigDecimal valorTotal;
    private final String motivo;

    public OrdemRejeitada(String dataHora, String conta, String investidor, String codigoAtivo,
                          String tipo, BigDecimal valorTotal, String motivo) {
        this.dataHora = dataHora;
        this.conta = conta;
        this.investidor = investidor;
        this.codigoAtivo = codigoAtivo;
        this.tipo = tipo;
        this.valorTotal = valorTotal;
        this.motivo = motivo;
    }

    public String getDataHora() {
        return dataHora;
    }

    public String getConta() {
        return conta;
    }

    public String getInvestidor() {
        return investidor;
    }

    public String getCodigoAtivo() {
        return codigoAtivo;
    }

    public String getTipo() {
        return tipo;
    }

    public BigDecimal getValorTotal() {
        return valorTotal;
    }

    public String getMotivo() {
        return motivo;
    }
}
