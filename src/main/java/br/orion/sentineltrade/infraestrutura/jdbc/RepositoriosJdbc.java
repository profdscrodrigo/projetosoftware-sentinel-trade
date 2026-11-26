package br.orion.sentineltrade.infraestrutura.jdbc;

import br.orion.sentineltrade.aplicacao.porta.FilaOrdens;
import br.orion.sentineltrade.aplicacao.porta.MensagemFila;
import br.orion.sentineltrade.aplicacao.porta.Persistencia;
import br.orion.sentineltrade.aplicacao.porta.Repositorios.*;
import br.orion.sentineltrade.dominio.*;
import br.orion.sentineltrade.dominio.Excecoes.ChaveDuplicadaException;
import br.orion.sentineltrade.dominio.Excecoes.ConcorrenciaException;
import br.orion.sentineltrade.infraestrutura.jdbc.Jdbc.Mapeador;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Adaptadores JDBC (PostgreSQL) das portas de persistência.
 * Regras: SQL sempre parametrizado; atualizações com controle de versão (concorrência otimista);
 * INSERT ... ON CONFLICT DO NOTHING onde a duplicidade é um resultado esperado (idempotência), para não
 * abortar a transação do PostgreSQL.
 */
public final class RepositoriosJdbc {
    private RepositoriosJdbc() {}

    public static Persistencia persistencia(TransacaoJdbc tx) {
        Jdbc j = new Jdbc(tx);
        return new Persistencia(new Usuarios(j), new Sessoes(j), new Contas(j), new Posicoes(j), new Ativos(j), new Ordens(j),
                new Execucoes(j), new Limites(j), new Auditoria(j), new Notificacoes(j), new Parametros(j), new Fila(j), tx);
    }

    private static void exigirUma(int linhas, String entidade) {
        if (linhas != 1) throw new ConcorrenciaException(entidade + " alterado por outra transação");
    }

    // ------------------------------------------------------------------------------------------ usuários
    static final class Usuarios implements UsuarioRepositorio {
        static final String SQL_INSERIR_USUARIO = """
                INSERT INTO usuarios (id, nome, email, papel, senha_hash, segredo_mfa, ativo, falhas_login, bloqueado_ate, criado_em, versao)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1)
                ON CONFLICT (email) DO NOTHING""";
        static final String SQL_ATUALIZAR_USUARIO = """
                UPDATE usuarios SET senha_hash = ?, ativo = ?, falhas_login = ?, bloqueado_ate = ?, versao = versao + 1
                WHERE id = ? AND versao = ?""";
        static final String SQL_USUARIO_POR_ID = "SELECT * FROM usuarios WHERE id = ?";
        static final String SQL_USUARIO_POR_EMAIL = "SELECT * FROM usuarios WHERE email = ?";
        static final String SQL_USUARIOS_POR_PAPEL = "SELECT * FROM usuarios WHERE papel = ? ORDER BY nome";
        static final String SQL_CONTAR_USUARIOS = "SELECT COUNT(*) AS total FROM usuarios";

        private final Jdbc j;
        Usuarios(Jdbc j) { this.j = j; }
        private static final Mapeador<Usuario> MAP = rs -> new Usuario(Jdbc.uuid(rs, "id"), rs.getString("nome"), rs.getString("email"),
                Papel.valueOf(rs.getString("papel")), rs.getString("senha_hash"), rs.getString("segredo_mfa"), rs.getBoolean("ativo"),
                rs.getInt("falhas_login"), Jdbc.instante(rs, "bloqueado_ate"), Jdbc.instante(rs, "criado_em"), rs.getLong("versao"));

