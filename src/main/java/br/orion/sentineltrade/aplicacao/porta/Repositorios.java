package br.orion.sentineltrade.aplicacao.porta;

import br.orion.sentineltrade.dominio.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Portas de persistência (arquitetura hexagonal). A camada de aplicação depende só destas interfaces;
 * as implementações (memória, JDBC/PostgreSQL) ficam em infraestrutura.
 */
public final class Repositorios {
    private Repositorios() {}

    public interface UsuarioRepositorio {
        void inserir(Usuario u);                    // ChaveDuplicadaException se o e-mail já existe
        void atualizar(Usuario u);
        Optional<Usuario> buscarPorId(UUID id);
        Optional<Usuario> buscarPorEmail(String email);
        List<Usuario> listarPorPapel(Papel papel);
        long contar();
    }

    public interface SessaoRepositorio {
        void salvar(Sessao s);
        Optional<Sessao> buscarPorTokenHash(String tokenHash);
    }

    public interface ContaRepositorio {
        void inserir(Conta c);
        void atualizar(Conta c);                    // ConcorrenciaException se a versão mudou
        Optional<Conta> buscarPorInvestidor(UUID investidorId);
    }

    public interface PosicaoRepositorio {
        Optional<Posicao> buscar(UUID investidorId, Ticker ticker);
        List<Posicao> listarPorInvestidor(UUID investidorId);
        void salvar(Posicao p);                     // insere se versao == 0; senão atualiza com controle de versão
    }

    public interface AtivoRepositorio {
        Optional<Ativo> buscar(Ticker ticker);
        List<Ativo> listar();
        void salvar(Ativo a);
    }

    public interface OrdemRepositorio {
        void inserir(Ordem o);                      // ChaveDuplicadaException se (investidor, chave) já existe
        void atualizar(Ordem o);                    // ConcorrenciaException se a versão mudou
        Optional<Ordem> buscarPorId(UUID id);
        Optional<Ordem> buscarPorChave(UUID investidorId, String chave);
        List<Ordem> listarPorInvestidor(UUID investidorId);
        List<Ordem> listarPorStatus(StatusOrdem status, int limite);
        /** Soma (quantidade × preço) das ordens que consomem limite: VALIDADA, ENVIADA, PARCIAL, EXECUTADA. */
        Dinheiro valorNegociadoNoPeriodo(UUID investidorId, Instant inicio, Instant fim);
    }

    public interface ExecucaoRepositorio {
        /** @return false se a execução (idExecucao) já havia sido registrada — idempotência. */
        boolean registrarSeNova(Execucao e);
        List<Execucao> listarPorOrdem(UUID ordemId);
    }

    public interface LimiteRiscoRepositorio {
        Optional<LimiteRisco> buscar(UUID investidorId);
        void salvar(LimiteRisco l);
    }

    public interface AuditoriaRepositorio {
        /** Serializa a leitura e a inserção da próxima sequência dentro da transação atual. */
        default void bloquearAppend() {}
        Optional<EventoAuditoria> ultimo();
        void anexar(EventoAuditoria e);             // somente inserção; ConcorrenciaException se seq já existe
        List<EventoAuditoria> listar(long aposSeq, int limite);
    }

    public interface NotificacaoRepositorio {
        void inserir(Notificacao n);
        void atualizar(Notificacao n);
        List<Notificacao> listarPorInvestidor(UUID investidorId, int limite);
        List<Notificacao> pendentesDeEnvio(int limite);
    }

    public interface ParametroRepositorio {
        Optional<String> buscar(String chave);
        void salvar(String chave, String valor);
        Map<String, String> listar();
    }
}
