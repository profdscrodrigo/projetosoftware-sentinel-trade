package br.orion.sentineltrade.aplicacao.porta;

import java.util.function.Supplier;

/** Fronteira de transação: tudo dentro de {@code executar} confirma junto ou desfaz junto. */
public interface GerenciadorTransacao {
    <T> T executar(Supplier<T> acao);
    default void executarSemRetorno(Runnable acao) { executar(() -> { acao.run(); return null; }); }
}
