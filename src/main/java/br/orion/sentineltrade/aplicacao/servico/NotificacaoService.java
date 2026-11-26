package br.orion.sentineltrade.aplicacao.servico;

import br.orion.sentineltrade.aplicacao.porta.EnviadorNotificacao;
import br.orion.sentineltrade.aplicacao.porta.Repositorios.NotificacaoRepositorio;
import br.orion.sentineltrade.dominio.Notificacao;
import br.orion.sentineltrade.dominio.Papel;
import br.orion.sentineltrade.dominio.TipoNotificacao;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * Notificações em duas etapas (outbox): {@link #criar} grava na MESMA transação do fato; o envio
 * ({@link #despacharPendentes}) acontece depois, fora da transação — uma falha de envio nunca desfaz uma ordem.
 */
public final class NotificacaoService {
    private final NotificacaoRepositorio repo;
    private final EnviadorNotificacao enviador;
    private final AutenticacaoService auth;
    private final Clock clock;

    public NotificacaoService(NotificacaoRepositorio repo, EnviadorNotificacao enviador, AutenticacaoService auth, Clock clock) {
        this.repo = repo; this.enviador = enviador; this.auth = auth; this.clock = clock;
    }

    public void criar(UUID investidorId, TipoNotificacao tipo, String mensagem) {
        repo.inserir(Notificacao.nova(investidorId, tipo, Sanitizador.paraLog(mensagem, 300), clock.instant()));
    }

    public int despacharPendentes() {
        int enviadas = 0;
        for (Notificacao n : repo.pendentesDeEnvio(100)) {
            try {
                enviador.enviar(n);
                n.marcarEnviada(clock.instant());
                repo.atualizar(n);
                enviadas++;
            } catch (RuntimeException e) {
                Log.aviso("Falha ao enviar notificação " + n.id() + "; será reenviada (" + e.getClass().getSimpleName() + ")");
            }
        }
        return enviadas;
    }

    public List<Notificacao> minhasNotificacoes(String token) {
        var sessao = auth.exigir(token, Papel.INVESTIDOR);
        return repo.listarPorInvestidor(sessao.usuarioId(), 50);
    }
}
