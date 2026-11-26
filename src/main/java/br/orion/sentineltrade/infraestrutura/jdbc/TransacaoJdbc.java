package br.orion.sentineltrade.infraestrutura.jdbc;

import br.orion.sentineltrade.aplicacao.porta.GerenciadorTransacao;
import br.orion.sentineltrade.aplicacao.servico.Log;
import br.orion.sentineltrade.dominio.Excecoes.ChaveDuplicadaException;
import br.orion.sentineltrade.dominio.Excecoes.ConcorrenciaException;
import br.orion.sentineltrade.dominio.Excecoes.PersistenciaException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.function.Supplier;

/**
 * Fronteira transacional sobre JDBC. Uma conexão por transação, vinculada à thread: todos os repositórios
 * chamados dentro de {@code executar} usam a MESMA conexão e confirmam (commit) ou desfazem (rollback) juntos.
 * Fora de uma transação, cada operação usa uma conexão em auto-commit.
 * (Para produção, trocar DriverManager por um pool — ex.: HikariCP — sem alterar o restante.)
 */
public final class TransacaoJdbc implements GerenciadorTransacao {
    @FunctionalInterface public interface UsaConexao<T> { T aplicar(Connection c) throws SQLException; }

    private final String url, usuario, senha;
    private final ThreadLocal<Connection> atual = new ThreadLocal<>();

    public TransacaoJdbc(String url, String usuario, String senha) { this.url = url; this.usuario = usuario; this.senha = senha; }

    private Connection abrir() throws SQLException {
        return usuario == null ? DriverManager.getConnection(url) : DriverManager.getConnection(url, usuario, senha);
    }

    @Override public <T> T executar(Supplier<T> acao) {
        if (atual.get() != null) return acao.get();                      // já dentro de uma transação: participa dela
        try (Connection c = abrir()) {
            c.setAutoCommit(false);
            atual.set(c);
            try {
                T r = acao.get();
                c.commit();
                return r;
            } catch (RuntimeException | Error e) {
                try { c.rollback(); } catch (SQLException ex) { e.addSuppressed(ex); }
                throw e;
            } catch (SQLException e) {                                    // falha no commit
                try { c.rollback(); } catch (SQLException ex) { e.addSuppressed(ex); }
                throw traduzir(e);
            } finally {
                atual.remove();
            }
        } catch (SQLException e) {
            throw traduzir(e);
        }
    }

    <T> T comConexao(UsaConexao<T> f) {
        Connection c = atual.get();
        try {
            if (c != null) return f.aplicar(c);
            try (Connection nova = abrir()) { return f.aplicar(nova); }
        } catch (SQLException e) {
            throw traduzir(e);
        }
    }

    /** Traduz SQLState em exceções do domínio. A mensagem ao usuário é genérica; só o SQLState vai ao log. */
    static RuntimeException traduzir(SQLException e) {
        String estado = e.getSQLState() == null ? "" : e.getSQLState();
        if (estado.equals("23505")) return new ChaveDuplicadaException("registro duplicado");
        if (estado.startsWith("40")) return new ConcorrenciaException("conflito de concorrência");   // serialização / deadlock
        Log.erro("Erro de banco de dados (SQLState=" + estado + ")", e);
        return new PersistenciaException("Erro ao acessar o banco de dados", e);
    }
}
