package br.orion.sentineltrade.dominio;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Ciclo de vida da ordem. A tabela de transições é a ÚNICA fonte de verdade:
 * estados terminais (EXECUTADA, REJEITADA, CANCELADA) não têm saída (RN04).
 */
public enum StatusOrdem {
    CRIADA, VALIDADA, ENVIADA, PARCIALMENTE_EXECUTADA, EXECUTADA, REJEITADA, CANCELADA;

    private static final Map<StatusOrdem, Set<StatusOrdem>> TRANSICOES = new EnumMap<>(StatusOrdem.class);
    static {
        TRANSICOES.put(CRIADA, EnumSet.of(VALIDADA, REJEITADA, CANCELADA));
        TRANSICOES.put(VALIDADA, EnumSet.of(ENVIADA, REJEITADA, CANCELADA));
        TRANSICOES.put(ENVIADA, EnumSet.of(PARCIALMENTE_EXECUTADA, EXECUTADA, REJEITADA, CANCELADA));
        TRANSICOES.put(PARCIALMENTE_EXECUTADA, EnumSet.of(PARCIALMENTE_EXECUTADA, EXECUTADA, CANCELADA));
        TRANSICOES.put(EXECUTADA, EnumSet.noneOf(StatusOrdem.class));
        TRANSICOES.put(REJEITADA, EnumSet.noneOf(StatusOrdem.class));
        TRANSICOES.put(CANCELADA, EnumSet.noneOf(StatusOrdem.class));
    }

    public boolean podeTransitarPara(StatusOrdem destino) { return TRANSICOES.get(this).contains(destino); }
    public boolean terminal() { return TRANSICOES.get(this).isEmpty(); }
    /** Estados em que há saldo/quantidade reservados para a ordem. */
    public boolean mantemReserva() { return this == VALIDADA || this == ENVIADA || this == PARCIALMENTE_EXECUTADA; }
}
