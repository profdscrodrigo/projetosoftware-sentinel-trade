package br.orion.sentineltrade.aplicacao.servico;

import br.orion.sentineltrade.aplicacao.porta.ProvedorCotacoes;
import br.orion.sentineltrade.aplicacao.porta.Repositorios.AtivoRepositorio;
import br.orion.sentineltrade.dominio.Ativo;
import br.orion.sentineltrade.dominio.Cotacao;
import br.orion.sentineltrade.dominio.Excecoes.IntegracaoException;
import br.orion.sentineltrade.dominio.Excecoes.NaoEncontradoException;
import br.orion.sentineltrade.dominio.Excecoes.RespostaInvalidaException;
import br.orion.sentineltrade.dominio.Ticker;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Consulta de cotações. O provedor é um sistema EXTERNO e NÃO CONFIÁVEL: toda cotação é revalidada
 * (ticker, preço, faixa de variação, data) antes de ser usada. Se o provedor falhar, devolve a última
 * cotação válida marcada como desatualizada (degradação controlada) — que o validador de ordens recusa.
 */
public final class CotacaoService {
    public record CotacaoConsultada(Cotacao cotacao, boolean desatualizada) {}

    private final ProvedorCotacoes provedor;
    private final ParametrosSistema params;
    private final AtivoRepositorio ativos;
    private final AutenticacaoService auth;
    private final Clock clock;
    private final Map<Ticker, Cotacao> cache = new ConcurrentHashMap<>();

    public CotacaoService(ProvedorCotacoes provedor, ParametrosSistema params, AtivoRepositorio ativos,
                          AutenticacaoService auth, Clock clock) {
        this.provedor = provedor; this.params = params; this.ativos = ativos; this.auth = auth; this.clock = clock;
    }

    /** Uso interno (validador de ordens). */
    public Optional<CotacaoConsultada> obter(Ticker ticker) {
        Instant agora = clock.instant();
        try {
            Optional<Cotacao> c = provedor.obter(ticker);
            if (c.isPresent()) {
                validarExterna(c.get(), ticker, agora);
                cache.put(ticker, c.get());
                return Optional.of(new CotacaoConsultada(c.get(), desatualizada(c.get(), agora)));
            }
        } catch (IntegracaoException e) {
            Log.aviso("Provedor de cotações falhou para " + ticker + ": " + e.getClass().getSimpleName());
        }
        Cotacao emCache = cache.get(ticker);
        return Optional.ofNullable(emCache).map(c -> new CotacaoConsultada(c, desatualizada(c, agora)));
    }

    public CotacaoConsultada consultar(String token, String tickerBruto) {
        auth.exigir(token);
        Ticker t = Ticker.de(tickerBruto);
        return obter(t).orElseThrow(() -> new NaoEncontradoException("Cotação indisponível para o ativo informado"));
    }

    public List<CotacaoConsultada> listar(String token) {
        auth.exigir(token);
        List<CotacaoConsultada> r = new ArrayList<>();
        for (Ativo a : ativos.listar()) obter(a.ticker()).ifPresent(r::add);
        return r;
    }

    private boolean desatualizada(Cotacao c, Instant agora) {
        long max = params.inteiro(ParametrosSistema.COTACAO_IDADE_MAX_SEG);
        return Duration.between(c.instante(), agora).getSeconds() > max;
    }

    /** Revalidação de dado externo (nunca confiar no provedor). */
    static void validarExterna(Cotacao c, Ticker esperado, Instant agora) {
        if (c == null || c.ticker() == null || !c.ticker().equals(esperado))
            throw new RespostaInvalidaException("Cotação de ativo diferente do solicitado");
        if (c.preco() == null || !c.preco().positivo() || c.preco().valor().compareTo(Validacoes.PRECO_MAXIMO) > 0)
            throw new RespostaInvalidaException("Preço de cotação inválido");
        if (c.fechamentoAnterior() == null || !c.fechamentoAnterior().positivo())
            throw new RespostaInvalidaException("Fechamento anterior inválido");
        if (c.instante() == null || c.instante().isAfter(agora.plusSeconds(5)))
            throw new RespostaInvalidaException("Data da cotação inválida");
        if (c.variacaoPercentual().abs().compareTo(new BigDecimal("50")) > 0)
            throw new RespostaInvalidaException("Variação implausível (> 50%)");
    }
}
