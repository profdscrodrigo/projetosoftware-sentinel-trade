package br.mackenzie.sentineltrade.repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import br.mackenzie.sentineltrade.model.Ativo;

@Repository
public class AtivoRepository {

    /** Ativo com a cotação mais recente. */
    private static final String SELECT_ATIVO =
            "SELECT a.id, a.codigo, a.nome, a.tipo, a.negociavel, c.preco, c.variacao_percentual "
                    + "FROM ativo a "
                    + "LEFT JOIN cotacao c ON c.id = (SELECT c2.id FROM cotacao c2 WHERE c2.ativo_id = a.id "
                    + "ORDER BY c2.data_hora DESC, c2.id DESC LIMIT 1) ";

    private static final RowMapper<Ativo> MAPEADOR = (rs, n) -> new Ativo(
            rs.getLong("id"), rs.getString("codigo"), rs.getString("nome"), rs.getString("tipo"),
            rs.getInt("negociavel") == 1, Valores.dinheiro(rs, "preco"), Valores.dinheiro(rs, "variacao_percentual"));

    private final JdbcTemplate jdbc;

    public AtivoRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Ativo> listar() {
        return jdbc.query(SELECT_ATIVO + "ORDER BY a.codigo", MAPEADOR);
    }

    public Optional<Ativo> buscarPorCodigo(String codigo) {
        return jdbc.query(SELECT_ATIVO + "WHERE a.codigo = ?", MAPEADOR, codigo).stream().findFirst();
    }

    public Optional<Ativo> buscarPorId(long id) {
        return jdbc.query(SELECT_ATIVO + "WHERE a.id = ?", MAPEADOR, id).stream().findFirst();
    }

    public void inserirCotacao(long ativoId, BigDecimal preco, BigDecimal variacao) {
        jdbc.update("INSERT INTO cotacao (ativo_id, preco, variacao_percentual) VALUES (?, ?, ?)",
                ativoId, preco, variacao);
    }
}
