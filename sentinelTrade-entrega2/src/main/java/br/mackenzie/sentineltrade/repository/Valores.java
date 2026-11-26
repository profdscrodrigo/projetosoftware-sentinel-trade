package br.mackenzie.sentineltrade.repository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;

/** Leitura padronizada de valores em dinheiro do banco (sempre 2 casas decimais). */
final class Valores {

    private Valores() {
    }

    static BigDecimal dinheiro(ResultSet rs, String coluna) throws SQLException {
        BigDecimal valor = rs.getBigDecimal(coluna);
        return valor == null ? null : valor.setScale(2, RoundingMode.HALF_UP);
    }
}
