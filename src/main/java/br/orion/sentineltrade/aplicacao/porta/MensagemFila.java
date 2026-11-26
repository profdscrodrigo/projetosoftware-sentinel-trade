package br.orion.sentineltrade.aplicacao.porta;

import java.time.Instant;
import java.util.UUID;

/** Mensagem da fila de transmissão (padrão outbox): uma ordem VALIDADA aguardando envio à corretora. */
public record MensagemFila(UUID id, UUID ordemId, int tentativas, Instant proximaTentativa) {}
