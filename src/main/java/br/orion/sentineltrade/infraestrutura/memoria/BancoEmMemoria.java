package br.orion.sentineltrade.infraestrutura.memoria;

import br.orion.sentineltrade.aplicacao.porta.*;
import br.orion.sentineltrade.aplicacao.porta.Repositorios.*;
import br.orion.sentineltrade.dominio.*;
import br.orion.sentineltrade.dominio.Excecoes.ChaveDuplicadaException;
import br.orion.sentineltrade.dominio.Excecoes.ConcorrenciaException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Adaptadores de persistência EM MEMÓRIA (testes e demonstração). Implementam as mesmas portas do JDBC.
 * Limitação assumida: a "transação" serializa as operações com um lock, mas NÃO desfaz alterações em caso de
 * erro — a atomicidade real é fornecida por TransacaoJdbc (PostgreSQL). As regras de negócio são ordenadas
 * de modo que validações falhem antes de qualquer mutação.
 */
public final class BancoEmMemoria {
    private final ReentrantLock trava = new ReentrantLock();
    private final UsuarioRepositorio usuarios = new Usuarios();
    private final SessaoRepositorio sessoes = new Sessoes();
    private final ContaRepositorio contas = new Contas();
    private final PosicaoRepositorio posicoes = new Posicoes();
    private final AtivoRepositorio ativos = new Ativos();
    private final OrdemRepositorio ordens = new Ordens();
    private final ExecucaoRepositorio execucoes = new Execucoes();
    private final LimiteRiscoRepositorio limites = new Limites();
    private final AuditoriaRepositorio auditoria = new Auditoria();
    private final NotificacaoRepositorio notificacoes = new Notificacoes();
    private final ParametroRepositorio parametros = new Parametros();
    private final FilaOrdens fila = new Fila();
    private final GerenciadorTransacao transacao = new GerenciadorTransacao() {
        @Override public <T> T executar(Supplier<T> acao) {
            trava.lock();
            try { return acao.get(); } finally { trava.unlock(); }
        }
    };

    public UsuarioRepositorio usuarios() { return usuarios; }
    public SessaoRepositorio sessoes() { return sessoes; }
    public ContaRepositorio contas() { return contas; }
    public PosicaoRepositorio posicoes() { return posicoes; }
    public AtivoRepositorio ativos() { return ativos; }
    public OrdemRepositorio ordens() { return ordens; }
    public ExecucaoRepositorio execucoes() { return execucoes; }
    public LimiteRiscoRepositorio limites() { return limites; }
    public AuditoriaRepositorio auditoria() { return auditoria; }
    public NotificacaoRepositorio notificacoes() { return notificacoes; }
    public ParametroRepositorio parametros() { return parametros; }
    public FilaOrdens fila() { return fila; }
    public GerenciadorTransacao transacao() { return transacao; }

    // ------------------------------------------------------------------------------------------
    private static final class Usuarios implements UsuarioRepositorio {
        private final Map<UUID, Usuario> porId = new LinkedHashMap<>();
        @Override public synchronized void inserir(Usuario u) {
            if (porId.values().stream().anyMatch(x -> x.email().equals(u.email()))) throw new ChaveDuplicadaException("e-mail");
            porId.put(u.id(), u); u.versaoPersistida(1);
        }
        @Override public synchronized void atualizar(Usuario u) { porId.put(u.id(), u); u.versaoPersistida(u.versao() + 1); }
        @Override public synchronized Optional<Usuario> buscarPorId(UUID id) { return Optional.ofNullable(porId.get(id)); }
        @Override public synchronized Optional<Usuario> buscarPorEmail(String e) { return porId.values().stream().filter(u -> u.email().equals(e)).findFirst(); }
        @Override public synchronized List<Usuario> listarPorPapel(Papel p) { return porId.values().stream().filter(u -> u.papel() == p).collect(Collectors.toList()); }
        @Override public synchronized long contar() { return porId.size(); }
    }

