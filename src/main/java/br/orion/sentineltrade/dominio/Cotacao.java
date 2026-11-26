package br.orion.sentineltrade.dominio;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

/** Cotação recebida do provedor. Dado externo: a validação semântica é feita por CotacaoService. */
public record Cotacao(Ticker ticker, Dinheiro preco, Dinheiro fechamentoAnterior, Instant instante) {
    /** Variação percentual em relação ao fechamento anterior (2 casas). */
    public BigDecimal variacaoPercentual() {
        if (fechamentoAnterior.valor().signum() == 0) return BigDecimal.ZERO.setScale(2);
        return preco.valor().subtract(fechamentoAnterior.valor())
                .multiply(BigDecimal.valueOf(100))
                .divide(fechamentoAnterior.valor(), 2, RoundingMode.HALF_EVEN);
    }
}
