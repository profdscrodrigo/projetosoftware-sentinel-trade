package br.orion.sentineltrade.dominio;

import br.orion.sentineltrade.dominio.Excecoes.RegraNegocioException;
import br.orion.sentineltrade.dominio.Excecoes.RespostaInvalidaException;
import br.orion.sentineltrade.dominio.Excecoes.ValidacaoException;
import java.time.Instant;
import java.util.UUID;

/**
 * Ordem de negociação (abstração). Compra e venda especializam a reserva, a liquidação e a liberação,
 * de modo que nenhum serviço precise de "if (lado == COMPRA)" para mexer em saldo ou posição.
 *
 * Invariantes (RNF02): investidor, ativo, lado, quantidade > 0 e preço > 0 sempre válidos.
 * Estado só muda via métodos que passam pela tabela de transições de {@link StatusOrdem}.
 */
public abstract class Ordem {
    private final UUID id;
    private final UUID investidorId;
    private final Ticker ticker;
    private final int quantidade;
    private final Dinheiro precoLimite;
    private final String chaveIdempotencia;
    private final String impressaoDigital;
    private final TipoOrdem tipo;
    private final UUID substituiOrdemId;
    private StatusOrdem status;
    private int quantidadeExecutada;
    private Dinheiro valorExecutado;
    private CodigoRegra motivoRejeicao;
    private final Instant criadaEm;
    private Instant atualizadaEm;
    private long versao;

    protected Ordem(UUID id, UUID investidorId, Ticker ticker, int quantidade, Dinheiro precoLimite,
                    String chave, String impressao, StatusOrdem status, int quantidadeExecutada,
                    Dinheiro valorExecutado, CodigoRegra motivo, Instant criadaEm, Instant atualizadaEm, long versao,
                    TipoOrdem tipo, UUID substituiOrdemId) {
        if (id == null || investidorId == null || ticker == null || precoLimite == null || status == null
                || chave == null || criadaEm == null || tipo == null)
            throw new ValidacaoException("Ordem com dados obrigatórios ausentes");
        if (quantidade <= 0) throw new ValidacaoException("Quantidade deve ser positiva");
        if (!precoLimite.positivo()) throw new ValidacaoException("Preço deve ser positivo");
        this.id = id; this.investidorId = investidorId; this.ticker = ticker; this.quantidade = quantidade;
        this.precoLimite = precoLimite; this.chaveIdempotencia = chave; this.impressaoDigital = impressao;
        this.status = status; this.quantidadeExecutada = quantidadeExecutada;
        this.valorExecutado = valorExecutado == null ? Dinheiro.ZERO : valorExecutado;
        this.motivoRejeicao = motivo; this.criadaEm = criadaEm; this.atualizadaEm = atualizadaEm; this.versao = versao;
        this.tipo = tipo; this.substituiOrdemId = substituiOrdemId;
    }

    public static Ordem nova(Lado lado, UUID investidorId, Ticker ticker, int quantidade, Dinheiro preco,
                             String chave, String impressao, Instant agora) {
        return nova(lado, TipoOrdem.LIMITE, investidorId, ticker, quantidade, preco, chave, impressao, agora, null);
    }

    /** {@code substituiOrdemId}: preenchido quando a ordem nasce de uma alteração (cancelar e substituir). */
    public static Ordem nova(Lado lado, TipoOrdem tipo, UUID investidorId, Ticker ticker, int quantidade, Dinheiro preco,
                             String chave, String impressao, Instant agora, UUID substituiOrdemId) {
        return reconstituir(lado, UUID.randomUUID(), investidorId, ticker, quantidade, preco, chave, impressao,
                StatusOrdem.CRIADA, 0, Dinheiro.ZERO, null, agora, agora, 0, tipo, substituiOrdemId);
    }

    /** Reconstitui uma ordem persistida (uso da camada de persistência). */
    public static Ordem reconstituir(Lado lado, UUID id, UUID investidorId, Ticker ticker, int quantidade,
                                     Dinheiro preco, String chave, String impressao, StatusOrdem status,
                                     int qtdExecutada, Dinheiro valorExecutado, CodigoRegra motivo,
                                     Instant criadaEm, Instant atualizadaEm, long versao,
                                     TipoOrdem tipo, UUID substituiOrdemId) {
        return switch (lado) {
            case COMPRA -> new OrdemCompra(id, investidorId, ticker, quantidade, preco, chave, impressao, status,
                    qtdExecutada, valorExecutado, motivo, criadaEm, atualizadaEm, versao, tipo, substituiOrdemId);
            case VENDA -> new OrdemVenda(id, investidorId, ticker, quantidade, preco, chave, impressao, status,
                    qtdExecutada, valorExecutado, motivo, criadaEm, atualizadaEm, versao, tipo, substituiOrdemId);
        };
    }

