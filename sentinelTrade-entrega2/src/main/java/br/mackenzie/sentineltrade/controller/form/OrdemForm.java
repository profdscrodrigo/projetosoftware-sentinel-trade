package br.mackenzie.sentineltrade.controller.form;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import br.mackenzie.sentineltrade.model.TipoOrdem;
import br.mackenzie.sentineltrade.service.PedidoOrdem;

/* Formulário de nova ordem. Cada campo tem formato, tamanho e faixa definidos (sanitização de dados) */
public class OrdemForm {

    @NotBlank(message = "Escolha um ativo.")
    @Pattern(regexp = "^[A-Z]{4}[0-9]{1,2}$", message = "Código de ativo inválido.")
    private String ativo;

    @NotNull(message = "Escolha COMPRA ou VENDA.")
    private TipoOrdem tipo;

    @NotNull(message = "Informe a quantidade.")
    @Positive(message = "A quantidade deve ser maior que zero.")
    @Max(value = 100000, message = "A quantidade máxima por ordem é 100.000.")
    private Integer quantidade;

    @NotNull(message = "Informe o preço.")
    @DecimalMin(value = "0.01", message = "O preço deve ser de pelo menos R$ 0,01.")
    @DecimalMax(value = "1000000.00", message = "O preço máximo é R$ 1.000.000,00.")
    @Digits(integer = 7, fraction = 2, message = "Use no máximo 2 casas decimais no preço.")
    private BigDecimal preco;

    /** Identificador único gerado ao abrir o formulário (evita ordem duplicada - RF-14). */
    @NotBlank(message = "Formulário inválido. Abra a tela de nova ordem novamente.")
    @Pattern(regexp = "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$",
            message = "Formulário inválido. Abra a tela de nova ordem novamente.")
    private String chaveIdempotencia;

    private boolean confirmaAltoRisco;

    public PedidoOrdem paraPedido() {
        return new PedidoOrdem(chaveIdempotencia, ativo, tipo, quantidade, preco, confirmaAltoRisco);
    }

    public String getAtivo() {
        return ativo;
    }

    public void setAtivo(String ativo) {
        this.ativo = ativo;
    }

    public TipoOrdem getTipo() {
        return tipo;
    }

    public void setTipo(TipoOrdem tipo) {
        this.tipo = tipo;
    }

    public Integer getQuantidade() {
        return quantidade;
    }

    public void setQuantidade(Integer quantidade) {
        this.quantidade = quantidade;
    }

    public BigDecimal getPreco() {
        return preco;
    }

    public void setPreco(BigDecimal preco) {
        this.preco = preco;
    }

    public String getChaveIdempotencia() {
        return chaveIdempotencia;
    }

    public void setChaveIdempotencia(String chaveIdempotencia) {
        this.chaveIdempotencia = chaveIdempotencia;
    }

    public boolean isConfirmaAltoRisco() {
        return confirmaAltoRisco;
    }

    public void setConfirmaAltoRisco(boolean confirmaAltoRisco) {
        this.confirmaAltoRisco = confirmaAltoRisco;
    }
}
