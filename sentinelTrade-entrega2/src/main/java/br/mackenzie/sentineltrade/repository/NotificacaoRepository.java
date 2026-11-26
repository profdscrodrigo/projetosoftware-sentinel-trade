package br.mackenzie.sentineltrade.repository;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import br.mackenzie.sentineltrade.model.Notificacao;

@Repository
public class NotificacaoRepository {

    private final JdbcTemplate jdbc;

    public NotificacaoRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Cria a notificação para o dono da conta da ordem. */
    public void inserirParaConta(long contaId, long ordemId, String mensagem) {
        jdbc.update("INSERT INTO notificacao (investidor_id, ordem_id, mensagem, status) "
                        + "SELECT investidor_id, ?, ?, 'ENVIADA' FROM conta WHERE id = ?",
                ordemId, mensagem, contaId);
    }

    public List<Notificacao> listarRecentes(long investidorId, int limite) {
        return jdbc.query(
                "SELECT mensagem, strftime('%d/%m/%Y %H:%M', criado_em, 'localtime') AS quando "
                        + "FROM notificacao WHERE investidor_id = ? ORDER BY id DESC LIMIT ?",
                (rs, n) -> new Notificacao(rs.getString("mensagem"), rs.getString("quando")),
                investidorId, limite);
    }
}
