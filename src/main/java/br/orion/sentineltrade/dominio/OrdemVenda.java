package br.orion.sentineltrade.dominio;

import java.time.Instant;
import java.util.UUID;

public final class OrdemVenda extends Ordem {
    OrdemVenda(UUID id, UUID inv, Ticker t, int q, Dinheiro p, String chave, String imp, StatusOrdem s,
               int qe, Dinheiro ve, CodigoRegra m, Instant c, Instant a, long v, TipoOrdem tipo, UUID substitui) {
        super(id, inv, t, q, p, chave, imp, s, qe, ve, m, c, a, v, tipo, substitui);
    }

    @Override public Lado lado() { return Lado.VENDA; }

    /** RN02: venda exige quantidade disponível do ativo na carteira. */
    @Override protected void reservar(Conta conta, Posicao posicao) { posicao.bloquear(quantidade()); }

    @Override protected void liquidar(Conta conta, Posicao posicao, int qtd, Dinheiro preco) {
        posicao.baixarBloqueada(qtd);
        conta.creditar(preco.vezes(qtd));
    }

    @Override protected void liberarReservaRestante(Conta conta, Posicao posicao) {
        if (quantidadeRestante() > 0) posicao.liberar(quantidadeRestante());
    }

    @Override protected boolean precoAceitavel(Dinheiro precoExecucao) { return precoExecucao.compareTo(precoLimite()) >= 0; }
}
