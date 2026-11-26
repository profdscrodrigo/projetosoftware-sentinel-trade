package br.orion.sentineltrade.interfaceweb;

import br.orion.sentineltrade.dominio.Excecoes.ValidacaoException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * JSON mínimo e ESTRITO, sem dependências.
 *  - Escrita: escapa aspas, barra, controles e também {@code < > &} e U+2028/2029 (defesa em profundidade contra injeção em HTML).
 *  - Leitura: aceita apenas UM objeto PLANO (sem aninhamento) com valores string/número/booleano/null, chaves únicas e dentro de
 *    uma allowlist, tamanhos limitados. Qualquer outra coisa é recusada. Números são mantidos como TEXTO e validados pelo
 *    domínio (ex.: Validacoes.quantidade), nunca convertidos para double.
 */
final class Json {
    private Json() {}

    private static final int MAX_CAMPOS = 20, MAX_CHAVE = 64, MAX_VALOR = 512;

    // ------------------------------------------------------------------------------------------ escrita
    static String escrever(Object o) {
        StringBuilder sb = new StringBuilder();
        escrever(o, sb);
        return sb.toString();
    }

    private static void escrever(Object o, StringBuilder sb) {
        if (o == null) sb.append("null");
        else if (o instanceof Boolean || o instanceof Integer || o instanceof Long) sb.append(o);
        else if (o instanceof Map<?, ?> m) {
            sb.append('{');
            boolean primeiro = true;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (!primeiro) sb.append(',');
                primeiro = false;
                texto(String.valueOf(e.getKey()), sb);
                sb.append(':');
                escrever(e.getValue(), sb);
            }
            sb.append('}');
        } else if (o instanceof Collection<?> c) {
            sb.append('[');
            boolean primeiro = true;
            for (Object x : c) { if (!primeiro) sb.append(','); primeiro = false; escrever(x, sb); }
            sb.append(']');
        } else texto(o.toString(), sb);                                   // String, Enum, UUID, Instant, Dinheiro, BigDecimal…
    }

    private static void texto(String s, StringBuilder sb) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20 || c == '<' || c == '>' || c == '&' || c == '\u2028' || c == '\u2029') sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        sb.append('"');
    }

    // ------------------------------------------------------------------------------------------ leitura
    /** @param permitidos allowlist de campos aceitos; campo fora dela é recusado. */
    static Map<String, Object> lerObjetoPlano(String texto, Set<String> permitidos) {
        Leitor l = new Leitor(texto == null ? "" : texto);
        Map<String, Object> r = l.objeto();
        for (String k : r.keySet())
            if (!permitidos.contains(k)) throw new ValidacaoException("Campo não permitido: " + limpar(k));
        return r;
    }

    /** Valor como texto: string → ela mesma; número/booleano → literal; null → null. */
    static String texto(Map<String, Object> m, String chave) {
        Object v = m.get(chave);
        return v == null ? null : v.toString();
    }

    static boolean booleano(Map<String, Object> m, String chave) {
        Object v = m.get(chave);
        if (v == null) return false;
        if (v instanceof Boolean b) return b;
        throw new ValidacaoException("O campo '" + chave + "' deve ser true ou false");
    }

    private static String limpar(String s) { return s.length() > 40 ? s.substring(0, 40) : s.replaceAll("[^\\p{L}\\p{N}_.-]", "?"); }

    private static final class Leitor {
        private final String s;
        private int i;
        Leitor(String s) { this.s = s; }

        Map<String, Object> objeto() {
            espaco();
            esperar('{');
            Map<String, Object> m = new LinkedHashMap<>();
            espaco();
            if (peek() == '}') { i++; fim(); return m; }
            while (true) {
                espaco();
                String k = string();
                if (k.length() > MAX_CHAVE) throw erro("chave longa demais");
                if (m.containsKey(k)) throw erro("campo repetido");
                espaco();
                esperar(':');
                espaco();
                m.put(k, valor());
                if (m.size() > MAX_CAMPOS) throw erro("campos demais");
                espaco();
                char c = proximo();
                if (c == '}') break;
                if (c != ',') throw erro("esperado ',' ou '}'");
            }
            fim();
            return m;
        }

        private Object valor() {
            char c = peek();
            if (c == '"') return string();
            if (s.startsWith("true", i)) { i += 4; return Boolean.TRUE; }
            if (s.startsWith("false", i)) { i += 5; return Boolean.FALSE; }
            if (s.startsWith("null", i)) { i += 4; return null; }
            if (c == '-' || (c >= '0' && c <= '9')) {
                int ini = i;
                while (i < s.length() && "+-.eE0123456789".indexOf(s.charAt(i)) >= 0) i++;
                if (i - ini > 30) throw erro("número longo demais");
                return s.substring(ini, i);
            }
            throw erro("valor não permitido (aninhamento e listas são recusados)");
        }

        private String string() {
            esperar('"');
            StringBuilder sb = new StringBuilder();
            while (true) {
                if (i >= s.length()) throw erro("texto não terminado");
                char c = s.charAt(i++);
                if (c == '"') break;
                if (c < 0x20) throw erro("caractere de controle em texto");
                if (c == '\\') {
                    if (i >= s.length()) throw erro("escape incompleto");
                    char e = s.charAt(i++);
                    switch (e) {
                        case '"' -> sb.append('"');
                        case '\\' -> sb.append('\\');
                        case '/' -> sb.append('/');
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case 'u' -> {
                            if (i + 4 > s.length()) throw erro("escape unicode incompleto");
                            try { sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16)); }
                            catch (NumberFormatException ex) { throw erro("escape unicode inválido"); }
                            i += 4;
                        }
                        default -> throw erro("escape inválido");
                    }
                } else sb.append(c);
                if (sb.length() > MAX_VALOR) throw erro("texto longo demais");
            }
            return sb.toString();
        }

        private void espaco() { while (i < s.length() && " \t\r\n".indexOf(s.charAt(i)) >= 0) i++; }
        private char peek() { if (i >= s.length()) throw erro("fim inesperado"); return s.charAt(i); }
        private char proximo() { char c = peek(); i++; return c; }
        private void esperar(char c) { if (proximo() != c) throw erro("esperado '" + c + "'"); }
        private void fim() { espaco(); if (i != s.length()) throw erro("conteúdo após o objeto"); }
        private ValidacaoException erro(String m) { return new ValidacaoException("JSON inválido: " + m); }
    }

    static List<Object> lista(Collection<?> c) { return new ArrayList<>(c); }
}
