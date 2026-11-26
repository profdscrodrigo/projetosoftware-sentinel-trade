package br.mackenzie.sentineltrade.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Locale;

import org.springframework.stereotype.Component;

/** Formata valores no padrão brasileiro. Nas telas é usado como ${@fmt.dinheiro(valor)}. */
@Component("fmt")
public class Formatador {

    private static final Locale BRASIL = Locale.forLanguageTag("pt-BR");

    /** 1234.5 -> "R$ 1.234,50" */
    public static String formatarDinheiro(BigDecimal valor) {
        BigDecimal v = valor == null ? BigDecimal.ZERO : valor.setScale(2, RoundingMode.HALF_UP);
        return NumberFormat.getCurrencyInstance(BRASIL).format(v);
    }

    public String dinheiro(BigDecimal valor) {
        return formatarDinheiro(valor);
    }

    /** 1.2 -> "+1,20%" */
    public String percentual(BigDecimal valor) {
        BigDecimal v = valor == null ? BigDecimal.ZERO : valor.setScale(2, RoundingMode.HALF_UP);
        NumberFormat formato = NumberFormat.getNumberInstance(BRASIL);
        formato.setMinimumFractionDigits(2);
        formato.setMaximumFractionDigits(2);
        return (v.signum() >= 0 ? "+" : "") + formato.format(v) + "%";
    }
}
