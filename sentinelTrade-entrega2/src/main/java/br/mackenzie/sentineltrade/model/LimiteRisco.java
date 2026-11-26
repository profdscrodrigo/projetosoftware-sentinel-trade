package br.mackenzie.sentineltrade.model;

import java.math.BigDecimal;

/* Limites de risco de uma conta (RF-05). */
public class LimiteRisco {

    private final BigDecimal porOrdem;
    private final BigDecimal diario;
    private final BigDecimal altoRisco;

    public LimiteRisco(BigDecimal porOrdem, BigDecimal diario, BigDecimal altoRisco) {
        this.porOrdem = porOrdem;
        this.diario = diario;
        this.altoRisco = altoRisco;
    }

    /** Valor máximo de uma única ordem. */
    public BigDecimal getPorOrdem() {
        return porOrdem;
    }

    /** Soma máxima das ordens feitas no dia. */
    public BigDecimal getDiario() {
        return diario;
    }

    /** Acima deste valor o investidor precisa confirmar a operação (RN-12). */
    public BigDecimal getAltoRisco() {
        return altoRisco;
    }
}
