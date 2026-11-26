package br.mackenzie.sentineltrade.repository;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import br.mackenzie.sentineltrade.model.RegistroAuditoria;

/** A tabela de auditoria só aceita INSERT: o banco bloqueia UPDATE e DELETE (RNF-07). */
@Repository
public class AuditoriaRepository {

    private final JdbcTemplate jdbc;

    public AuditoriaRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void inserir(Long usuarioId, String acao, String entidade, Long entidadeId, String detalhes) {
        jdbc.update("INSERT INTO auditoria (usuario_id, acao, entidade, entidade_id, detalhes) VALUES (?, ?, ?, ?, ?)",
                usuarioId, acao, entidade, entidadeId, detalhes);
    }

    public List<RegistroAuditoria> listarRecentes(int limite) {
        return jdbc.query(
                "SELECT strftime('%d/%m/%Y %H:%M:%S', a.data_hora, 'localtime') AS quando, "
                        + "COALESCE(u.nome, 'Sistema') AS quem, a.acao, a.entidade, a.entidade_id, a.detalhes "
                        + "FROM auditoria a LEFT JOIN usuario u ON u.id = a.usuario_id "
                        + "ORDER BY a.id DESC LIMIT ?",
                (rs, n) -> {
                    long entidadeId = rs.getLong("entidade_id");
                    return new RegistroAuditoria(rs.getString("quando"), rs.getString("quem"), rs.getString("acao"),
                            rs.getString("entidade"), rs.wasNull() ? null : entidadeId, rs.getString("detalhes"));
                },
                limite);
    }
}
