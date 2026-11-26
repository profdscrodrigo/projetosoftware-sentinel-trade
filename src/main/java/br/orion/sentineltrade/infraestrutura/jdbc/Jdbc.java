package br.orion.sentineltrade.infraestrutura.jdbc;

import br.orion.sentineltrade.dominio.Dinheiro;
import br.orion.sentineltrade.dominio.Ticker;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Auxiliar de acesso: TODA consulta é um PreparedStatement com parâmetros posicionais (?).
 * Nenhum dado de entrada é concatenado em SQL — prevenção de SQL Injection.
 */
final class Jdbc {
    @FunctionalInterface interface Mapeador<T> { T mapear(ResultSet rs) throws SQLException; }

    private final TransacaoJdbc tx;
    Jdbc(TransacaoJdbc tx) { this.tx = tx; }

    int atualizar(String sql, Object... p) {
        return tx.comConexao(c -> {
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                vincular(ps, p);
                return ps.executeUpdate();
            }
        });
    }

    <T> List<T> consultar(String sql, Mapeador<T> m, Object... p) {
        return tx.comConexao(c -> {
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                vincular(ps, p);
                try (ResultSet rs = ps.executeQuery()) {
                    List<T> r = new ArrayList<>();
                    while (rs.next()) r.add(m.mapear(rs));
                    return r;
                }
            }
        });
    }

    <T> Optional<T> um(String sql, Mapeador<T> m, Object... p) {
        List<T> r = consultar(sql, m, p);
        return r.isEmpty() ? Optional.empty() : Optional.of(r.get(0));
    }

    private static void vincular(PreparedStatement ps, Object[] p) throws SQLException {
        for (int i = 0; i < p.length; i++) {
            Object v = p[i];
            int pos = i + 1;
            if (v == null) ps.setObject(pos, null);
            else if (v instanceof Instant t) ps.setObject(pos, OffsetDateTime.ofInstant(t, ZoneOffset.UTC));
            else if (v instanceof UUID || v instanceof Enum<?> || v instanceof Ticker) ps.setString(pos, v.toString());
            else if (v instanceof Dinheiro d) ps.setBigDecimal(pos, d.valor());
            else if (v instanceof BigDecimal b) ps.setBigDecimal(pos, b);
            else ps.setObject(pos, v);
        }
    }

    static Instant instante(ResultSet rs, String coluna) throws SQLException {
        OffsetDateTime o = rs.getObject(coluna, OffsetDateTime.class);
        return o == null ? null : o.toInstant();
    }
    static Dinheiro dinheiro(ResultSet rs, String coluna) throws SQLException { return Dinheiro.de(rs.getBigDecimal(coluna)); }
    static UUID uuid(ResultSet rs, String coluna) throws SQLException { return UUID.fromString(rs.getString(coluna)); }
}