        @Override public void inserir(Usuario u) {
            if (j.atualizar(SQL_INSERIR_USUARIO, u.id(), u.nome(), u.email(), u.papel(), u.senhaHash(), u.segredoMfa(), u.ativo(),
                    u.falhasLogin(), u.bloqueadoAte(), u.criadoEm()) == 0) throw new ChaveDuplicadaException("e-mail");
            u.versaoPersistida(1);
        }
        @Override public void atualizar(Usuario u) {
            exigirUma(j.atualizar(SQL_ATUALIZAR_USUARIO, u.senhaHash(), u.ativo(), u.falhasLogin(), u.bloqueadoAte(), u.id(), u.versao()), "usuário");
            u.versaoPersistida(u.versao() + 1);
        }
        @Override public Optional<Usuario> buscarPorId(UUID id) { return j.um(SQL_USUARIO_POR_ID, MAP, id); }
        @Override public Optional<Usuario> buscarPorEmail(String e) { return j.um(SQL_USUARIO_POR_EMAIL, MAP, e); }
        @Override public List<Usuario> listarPorPapel(Papel p) { return j.consultar(SQL_USUARIOS_POR_PAPEL, MAP, p); }
        @Override public long contar() { return j.um(SQL_CONTAR_USUARIOS, rs -> rs.getLong("total")).orElse(0L); }
    }

    // ------------------------------------------------------------------------------------------ sessões
    static final class Sessoes implements SessaoRepositorio {
        static final String SQL_SALVAR_SESSAO = """
                INSERT INTO sessoes (id, usuario_id, papel, estado, token_hash, criada_em, expira_em, tentativas_mfa)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET estado = EXCLUDED.estado, tentativas_mfa = EXCLUDED.tentativas_mfa""";
        static final String SQL_SESSAO_POR_TOKEN = "SELECT * FROM sessoes WHERE token_hash = ?";

        private final Jdbc j;
        Sessoes(Jdbc j) { this.j = j; }
        @Override public void salvar(Sessao s) {
            j.atualizar(SQL_SALVAR_SESSAO, s.id(), s.usuarioId(), s.papel(), s.estado(), s.tokenHash(), s.criadaEm(), s.expiraEm(), s.tentativasMfa());
        }
        @Override public Optional<Sessao> buscarPorTokenHash(String h) {
            return j.um(SQL_SESSAO_POR_TOKEN, rs -> new Sessao(Jdbc.uuid(rs, "id"), Jdbc.uuid(rs, "usuario_id"), Papel.valueOf(rs.getString("papel")),
                    Sessao.Estado.valueOf(rs.getString("estado")), rs.getString("token_hash").trim(), Jdbc.instante(rs, "criada_em"),
                    Jdbc.instante(rs, "expira_em"), rs.getInt("tentativas_mfa")), h);
        }
    }

    // ------------------------------------------------------------------------------------------ contas
    static final class Contas implements ContaRepositorio {
        static final String SQL_INSERIR_CONTA = "INSERT INTO contas (id, investidor_id, saldo_disponivel, saldo_bloqueado, versao) VALUES (?, ?, ?, ?, 1)";
        static final String SQL_ATUALIZAR_CONTA = """
                UPDATE contas SET saldo_disponivel = ?, saldo_bloqueado = ?, versao = versao + 1
                WHERE id = ? AND versao = ?""";
        static final String SQL_CONTA_POR_INVESTIDOR = "SELECT * FROM contas WHERE investidor_id = ?";

        private final Jdbc j;
        Contas(Jdbc j) { this.j = j; }
        @Override public void inserir(Conta c) {
            j.atualizar(SQL_INSERIR_CONTA, c.id(), c.investidorId(), c.saldoDisponivel(), c.saldoBloqueado());
            c.versaoPersistida(1);
        }
        @Override public void atualizar(Conta c) {
            exigirUma(j.atualizar(SQL_ATUALIZAR_CONTA, c.saldoDisponivel(), c.saldoBloqueado(), c.id(), c.versao()), "conta");
            c.versaoPersistida(c.versao() + 1);
        }
        @Override public Optional<Conta> buscarPorInvestidor(UUID id) {
            return j.um(SQL_CONTA_POR_INVESTIDOR, rs -> new Conta(Jdbc.uuid(rs, "id"), Jdbc.uuid(rs, "investidor_id"),
                    Jdbc.dinheiro(rs, "saldo_disponivel"), Jdbc.dinheiro(rs, "saldo_bloqueado"), rs.getLong("versao")), id);
        }
    }

