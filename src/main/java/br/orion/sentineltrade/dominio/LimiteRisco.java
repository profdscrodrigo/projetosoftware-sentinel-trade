package br.orion.sentineltrade.dominio;

import br.orion.sentineltrade.dominio.Excecoes.ValidacaoException;
import java.util.UUID;

/** Limites financeiros do investidor, mantidos por Administrador/Analista de Risco. */
public final class LimiteRisco {
    private final UUID investidorId;
    private final Dinheiro valorMaximoPorOrdem;
    private final Dinheiro limiteDiario;
    private final Dinheiro valorConfirmacao;
    private long versao;

    public LimiteRisco(UUID investidorId, Dinheiro valorMaximoPorOrdem, Dinheiro limiteDiario, Dinheiro valorConfirmacao, long versao) {
        if (!valorMaximoPorOrdem.positivo() || !limiteDiario.positivo() || !valorConfirmacao.positivo())
            throw new ValidacaoException("Limites devem ser positivos");
        if (limiteDiario.compareTo(valorMaximoPorOrdem) < 0)
            throw new ValidacaoException("Limite diário não pode ser menor que o limite por ordem");
        if (valorConfirmacao.compareTo(valorMaximoPorOrdem) > 0)
            throw new ValidacaoException("Valor de confirmação não pode exceder o limite por ordem");
        this.investidorId = investidorId; this.valorMaximoPorOrdem = valorMaximoPorOrdem;
        this.limiteDiario = limiteDiario; this.valorConfirmacao = valorConfirmacao; this.versao = versao;
    }

    public UUID investidorId() { return investidorId; }
    public Dinheiro valorMaximoPorOrdem() { return valorMaximoPorOrdem; }
    public Dinheiro limiteDiario() { return limiteDiario; }
    public Dinheiro valorConfirmacao() { return valorConfirmacao; }
    public long versao() { return versao; }
    public void versaoPersistida(long v) { this.versao = v; }
}
