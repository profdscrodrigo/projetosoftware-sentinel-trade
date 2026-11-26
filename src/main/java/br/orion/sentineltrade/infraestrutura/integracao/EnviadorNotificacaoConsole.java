package br.orion.sentineltrade.infraestrutura.integracao;

import br.orion.sentineltrade.aplicacao.porta.EnviadorNotificacao;
import br.orion.sentineltrade.aplicacao.servico.Sanitizador;
import br.orion.sentineltrade.dominio.Notificacao;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

/** Serviço de notificação simulado: imprime no console e guarda o que enviou (útil em testes). */
public final class EnviadorNotificacaoConsole implements EnviadorNotificacao {
    private final PrintStream saida;
    private final List<Notificacao> enviadas = new ArrayList<>();

    public EnviadorNotificacaoConsole(PrintStream saida) { this.saida = saida; }

    @Override public synchronized void enviar(Notificacao n) {
        enviadas.add(n);
        if (saida != null) saida.println("   [NOTIFICAÇÃO " + n.tipo() + "] " + Sanitizador.paraLog(n.mensagem(), 300));
    }
    public synchronized List<Notificacao> enviadas() { return new ArrayList<>(enviadas); }
}
