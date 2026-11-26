package br.orion.sentineltrade.aplicacao.servico;

/**
 * Dados BRUTOS vindos da interface (todos texto): validados no servidor antes de qualquer uso.
 * {@code tipo}: "LIMITE" (padrão) ou "MERCADO"; em ordem a mercado o preço deve vir vazio.
 */
public record SolicitacaoOrdem(String lado, String tipo, String ticker, String quantidade, String preco,
                               String chaveIdempotencia, boolean confirmada) {
    /** Ordem a preço limite (forma usada até a Fase 1). */
    public SolicitacaoOrdem(String lado, String ticker, String quantidade, String preco, String chaveIdempotencia, boolean confirmada) {
        this(lado, "LIMITE", ticker, quantidade, preco, chaveIdempotencia, confirmada);
    }
}
