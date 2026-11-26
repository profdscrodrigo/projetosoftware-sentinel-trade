package br.orion.sentineltrade.aplicacao.servico;

import br.orion.sentineltrade.aplicacao.porta.Repositorios.ParametroRepositorio;
import br.orion.sentineltrade.dominio.Excecoes.ValidacaoException;
import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.TreeMap;

/** Parâmetros operacionais (RF12). Só chaves da allowlist são aceitas, com valor validado por tipo. */
public final class ParametrosSistema {
    public static final String MERCADO_ABERTURA = "mercado.abertura";
    public static final String MERCADO_FECHAMENTO = "mercado.fechamento";
    public static final String MERCADO_FORCAR_ABERTO = "mercado.forcar_aberto";
    public static final String COTACAO_IDADE_MAX_SEG = "cotacao.idade_maxima_segundos";
    public static final String ORDEM_FAIXA_PRECO_PCT = "ordem.faixa_preco_percentual";
    public static final String ORDEM_MAX_POR_MINUTO = "ordem.max_por_minuto";

    private static final Map<String, String> PADROES = Map.of(
            MERCADO_ABERTURA, "10:00",
            MERCADO_FECHAMENTO, "17:55",
            MERCADO_FORCAR_ABERTO, "false",
            COTACAO_IDADE_MAX_SEG, "60",
            ORDEM_FAIXA_PRECO_PCT, "10",
            ORDEM_MAX_POR_MINUTO, "30");

    private final ParametroRepositorio repo;

    public ParametrosSistema(ParametroRepositorio repo) { this.repo = repo; }

    public String texto(String chave) { return repo.buscar(chave).orElse(PADROES.get(chave)); }
    public int inteiro(String chave) { return Integer.parseInt(texto(chave)); }
    public BigDecimal decimal(String chave) { return new BigDecimal(texto(chave)); }
    public boolean booleano(String chave) { return Boolean.parseBoolean(texto(chave)); }
    public LocalTime hora(String chave) { return LocalTime.parse(texto(chave)); }

    public void definir(String chave, String valor) {
        if (!PADROES.containsKey(chave)) throw new ValidacaoException("Parâmetro desconhecido");
        String v = valor == null ? "" : valor.trim();
        try {
            switch (chave) {
                case MERCADO_ABERTURA, MERCADO_FECHAMENTO -> LocalTime.parse(v);
                case MERCADO_FORCAR_ABERTO -> { if (!v.equals("true") && !v.equals("false")) throw new ValidacaoException("Use true ou false"); }
                case COTACAO_IDADE_MAX_SEG, ORDEM_MAX_POR_MINUTO -> {
                    int n = Integer.parseInt(v);
                    if (n < 1 || n > 86_400) throw new ValidacaoException("Valor fora do intervalo (1 a 86400)");
                }
                case ORDEM_FAIXA_PRECO_PCT -> {
                    BigDecimal d = new BigDecimal(v);
                    if (d.compareTo(new BigDecimal("0.1")) < 0 || d.compareTo(new BigDecimal("100")) > 0)
                        throw new ValidacaoException("Valor fora do intervalo (0.1 a 100)");
                }
                default -> throw new ValidacaoException("Parâmetro desconhecido");
            }
        } catch (DateTimeParseException | NumberFormatException e) {
            throw new ValidacaoException("Valor inválido para o parâmetro");
        }
        repo.salvar(chave, v);
    }

    public Map<String, String> todos() {
        Map<String, String> r = new TreeMap<>(PADROES);
        r.putAll(repo.listar());
        return r;
    }
}