    // ------------------------------------------------------------------------------------------ posições
    static final class Posicoes implements PosicaoRepositorio {
        static final String SQL_INSERIR_POSICAO = """
                INSERT INTO posicoes (investidor_id, ticker, quantidade, quantidade_bloqueada, custo_total, versao)
                VALUES (?, ?, ?, ?, ?, 1)
                ON CONFLICT (investidor_id, ticker) DO NOTHING""";
        static final String SQL_ATUALIZAR_POSICAO = """
                UPDATE posicoes SET quantidade = ?, quantidade_bloqueada = ?, custo_total = ?, versao = versao + 1
                WHERE investidor_id = ? AND ticker = ? AND versao = ?""";
        static final String SQL_POSICAO = "SELECT * FROM posicoes WHERE investidor_id = ? AND ticker = ?";
        static final String SQL_POSICOES_DO_INVESTIDOR = "SELECT * FROM posicoes WHERE investidor_id = ? ORDER BY ticker";

        private final Jdbc j;
        Posicoes(Jdbc j) { this.j = j; }
        private static final Mapeador<Posicao> MAP = rs -> new Posicao(Jdbc.uuid(rs, "investidor_id"), Ticker.de(rs.getString("ticker")),
                rs.getInt("quantidade"), rs.getInt("quantidade_bloqueada"), Jdbc.dinheiro(rs, "custo_total"), rs.getLong("versao"));

        @Override public Optional<Posicao> buscar(UUID i, Ticker t) { return j.um(SQL_POSICAO, MAP, i, t); }
        @Override public List<Posicao> listarPorInvestidor(UUID i) { return j.consultar(SQL_POSICOES_DO_INVESTIDOR, MAP, i); }
        @Override public void salvar(Posicao p) {
            if (p.versao() == 0) {
                exigirUma(j.atualizar(SQL_INSERIR_POSICAO, p.investidorId(), p.ticker(), p.quantidade(), p.quantidadeBloqueada(), p.custoTotal()), "posição");
                p.versaoPersistida(1);
            } else {
                exigirUma(j.atualizar(SQL_ATUALIZAR_POSICAO, p.quantidade(), p.quantidadeBloqueada(), p.custoTotal(),
                        p.investidorId(), p.ticker(), p.versao()), "posição");
                p.versaoPersistida(p.versao() + 1);
            }
        }
    }

    // ------------------------------------------------------------------------------------------ ativos
    static final class Ativos implements AtivoRepositorio {
        static final String SQL_INSERIR_ATIVO = "INSERT INTO ativos (ticker, nome, tipo, status, versao) VALUES (?, ?, ?, ?, 1)";
        static final String SQL_ATUALIZAR_ATIVO = "UPDATE ativos SET status = ?, versao = versao + 1 WHERE ticker = ? AND versao = ?";
        static final String SQL_ATIVO = "SELECT * FROM ativos WHERE ticker = ?";
        static final String SQL_ATIVOS = "SELECT * FROM ativos ORDER BY ticker";

        private final Jdbc j;
        Ativos(Jdbc j) { this.j = j; }
        private static final Mapeador<Ativo> MAP = rs -> new Ativo(Ticker.de(rs.getString("ticker")), rs.getString("nome"),
                TipoAtivo.valueOf(rs.getString("tipo")), StatusAtivo.valueOf(rs.getString("status")), rs.getLong("versao"));

