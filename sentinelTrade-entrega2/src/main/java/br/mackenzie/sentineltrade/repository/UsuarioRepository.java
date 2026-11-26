package br.mackenzie.sentineltrade.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import br.mackenzie.sentineltrade.model.Administrador;
import br.mackenzie.sentineltrade.model.AnalistaRisco;
import br.mackenzie.sentineltrade.model.Investidor;
import br.mackenzie.sentineltrade.model.Perfil;
import br.mackenzie.sentineltrade.model.Usuario;

@Repository
public class UsuarioRepository {

    /** Dados usados só na hora do login (o hash da senha nunca sai da camada de segurança). */
    public record DadosLogin(long id, String senhaHash, boolean ativo, boolean bloqueado) {
    }

    private final JdbcTemplate jdbc;

    public UsuarioRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<DadosLogin> buscarDadosLogin(String email) {
        List<DadosLogin> lista = jdbc.query(
                "SELECT id, senha_hash, ativo, "
                        + "(bloqueado_ate IS NOT NULL AND bloqueado_ate > datetime('now')) AS bloqueado "
                        + "FROM usuario WHERE email = ?",
                (rs, n) -> new DadosLogin(rs.getLong("id"), rs.getString("senha_hash"),
                        rs.getInt("ativo") == 1, rs.getInt("bloqueado") == 1),
                email);
        return lista.stream().findFirst();
    }

    /** Conta mais uma tentativa errada; ao chegar no máximo, bloqueia por alguns minutos (RN-11). */
    public void registrarFalhaLogin(long id, int maximoTentativas, int minutosBloqueio) {
        jdbc.update("UPDATE usuario SET tentativas_login = tentativas_login + 1, "
                        + "bloqueado_ate = CASE WHEN tentativas_login + 1 >= ? "
                        + "THEN datetime('now', ?) ELSE bloqueado_ate END WHERE id = ?",
                maximoTentativas, "+" + minutosBloqueio + " minutes", id);
    }

    public void zerarTentativas(long id) {
        jdbc.update("UPDATE usuario SET tentativas_login = 0, bloqueado_ate = NULL WHERE id = ?", id);
    }

    /** Monta o objeto certo (Investidor, Administrador ou AnalistaRisco) conforme o perfil. */
    public Optional<Usuario> buscarPorId(long id) {
        List<Usuario> lista = jdbc.query(
                "SELECT u.id, u.nome, u.email, u.perfil, i.id AS investidor_id, c.id AS conta_id "
                        + "FROM usuario u "
                        + "LEFT JOIN investidor i ON i.usuario_id = u.id "
                        + "LEFT JOIN conta c ON c.investidor_id = i.id "
                        + "WHERE u.id = ?",
                (rs, n) -> {
                    Perfil perfil = Perfil.valueOf(rs.getString("perfil"));
                    long usuarioId = rs.getLong("id");
                    String nome = rs.getString("nome");
                    String email = rs.getString("email");
                    return switch (perfil) {
                        case INVESTIDOR -> new Investidor(usuarioId, nome, email,
                                rs.getLong("investidor_id"), rs.getLong("conta_id"));
                        case ADMINISTRADOR -> new Administrador(usuarioId, nome, email);
                        case ANALISTA_RISCO -> new AnalistaRisco(usuarioId, nome, email);
                    };
                },
                id);
        return lista.stream().findFirst();
    }
}
