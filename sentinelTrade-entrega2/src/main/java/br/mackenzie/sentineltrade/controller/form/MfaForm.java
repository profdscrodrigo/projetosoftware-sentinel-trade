package br.mackenzie.sentineltrade.controller.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/* Código de 6 dígitos do segundo fator */
public class MfaForm {

    @NotBlank
    @Pattern(regexp = "^[0-9]{6}$")
    private String codigo;

    public String getCodigo() {
        return codigo;
    }

    public void setCodigo(String codigo) {
        this.codigo = codigo;
    }
}
