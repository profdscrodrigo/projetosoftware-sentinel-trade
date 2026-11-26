package br.orion.sentineltrade.aplicacao.porta;

import br.orion.sentineltrade.dominio.Dinheiro;
import br.orion.sentineltrade.dominio.Lado;
import br.orion.sentineltrade.dominio.Ticker;
import java.util.UUID;

/** Dados enviados à corretora. {@code idCliente} (= id da ordem) é a chave de idempotência do lado da corretora. */
public record OrdemParaEnvio(UUID idCliente, Ticker ticker, Lado lado, int quantidade, Dinheiro precoLimite) {}
