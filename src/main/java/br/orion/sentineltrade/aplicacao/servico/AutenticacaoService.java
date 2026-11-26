package br.orion.sentineltrade.aplicacao.servico;

import br.orion.sentineltrade.aplicacao.porta.GerenciadorTransacao;
import br.orion.sentineltrade.aplicacao.porta.HasherSenha;
import br.orion.sentineltrade.aplicacao.porta.Repositorios.SessaoRepositorio;
import br.orion.sentineltrade.aplicacao.porta.Repositorios.UsuarioRepositorio;
import br.orion.sentineltrade.aplicacao.porta.ServicoMfa;
import br.orion.sentineltrade.dominio.Excecoes.AcessoNegadoException;
import br.orion.sentineltrade.dominio.Excecoes.AutenticacaoException;
import br.orion.sentineltrade.dominio.Excecoes.SentinelException;
import br.orion.sentineltrade.dominio.Papel;
import br.orion.sentineltrade.dominio.Sessao;
import br.orion.sentineltrade.dominio.Sessao.Estado;
import br.orion.sentineltrade.dominio.TipoEvento;
import br.orion.sentineltrade.dominio.Usuario;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;

/**
 * Autenticação em dois passos (senha + TOTP) e autorização por perfil, sempre validadas no servidor.
 *
 * Decisões de segurança: mensagem única para qualquer falha de credencial (sem enumeração de usuários);
 * verificação de hash mesmo para usuário inexistente (tempo constante aproximado); bloqueio temporário
 * após falhas; token de sessão aleatório de 256 bits, armazenado apenas como hash; rotação de token após o MFA.
 */
public final class AutenticacaoService {
    public static final int MAX_FALHAS_LOGIN = 5;
    public static final Duration BLOQUEIO = Duration.ofMinutes(15);
    public static final Duration TTL_PENDENTE = Duration.ofMinutes(5);
    public static final Duration TTL_SESSAO = Duration.ofMinutes(15);
    public static final int MAX_TENTATIVAS_MFA = 3;
    private static final String MSG_CREDENCIAIS = "Credenciais inválidas ou conta temporariamente bloqueada";

    private record Resultado(String valor, SentinelException falha) {
        static Resultado ok(String v) { return new Resultado(v, null); }
        static Resultado falha(SentinelException e) { return new Resultado(null, e); }
    }

    private final UsuarioRepositorio usuarios;
    private final SessaoRepositorio sessoes;
    private final HasherSenha hasher;
    private final ServicoMfa mfa;
    private final AuditoriaService auditoria;
    private final GerenciadorTransacao tx;
    private final LimitadorDeTaxa limitador;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private final String hashFalso;

    public AutenticacaoService(UsuarioRepositorio usuarios, SessaoRepositorio sessoes, HasherSenha hasher, ServicoMfa mfa,
                               AuditoriaService auditoria, GerenciadorTransacao tx, LimitadorDeTaxa limitador, Clock clock) {
        this.usuarios = usuarios; this.sessoes = sessoes; this.hasher = hasher; this.mfa = mfa;
        this.auditoria = auditoria; this.tx = tx; this.limitador = limitador; this.clock = clock;
        this.hashFalso = hasher.hash(novoToken().toCharArray());
    }

    /** Passo 1: valida e-mail e senha. Devolve um token PENDENTE (válido só para o passo 2). */
    public String iniciarLogin(String email, String senha) {
        String emailNorm = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        limitador.registrar("login:" + emailNorm, 10, Duration.ofMinutes(1));
        char[] pwd = senha == null ? new char[0] : senha.toCharArray();
        try {
            return desembrulhar(Retentativa.comConflito(3, () -> tx.executar(() -> {
                Instant agora = clock.instant();
                Optional<Usuario> opt = Validacoes.emailValido(emailNorm) ? usuarios.buscarPorEmail(emailNorm) : Optional.empty();
                if (opt.isEmpty()) {
                    hasher.verifica(pwd, hashFalso);
                    auditoria.registrar(TipoEvento.LOGIN_FALHA, null, "USUARIO", "-",
                            "identificador desconhecido: " + Sanitizador.paraLog(emailNorm, 80));
                    return Resultado.falha(new AutenticacaoException(MSG_CREDENCIAIS));
                }
                Usuario u = opt.get();
                boolean senhaOk = hasher.verifica(pwd, u.senhaHash());
                if (u.bloqueado(agora) || !u.ativo()) {
                    auditoria.registrar(TipoEvento.LOGIN_BLOQUEADO, u.id(), "USUARIO", u.id().toString(), "tentativa durante bloqueio");
                    return Resultado.falha(new AutenticacaoException(MSG_CREDENCIAIS));
                }
                if (!senhaOk) {
                    u.registrarFalhaLogin(agora, MAX_FALHAS_LOGIN, BLOQUEIO);
                    usuarios.atualizar(u);
                    auditoria.registrar(TipoEvento.LOGIN_FALHA, u.id(), "USUARIO", u.id().toString(), "senha incorreta");
                    return Resultado.falha(new AutenticacaoException(MSG_CREDENCIAIS));
                }
                u.registrarSucessoLogin();
                usuarios.atualizar(u);
                String token = novoToken();
                sessoes.salvar(Sessao.nova(u.id(), u.papel(), Estado.PENDENTE_MFA, hashToken(token), agora, TTL_PENDENTE));
                return Resultado.ok(token);
            })));
        } finally {
            Arrays.fill(pwd, '\0');
        }
    }

