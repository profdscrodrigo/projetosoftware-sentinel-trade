package br.orion.sentineltrade.infraestrutura.integracao;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/** Relógio controlável (demonstração e testes): permite simular horário de mercado, expiração e recuos. */
public final class RelogioAjustavel extends Clock {
    private volatile Instant atual;
    private final ZoneId zona;

    public RelogioAjustavel(Instant inicio, ZoneId zona) { this.atual = inicio; this.zona = zona; }

    public void avancar(Duration d) { atual = atual.plus(d); }
    public void definir(Instant i) { atual = i; }

    @Override public ZoneId getZone() { return zona; }
    @Override public Clock withZone(ZoneId z) { return new RelogioAjustavel(atual, z); }
    @Override public Instant instant() { return atual; }
}
