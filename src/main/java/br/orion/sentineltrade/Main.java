package br.orion.sentineltrade;

import br.orion.sentineltrade.DadosDemonstracao.Credencial;
import br.orion.sentineltrade.aplicacao.servico.ParametrosSistema;
import br.orion.sentineltrade.interfacecli.CliApp;
import br.orion.sentineltrade.interfacecli.DemoScript;
import br.orion.sentineltrade.interfaceweb.ServidorWeb;
import java.io.IOException;
import java.time.Clock;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Ponto de entrada.  java -cp ... br.orion.sentineltrade.Main            → console interativo
 *                    java -cp ... br.orion.sentineltrade.Main --web      → API REST + interface web (http://127.0.0.1:8080)
 *                    java -cp ... br.orion.sentineltrade.Main --demo     → roteiro automatizado de demonstração
 * Sistema 100% SIMULADO: nenhum dinheiro, ativo, bolsa ou corretora reais.
 */
public final class Main {
    private Main() {}

    public static void main(String[] args) {
        System.setProperty("java.util.logging.SimpleFormatter.format", "%4$s: %5$s%n");
        java.util.logging.Logger.getLogger("").setLevel(java.util.logging.Level.WARNING);
        Config cfg = Config.carregar();

        if (args.length > 0 && args[0].equals("--demo")) { new DemoScript(System.out).executar(); return; }

        Aplicacao app = cfg.jdbc() ? Aplicacao.comJdbc(Clock.systemUTC(), cfg, System.out)
                                   : Aplicacao.emMemoria(Clock.systemUTC(), cfg, System.out);
        List<Credencial> credenciais = List.of();
        if (!cfg.jdbc() || cfg.demo()) {
            if (app.persistencia.usuarios().contar() == 0) credenciais = DadosDemonstracao.carregar(app, cfg.bruto());
            else DadosDemonstracao.definirCotacoesIniciais(app);
        } else {
            DadosDemonstracao.definirCotacoesIniciais(app);
        }
        if (cfg.mercadoSempreAberto()) app.parametros.definir(ParametrosSistema.MERCADO_FORCAR_ABERTO, "true");   // só para demonstrações fora do horário
        app.iniciarWorker(1000);
        ScheduledExecutorService mercado = Executors.newSingleThreadScheduledExecutor(r -> { Thread t = new Thread(r, "cotacoes"); t.setDaemon(true); return t; });
        Random rnd = new Random();
        mercado.scheduleWithFixedDelay(() -> app.cotacoesSimuladas.variar(rnd), 5, 5, TimeUnit.SECONDS);
        try {
            if (args.length > 0 && args[0].equals("--web")) executarWeb(app, cfg, credenciais);
            else new CliApp(app, credenciais, System.in, System.out).executar();
        } catch (IOException e) {
            System.err.println("Não foi possível iniciar o servidor web: " + e.getMessage());
        } finally {
            app.encerrar();
            mercado.shutdownNow();
        }
    }

    private static void executarWeb(Aplicacao app, Config cfg, List<Credencial> credenciais) throws IOException {
        ServidorWeb web = new ServidorWeb(app, cfg.demo() || !credenciais.isEmpty());
        int porta = web.iniciar(cfg.webEndereco(), cfg.webPorta());
        System.out.println("SentinelTrade (SIMULADO) — interface web em http://" + cfg.webEndereco() + ":" + porta + "/");
        if (!credenciais.isEmpty()) {
            System.out.println("Contas de demonstração (senhas geradas aleatoriamente nesta execução):");
            for (Credencial c : credenciais) System.out.printf("  %-14s %-22s senha: %s%n", c.papel(), c.email(), c.senha());
            System.out.println("O botão \"autenticador simulado\" da tela de MFA preenche o código do momento.");
        }
        System.out.println("Ctrl+C para encerrar.");
        try { Thread.currentThread().join(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        web.parar();
    }
}