    private static final class Sessoes implements SessaoRepositorio {
        private final Map<String, Sessao> porHash = new ConcurrentHashMap<>();
        @Override public void salvar(Sessao s) { porHash.put(s.tokenHash(), s); }
        @Override public Optional<Sessao> buscarPorTokenHash(String h) { return Optional.ofNullable(porHash.get(h)); }
    }

    private static final class Contas implements ContaRepositorio {
        private final Map<UUID, Conta> porInvestidor = new ConcurrentHashMap<>();
        @Override public void inserir(Conta c) { porInvestidor.put(c.investidorId(), c); c.versaoPersistida(1); }
        @Override public void atualizar(Conta c) { porInvestidor.put(c.investidorId(), c); c.versaoPersistida(c.versao() + 1); }
        @Override public Optional<Conta> buscarPorInvestidor(UUID id) { return Optional.ofNullable(porInvestidor.get(id)); }
    }

    private static final class Posicoes implements PosicaoRepositorio {
        private final Map<String, Posicao> mapa = new ConcurrentHashMap<>();
        private static String k(UUID i, Ticker t) { return i + "|" + t.codigo(); }
        @Override public Optional<Posicao> buscar(UUID i, Ticker t) { return Optional.ofNullable(mapa.get(k(i, t))); }
        @Override public List<Posicao> listarPorInvestidor(UUID i) {
            return mapa.values().stream().filter(p -> p.investidorId().equals(i))
                    .sorted(Comparator.comparing(p -> p.ticker().codigo())).collect(Collectors.toList());
        }
        @Override public void salvar(Posicao p) { mapa.put(k(p.investidorId(), p.ticker()), p); p.versaoPersistida(p.versao() + 1); }
    }

    private static final class Ativos implements AtivoRepositorio {
        private final Map<String, Ativo> mapa = new TreeMap<>();
        @Override public synchronized Optional<Ativo> buscar(Ticker t) { return Optional.ofNullable(mapa.get(t.codigo())); }
        @Override public synchronized List<Ativo> listar() { return new ArrayList<>(mapa.values()); }
        @Override public synchronized void salvar(Ativo a) { mapa.put(a.ticker().codigo(), a); a.versaoPersistida(a.versao() + 1); }
    }

    private static final class Ordens implements OrdemRepositorio {
        private final Map<UUID, Ordem> porId = new LinkedHashMap<>();
        private final Map<String, UUID> porChave = new HashMap<>();
        @Override public synchronized void inserir(Ordem o) {
            String k = o.investidorId() + "|" + o.chaveIdempotencia();
            if (porChave.containsKey(k)) throw new ChaveDuplicadaException("chave de idempotência");
            porId.put(o.id(), o); porChave.put(k, o.id()); o.versaoPersistida(1);
        }
        @Override public synchronized void atualizar(Ordem o) { porId.put(o.id(), o); o.versaoPersistida(o.versao() + 1); }
        @Override public synchronized Optional<Ordem> buscarPorId(UUID id) { return Optional.ofNullable(porId.get(id)); }
        @Override public synchronized Optional<Ordem> buscarPorChave(UUID inv, String chave) {
            return Optional.ofNullable(porChave.get(inv + "|" + chave)).map(porId::get);
        }
        @Override public synchronized List<Ordem> listarPorInvestidor(UUID inv) {
            List<Ordem> r = porId.values().stream().filter(o -> o.investidorId().equals(inv)).collect(Collectors.toList());
            Collections.reverse(r);                                           // mais recentes primeiro
            return r;
        }
        @Override public synchronized List<Ordem> listarPorStatus(StatusOrdem s, int limite) {
            List<Ordem> r = porId.values().stream().filter(o -> o.status() == s).collect(Collectors.toList());
            Collections.reverse(r);
            return r.size() > limite ? new ArrayList<>(r.subList(0, limite)) : r;
        }
        @Override public synchronized Dinheiro valorNegociadoNoPeriodo(UUID inv, Instant ini, Instant fim) {
            Set<StatusOrdem> conta = EnumSet.of(StatusOrdem.VALIDADA, StatusOrdem.ENVIADA, StatusOrdem.PARCIALMENTE_EXECUTADA, StatusOrdem.EXECUTADA);
            Dinheiro soma = Dinheiro.ZERO;
            for (Ordem o : porId.values())
                if (o.investidorId().equals(inv) && conta.contains(o.status()) && !o.criadaEm().isBefore(ini) && o.criadaEm().isBefore(fim))
                    soma = soma.mais(o.valorTotal());
            return soma;
        }
    }

