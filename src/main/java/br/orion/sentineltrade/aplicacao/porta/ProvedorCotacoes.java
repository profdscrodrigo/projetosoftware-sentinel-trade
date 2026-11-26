package br.orion.sentineltrade.aplicacao.porta;

import br.orion.sentineltrade.dominio.Cotacao;
import br.orion.sentineltrade.dominio.Ticker;
import java.util.Optional;

public interface ProvedorCotacoes {
    /** @throws br.orion.sentineltrade.dominio.Excecoes.IntegracaoException se o provedor falhar. */
    Optional<Cotacao> obter(Ticker ticker);
}
