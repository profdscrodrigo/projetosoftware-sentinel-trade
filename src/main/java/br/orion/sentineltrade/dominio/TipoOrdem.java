package br.orion.sentineltrade.dominio;

/**
 * LIMITE: o investidor informa o preço limite.
 * MERCADO: o investidor não informa preço; o sistema usa um preço de PROTEÇÃO (cotação ± faixa permitida) como limite,
 * de modo que a reserva de saldo e os controles de risco continuam valendo. A execução ocorre ao preço de mercado.
 */
public enum TipoOrdem { LIMITE, MERCADO }
