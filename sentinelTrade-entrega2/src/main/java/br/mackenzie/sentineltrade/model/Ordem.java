package br.mackenzie.sentineltrade.model;

import java.math.BigDecimal;

/* Ordem de compra ou venda. Encapsulamento: o status só muda pelo método mudarStatus(), que respeita as regras de StatusOrdem */
public class Ordem {

    private Long id;
    private final String chaveIdempotencia;
    private final long contaId;
    private final long ativoId;
    private final String codigoAtivo;
    private final TipoOrdem tipo;
    private final int quantidade;
    private BigDecimal preco;
    private StatusOrdem status;
    private String motivo;
    private String criadoEm;

    /** Cria uma ordem nova. Toda ordem nova começa como CRIADA. */
    public Ordem(String chaveIdempotencia, long contaId, long ativoId, String codigoAtivo,
                 TipoOrdem tipo, int quantidade, BigDecimal preco) {
        if (chaveIdempotencia == null || chaveIdempotencia.isBlank()) {
            throw new IllegalArgumentException("A ordem precisa de um identificador único.");
        }
        if (tipo == null) {
            throw new IllegalArgumentException("Informe se a ordem é de compra ou de venda.");
        }
        if (quantidade <= 0) {
            throw new IllegalArgumentException("A quantidade deve ser maior que zero.");
        }
        if (preco == null || preco.signum() <= 0) {
            throw new IllegalArgumentException("O preço deve ser maior que zero.");
        }
        this.chaveIdempotencia = chaveIdempotencia;
        this.contaId = contaId;
        this.ativoId = ativoId;
        this.codigoAtivo = codigoAtivo;
        this.tipo = tipo;
        this.quantidade = quantidade;
        this.preco = preco;
        this.status = StatusOrdem.CRIADA;
    }

    /** Recria uma ordem que já estava salva no banco. */
    public static Ordem doBanco(long id, String chaveIdempotencia, long contaId, long ativoId, String codigoAtivo,
                                TipoOrdem tipo, int quantidade, BigDecimal preco, StatusOrdem status,
                                String motivo, String criadoEm) {
        Ordem ordem = new Ordem(chaveIdempotencia, contaId, ativoId, codigoAtivo, tipo, quantidade, preco);
        ordem.id = id;
        ordem.status = status;
        ordem.motivo = motivo;
        ordem.criadoEm = criadoEm;
        return ordem;
    }

    /** Muda o status só se a transição for permitida (diagrama de estados). */
    public void mudarStatus(StatusOrdem novo, String motivo) {
        if (!status.podeMudarPara(novo)) {
            throw new IllegalStateException("Mudança de status não permitida: " + status + " -> " + novo);
        }
        this.status = novo;
        this.motivo = motivo;
    }

    /** Atualiza o preço de uma ordem que ainda está aguardando execução. */
    public void alterarPreco(BigDecimal novoPreco) {
        if (!isPrecoAlteravel()) {
            throw new IllegalStateException("Só é possível alterar o preço de uma ordem aguardando execução.");
        }
        if (novoPreco == null || novoPreco.signum() <= 0) {
            throw new IllegalArgumentException("O preço deve ser maior que zero.");
        }
        this.preco = novoPreco;
    }

    /** Valor total da ordem = quantidade x preço. */
    public BigDecimal getValorTotal() {
        return preco.multiply(BigDecimal.valueOf(quantidade));
    }

    public boolean isCancelavel() {
        return status.podeMudarPara(StatusOrdem.CANCELADA);
    }

    public boolean isPrecoAlteravel() {
        return status == StatusOrdem.ENVIADA;
    }

    /** O id é definido uma única vez, quando a ordem é salva no banco. */
    public void definirId(long id) {
        if (this.id != null) {
            throw new IllegalStateException("A ordem já possui id.");
        }
        this.id = id;
    }

    public Long getId() {
        return id;
    }

    public String getChaveIdempotencia() {
        return chaveIdempotencia;
    }

    public long getContaId() {
        return contaId;
    }

    public long getAtivoId() {
        return ativoId;
    }

    public String getCodigoAtivo() {
        return codigoAtivo;
    }

    public TipoOrdem getTipo() {
        return tipo;
    }

    public int getQuantidade() {
        return quantidade;
    }

    public BigDecimal getPreco() {
        return preco;
    }

    public StatusOrdem getStatus() {
        return status;
    }

    public String getMotivo() {
        return motivo;
    }

    public String getCriadoEm() {
        return criadoEm;
    }
}
