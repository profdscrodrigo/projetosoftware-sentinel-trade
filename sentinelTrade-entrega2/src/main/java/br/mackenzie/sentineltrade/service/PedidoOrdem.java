package br.mackenzie.sentineltrade.service;

import java.math.BigDecimal;

import br.mackenzie.sentineltrade.model.TipoOrdem;

/** Dados de uma nova ordem, já validados na tela. */
public record PedidoOrdem(String chaveIdempotencia, String codigoAtivo, TipoOrdem tipo,
                          int quantidade, BigDecimal preco, boolean confirmaAltoRisco) {
}
