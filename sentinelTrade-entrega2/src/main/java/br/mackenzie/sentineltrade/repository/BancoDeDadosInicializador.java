package br.mackenzie.sentineltrade.repository;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Na primeira execução cria o banco usando database/schema.sql e database/seed.sql. Se o banco já existir, não faz nada. */
@Component
public class BancoDeDadosInicializador implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(BancoDeDadosInicializador.class);

    private final JdbcTemplate jdbc;
    private final Path pastaScripts;

    public BancoDeDadosInicializador(JdbcTemplate jdbc, @Value("${sentinel.scripts-dir:database}") String pastaScripts) {
        this.jdbc = jdbc;
        this.pastaScripts = Path.of(pastaScripts);
    }

    @Override
    public void afterPropertiesSet() throws IOException {
        Integer tabelas = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = 'usuario'", Integer.class);
        if (tabelas != null && tabelas > 0) {
            log.info("Banco de dados já existe.");
            return;
        }
        log.info("Criando banco de dados a partir de {}", pastaScripts.toAbsolutePath());
        executar(pastaScripts.resolve("schema.sql"));
        executar(pastaScripts.resolve("seed.sql"));
    }

    private void executar(Path arquivo) throws IOException {
        String script = Files.readString(arquivo, StandardCharsets.UTF_8);
        for (String comando : ScriptSql.dividir(script)) {
            jdbc.execute(comando);
        }
    }
}
