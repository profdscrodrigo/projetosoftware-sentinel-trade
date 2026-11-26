package br.orion.sentineltrade.aplicacao.servico;

/**
 * Codificação de SAÍDA para o contexto "log / terminal": remove caracteres de controle
 * (quebras de linha → log forging; ESC → injeção de sequências ANSI) e limita o tamanho.
 * Para HTML (futura camada web) usar OWASP Java Encoder: Encode.forHtml(...).
 */
public final class Sanitizador {
    private Sanitizador() {}

    public static String paraLog(String texto, int max) {
        if (texto == null) return "-";
        String limpo = texto.replaceAll("[\\p{Cntrl}\\p{Cf}\\u2028\\u2029]", " ");
        return limpo.length() <= max ? limpo : limpo.substring(0, max) + "…";
    }
    public static String paraLog(String texto) { return paraLog(texto, 200); }
}
