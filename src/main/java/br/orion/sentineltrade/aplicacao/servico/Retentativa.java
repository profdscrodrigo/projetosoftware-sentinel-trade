package br.orion.sentineltrade.aplicacao.servico;

import br.orion.sentineltrade.dominio.Excecoes.ConcorrenciaException;
import java.util.function.Supplier;

/** Repete uma operação transacional quando outra transação alterou o mesmo registro (concorrência otimista). */
public final class Retentativa {
    private Retentativa() {}
    public static <T> T comConflito(int maxTentativas, Supplier<T> acao) {
        for (int i = 1; ; i++) {
            try { return acao.get(); }
            catch (ConcorrenciaException e) {
                if (i >= maxTentativas) throw e;
                Log.aviso("Conflito de concorrência; tentativa " + i + " de " + maxTentativas);
            }
        }
    }
}
