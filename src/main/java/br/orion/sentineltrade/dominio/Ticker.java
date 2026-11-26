package br.orion.sentineltrade.dominio;

import br.orion.sentineltrade.dominio.Excecoes.ValidacaoException;
import java.util.Locale;
import java.util.regex.Pattern;

/** Código de negociação (ex.: PETR4, MXRF11). Allowlist: 4 letras maiúsculas + 1 ou 2 dígitos. */
public record Ticker(String codigo) {
    private static final Pattern PADRAO = Pattern.compile("^[A-Z]{4}[0-9]{1,2}$");

    public Ticker {
        if (codigo == null || !PADRAO.matcher(codigo).matches())
            throw new ValidacaoException("Código de ativo inválido (formato esperado: PETR4, MXRF11)");
    }

    /** Normaliza (trim + maiúsculas) e valida entrada externa. */
    public static Ticker de(String bruto) {
        return new Ticker(bruto == null ? null : bruto.strip().toUpperCase(Locale.ROOT));
    }

    @Override public String toString() { return codigo; }
}
