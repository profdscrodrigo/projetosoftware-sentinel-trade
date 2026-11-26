package br.orion.sentineltrade.infraestrutura.integracao;

import br.orion.sentineltrade.aplicacao.porta.ProvedorCotacoes;
import br.orion.sentineltrade.dominio.Cotacao;
import br.orion.sentineltrade.dominio.Dinheiro;
import br.orion.sentineltrade.dominio.Excecoes.IntegracaoIndisponivelException;
import br.orion.sentineltrade.dominio.Ticker;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/** Provedor de cotações simulado (passeio aleatório limitado), com chaves para simular falhas e dados corrompidos. */
public final class CotacoesSimuladas implements ProvedorCotacoes {
    private final Clock clock;
    private final Map<Ticker, Cotacao> cotacoes = new ConcurrentHashMap<>();
    private volatile boolean indisponivel;

    public CotacoesSimuladas(Clock clock) { this.clock = clock; }

    public void definir(String ticker, String preco, String fechamentoAnterior) {
        Ticker t = Ticker.de(ticker);
        cotacoes.put(t, new Cotacao(t, Dinheiro.de(preco), Dinheiro.de(fechamentoAnterior), clock.instant()));
    }

    /** Injeta uma cotação sem qualquer validação (simula dado corrompido vindo do provedor). */
    public void injetarBruta(Cotacao c) { cotacoes.put(c.ticker(), c); }

    public void atualizarInstantes() { cotacoes.replaceAll((t, c) -> new Cotacao(t, c.preco(), c.fechamentoAnterior(), clock.instant())); }

    /** Variação aleatória de até ±0,5% por tick. */
    public void variar(Random rnd) {
        cotacoes.replaceAll((t, c) -> {
            BigDecimal fator = BigDecimal.ONE.add(BigDecimal.valueOf((rnd.nextDouble() - 0.5) / 100));
            Dinheiro novo = Dinheiro.de(c.preco().valor().multiply(fator));
            if (!novo.positivo()) novo = c.preco();
            return new Cotacao(t, novo, c.fechamentoAnterior(), clock.instant());
        });
    }

    public void setIndisponivel(boolean v) { this.indisponivel = v; }

    @Override public Optional<Cotacao> obter(Ticker ticker) {
        if (indisponivel) throw new IntegracaoIndisponivelException("Provedor de cotações indisponível");
        return Optional.ofNullable(cotacoes.get(ticker));
    }
}
