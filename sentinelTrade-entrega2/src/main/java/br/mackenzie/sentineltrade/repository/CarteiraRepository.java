package br.mackenzie.sentineltrade.repository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import br.mackenzie.sentineltrade.model.Posicao;

@Repository
public class CarteiraRepository {

    private final JdbcTemplate jdbc;

    public CarteiraRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Ativos da carteira (só os que têm quantidade maior que zero), com a cotação atual. */
    public List<Posicao> listarPorConta(long contaId) {
        return jdbc.query(
                "SELECT a.codigo, a.nome, p.quantidade, p.preco_medio, "
                        + "(SELECT ct.preco FROM cotacao ct WHERE ct.ativo_id = a.id "
                        + " ORDER BY ct.data_hora DESC, ct.id DESC LIMIT 1) AS cotacao "
                        + "FROM posicao p "
                        + "JOIN carteira c ON c.id = p.carteira_id "
                        + "JOIN ativo a ON a.id = p.ativo_id "
                        + "WHERE c.conta_id = ? AND p.quantidade > 0 "
                        + "ORDER BY a.codigo",
                (rs, n) -> new Posicao(rs.getString("codigo"), rs.getString("nome"), rs.getInt("quantidade"),
                        Valores.dinheiro(rs, "preco_medio"), Valores.dinheiro(rs, "cotacao")),
                contaId);
    }

    public int quantidade(long contaId, long ativoId) {
        List<Integer> lista = jdbc.query(
                "SELECT p.quantidade FROM posicao p JOIN carteira c ON c.id = p.carteira_id "
                        + "WHERE c.conta_id = ? AND p.ativo_id = ?",
                (rs, n) -> rs.getInt("quantidade"), contaId, ativoId);
        return lista.isEmpty() ? 0 : lista.get(0);
    }

    /** Compra: soma a quantidade e recalcula o preço médio. */
    public void registrarCompra(long contaId, long ativoId, int quantidade, BigDecimal preco) {
        List<BigDecimal[]> atual = jdbc.query(
                "SELECT p.quantidade, p.preco_medio FROM posicao p JOIN carteira c ON c.id = p.carteira_id "
                        + "WHERE c.conta_id = ? AND p.ativo_id = ?",
                (rs, n) -> new BigDecimal[] {BigDecimal.valueOf(rs.getInt("quantidade")),
                        Valores.dinheiro(rs, "preco_medio")},
                contaId, ativoId);

        BigDecimal qtdAtual = atual.isEmpty() ? BigDecimal.ZERO : atual.get(0)[0];
        BigDecimal medioAtual = atual.isEmpty() ? BigDecimal.ZERO : atual.get(0)[1];
        BigDecimal qtdCompra = BigDecimal.valueOf(quantidade);
        BigDecimal novaQtd = qtdAtual.add(qtdCompra);
        BigDecimal novoMedio = qtdAtual.multiply(medioAtual).add(qtdCompra.multiply(preco))
                .divide(novaQtd, 2, RoundingMode.HALF_UP);

        jdbc.update("INSERT INTO posicao (carteira_id, ativo_id, quantidade, preco_medio) "
                        + "VALUES ((SELECT id FROM carteira WHERE conta_id = ?), ?, ?, ?) "
                        + "ON CONFLICT (carteira_id, ativo_id) "
                        + "DO UPDATE SET quantidade = excluded.quantidade, preco_medio = excluded.preco_medio",
                contaId, ativoId, novaQtd.intValue(), novoMedio);
    }

    /** Venda: diminui a quantidade (o banco impede que fique negativa). */
    public void registrarVenda(long contaId, long ativoId, int quantidade) {
        jdbc.update("UPDATE posicao SET quantidade = quantidade - ? "
                        + "WHERE ativo_id = ? AND carteira_id = (SELECT id FROM carteira WHERE conta_id = ?)",
                quantidade, ativoId, contaId);
    }
}
