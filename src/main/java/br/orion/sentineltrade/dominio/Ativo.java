package br.orion.sentineltrade.dominio;

public final class Ativo {
    private final Ticker ticker;
    private final String nome;
    private final TipoAtivo tipo;
    private StatusAtivo status;
    private long versao;

    public Ativo(Ticker ticker, String nome, TipoAtivo tipo, StatusAtivo status, long versao) {
        this.ticker = ticker; this.nome = nome; this.tipo = tipo; this.status = status; this.versao = versao;
    }

    public boolean negociavel() { return status == StatusAtivo.ATIVO; }
    public void alterarStatus(StatusAtivo novo) { this.status = novo; }

    public Ticker ticker() { return ticker; }
    public String nome() { return nome; }
    public TipoAtivo tipo() { return tipo; }
    public StatusAtivo status() { return status; }
    public long versao() { return versao; }
    public void versaoPersistida(long v) { this.versao = v; }
}
