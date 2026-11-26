package br.orion.sentineltrade.dominio;

/** Códigos estáveis de regras de negócio (motivos de rejeição e de bloqueio). */
public enum CodigoRegra {
    SALDO_INSUFICIENTE("Saldo disponível insuficiente"),
    POSICAO_INSUFICIENTE("Quantidade disponível do ativo insuficiente"),
    LIMITE_POR_ORDEM_EXCEDIDO("Limite de risco por ordem excedido"),
    LIMITE_DIARIO_EXCEDIDO("Limite diário de risco excedido"),
    LIMITE_NAO_CONFIGURADO("Limite de risco não configurado para o investidor"),
    MERCADO_FECHADO("Mercado fechado"),
    ATIVO_INDISPONIVEL("Ativo indisponível para negociação"),
    COTACAO_INDISPONIVEL("Cotação indisponível"),
    COTACAO_DESATUALIZADA("Cotação desatualizada"),
    PRECO_FORA_DA_FAIXA("Preço fora da faixa permitida em relação à cotação"),
    REJEITADA_PELA_CORRETORA("Ordem rejeitada pela corretora"),
    FALHA_INTEGRACAO("Falha de comunicação com a corretora"),
    TRANSICAO_INVALIDA("Transição de estado inválida"),
    ORDEM_NAO_CANCELAVEL("Ordem não pode ser cancelada no estado atual"),
    ORDEM_NAO_ALTERAVEL("Ordem não pode ser alterada no estado atual");

    private final String descricao;
    CodigoRegra(String d) { this.descricao = d; }
    public String descricao() { return descricao; }
}