    /** Passo 2: valida o código TOTP. Devolve o token da sessão ATIVA (o token pendente é consumido). */
    public String confirmarMfa(String tokenPendente, String codigo) {
        String th = hashToken(tokenPendente);
        limitador.registrar("mfa:" + th, 10, Duration.ofMinutes(1));
        return desembrulhar(Retentativa.comConflito(3, () -> tx.executar(() -> {
            Instant agora = clock.instant();
            Sessao pendente = sessoes.buscarPorTokenHash(th).filter(s -> s.valida(agora, Estado.PENDENTE_MFA)).orElse(null);
            if (pendente == null) return Resultado.falha(new AutenticacaoException("Sessão inválida ou expirada"));
            Usuario u = usuarios.buscarPorId(pendente.usuarioId()).orElse(null);
            if (u == null) return Resultado.falha(new AutenticacaoException("Sessão inválida ou expirada"));
            boolean ok = Validacoes.codigoMfaValido(codigo) && mfa.verificar(u.segredoMfa(), codigo, agora);
            if (!ok) {
                if (pendente.registrarFalhaMfa() >= MAX_TENTATIVAS_MFA) pendente.revogar();
                sessoes.salvar(pendente);
                auditoria.registrar(TipoEvento.MFA_FALHA, u.id(), "USUARIO", u.id().toString(), "código MFA inválido");
                return Resultado.falha(new AutenticacaoException("Código de verificação inválido"));
            }
            pendente.revogar();
            sessoes.salvar(pendente);
            String token = novoToken();
            sessoes.salvar(Sessao.nova(u.id(), u.papel(), Estado.ATIVA, hashToken(token), agora, TTL_SESSAO));
            auditoria.registrar(TipoEvento.LOGIN_SUCESSO, u.id(), "USUARIO", u.id().toString(), "login com MFA");
            return Resultado.ok(token);
        })));
    }

    /** Valida o token e devolve a sessão ATIVA. */
    public Sessao validarToken(String token) {
        Instant agora = clock.instant();
        return sessoes.buscarPorTokenHash(hashToken(token))
                .filter(s -> s.valida(agora, Estado.ATIVA))
                .orElseThrow(() -> new AutenticacaoException("Sessão inválida ou expirada"));
    }

    /** Controle de acesso por perfil (RBAC), no servidor. Sem papéis informados, basta estar autenticado. */
    public Sessao exigir(String token, Papel... permitidos) {
        Sessao s = validarToken(token);
        if (permitidos.length == 0) return s;
        for (Papel p : permitidos) if (p == s.papel()) return s;
        tx.executarSemRetorno(() -> auditoria.registrar(TipoEvento.ACESSO_NEGADO, s.usuarioId(), "SESSAO", s.id().toString(),
                "perfil " + s.papel() + " sem permissão"));
        throw new AcessoNegadoException("Acesso negado ao recurso solicitado");
    }

    public void encerrar(String token) {
        tx.executarSemRetorno(() -> sessoes.buscarPorTokenHash(hashToken(token)).ifPresent(s -> {
            s.revogar();
            sessoes.salvar(s);
            auditoria.registrar(TipoEvento.SESSAO_ENCERRADA, s.usuarioId(), "SESSAO", s.id().toString(), "logout");
        }));
    }

    private String desembrulhar(Resultado r) {
        if (r.falha() != null) throw r.falha();   // lançado APÓS o commit: a auditoria da falha é preservada
        return r.valor();
    }

    private String novoToken() {
        byte[] b = new byte[32];
        random.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    private static String hashToken(String token) {
        if (token == null || token.length() > 128) return Hashes.sha256Hex("token-invalido");
        return Hashes.sha256Hex(token);
    }
}
