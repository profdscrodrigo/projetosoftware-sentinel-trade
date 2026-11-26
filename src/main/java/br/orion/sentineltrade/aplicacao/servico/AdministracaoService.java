package br.orion.sentineltrade.aplicacao.servico;

import br.orion.sentineltrade.aplicacao.porta.GerenciadorTransacao;
import br.orion.sentineltrade.aplicacao.porta.HasherSenha;
import br.orion.sentineltrade.aplicacao.porta.Repositorios.*;
import br.orion.sentineltrade.aplicacao.porta.ServicoMfa;
import br.orion.sentineltrade.dominio.*;
import br.orion.sentineltrade.dominio.Excecoes.*;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Funções administrativas (RF12, RF13, RF20): investidores, depósito simulado, ativos, parâmetros e auditoria.
 * Todas exigem o perfil ADMINISTRADOR, validado no servidor, e geram auditoria na mesma transação.
 */
public final class AdministracaoService {
    private final AutenticacaoService auth;
    private final GerenciadorTransacao tx;
    private final UsuarioRepositorio usuarios;
    private final ContaRepositorio contas;
    private final LimiteRiscoRepositorio limites;
    private final AtivoRepositorio ativos;
    private final ParametrosSistema params;
    private final AuditoriaService auditoria;
    private final HasherSenha hasher;
    private final ServicoMfa mfa;
    private final Clock clock;

    public AdministracaoService(AutenticacaoService auth, GerenciadorTransacao tx, UsuarioRepositorio usuarios, ContaRepositorio contas,
                                LimiteRiscoRepositorio limites, AtivoRepositorio ativos, ParametrosSistema params,
                                AuditoriaService auditoria, HasherSenha hasher, ServicoMfa mfa, Clock clock) {
        this.auth = auth; this.tx = tx; this.usuarios = usuarios; this.contas = contas; this.limites = limites;
        this.ativos = ativos; this.params = params; this.auditoria = auditoria; this.hasher = hasher; this.mfa = mfa; this.clock = clock;
    }

    public UUID cadastrarInvestidor(String token, String nome, String email, String senha) {
        Sessao s = auth.exigir(token, Papel.ADMINISTRADOR);
        return provisionar(s.usuarioId(), nome, email, senha, Papel.INVESTIDOR).id();
    }

    /**
     * Cria usuário (senha com hash PBKDF2, segredo MFA novo). Para INVESTIDOR cria também conta e limites padrão.
     * Público apenas para o provisionamento inicial (DadosDemonstracao); o acesso externo passa por {@link #cadastrarInvestidor}.
     */
    public Usuario provisionar(UUID autor, String nome, String email, String senha, Papel papel) {
        String n = Validacoes.nome(nome);
        String e = Validacoes.email(email);
        Validacoes.senha(senha, e);
        String hash = hasher.hash(senha.toCharArray());
        return tx.executar(() -> {
            if (usuarios.buscarPorEmail(e).isPresent()) throw new ConflitoException("E-mail já cadastrado");
            Usuario u = Usuario.novo(n, e, papel, hash, mfa.gerarSegredo(), clock.instant());
            try { usuarios.inserir(u); } catch (ChaveDuplicadaException ex) { throw new ConflitoException("E-mail já cadastrado"); }
            if (papel == Papel.INVESTIDOR) {
                contas.inserir(Conta.nova(u.id()));
                limites.salvar(new LimiteRisco(u.id(), Dinheiro.de("50000"), Dinheiro.de("200000"), Dinheiro.de("20000"), 0));
            }
            auditoria.registrar(TipoEvento.USUARIO_CADASTRADO, autor, "USUARIO", u.id().toString(), "perfil " + papel);
            return u;
        });
    }

    public void depositarSimulado(String token, String idInvestidor, String valor) {
        Sessao s = auth.exigir(token, Papel.ADMINISTRADOR);
        UUID inv = Validacoes.uuid(idInvestidor);
        Dinheiro v = Validacoes.preco(valor);
        Retentativa.comConflito(3, () -> tx.executar(() -> {
            Conta c = contas.buscarPorInvestidor(inv).orElseThrow(() -> new NaoEncontradoException("Investidor não encontrado"));
            c.depositar(v);
            contas.atualizar(c);
            auditoria.registrar(TipoEvento.DEPOSITO_SIMULADO, s.usuarioId(), "CONTA", c.id().toString(), "crédito simulado " + v);
            return null;
        }));
    }

    public void cadastrarAtivo(String token, String ticker, String nome, String tipo) {
        Sessao s = auth.exigir(token, Papel.ADMINISTRADOR);
        Ticker t = Ticker.de(ticker);
        String n = Validacoes.nome(nome);
        TipoAtivo ta;
        try { ta = TipoAtivo.valueOf(tipo == null ? "" : tipo.trim().toUpperCase()); }
        catch (IllegalArgumentException ex) { throw new ValidacaoException("Tipo de ativo inválido (ACAO, ETF ou FII)"); }
        tx.executarSemRetorno(() -> {
            if (ativos.buscar(t).isPresent()) throw new ConflitoException("Ativo já cadastrado");
            ativos.salvar(new Ativo(t, n, ta, StatusAtivo.ATIVO, 0));
            auditoria.registrar(TipoEvento.ATIVO_CADASTRADO, s.usuarioId(), "ATIVO", t.codigo(), ta + " " + n);
        });
    }

    public void alterarStatusAtivo(String token, String ticker, String status) {
        Sessao s = auth.exigir(token, Papel.ADMINISTRADOR);
        Ticker t = Ticker.de(ticker);
        StatusAtivo novo;
        try { novo = StatusAtivo.valueOf(status == null ? "" : status.trim().toUpperCase()); }
        catch (IllegalArgumentException ex) { throw new ValidacaoException("Status inválido (ATIVO ou SUSPENSO)"); }
        tx.executarSemRetorno(() -> {
            Ativo a = ativos.buscar(t).orElseThrow(() -> new NaoEncontradoException("Ativo não encontrado"));
            a.alterarStatus(novo);
            ativos.salvar(a);
            auditoria.registrar(TipoEvento.ATIVO_ALTERADO, s.usuarioId(), "ATIVO", t.codigo(), "status " + novo);
        });
    }

    public List<Ativo> listarAtivos(String token) { auth.exigir(token); return ativos.listar(); }

    public void definirParametro(String token, String chave, String valor) {
        Sessao s = auth.exigir(token, Papel.ADMINISTRADOR);
        tx.executarSemRetorno(() -> {
            params.definir(chave, valor);
            auditoria.registrar(TipoEvento.PARAMETRO_ALTERADO, s.usuarioId(), "PARAMETRO", Sanitizador.paraLog(chave, 60), "novo valor " + Sanitizador.paraLog(valor, 40));
        });
    }

    public Map<String, String> listarParametros(String token) { auth.exigir(token, Papel.ADMINISTRADOR); return params.todos(); }

    public List<EventoAuditoria> consultarAuditoria(String token, long aposSeq, int limite) {
        auth.exigir(token, Papel.ADMINISTRADOR);
        return auditoria.listar(aposSeq, limite);
    }

    public AuditoriaService.Verificacao verificarIntegridadeAuditoria(String token) {
        auth.exigir(token, Papel.ADMINISTRADOR);
        return auditoria.verificarIntegridade();
    }
}
