package br.mackenzie.sentineltrade.controller.form;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

/* Novo preço (usado para alterar uma ordem e para mudar a cotação de um ativo) */
public class PrecoForm {

    @NotNull
    @DecimalMin("0.01")
    @DecimalMax("1000000.00")
    @Digits(integer = 7, fraction = 2)
    private BigDecimal preco;

    public BigDecimal getPreco() {
        return preco;
    }

    public void setPreco(BigDecimal preco) {
        this.preco = preco;
    }
}
