package br.mackenzie.sentineltrade.model;

import java.math.BigDecimal;

/* Linha da tela do analista de risco: conta, dono, saldo e limites. */
public class ContaComLimite {

    private final long contaId;
    private final String numero;
    private final String investidor;
    private final BigDecimal saldo;
    private final LimiteRisco limite;

    public ContaComLimite(long contaId, String numero, String investidor, BigDecimal saldo, LimiteRisco limite) {
        this.contaId = contaId;
        this.numero = numero;
        this.investidor = investidor;
        this.saldo = saldo;
        this.limite = limite;
    }

    public long getContaId() {
        return contaId;
    }

    public String getNumero() {
        return numero;
    }

    public String getInvestidor() {
        return investidor;
    }

    public BigDecimal getSaldo() {
        return saldo;
    }

    public LimiteRisco getLimite() {
        return limite;
    }
}
