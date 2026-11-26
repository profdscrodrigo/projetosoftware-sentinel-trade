package br.mackenzie.sentineltrade.seguranca;

/** Limpa textos antes de gravar em log/auditoria. Remove quebras de linha e caracteres de controle (evita "log injection") e limita o tamanho. */
public final class Sanitizador {

    private static final int TAMANHO_MAXIMO = 200;

    private Sanitizador() {
    }

    public static String paraLog(String texto) {
        if (texto == null) {
            return null;
        }
        String limpo = texto.replaceAll("[\\r\\n\\t]", " ").replaceAll("\\p{Cntrl}", "").strip();
        return limpo.length() > TAMANHO_MAXIMO ? limpo.substring(0, TAMANHO_MAXIMO) + "..." : limpo;
    }
}
