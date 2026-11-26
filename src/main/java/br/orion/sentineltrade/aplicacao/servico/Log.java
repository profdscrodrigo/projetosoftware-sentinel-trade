package br.orion.sentineltrade.aplicacao.servico;

import java.lang.System.Logger.Level;

/** Fachada de log (System.Logger). Nunca registrar senha, token, segredo MFA ou hash. */
public final class Log {
    private static final System.Logger L = System.getLogger("sentineltrade");
    private Log() {}
    public static void info(String msg) { L.log(Level.INFO, msg); }
    public static void aviso(String msg) { L.log(Level.WARNING, msg); }
    public static void erro(String msg, Throwable t) { L.log(Level.ERROR, msg, t); }
}
