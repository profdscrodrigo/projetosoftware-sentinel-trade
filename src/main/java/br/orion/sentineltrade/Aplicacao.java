package br.orion.sentineltrade;

import br.orion.sentineltrade.aplicacao.porta.*;
import br.orion.sentineltrade.aplicacao.porta.Repositorios.*;
import br.orion.sentineltrade.aplicacao.servico.*;
import br.orion.sentineltrade.infraestrutura.integracao.*;
import br.orion.sentineltrade.infraestrutura.jdbc.RepositoriosJdbc;
import br.orion.sentineltrade.infraestrutura.jdbc.TransacaoJdbc;
import br.orion.sentineltrade.infraestrutura.memoria.BancoEmMemoria;
import br.orion.sentineltrade.infraestrutura.seguranca.CifradorSegredo;
import br.orion.sentineltrade.infraestrutura.seguranca.Pbkdf2Hasher;
import br.orion.sentineltrade.infraestrutura.seguranca.TotpMfa;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Composition root: o ÚNICO lugar que conhece as implementações concretas e as liga às portas.
 * O restante do sistema depende apenas de interfaces.
 */
public final class Aplicacao {
    public final Clock relogio;
    public final Persistencia persistencia;
    public final ParametrosSistema parametros;
    public final ServicoMfa mfa;
    public final AuditoriaService auditoria;
    public final AutenticacaoService auth;
    public final NotificacaoService notificacoes;
    public final CotacaoService cotacoes;
    public final CarteiraService carteira;
    public final OrdemService ordens;
    public final ProcessadorOrdens processador;
    public final AdministracaoService admin;
    public final RiscoService risco;
    public final CorretoraGateway corretora;
    public final EnviadorNotificacaoConsole enviador;
    /** Presentes apenas quando as integrações são as simuladas. */
    public final CotacoesSimuladas cotacoesSimuladas;
    public final CorretoraSimulada corretoraSimulada;
    public final CorretoraResiliente corretoraResiliente;
    private ScheduledExecutorService worker;

    public Aplicacao(Clock relogio, Persistencia p, HasherSenha hasher, Config cfg, java.io.PrintStream saidaNotificacoes) {
        this.relogio = relogio;
        this.persistencia = p;
        ZoneId zona = ZoneId.of(cfg.zona());
        this.mfa = new TotpMfa(cifradorMfa(cfg));
        this.cotacoesSimuladas = new CotacoesSimuladas(relogio);
        this.corretoraSimulada = new CorretoraSimulada(relogio, cotacoesSimuladas);
        this.corretoraResiliente = new CorretoraResiliente(corretoraSimulada, relogio, Duration.ofMillis(cfg.corretoraTimeoutMs()),
                cfg.corretoraFalhasParaAbrir(), Duration.ofSeconds(cfg.corretoraTempoAbertoSeg()));
        this.corretora = corretoraResiliente;
        this.enviador = new EnviadorNotificacaoConsole(saidaNotificacoes);

        LimitadorDeTaxa limitador = new LimitadorDeTaxa(relogio);
        this.parametros = new ParametrosSistema(p.parametros());
        this.auditoria = new AuditoriaService(p.auditoria(), relogio);
        this.auth = new AutenticacaoService(p.usuarios(), p.sessoes(), hasher, mfa, auditoria, p.transacao(), limitador, relogio);
        this.notificacoes = new NotificacaoService(p.notificacoes(), enviador, auth, relogio);
        this.cotacoes = new CotacaoService(cotacoesSimuladas, parametros, p.ativos(), auth, relogio);
        CalendarioMercado calendario = new CalendarioMercado(parametros, zona);
        ValidadorOrdem validador = new ValidadorOrdem(p.ativos(), calendario, cotacoes, parametros);
        this.carteira = new CarteiraService(auth, p.contas(), p.posicoes(), cotacoes);
        this.processador = new ProcessadorOrdens(p.fila(), p.ordens(), p.contas(), p.posicoes(), p.execucoes(), corretora,
                auditoria, notificacoes, p.transacao(), relogio);
        this.ordens = new OrdemService(auth, p.transacao(), p.ordens(), p.contas(), p.posicoes(), p.limites(), p.execucoes(),
                p.fila(), validador, auditoria, notificacoes, corretora, processador, limitador, parametros, relogio, zona);
        this.admin = new AdministracaoService(auth, p.transacao(), p.usuarios(), p.contas(), p.limites(), p.ativos(), parametros,
                auditoria, hasher, mfa, relogio);
        this.risco = new RiscoService(auth, p.transacao(), p.limites(), p.usuarios(), p.ordens(), auditoria);
    }

    /**
     * Chave de cifra do segredo MFA. Com PostgreSQL ela é OBRIGATÓRIA (os segredos persistem e precisam ser decifráveis
     * depois); em memória, sem chave configurada, usa-se uma chave descartável gerada na partida.
     */
    private static CifradorSegredo cifradorMfa(Config cfg) {
        if (cfg.mfaChave() != null && !cfg.mfaChave().isBlank()) return CifradorSegredo.deBase64(cfg.mfaChave());
        if (cfg.jdbc()) throw new IllegalStateException("Defina MFA_ENCRYPTION_KEY (32 bytes em Base64; gere com: openssl rand -base64 32)");
        return CifradorSegredo.efemero();
    }

    public static Persistencia persistenciaEmMemoria() {
        BancoEmMemoria b = new BancoEmMemoria();
        return new Persistencia(b.usuarios(), b.sessoes(), b.contas(), b.posicoes(), b.ativos(), b.ordens(), b.execucoes(),
                b.limites(), b.auditoria(), b.notificacoes(), b.parametros(), b.fila(), b.transacao());
    }

    public static Aplicacao emMemoria(Clock relogio, Config cfg, java.io.PrintStream saidaNotificacoes) {
        return new Aplicacao(relogio, persistenciaEmMemoria(), new Pbkdf2Hasher(cfg.pbkdf2Iteracoes()), cfg, saidaNotificacoes);
    }

    /** PostgreSQL via JDBC. Requer o driver no classpath (dependência runtime no pom.xml) e as variáveis DB_*. */
    public static Aplicacao comJdbc(Clock relogio, Config cfg, java.io.PrintStream saidaNotificacoes) {
        Persistencia p = RepositoriosJdbc.persistencia(new TransacaoJdbc(cfg.dbUrl(), cfg.dbUsuario(), cfg.dbSenha()));
        return new Aplicacao(relogio, p, new Pbkdf2Hasher(cfg.pbkdf2Iteracoes()), cfg, saidaNotificacoes);
    }

    /** Worker em segundo plano: transmite a fila, coleta execuções e despacha notificações. */
    public synchronized void iniciarWorker(long periodoMs) {
        if (worker != null) return;
        worker = Executors.newSingleThreadScheduledExecutor(r -> { Thread t = new Thread(r, "worker-ordens"); t.setDaemon(true); return t; });
        worker.scheduleWithFixedDelay(() -> {
            try { processador.ciclo(); } catch (RuntimeException e) { Log.erro("Falha no ciclo do worker", e); }
        }, periodoMs, periodoMs, TimeUnit.MILLISECONDS);
    }

    public synchronized void encerrar() { if (worker != null) worker.shutdownNow(); }
}
