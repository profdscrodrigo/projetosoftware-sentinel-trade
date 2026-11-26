package br.orion.sentineltrade;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Configuração lida de VARIÁVEIS DE AMBIENTE (e, para conveniência local, de um arquivo .env não versionado).
 * Nenhum segredo existe no código nem no repositório; ver .env.example para os nomes.
 */
public record Config(String armazenamento, String dbUrl, String dbUsuario, String dbSenha, boolean demo,
                     int pbkdf2Iteracoes, long corretoraTimeoutMs, int corretoraFalhasParaAbrir,
                     long corretoraTempoAbertoSeg, String zona, String mfaChave, boolean mercadoSempreAberto,
                     int webPorta, String webEndereco, Map<String, String> bruto) {

    public static Config carregar() {
        Map<String, String> v = new HashMap<>();
        Path env = Path.of(".env");
        if (Files.isRegularFile(env)) {
            try {
                for (String linha : Files.readAllLines(env)) {
                    String l = linha.trim();
                    if (l.isEmpty() || l.startsWith("#") || !l.contains("=")) continue;
                    int i = l.indexOf('=');
                    v.put(l.substring(0, i).trim(), l.substring(i + 1).trim());
                }
            } catch (IOException e) { System.err.println("Aviso: não foi possível ler .env"); }
        }
        v.putAll(System.getenv());                                   // variáveis de ambiente têm precedência
        return de(v);
    }

    public static Config de(Map<String, String> v) {
        return new Config(
                v.getOrDefault("SENTINEL_STORAGE", "memory"),
                v.getOrDefault("DB_URL", "jdbc:postgresql://localhost:5432/sentineltrade"),
                v.get("DB_USER"), v.get("DB_PASSWORD"),
                Boolean.parseBoolean(v.getOrDefault("SENTINEL_DEMO", "false")),
                Integer.parseInt(v.getOrDefault("PBKDF2_ITERATIONS", "600000")),
                Long.parseLong(v.getOrDefault("BROKER_TIMEOUT_MS", "2000")),
                Integer.parseInt(v.getOrDefault("BROKER_CIRCUIT_FAILURES", "5")),
                Long.parseLong(v.getOrDefault("BROKER_CIRCUIT_OPEN_SECONDS", "30")),
                v.getOrDefault("SENTINEL_ZONE", "America/Sao_Paulo"),
                v.get("MFA_ENCRYPTION_KEY"),
                Boolean.parseBoolean(v.getOrDefault("SENTINEL_MARKET_ALWAYS_OPEN", "false")),
                Integer.parseInt(v.getOrDefault("WEB_PORT", "8080")),
                v.getOrDefault("WEB_BIND", "127.0.0.1"),
                v);
    }

    public boolean jdbc() { return "jdbc".equalsIgnoreCase(armazenamento); }
}