        @Override public Optional<Ativo> buscar(Ticker t) { return j.um(SQL_ATIVO, MAP, t); }
        @Override public List<Ativo> listar() { return j.consultar(SQL_ATIVOS, MAP); }
        @Override public void salvar(Ativo a) {
            if (a.versao() == 0) { j.atualizar(SQL_INSERIR_ATIVO, a.ticker(), a.nome(), a.tipo(), a.status()); a.versaoPersistida(1); }
            else { exigirUma(j.atualizar(SQL_ATUALIZAR_ATIVO, a.status(), a.ticker(), a.versao()), "ativo"); a.versaoPersistida(a.versao() + 1); }
        }
    }

    // ------------------------------------------------------------------------------------------ ordens
    static final class Ordens implements OrdemRepositorio {
        static final String SQL_INSERIR_ORDEM = """
                INSERT INTO ordens (id, investidor_id, ticker, lado, tipo_ordem, quantidade, preco_limite, chave_idempotencia, impressao_digital,
                                    status, quantidade_executada, valor_executado, motivo_rejeicao, substitui_ordem_id, criada_em, atualizada_em, versao)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1)
                ON CONFLICT (investidor_id, chave_idempotencia) DO NOTHING""";
        static final String SQL_ATUALIZAR_ORDEM = """
                UPDATE ordens SET status = ?, quantidade_executada = ?, valor_executado = ?, motivo_rejeicao = ?,
                                  atualizada_em = ?, versao = versao + 1
                WHERE id = ? AND versao = ?""";
        static final String SQL_ORDEM_POR_ID = "SELECT * FROM ordens WHERE id = ?";
        static final String SQL_ORDEM_POR_CHAVE = "SELECT * FROM ordens WHERE investidor_id = ? AND chave_idempotencia = ?";
        static final String SQL_ORDENS_DO_INVESTIDOR = "SELECT * FROM ordens WHERE investidor_id = ? ORDER BY criada_em DESC, id DESC";
        static final String SQL_ORDENS_POR_STATUS = "SELECT * FROM ordens WHERE status = ? ORDER BY criada_em DESC, id DESC LIMIT ?";
        static final String SQL_VALOR_NEGOCIADO = """
                SELECT COALESCE(SUM(quantidade * preco_limite), 0) AS total FROM ordens
                WHERE investidor_id = ? AND status IN ('VALIDADA', 'ENVIADA', 'PARCIALMENTE_EXECUTADA', 'EXECUTADA')
                  AND criada_em >= ? AND criada_em < ?""";

        private final Jdbc j;
        Ordens(Jdbc j) { this.j = j; }
        private static final Mapeador<Ordem> MAP = rs -> {
            String motivo = rs.getString("motivo_rejeicao");
            String subst = rs.getString("substitui_ordem_id");
            return Ordem.reconstituir(Lado.valueOf(rs.getString("lado")), Jdbc.uuid(rs, "id"), Jdbc.uuid(rs, "investidor_id"),
                    Ticker.de(rs.getString("ticker")), rs.getInt("quantidade"), Jdbc.dinheiro(rs, "preco_limite"),
                    rs.getString("chave_idempotencia"), rs.getString("impressao_digital").trim(), StatusOrdem.valueOf(rs.getString("status")),
                    rs.getInt("quantidade_executada"), Jdbc.dinheiro(rs, "valor_executado"), motivo == null ? null : CodigoRegra.valueOf(motivo),
                    Jdbc.instante(rs, "criada_em"), Jdbc.instante(rs, "atualizada_em"), rs.getLong("versao"),
                    TipoOrdem.valueOf(rs.getString("tipo_ordem")), subst == null ? null : UUID.fromString(subst));
        };

