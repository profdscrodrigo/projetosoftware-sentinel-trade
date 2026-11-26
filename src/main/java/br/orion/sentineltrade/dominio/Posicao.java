package br.orion.sentineltrade.dominio;

import br.orion.sentineltrade.dominio.Excecoes.RegraNegocioException;
import java.util.UUID;

/** Posição do investidor em um ativo (carteira). Quantidade bloqueada = reservada por ordens de venda. */
public final class Posicao {
    private final UUID investidorId;
    private final Ticker ticker;
    private int quantidade;
    private int quantidadeBloqueada;
    private Dinheiro custoTotal;
    private long versao;

    public Posicao(UUID investidorId, Ticker ticker, int quantidade, int quantidadeBloqueada, Dinheiro custoTotal, long versao) {
        this.investidorId = investidorId; this.ticker = ticker; this.quantidade = quantidade;
        this.quantidadeBloqueada = quantidadeBloqueada; this.custoTotal = custoTotal; this.versao = versao;
    }

    public static Posicao vazia(UUID investidorId, Ticker ticker) {
        return new Posicao(investidorId, ticker, 0, 0, Dinheiro.ZERO, 0);
    }

    public int quantidadeDisponivel() { return quantidade - quantidadeBloqueada; }

    public void bloquear(int q) {
        if (quantidadeDisponivel() < q) throw new RegraNegocioException(CodigoRegra.POSICAO_INSUFICIENTE);
        quantidadeBloqueada += q;
    }

    public void liberar(int q) {
        if (quantidadeBloqueada < q) throw new IllegalStateException("Inconsistência: quantidade bloqueada menor que a reserva");
        quantidadeBloqueada -= q;
    }

    /** Execução de venda: baixa da quantidade (que estava bloqueada) e do custo proporcional. */
    public void baixarBloqueada(int q) {
        if (quantidadeBloqueada < q) throw new IllegalStateException("Inconsistência: venda acima da quantidade bloqueada");
        Dinheiro custoBaixado = custoTotal.proporcional(q, quantidade);
        quantidade -= q;
        quantidadeBloqueada -= q;
        custoTotal = custoTotal.menos(custoBaixado);
    }

    /** Execução de compra: acrescenta quantidade e custo. */
    public void adicionar(int q, Dinheiro custo) {
        quantidade += q;
        custoTotal = custoTotal.mais(custo);
    }

    public Dinheiro precoMedio() { return quantidade == 0 ? Dinheiro.ZERO : custoTotal.dividir(quantidade); }

    public UUID investidorId() { return investidorId; }
    public Ticker ticker() { return ticker; }
    public int quantidade() { return quantidade; }
    public int quantidadeBloqueada() { return quantidadeBloqueada; }
    public Dinheiro custoTotal() { return custoTotal; }
    public long versao() { return versao; }
    /** Uso exclusivo da camada de persistência. */
    public void versaoPersistida(long v) { this.versao = v; }
}
