package br.orion.sentineltrade.aplicacao.servico;

import br.orion.sentineltrade.dominio.Excecoes.LimiteTaxaException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/** Janela deslizante em memória: limita tentativas de login e frequência de envio de ordens. */
public final class LimitadorDeTaxa {
    private final Clock clock;
    private final Map<String, Deque<Instant>> janelas = new HashMap<>();

    public LimitadorDeTaxa(Clock clock) { this.clock = clock; }

    public synchronized void registrar(String chave, int maximo, Duration janela) {
        Instant agora = clock.instant();
        Deque<Instant> d = janelas.computeIfAbsent(chave, k -> new ArrayDeque<>());
        while (!d.isEmpty() && d.peekFirst().isBefore(agora.minus(janela))) d.pollFirst();
        if (d.size() >= maximo) throw new LimiteTaxaException("Muitas requisições. Aguarde e tente novamente.");
        d.addLast(agora);
        if (janelas.size() > 10_000) janelas.values().removeIf(Deque::isEmpty); // evita crescimento ilimitado
    }
}
