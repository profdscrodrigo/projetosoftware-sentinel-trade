package br.mackenzie.sentineltrade.controller.form;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import br.mackenzie.sentineltrade.model.LimiteRisco;

/* Novos limites de risco de uma conta */
public class LimiteForm {

    @NotNull
    @DecimalMin("1.00")
    @DecimalMax("10000000.00")
    @Digits(integer = 8, fraction = 2)
    private BigDecimal porOrdem;

    @NotNull
    @DecimalMin("1.00")
    @DecimalMax("10000000.00")
    @Digits(integer = 8, fraction = 2)
    private BigDecimal diario;

    @NotNull
    @DecimalMin("1.00")
    @DecimalMax("10000000.00")
    @Digits(integer = 8, fraction = 2)
    private BigDecimal altoRisco;

    public LimiteRisco paraLimite() {
        return new LimiteRisco(porOrdem, diario, altoRisco);
    }

    public BigDecimal getPorOrdem() {
        return porOrdem;
    }

    public void setPorOrdem(BigDecimal porOrdem) {
        this.porOrdem = porOrdem;
    }

    public BigDecimal getDiario() {
        return diario;
    }

    public void setDiario(BigDecimal diario) {
        this.diario = diario;
    }

    public BigDecimal getAltoRisco() {
        return altoRisco;
    }

    public void setAltoRisco(BigDecimal altoRisco) {
        this.altoRisco = altoRisco;
    }
}
