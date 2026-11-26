package br.orion.sentineltrade.infraestrutura.integracao;

import br.orion.sentineltrade.aplicacao.porta.*;
import br.orion.sentineltrade.dominio.Excecoes.IntegracaoException;
import br.orion.sentineltrade.dominio.Excecoes.IntegracaoIndisponivelException;
import br.orion.sentineltrade.dominio.Excecoes.TimeoutIntegracaoException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

/**
 * Decorator de resiliência para qualquer {@link CorretoraGateway}: TIMEOUT por chamada e CIRCUIT BREAKER
 * (FECHADO → ABERTO após N falhas seguidas → MEIO_ABERTO após o tempo de espera → FECHADO se a chamada de
 * prova tiver sucesso). Com o circuito aberto, falha rápido, sem sobrecarregar a corretora em dificuldade.
 * As retentativas duráveis ficam por conta da fila (ProcessadorOrdens).
 */
public final class CorretoraResiliente implements CorretoraGateway {
    public enum Estado { FECHADO, ABERTO, MEIO_ABERTO }

    private final CorretoraGateway delegado;
    private final Clock clock;
    private final Duration timeout;
    private final int limiteFalhas;
    private final Duration tempoAberto;
    private final ExecutorService pool = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "corretora-chamada");
        t.setDaemon(true);
        return t;
    });
    private Estado estado = Estado.FECHADO;
    private int falhasSeguidas;
    private Instant abertoEm;

    public CorretoraResiliente(CorretoraGateway delegado, Clock clock, Duration timeout, int limiteFalhas, Duration tempoAberto) {
        this.delegado = delegado; this.clock = clock; this.timeout = timeout; this.limiteFalhas = limiteFalhas; this.tempoAberto = tempoAberto;
    }

    public synchronized Estado estado() { return estado; }

    @Override public AckCorretora enviar(OrdemParaEnvio o) { return chamar(() -> delegado.enviar(o)); }
    @Override public boolean cancelar(UUID id) { return chamar(() -> delegado.cancelar(id)); }
    @Override public List<RelatorioExecucao> consultarRelatorios() { return chamar(delegado::consultarRelatorios); }
    @Override public void confirmarRelatorios(Collection<String> ids) { chamar(() -> { delegado.confirmarRelatorios(ids); return null; }); }

    private <T> T chamar(Callable<T> chamada) {
        permitir();
        Future<T> f = pool.submit(chamada);
        try {
            T r = f.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            registrarSucesso();
            return r;
        } catch (TimeoutException e) {
            f.cancel(true);
            registrarFalha();
            throw new TimeoutIntegracaoException("Tempo limite excedido na comunicação com a corretora");
        } catch (ExecutionException e) {
            registrarFalha();
            if (e.getCause() instanceof IntegracaoException ie) throw ie;
            throw new IntegracaoIndisponivelException("Falha inesperada na integração com a corretora", e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IntegracaoIndisponivelException("Chamada interrompida", e);
        }
    }

    private synchronized void permitir() {
        if (estado == Estado.ABERTO) {
            if (Duration.between(abertoEm, clock.instant()).compareTo(tempoAberto) >= 0) estado = Estado.MEIO_ABERTO;
            else throw new IntegracaoIndisponivelException("Circuito aberto: corretora temporariamente isolada");
        }
    }
    private synchronized void registrarSucesso() { falhasSeguidas = 0; estado = Estado.FECHADO; }
    private synchronized void registrarFalha() {
        falhasSeguidas++;
        if (estado == Estado.MEIO_ABERTO || falhasSeguidas >= limiteFalhas) { estado = Estado.ABERTO; abertoEm = clock.instant(); }
    }
}
