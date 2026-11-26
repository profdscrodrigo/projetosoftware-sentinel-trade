package br.mackenzie.sentineltrade.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ParametroRepository {

    private final JdbcTemplate jdbc;

    public ParametroRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<String> buscar(String chave) {
        List<String> lista = jdbc.query("SELECT valor FROM parametro_sistema WHERE chave = ?",
                (rs, n) -> rs.getString("valor"), chave);
        return lista.stream().findFirst();
    }

    public boolean isVerdadeiro(String chave) {
        return buscar(chave).map("true"::equalsIgnoreCase).orElse(false);
    }

    public int inteiro(String chave, int padrao) {
        try {
            return buscar(chave).map(Integer::parseInt).orElse(padrao);
        } catch (NumberFormatException e) {
            return padrao;
        }
    }

    public void atualizar(String chave, String valor) {
        jdbc.update("UPDATE parametro_sistema SET valor = ?, atualizado_em = datetime('now') WHERE chave = ?",
                valor, chave);
    }
}
