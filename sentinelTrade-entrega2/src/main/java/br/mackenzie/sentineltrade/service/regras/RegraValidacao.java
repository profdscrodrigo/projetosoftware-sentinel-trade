package br.mackenzie.sentineltrade.service.regras;

import java.util.Optional;

import br.mackenzie.sentineltrade.model.Ordem;

/**
 * INTERFACE comum a todas as regras de validação (RF-08).
 * Cada regra devolve o motivo da recusa, ou vazio se a ordem passou.
 */
public interface RegraValidacao {

    Optional<String> verificar(Ordem ordem, ContextoValidacao contexto);
}
