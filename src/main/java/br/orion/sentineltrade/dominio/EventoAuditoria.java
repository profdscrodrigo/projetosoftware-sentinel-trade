package br.orion.sentineltrade.dominio;

import java.time.Instant;
import java.util.UUID;

/** Registro imutável da trilha de auditoria, encadeado por hash (cada evento referencia o hash do anterior). */
public record EventoAuditoria(long seq, Instant instante, String tipo, UUID usuarioId,
                              String entidade, String entidadeId, String detalhes,
                              String hashAnterior, String hash) {}
