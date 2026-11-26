package br.orion.sentineltrade.aplicacao.servico;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/** Situação do mercado: dias úteis dentro da janela [abertura, fechamento). Feriados não são modelados (simulação). */
public final class CalendarioMercado {
    private final ParametrosSistema params;
    private final ZoneId zona;

    public CalendarioMercado(ParametrosSistema params, ZoneId zona) { this.params = params; this.zona = zona; }

    public boolean aberto(Instant agora) {
        if (params.booleano(ParametrosSistema.MERCADO_FORCAR_ABERTO)) return true;
        ZonedDateTime z = agora.atZone(zona);
        if (z.getDayOfWeek() == DayOfWeek.SATURDAY || z.getDayOfWeek() == DayOfWeek.SUNDAY) return false;
        LocalTime t = z.toLocalTime();
        return !t.isBefore(params.hora(ParametrosSistema.MERCADO_ABERTURA)) && t.isBefore(params.hora(ParametrosSistema.MERCADO_FECHAMENTO));
    }

    public ZoneId zona() { return zona; }
}