        @Override public void inserir(Ordem o) {
            int n = j.atualizar(SQL_INSERIR_ORDEM, o.id(), o.investidorId(), o.ticker(), o.lado(), o.tipo(), o.quantidade(), o.precoLimite(),
                    o.chaveIdempotencia(), o.impressaoDigital(), o.status(), o.quantidadeExecutada(), o.valorExecutado(),
                    o.motivoRejeicao(), o.substituiOrdemId(), o.criadaEm(), o.atualizadaEm());
            if (n == 0) throw new ChaveDuplicadaException("chave de idempotência");
            o.versaoPersistida(1);
        }
        @Override public void atualizar(Ordem o) {
            exigirUma(j.atualizar(SQL_ATUALIZAR_ORDEM, o.status(), o.quantidadeExecutada(), o.valorExecutado(), o.motivoRejeicao(),
                    o.atualizadaEm(), o.id(), o.versao()), "ordem");
            o.versaoPersistida(o.versao() + 1);
        }
        @Override public Optional<Ordem> buscarPorId(UUID id) { return j.um(SQL_ORDEM_POR_ID, MAP, id); }
        @Override public Optional<Ordem> buscarPorChave(UUID inv, String chave) { return j.um(SQL_ORDEM_POR_CHAVE, MAP, inv, chave); }
        @Override public List<Ordem> listarPorInvestidor(UUID inv) { return j.consultar(SQL_ORDENS_DO_INVESTIDOR, MAP, inv); }
        @Override public List<Ordem> listarPorStatus(StatusOrdem s, int limite) { return j.consultar(SQL_ORDENS_POR_STATUS, MAP, s, limite); }
        @Override public Dinheiro valorNegociadoNoPeriodo(UUID inv, Instant ini, Instant fim) {
            return j.um(SQL_VALOR_NEGOCIADO, rs -> Jdbc.dinheiro(rs, "total"), inv, ini, fim).orElse(Dinheiro.ZERO);
        }
    }

    // ------------------------------------------------------------------------------------------ execuções
    static final class Execucoes implements ExecucaoRepositorio {
        static final String SQL_INSERIR_EXECUCAO = """
                INSERT INTO execucoes (id_execucao, ordem_id, quantidade, preco, executada_em) VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (id_execucao) DO NOTHING""";
        static final String SQL_EXECUCOES_DA_ORDEM = "SELECT * FROM execucoes WHERE ordem_id = ? ORDER BY executada_em, id_execucao";

        private final Jdbc j;
        Execucoes(Jdbc j) { this.j = j; }
        @Override public boolean registrarSeNova(Execucao e) {
            return j.atualizar(SQL_INSERIR_EXECUCAO, e.idExecucao(), e.ordemId(), e.quantidade(), e.preco(), e.executadaEm()) == 1;
        }
        @Override public List<Execucao> listarPorOrdem(UUID id) {
            return j.consultar(SQL_EXECUCOES_DA_ORDEM, rs -> new Execucao(rs.getString("id_execucao"), Jdbc.uuid(rs, "ordem_id"),
                    rs.getInt("quantidade"), Jdbc.dinheiro(rs, "preco"), Jdbc.instante(rs, "executada_em")), id);
        }
    }

    // ------------------------------------------------------------------------------------------ limites
    static final class Limites implements LimiteRiscoRepositorio {
        static final String SQL_INSERIR_LIMITE = """
                INSERT INTO limites_risco (investidor_id, valor_maximo_por_ordem, limite_diario, valor_confirmacao, versao)
                VALUES (?, ?, ?, ?, 1)""";
        static final String SQL_ATUALIZAR_LIMITE = """
                UPDATE limites_risco SET valor_maximo_por_ordem = ?, limite_diario = ?, valor_confirmacao = ?, versao = versao + 1
                WHERE investidor_id = ? AND versao = ?""";
        static final String SQL_LIMITE = "SELECT * FROM limites_risco WHERE investidor_id = ?";

        private final Jdbc j;
        Limites(Jdbc j) { this.j = j; }
        @Override public Optional<LimiteRisco> buscar(UUID i) {
            return j.um(SQL_LIMITE, rs -> new LimiteRisco(Jdbc.uuid(rs, "investidor_id"), Jdbc.dinheiro(rs, "valor_maximo_por_ordem"),
                    Jdbc.dinheiro(rs, "limite_diario"), Jdbc.dinheiro(rs, "valor_confirmacao"), rs.getLong("versao")), i);
        }
        @Override public void salvar(LimiteRisco l) {
            if (l.versao() == 0) {
                j.atualizar(SQL_INSERIR_LIMITE, l.investidorId(), l.valorMaximoPorOrdem(), l.limiteDiario(), l.valorConfirmacao());
                l.versaoPersistida(1);
            } else {
                exigirUma(j.atualizar(SQL_ATUALIZAR_LIMITE, l.valorMaximoPorOrdem(), l.limiteDiario(), l.valorConfirmacao(),
                        l.investidorId(), l.versao()), "limite");
                l.versaoPersistida(l.versao() + 1);
            }
        }
    }

