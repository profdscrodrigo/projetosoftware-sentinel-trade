package br.orion.sentineltrade.dominio;

import br.orion.sentineltrade.dominio.Excecoes.ValidacaoException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

/** Valor monetário imutável, sempre com 2 casas decimais (BigDecimal — nunca double). */
public final class Dinheiro implements Comparable<Dinheiro> {
    public static final Dinheiro ZERO = new Dinheiro(BigDecimal.ZERO);
    private final BigDecimal valor;

    private Dinheiro(BigDecimal v) { this.valor = v.setScale(2, RoundingMode.HALF_EVEN); }

    public static Dinheiro de(BigDecimal v) {
        if (v == null) throw new ValidacaoException("Valor monetário obrigatório");
        return new Dinheiro(v);
    }
    public static Dinheiro de(String texto) {
        try { return de(new BigDecimal(texto.strip())); }
        catch (NumberFormatException | NullPointerException e) { throw new ValidacaoException("Valor monetário inválido"); }
    }

    public Dinheiro mais(Dinheiro o) { return new Dinheiro(valor.add(o.valor)); }
    public Dinheiro menos(Dinheiro o) { return new Dinheiro(valor.subtract(o.valor)); }
    public Dinheiro vezes(long q) { return new Dinheiro(valor.multiply(BigDecimal.valueOf(q))); }
    /** valor * parte / total, arredondado (usado no rateio do custo médio). */
    public Dinheiro proporcional(long parte, long total) {
        return new Dinheiro(valor.multiply(BigDecimal.valueOf(parte)).divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_EVEN));
    }
    public Dinheiro dividir(long d) { return new Dinheiro(valor.divide(BigDecimal.valueOf(d), 2, RoundingMode.HALF_EVEN)); }

    public boolean positivo() { return valor.signum() > 0; }
    public boolean negativo() { return valor.signum() < 0; }
    public BigDecimal valor() { return valor; }

    public String formatado() { return String.format(Locale.of("pt", "BR"), "R$ %,.2f", valor); }

    @Override public int compareTo(Dinheiro o) { return valor.compareTo(o.valor); }
    @Override public boolean equals(Object o) { return o instanceof Dinheiro d && valor.compareTo(d.valor) == 0; }
    @Override public int hashCode() { return valor.stripTrailingZeros().hashCode(); }
    @Override public String toString() { return valor.toPlainString(); }
}
