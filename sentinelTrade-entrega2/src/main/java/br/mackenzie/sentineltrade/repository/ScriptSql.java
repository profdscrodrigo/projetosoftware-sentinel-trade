package br.mackenzie.sentineltrade.repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Divide um arquivo .sql em comandos separados (respeitando os blocos CREATE TRIGGER ... END;). */
public final class ScriptSql {

    private ScriptSql() {
    }

    public static List<String> dividir(String script) {
        List<String> comandos = new ArrayList<>();
        StringBuilder atual = new StringBuilder();
        boolean dentroDeGatilho = false;

        for (String linha : script.split("\\R")) {
            String limpa = linha.strip();
            if (limpa.isEmpty() || limpa.startsWith("--")) {
                continue;
            }
            atual.append(linha).append('\n');
            if (limpa.toUpperCase(Locale.ROOT).startsWith("CREATE TRIGGER")) {
                dentroDeGatilho = true;
            }
            boolean fimDoComando = dentroDeGatilho ? limpa.equalsIgnoreCase("END;") : limpa.endsWith(";");
            if (fimDoComando) {
                comandos.add(atual.toString().trim());
                atual.setLength(0);
                dentroDeGatilho = false;
            }
        }
        if (!atual.toString().isBlank()) {
            comandos.add(atual.toString().trim());
        }
        return comandos;
    }
}