    // ------------------------------------------------------------------------------------------ auditoria (append-only)
    static final class Auditoria implements AuditoriaRepositorio {
        static final String SQL_ULTIMO_EVENTO = "SELECT * FROM auditoria ORDER BY seq DESC LIMIT 1";
        static final String SQL_ANEXAR_EVENTO = """
                INSERT INTO auditoria (seq, instante, tipo, usuario_id, entidade, entidade_id, detalhes, hash_anterior, hash)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (seq) DO NOTHING""";
        static final String SQL_LISTAR_EVENTOS = "SELECT * FROM auditoria WHERE seq > ? ORDER BY seq LIMIT ?";

        private final Jdbc j;
        Auditoria(Jdbc j) { this.j = j; }
        private static final Mapeador<EventoAuditoria> MAP = rs -> {
            String u = rs.getString("usuario_id");
            return new EventoAuditoria(rs.getLong("seq"), Jdbc.instante(rs, "instante"), rs.getString("tipo"), u == null ? null : UUID.fromString(u),
                    rs.getString("entidade"), rs.getString("entidade_id"), rs.getString("detalhes"),
                    rs.getString("hash_anterior").trim(), rs.getString("hash").trim());
        };
        @Override public void bloquearAppend() {
            // Lock transacional global: evita que duas transações calculem a mesma próxima sequência.
            j.consultar("SELECT pg_advisory_xact_lock(7263145101::bigint)", rs -> 1);
        }
        @Override public Optional<EventoAuditoria> ultimo() { return j.um(SQL_ULTIMO_EVENTO, MAP); }
        @Override public void anexar(EventoAuditoria e) {
            if (j.atualizar(SQL_ANEXAR_EVENTO, e.seq(), e.instante(), e.tipo(), e.usuarioId(), e.entidade(), e.entidadeId(),
                    e.detalhes(), e.hashAnterior(), e.hash()) == 0) throw new ConcorrenciaException("sequência de auditoria já utilizada");
        }
        @Override public List<EventoAuditoria> listar(long aposSeq, int limite) { return j.consultar(SQL_LISTAR_EVENTOS, MAP, aposSeq, limite); }
    }

    // ------------------------------------------------------------------------------------------ notificações
    static final class Notificacoes implements NotificacaoRepositorio {
        static final String SQL_INSERIR_NOTIFICACAO = """
                INSERT INTO notificacoes (id, investidor_id, tipo, mensagem, criada_em, enviada_em) VALUES (?, ?, ?, ?, ?, ?)""";
        static final String SQL_MARCAR_ENVIADA = "UPDATE notificacoes SET enviada_em = ? WHERE id = ?";
        static final String SQL_NOTIFICACOES_DO_INVESTIDOR = "SELECT * FROM notificacoes WHERE investidor_id = ? ORDER BY criada_em DESC LIMIT ?";
        static final String SQL_NOTIFICACOES_PENDENTES = "SELECT * FROM notificacoes WHERE enviada_em IS NULL ORDER BY criada_em LIMIT ?";

