package br.mackenzie.sentineltrade.repository;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import br.mackenzie.sentineltrade.model.HistoricoStatus;
import br.mackenzie.sentineltrade.model.Ordem;
import br.mackenzie.sentineltrade.model.OrdemRejeitada;
import br.mackenzie.sentineltrade.model.StatusOrdem;
import br.mackenzie.sentineltrade.model.TipoOrdem;

@Repository
public class OrdemRepository {

    private static final String SELECT_ORDEM =
            "SELECT o.id, o.chave_idempotencia, o.conta_id, o.ativo_id, a.codigo, o.tipo, o.quantidade, o.preco, "
                    + "o.status, o.motivo, strftime('%d/%m/%Y %H:%M', o.criado_em, 'localtime') AS criado "
                    + "FROM ordem o JOIN ativo a ON a.id = o.ativo_id ";

    private static final RowMapper<Ordem> MAPEADOR = (rs, n) -> Ordem.doBanco(
            rs.getLong("id"), rs.getString("chave_idempotencia"), rs.getLong("conta_id"), rs.getLong("ativo_id"),
            rs.getString("codigo"), TipoOrdem.valueOf(rs.getString("tipo")), rs.getInt("quantidade"),
            Valores.dinheiro(rs, "preco"), StatusOrdem.valueOf(rs.getString("status")),
            rs.getString("motivo"), rs.getString("criado"));

    private final JdbcTemplate jdbc;

    public OrdemRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** RF-14: verifica se já existe ordem com esta chave de idempotência. */
    public boolean existeChave(String chave) {
        Integer total = jdbc.queryForObject("SELECT COUNT(*) FROM ordem WHERE chave_idempotencia = ?",
                Integer.class, chave);
        return total != null && total > 0;
    }

    /** Salva a ordem nova (o gatilho do banco registra histórico e auditoria). */
    public void inserir(Ordem ordem) {
        jdbc.update("INSERT INTO ordem (chave_idempotencia, conta_id, ativo_id, tipo, quantidade, preco, status) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                ordem.getChaveIdempotencia(), ordem.getContaId(), ordem.getAtivoId(), ordem.getTipo().name(),
                ordem.getQuantidade(), ordem.getPreco(), ordem.getStatus().name());
        Long id = jdbc.queryForObject("SELECT id FROM ordem WHERE chave_idempotencia = ?", Long.class,
                ordem.getChaveIdempotencia());
        ordem.definirId(id);
    }

    /** Grava o novo status (o gatilho do banco registra histórico e auditoria). */
    public void atualizarStatus(Ordem ordem) {
        jdbc.update("UPDATE ordem SET status = ?, motivo = ?, atualizado_em = datetime('now') WHERE id = ?",
                ordem.getStatus().name(), ordem.getMotivo(), ordem.getId());
    }

    public void atualizarPreco(Ordem ordem) {
        jdbc.update("UPDATE ordem SET preco = ?, atualizado_em = datetime('now') WHERE id = ?",
                ordem.getPreco(), ordem.getId());
    }

    public Optional<Ordem> buscarPorId(long id) {
        return jdbc.query(SELECT_ORDEM + "WHERE o.id = ?", MAPEADOR, id).stream().findFirst();
    }

    public List<Ordem> listarPorConta(long contaId) {
        return jdbc.query(SELECT_ORDEM + "WHERE o.conta_id = ? ORDER BY o.id DESC", MAPEADOR, contaId);
    }

    public List<Ordem> listarPorContaEStatus(long contaId, StatusOrdem status) {
        return jdbc.query(SELECT_ORDEM + "WHERE o.conta_id = ? AND o.status = ? ORDER BY o.id DESC",
                MAPEADOR, contaId, status.name());
    }

    public List<Ordem> listarPorStatus(StatusOrdem status) {
        return jdbc.query(SELECT_ORDEM + "WHERE o.status = ? ORDER BY o.id", MAPEADOR, status.name());
    }

    public List<Ordem> listarPorAtivoEStatus(long ativoId, StatusOrdem status) {
        return jdbc.query(SELECT_ORDEM + "WHERE o.ativo_id = ? AND o.status = ? ORDER BY o.id",
                MAPEADOR, ativoId, status.name());
    }

    /** Soma das ordens de hoje que contam para o limite diário (sem a própria ordem). */
    public BigDecimal totalDoDia(long contaId, long excetoOrdemId) {
        BigDecimal total = jdbc.queryForObject(
                "SELECT COALESCE(SUM(quantidade * preco), 0) FROM ordem "
                        + "WHERE conta_id = ? AND id <> ? "
                        + "AND date(criado_em, 'localtime') = date('now', 'localtime') "
                        + "AND status NOT IN ('REJEITADA', 'CANCELADA')",
                BigDecimal.class, contaId, excetoOrdemId);
        return total == null ? BigDecimal.ZERO : total;
    }

    /** Histórico de status das ordens de uma conta, agrupado por ordem (RF-10). */
    public Map<Long, List<HistoricoStatus>> historicoDaConta(long contaId) {
        List<HistoricoStatus> linhas = jdbc.query(
                "SELECT h.ordem_id, h.status_novo, h.motivo, "
                        + "strftime('%d/%m/%Y %H:%M:%S', h.data_hora, 'localtime') AS quando "
                        + "FROM historico_status_ordem h JOIN ordem o ON o.id = h.ordem_id "
                        + "WHERE o.conta_id = ? ORDER BY h.id",
                (rs, n) -> new HistoricoStatus(rs.getLong("ordem_id"), rs.getString("status_novo"),
                        rs.getString("motivo"), rs.getString("quando")),
                contaId);
        Map<Long, List<HistoricoStatus>> mapa = new LinkedHashMap<>();
        for (HistoricoStatus h : linhas) {
            mapa.computeIfAbsent(h.getOrdemId(), id -> new java.util.ArrayList<>()).add(h);
        }
        return mapa;
    }

    /** Ordens rejeitadas mais recentes, para o analista de risco (RF-18). */
    public List<OrdemRejeitada> listarRejeitadas(int limite) {
        return jdbc.query(
                "SELECT strftime('%d/%m/%Y %H:%M', o.criado_em, 'localtime') AS quando, c.numero, u.nome, "
                        + "a.codigo, o.tipo, o.quantidade * o.preco AS total, o.motivo "
                        + "FROM ordem o "
                        + "JOIN conta c ON c.id = o.conta_id "
                        + "JOIN investidor i ON i.id = c.investidor_id "
                        + "JOIN usuario u ON u.id = i.usuario_id "
                        + "JOIN ativo a ON a.id = o.ativo_id "
                        + "WHERE o.status = 'REJEITADA' ORDER BY o.id DESC LIMIT ?",
                (rs, n) -> new OrdemRejeitada(rs.getString("quando"), rs.getString("numero"), rs.getString("nome"),
                        rs.getString("codigo"), rs.getString("tipo"), Valores.dinheiro(rs, "total"),
                        rs.getString("motivo")),
                limite);
    }
}
