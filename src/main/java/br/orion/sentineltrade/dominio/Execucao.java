package br.orion.sentineltrade.dominio;

import java.time.Instant;
import java.util.UUID;

/** Execução (total ou parcial) informada pela corretora. {@code idExecucao} é o identificador da corretora (idempotência). */
public record Execucao(String idExecucao, UUID ordemId, int quantidade, Dinheiro preco, Instant executadaEm) {}