        private final Jdbc j;
        Notificacoes(Jdbc j) { this.j = j; }
        private static final Mapeador<Notificacao> MAP = rs -> new Notificacao(Jdbc.uuid(rs, "id"), Jdbc.uuid(rs, "investidor_id"),
                TipoNotificacao.valueOf(rs.getString("tipo")), rs.getString("mensagem"), Jdbc.instante(rs, "criada_em"), Jdbc.instante(rs, "enviada_em"));
        @Override public void inserir(Notificacao n) { j.atualizar(SQL_INSERIR_NOTIFICACAO, n.id(), n.investidorId(), n.tipo(), n.mensagem(), n.criadaEm(), n.enviadaEm()); }
        @Override public void atualizar(Notificacao n) { j.atualizar(SQL_MARCAR_ENVIADA, n.enviadaEm(), n.id()); }
        @Override public List<Notificacao> listarPorInvestidor(UUID i, int limite) { return j.consultar(SQL_NOTIFICACOES_DO_INVESTIDOR, MAP, i, limite); }
        @Override public List<Notificacao> pendentesDeEnvio(int limite) { return j.consultar(SQL_NOTIFICACOES_PENDENTES, MAP, limite); }
    }

    // ------------------------------------------------------------------------------------------ parâmetros
    static final class Parametros implements ParametroRepositorio {
        static final String SQL_PARAMETRO = "SELECT valor FROM parametros WHERE chave = ?";
        static final String SQL_SALVAR_PARAMETRO = """
                INSERT INTO parametros (chave, valor) VALUES (?, ?)
                ON CONFLICT (chave) DO UPDATE SET valor = EXCLUDED.valor""";
        static final String SQL_PARAMETROS = "SELECT chave, valor FROM parametros ORDER BY chave";

        private final Jdbc j;
        Parametros(Jdbc j) { this.j = j; }
        @Override public Optional<String> buscar(String c) { return j.um(SQL_PARAMETRO, rs -> rs.getString("valor"), c); }
        @Override public void salvar(String c, String v) { j.atualizar(SQL_SALVAR_PARAMETRO, c, v); }
        @Override public Map<String, String> listar() {
            Map<String, String> m = new LinkedHashMap<>();
            j.consultar(SQL_PARAMETROS, rs -> m.put(rs.getString("chave"), rs.getString("valor")));
            return m;
        }
    }

    // ------------------------------------------------------------------------------------------ fila (outbox)
    static final class Fila implements FilaOrdens {
        static final String SQL_PUBLICAR_MENSAGEM = """
                INSERT INTO fila_ordens (id, ordem_id, tentativas, proxima_tentativa, status) VALUES (?, ?, ?, ?, 'PENDENTE')""";
        static final String SQL_MENSAGENS_DISPONIVEIS = """
                SELECT id, ordem_id, tentativas, proxima_tentativa FROM fila_ordens
                WHERE status = 'PENDENTE' AND proxima_tentativa <= ? ORDER BY proxima_tentativa LIMIT ?""";
        static final String SQL_CONCLUIR_MENSAGEM = "UPDATE fila_ordens SET status = 'CONCLUIDA' WHERE id = ?";
        static final String SQL_REAGENDAR_MENSAGEM = "UPDATE fila_ordens SET tentativas = ?, proxima_tentativa = ? WHERE id = ?";

        private final Jdbc j;
        Fila(Jdbc j) { this.j = j; }
        @Override public void publicar(MensagemFila m) { j.atualizar(SQL_PUBLICAR_MENSAGEM, m.id(), m.ordemId(), m.tentativas(), m.proximaTentativa()); }
        @Override public List<MensagemFila> disponiveis(Instant agora, int limite) {
            return j.consultar(SQL_MENSAGENS_DISPONIVEIS, rs -> new MensagemFila(Jdbc.uuid(rs, "id"), Jdbc.uuid(rs, "ordem_id"),
                    rs.getInt("tentativas"), Jdbc.instante(rs, "proxima_tentativa")), agora, limite);
        }
        @Override public void concluir(UUID id) { j.atualizar(SQL_CONCLUIR_MENSAGEM, id); }
        @Override public void reagendar(UUID id, int tentativas, Instant proxima) { j.atualizar(SQL_REAGENDAR_MENSAGEM, tentativas, proxima, id); }
    }
}