    private static final class Execucoes implements ExecucaoRepositorio {
        private final Map<String, Execucao> mapa = new LinkedHashMap<>();
        @Override public synchronized boolean registrarSeNova(Execucao e) { return mapa.putIfAbsent(e.idExecucao(), e) == null; }
        @Override public synchronized List<Execucao> listarPorOrdem(UUID id) {
            return mapa.values().stream().filter(e -> e.ordemId().equals(id)).collect(Collectors.toList());
        }
    }

    private static final class Limites implements LimiteRiscoRepositorio {
        private final Map<UUID, LimiteRisco> mapa = new ConcurrentHashMap<>();
        @Override public Optional<LimiteRisco> buscar(UUID i) { return Optional.ofNullable(mapa.get(i)); }
        @Override public void salvar(LimiteRisco l) { mapa.put(l.investidorId(), l); l.versaoPersistida(l.versao() + 1); }
    }

    private static final class Auditoria implements AuditoriaRepositorio {
        private final List<EventoAuditoria> eventos = new ArrayList<>();
        @Override public synchronized Optional<EventoAuditoria> ultimo() { return eventos.isEmpty() ? Optional.empty() : Optional.of(eventos.get(eventos.size() - 1)); }
        @Override public synchronized void anexar(EventoAuditoria e) {
            if (e.seq() != eventos.size() + 1L) throw new ConcorrenciaException("sequência de auditoria já utilizada");
            eventos.add(e);                                                   // somente inserção: não há update/delete
        }
        @Override public synchronized List<EventoAuditoria> listar(long aposSeq, int limite) {
            return eventos.stream().filter(e -> e.seq() > aposSeq).limit(limite).collect(Collectors.toList());
        }
    }

    private static final class Notificacoes implements NotificacaoRepositorio {
        private final List<Notificacao> lista = new ArrayList<>();
        @Override public synchronized void inserir(Notificacao n) { lista.add(n); }
        @Override public synchronized void atualizar(Notificacao n) { /* mesma referência */ }
        @Override public synchronized List<Notificacao> listarPorInvestidor(UUID i, int limite) {
            List<Notificacao> r = lista.stream().filter(n -> n.investidorId().equals(i)).collect(Collectors.toList());
            Collections.reverse(r);
            return r.size() > limite ? new ArrayList<>(r.subList(0, limite)) : r;
        }
        @Override public synchronized List<Notificacao> pendentesDeEnvio(int limite) {
            return lista.stream().filter(n -> n.enviadaEm() == null).limit(limite).collect(Collectors.toList());
        }
    }

    private static final class Parametros implements ParametroRepositorio {
        private final Map<String, String> mapa = new ConcurrentHashMap<>();
        @Override public Optional<String> buscar(String c) { return Optional.ofNullable(mapa.get(c)); }
        @Override public void salvar(String c, String v) { mapa.put(c, v); }
        @Override public Map<String, String> listar() { return new HashMap<>(mapa); }
    }

    private static final class Fila implements FilaOrdens {
        private final Map<UUID, MensagemFila> mapa = new LinkedHashMap<>();
        @Override public synchronized void publicar(MensagemFila m) { mapa.put(m.id(), m); }
        @Override public synchronized List<MensagemFila> disponiveis(Instant agora, int limite) {
            return mapa.values().stream().filter(m -> !m.proximaTentativa().isAfter(agora)).limit(limite).collect(Collectors.toList());
        }
        @Override public synchronized void concluir(UUID id) { mapa.remove(id); }
        @Override public synchronized void reagendar(UUID id, int tentativas, Instant proxima) {
            MensagemFila m = mapa.get(id);
            if (m != null) mapa.put(id, new MensagemFila(m.id(), m.ordemId(), tentativas, proxima));
        }
    }
}
