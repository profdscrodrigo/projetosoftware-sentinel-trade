package br.orion.sentineltrade.dominio;

import java.time.Instant;
import java.util.UUID;

public final class OrdemCompra extends Ordem {
    OrdemCompra(UUID id, UUID inv, Ticker t, int q, Dinheiro p, String chave, String imp, StatusOrdem s,
                int qe, Dinheiro ve, CodigoRegra m, Instant c, Instant a, long v, TipoOrdem tipo, UUID substitui) {
        super(id, inv, t, q, p, chave, imp, s, qe, ve, m, c, a, v, tipo, substitui);
    }

    @Override public Lado lado() { return Lado.COMPRA; }

    /** RN01: compra exige saldo disponível ≥ quantidade × preço. */
    @Override protected void reservar(Conta conta, Posicao posicao) { conta.bloquear(valorTotal()); }

    @Override protected void liquidar(Conta conta, Posicao posicao, int qtd, Dinheiro preco) {
        Dinheiro reservado = precoLimite().vezes(qtd);
        Dinheiro gasto = preco.vezes(qtd);
        conta.consumirReserva(reservado, gasto);   // devolve a diferença se o preço foi melhor que o limite
        posicao.adicionar(qtd, gasto);
    }

    @Override protected void liberarReservaRestante(Conta conta, Posicao posicao) {
        if (quantidadeRestante() > 0) conta.liberar(precoLimite().vezes(quantidadeRestante()));
    }

    @Override protected boolean precoAceitavel(Dinheiro precoExecucao) { return precoExecucao.compareTo(precoLimite()) <= 0; }
}