    // ---- especializações (polimorfismo) ----
    public abstract Lado lado();
    /** Reserva saldo (compra) ou quantidade (venda). Tudo ou nada: se lançar exceção, nada foi alterado. */
    protected abstract void reservar(Conta conta, Posicao posicao);
    protected abstract void liquidar(Conta conta, Posicao posicao, int qtd, Dinheiro preco);
    protected abstract void liberarReservaRestante(Conta conta, Posicao posicao);
    /** Compra: preço de execução ≤ limite. Venda: ≥ limite. */
    protected abstract boolean precoAceitavel(Dinheiro precoExecucao);

    // ---- comportamento comum ----
    public Dinheiro valorTotal() { return precoLimite.vezes(quantidade); }
    public int quantidadeRestante() { return quantidade - quantidadeExecutada; }

    /** CRIADA → VALIDADA, já com a reserva feita. */
    public void reservarEValidar(Conta conta, Posicao posicao, Instant agora) {
        if (!status.podeTransitarPara(StatusOrdem.VALIDADA))
            throw new RegraNegocioException(CodigoRegra.TRANSICAO_INVALIDA, status + " -> " + StatusOrdem.VALIDADA);
        reservar(conta, posicao);
        transitar(StatusOrdem.VALIDADA, agora);
    }

    public void marcarEnviada(Instant agora) { transitar(StatusOrdem.ENVIADA, agora); }

    /** Aplica uma execução (parcial ou total) e liquida saldo/posição de forma atômica. */
    public void aplicarExecucao(int qtd, Dinheiro preco, Instant agora, Conta conta, Posicao posicao) {
        if (qtd <= 0 || qtd > quantidadeRestante() || preco == null || !preco.positivo() || !precoAceitavel(preco))
            throw new RespostaInvalidaException("Execução inconsistente com a ordem " + id);
        StatusOrdem destino = (quantidadeExecutada + qtd == quantidade)
                ? StatusOrdem.EXECUTADA : StatusOrdem.PARCIALMENTE_EXECUTADA;
        transitar(destino, agora);                       // valida antes de qualquer mutação
        quantidadeExecutada += qtd;
        valorExecutado = valorExecutado.mais(preco.vezes(qtd));
        liquidar(conta, posicao, qtd, preco);
    }

    public void rejeitar(CodigoRegra motivo, Instant agora, Conta conta, Posicao posicao) {
        boolean tinhaReserva = status.mantemReserva();
        transitar(StatusOrdem.REJEITADA, agora);
        this.motivoRejeicao = motivo;
        if (tinhaReserva) liberarReservaRestante(conta, posicao);
    }

    /** Cancela a ordem (ou o saldo remanescente de uma ordem parcialmente executada). */
    public void cancelar(Instant agora, Conta conta, Posicao posicao) {
        boolean tinhaReserva = status.mantemReserva();
        if (status.terminal())
            throw new RegraNegocioException(CodigoRegra.ORDEM_NAO_CANCELAVEL, "estado " + status);
        transitar(StatusOrdem.CANCELADA, agora);
        if (tinhaReserva) liberarReservaRestante(conta, posicao);
    }

    private void transitar(StatusOrdem destino, Instant agora) {
        if (!status.podeTransitarPara(destino))
            throw new RegraNegocioException(CodigoRegra.TRANSICAO_INVALIDA, status + " -> " + destino);
        status = destino;
        atualizadaEm = agora;
    }

    public UUID id() { return id; }
    public UUID investidorId() { return investidorId; }
    public Ticker ticker() { return ticker; }
    public int quantidade() { return quantidade; }
    public Dinheiro precoLimite() { return precoLimite; }
    public String chaveIdempotencia() { return chaveIdempotencia; }
    public String impressaoDigital() { return impressaoDigital; }
    public TipoOrdem tipo() { return tipo; }
    public UUID substituiOrdemId() { return substituiOrdemId; }
    /** Estados em que a ordem ainda pode ser substituída: já validada, ainda sem nenhuma execução. */
    public boolean alteravel() {
        return (status == StatusOrdem.VALIDADA || status == StatusOrdem.ENVIADA) && quantidadeExecutada == 0;
    }
    public StatusOrdem status() { return status; }
    public int quantidadeExecutada() { return quantidadeExecutada; }
    public Dinheiro valorExecutado() { return valorExecutado; }
    public CodigoRegra motivoRejeicao() { return motivoRejeicao; }
    public Instant criadaEm() { return criadaEm; }
    public Instant atualizadaEm() { return atualizadaEm; }
    public long versao() { return versao; }
    /** Uso exclusivo da camada de persistência. */
    public void versaoPersistida(long v) { this.versao = v; }
}
