package br.orion.sentineltrade.infraestrutura.integracao;

import static org.junit.jupiter.api.Assertions.*;

import br.orion.sentineltrade.aplicacao.porta.*;
import br.orion.sentineltrade.dominio.*;
import br.orion.sentineltrade.dominio.Excecoes.*;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class CorretoraResilienteTest {
    /** Corretora de mentira, controlável. */
    private static final class Falsa implements CorretoraGateway {
        final AtomicInteger chamadas = new AtomicInteger();
        volatile boolean falhar;
        volatile long demoraMs;
        @Override public AckCorretora enviar(OrdemParaEnvio o) {
            chamadas.incrementAndGet();
            if (demoraMs > 0) try { Thread.sleep(demoraMs); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            if (falhar) throw new IntegracaoIndisponivelException("fora");
            return new AckCorretora(true, null);
        }
        @Override public boolean cancelar(UUID id) { return true; }
        @Override public List<RelatorioExecucao> consultarRelatorios() { return List.of(); }
        @Override public void confirmarRelatorios(Collection<String> ids) {}
    }

    private final RelogioAjustavel relogio = new RelogioAjustavel(Instant.parse("2026-11-24T14:00:00Z"), ZoneId.of("UTC"));
    private final Falsa falsa = new Falsa();
    private final CorretoraResiliente r = new CorretoraResiliente(falsa, relogio, Duration.ofMillis(100), 3, Duration.ofSeconds(30));
    private final OrdemParaEnvio ordem = new OrdemParaEnvio(UUID.randomUUID(), Ticker.de("PETR4"), Lado.COMPRA, 1, Dinheiro.de("10"));

    @Test void chamadaLentaEstouraOTimeout() {
        falsa.demoraMs = 1000;
        assertThrows(TimeoutIntegracaoException.class, () -> r.enviar(ordem));
    }

    @Test void circuitoAbreAposFalhasSeguidasEFalhaRapidoSemChamarACorretora() {
        falsa.falhar = true;
        for (int i = 0; i < 3; i++) assertThrows(IntegracaoIndisponivelException.class, () -> r.enviar(ordem));
        assertEquals(CorretoraResiliente.Estado.ABERTO, r.estado());
        int antes = falsa.chamadas.get();
        IntegracaoIndisponivelException e = assertThrows(IntegracaoIndisponivelException.class, () -> r.enviar(ordem));
        assertTrue(e.getMessage().contains("Circuito aberto"));
        assertEquals(antes, falsa.chamadas.get(), "com o circuito aberto a corretora nem é chamada");
    }

    @Test void aposOTempoDeEsperaUmaChamadaDeProvaFechaOCircuitoSeDerCerto() {
        falsa.falhar = true;
        for (int i = 0; i < 3; i++) assertThrows(IntegracaoException.class, () -> r.enviar(ordem));
        falsa.falhar = false;
        relogio.avancar(Duration.ofSeconds(31));
        assertTrue(r.enviar(ordem).aceita());
        assertEquals(CorretoraResiliente.Estado.FECHADO, r.estado());
    }

    @Test void chamadaDeProvaQueFalhaReabreOCircuito() {
        falsa.falhar = true;
        for (int i = 0; i < 3; i++) assertThrows(IntegracaoException.class, () -> r.enviar(ordem));
        relogio.avancar(Duration.ofSeconds(31));
        assertThrows(IntegracaoIndisponivelException.class, () -> r.enviar(ordem));      // prova falha
        assertEquals(CorretoraResiliente.Estado.ABERTO, r.estado());
    }

    @Test void sucessoZeraOContadorDeFalhasSeguidas() {
        falsa.falhar = true;
        assertThrows(IntegracaoException.class, () -> r.enviar(ordem));
        assertThrows(IntegracaoException.class, () -> r.enviar(ordem));
        falsa.falhar = false;
        r.enviar(ordem);
        falsa.falhar = true;
        assertThrows(IntegracaoException.class, () -> r.enviar(ordem));
        assertEquals(CorretoraResiliente.Estado.FECHADO, r.estado(), "falhas não são consecutivas");
    }
}
