package br.mackenzie.sentineltrade.repository;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import br.mackenzie.sentineltrade.model.ContaComLimite;
import br.mackenzie.sentineltrade.model.LimiteRisco;

@Repository
public class ContaRepository {

    private final JdbcTemplate jdbc;

    public ContaRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public BigDecimal buscarSaldo(long contaId) {
        return jdbc.queryForObject("SELECT saldo_disponivel FROM conta WHERE id = ?",
                (rs, n) -> Valores.dinheiro(rs, "saldo_disponivel"), contaId);
    }

    public void atualizarSaldo(long contaId, BigDecimal novoSaldo) {
        jdbc.update("UPDATE conta SET saldo_disponivel = ? WHERE id = ?", novoSaldo, contaId);
    }

    public boolean existe(long contaId) {
        Integer total = jdbc.queryForObject("SELECT COUNT(*) FROM conta WHERE id = ?", Integer.class, contaId);
        return total != null && total > 0;
    }

    public LimiteRisco buscarLimite(long contaId) {
        return jdbc.queryForObject(
                "SELECT limite_por_ordem, limite_diario, limite_alto_risco FROM limite_risco WHERE conta_id = ?",
                (rs, n) -> new LimiteRisco(Valores.dinheiro(rs, "limite_por_ordem"),
                        Valores.dinheiro(rs, "limite_diario"), Valores.dinheiro(rs, "limite_alto_risco")),
                contaId);
    }

    public void atualizarLimite(long contaId, LimiteRisco limite, long usuarioId) {
        jdbc.update("UPDATE limite_risco SET limite_por_ordem = ?, limite_diario = ?, limite_alto_risco = ?, "
                        + "atualizado_por = ?, atualizado_em = datetime('now') WHERE conta_id = ?",
                limite.getPorOrdem(), limite.getDiario(), limite.getAltoRisco(), usuarioId, contaId);
    }

    public List<ContaComLimite> listarComLimites() {
        return jdbc.query(
                "SELECT c.id, c.numero, u.nome, c.saldo_disponivel, "
                        + "l.limite_por_ordem, l.limite_diario, l.limite_alto_risco "
                        + "FROM conta c "
                        + "JOIN investidor i ON i.id = c.investidor_id "
                        + "JOIN usuario u ON u.id = i.usuario_id "
                        + "JOIN limite_risco l ON l.conta_id = c.id "
                        + "ORDER BY c.numero",
                (rs, n) -> new ContaComLimite(rs.getLong("id"), rs.getString("numero"), rs.getString("nome"),
                        Valores.dinheiro(rs, "saldo_disponivel"),
                        new LimiteRisco(Valores.dinheiro(rs, "limite_por_ordem"),
                                Valores.dinheiro(rs, "limite_diario"),
                                Valores.dinheiro(rs, "limite_alto_risco"))));
    }
}
