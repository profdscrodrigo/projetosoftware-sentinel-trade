package br.orion.sentineltrade.interfacecli;

import br.orion.sentineltrade.Aplicacao;
import br.orion.sentineltrade.Config;
import br.orion.sentineltrade.DadosDemonstracao;
import br.orion.sentineltrade.aplicacao.servico.AuditoriaService;
import br.orion.sentineltrade.aplicacao.servico.AutenticacaoService;
import br.orion.sentineltrade.aplicacao.servico.ParametrosSistema;
import br.orion.sentineltrade.aplicacao.servico.SolicitacaoOrdem;
import br.orion.sentineltrade.dominio.*;
import br.orion.sentineltrade.dominio.Excecoes.SentinelException;
import br.orion.sentineltrade.infraestrutura.integracao.RelogioAjustavel;
import java.io.PrintStream;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;

/**
 * Roteiro automatizado que exercita, de ponta a ponta, as regras críticas do SentinelTrade e imprime as evidências.
 * Usa relógio controlável (terça-feira 11:00 em Brasília) e senhas geradas aleatoriamente (nunca impressas).
 */
public final class DemoScript {
    private static final String ANA = "ana@orion.example", BRUNO = "bruno@orion.example";
    private final PrintStream out;
    private final RelogioAjustavel relogio = new RelogioAjustavel(Instant.parse("2026-11-24T14:00:00Z"), ZoneId.of("America/Sao_Paulo"));
    private Aplicacao app;
    private List<DadosDemonstracao.Credencial> creds;

    public DemoScript(PrintStream out) { this.out = out; }

    public void executar() {
        java.util.logging.Logger.getLogger("").setLevel(java.util.logging.Level.SEVERE);   // avisos de log fora da narrativa
        app = Aplicacao.emMemoria(relogio, Config.de(Map.of()), out);
        creds = DadosDemonstracao.carregar(app, Map.of());
        out.println("SentinelTrade — demonstração automatizada (dados 100% simulados; relógio: " + relogio.instant() + " = terça 11:00 BRT)");

        secao("1. Autenticação (RF01/RF02/RNF01)");
        tenta("senha errada", () -> app.auth.iniciarLogin(ANA, "senha-errada"));
        tenta("usuário inexistente (mensagem idêntica: sem enumeração)", () -> app.auth.iniciarLogin("ninguem@orion.example", "x"));
        String pendente = app.auth.iniciarLogin(ANA, senha(ANA));
        out.println("   senha correta → token PENDENTE (ainda sem acesso): " + (tentaSilencioso(() -> app.auth.exigir(pendente)) ? "acesso negado ✔" : "FALHA"));
        tenta("código MFA errado", () -> app.auth.confirmarMfa(pendente, codigoErrado(ANA)));
        String ana = app.auth.confirmarMfa(pendente, DadosDemonstracao.codigoMfaAtual(app, ANA));
        out.println("   código MFA correto → sessão ATIVA ✔");
        String guardado = app.persistencia.usuarios().buscarPorEmail(ANA).orElseThrow().segredoMfa();
        out.println("   segredo MFA gravado no banco (AES-256-GCM, versionado): " + guardado.substring(0, 14) + "… (" + guardado.length() + " caracteres; nunca em texto puro)");
        for (int i = 0; i < AutenticacaoService.MAX_FALHAS_LOGIN; i++) tentaSilencioso(() -> app.auth.iniciarLogin("risco@orion.example", "errada"));
        tenta("conta bloqueada após 5 falhas (mesmo com a senha correta)", () -> app.auth.iniciarLogin("risco@orion.example", senha("risco@orion.example")));
        relogio.avancar(AutenticacaoService.BLOQUEIO.plusMinutes(1));
        out.println("   +16 min: login do analista volta a funcionar ✔ (" + (login("risco@orion.example") != null) + ")");
        refrescar();   // o provedor simulado publica cotações novas (as antigas já estariam desatualizadas)
        tenta("controle de acesso por perfil: INVESTIDOR tenta cadastrar ativo", () -> { app.admin.cadastrarAtivo(login(ANA), "HGLG11", "Teste", "FII"); return null; });

        secao("2. Consultas: cotações e carteira (RF03/RF04)");
        ana = login(ANA);
        for (var q : app.cotacoes.listar(ana)) out.printf("   %-7s %8s  var %6s%%%n", q.cotacao().ticker(), q.cotacao().preco(), q.cotacao().variacaoPercentual());
        carteira(ana);

        secao("3. Compra com ciclo de vida completo (RF05/RF08/RF09/RF11/RF17)");
        Ordem compra = enviar(ana, SolicitacaoOrdem("COMPRA", "PETR4", "100", "38.50"));
        saldo(ana);
        app.processador.ciclo();
        out.println("   após o worker (envio + execução + liquidação + notificação):");
        out.println("   " + CliApp.descrever(app.ordens.consultarOrdem(ana, compra.id().toString())));
        carteira(ana);

        secao("4. Idempotência: reenvio com a mesma chave não duplica (RN03/RNF06)");
        SolicitacaoOrdem uma = SolicitacaoOrdem("COMPRA", "ITUB4", "100", "33.20");
        for (int i = 1; i <= 3; i++) { var r = app.ordens.enviarOrdem(ana, uma); out.println("   envio #" + i + ": ordem " + r.ordem().id().toString().substring(0, 8) + " duplicada=" + r.duplicada()); }
        out.println("   ordens no histórico: " + app.ordens.historico(ana).size() + " (a ITUB4 conta uma vez) | saldo bloqueado: " + app.carteira.consultarSaldo(ana).bloqueado().formatado());
        tenta("mesma chave com conteúdo diferente", () -> app.ordens.enviarOrdem(login(ANA), new SolicitacaoOrdem("COMPRA", "ITUB4", "999", "33.20", uma.chaveIdempotencia(), false)));
        app.processador.ciclo();

        secao("5. Validações de negócio e rejeições registradas (RF08)");
        refrescar(); ana = login(ANA);
        enviar(ana, SolicitacaoOrdem("COMPRA", "BOVA11", "500", "128.70"));                   // limite por ordem
        enviar(ana, SolicitacaoOrdem("VENDA", "MXRF11", "10", "10.35"));                      // sem posição
        enviar(ana, SolicitacaoOrdem("COMPRA", "PETR4", "10", "50.00"));                      // preço fora da faixa
        app.admin.alterarStatusAtivo(login("admin@orion.example"), "VALE3", "SUSPENSO");
        enviar(ana, SolicitacaoOrdem("COMPRA", "VALE3", "10", "62.40"));                      // ativo suspenso
        app.admin.alterarStatusAtivo(login("admin@orion.example"), "VALE3", "ATIVO");
        app.risco.definirLimites(login("risco@orion.example"), id(ANA), "500000", "1000000", "500000");
        out.println("   (analista de risco elevou os limites de Ana)");
        ana = login(ANA);
        enviar(ana, SolicitacaoOrdem("COMPRA", "MXRF11", "10000", "10.35"));                  // saldo insuficiente
        out.println("   saldo de Ana permanece: " + app.carteira.consultarSaldo(ana).disponivel().formatado());

        secao("6. Confirmação adicional para ordem de alto valor («extend»)");
        app.risco.definirLimites(login("risco@orion.example"), id(ANA), "50000", "200000", "20000");
        ana = login(ANA);
        SolicitacaoOrdem alta = SolicitacaoOrdem("COMPRA", "PETR4", "700", "38.50");
        tenta("sem confirmação (R$ 26.950 > R$ 20.000)", () -> app.ordens.enviarOrdem(login(ANA), alta));
        var conf = app.ordens.enviarOrdem(ana, new SolicitacaoOrdem(alta.lado(), alta.ticker(), alta.quantidade(), alta.preco(), alta.chaveIdempotencia(), true));
        out.println("   com confirmação → " + CliApp.descrever(conf.ordem()));
        app.processador.ciclo();

        secao("7. Execução parcial: corretora executa em lotes de 40 (RF09)");
        app.corretoraSimulada.setLoteMaximo(40);
        String bruno = login(BRUNO);
        Ordem venda = enviar(bruno, SolicitacaoOrdem("VENDA", "PETR4", "100", "38.50"));
        for (int i = 1; i <= 3; i++) { app.processador.ciclo(); out.println("   ciclo " + i + ": " + CliApp.descrever(app.ordens.consultarOrdem(bruno, venda.id().toString()))); }
        app.corretoraSimulada.setLoteMaximo(Integer.MAX_VALUE);
        out.println("   saldo de Bruno: " + app.carteira.consultarSaldo(bruno).disponivel().formatado());

        secao("8. Cancelamento (RF07)");
        refrescar(); ana = login(ANA);
        Ordem repouso = enviar(ana, SolicitacaoOrdem("COMPRA", "PETR4", "100", "36.00"));      // não cruza a cotação: fica no livro
        app.processador.ciclo();
        out.println("   " + CliApp.descrever(app.ordens.consultarOrdem(ana, repouso.id().toString())));
        out.println("   cancelando… " + CliApp.descrever(app.ordens.cancelarOrdem(ana, repouso.id().toString())));
        out.println("   saldo bloqueado após cancelar: " + app.carteira.consultarSaldo(ana).bloqueado().formatado());
        tenta("cancelar ordem já EXECUTADA", () -> app.ordens.cancelarOrdem(login(ANA), compra.id().toString()));

        secao("9. Ordem a mercado e alteração de ordem (RF21/RF22)");
        refrescar(); ana = login(ANA);
        var mkt = app.ordens.enviarOrdem(ana, new SolicitacaoOrdem("COMPRA", "MERCADO", "PETR4", "100", null, UUID.randomUUID().toString(), false));
        out.println("   COMPRA A MERCADO 100 PETR4: limite de PROTEÇÃO " + mkt.ordem().precoLimite() + " (cotação " + app.cotacoes.consultar(ana, "PETR4").cotacao().preco() + " + faixa) → " + mkt.ordem().status());
        app.processador.ciclo();
        out.println("   após o worker: " + CliApp.descrever(app.ordens.consultarOrdem(ana, mkt.ordem().id().toString())) + " | executada ao preço de MERCADO");
        Ordem base = enviar(ana, SolicitacaoOrdem("COMPRA", "PETR4", "100", "36.00"));
        app.processador.ciclo();
        out.println("   " + CliApp.descrever(app.ordens.consultarOrdem(ana, base.id().toString())) + " (já na corretora)");
        var alt = app.ordens.alterarOrdem(ana, base.id().toString(), "50", "35.00", UUID.randomUUID().toString(), false);
        out.println("   ALTERAR (cancelar e substituir) →");
        out.println("     original: " + CliApp.descrever(alt.substituida()));
        out.println("     nova    : " + CliApp.descrever(alt.nova()) + " | substitui " + alt.nova().substituiOrdemId().toString().substring(0, 8));
        out.println("     reserva da nova: " + app.carteira.consultarSaldo(ana).bloqueado().formatado() + " (a da antiga foi devolvida)");
        final String idNova = alt.nova().id().toString();
        final String tAna = ana, tBruno = login(BRUNO);
        tenta("alteração impossível (preço fora da faixa): a ordem NÃO é tocada", () -> app.ordens.alterarOrdem(tAna, idNova, "50", "90.00", UUID.randomUUID().toString(), false));
        out.println("   a nova segue intacta: " + CliApp.descrever(app.ordens.consultarOrdem(tAna, idNova)));
        tenta("alterar ordem de outro investidor", () -> app.ordens.alterarOrdem(tBruno, idNova, "10", "35.00", UUID.randomUUID().toString(), false));
        app.ordens.cancelarOrdem(tAna, idNova);

        secao("10. Falha da corretora: nada é apagado nem corrompido (RN07/RNF04)");
        app.corretoraSimulada.falharProximas(2);
        refrescar(); ana = login(ANA);
        Ordem transit = enviar(ana, SolicitacaoOrdem("COMPRA", "VALE3", "10", "62.40"));
        app.processador.ciclo();
        out.println("   corretora com falha transitória → " + CliApp.descrever(app.ordens.consultarOrdem(ana, transit.id().toString())) + " (continua na fila)");
        relogio.avancar(Duration.ofSeconds(61)); refrescar();
        app.processador.ciclo();
        out.println("   corretora voltou → " + CliApp.descrever(app.ordens.consultarOrdem(login(ANA), transit.id().toString())));

        app.corretoraSimulada.setIndisponivel(true);
        refrescar(); ana = login(ANA);
        Ordem queda = enviar(ana, SolicitacaoOrdem("COMPRA", "VALE3", "10", "62.40"));
        out.println("   corretora FORA DO AR. saldo bloqueado: " + app.carteira.consultarSaldo(ana).bloqueado().formatado());
        for (int i = 1; i <= ProcessadorOrdens_MAX(); i++) {
            app.processador.ciclo();
            out.println("   tentativa " + i + ": " + CliApp.descrever(app.ordens.consultarOrdem(login(ANA), queda.id().toString())) + " | circuito: " + app.corretoraResiliente.estado());
            relogio.avancar(Duration.ofSeconds(61));
        }
        app.corretoraSimulada.setIndisponivel(false);
        out.println("   reserva devolvida; saldo bloqueado: " + app.carteira.consultarSaldo(login(ANA)).bloqueado().formatado());

        secao("11. Provedor de cotações não confiável (dados externos revalidados)");
        refrescar();
        app.cotacoesSimuladas.injetarBruta(new Cotacao(Ticker.de("PETR4"), Dinheiro.de("-5.00"), Dinheiro.de("38.10"), relogio.instant()));
        out.println("   provedor envia PETR4 = -5,00 → sistema mantém a última válida: " + app.cotacoes.consultar(login(ANA), "PETR4").cotacao().preco());
        DadosDemonstracao.definirCotacoesIniciais(app);
        app.cotacoesSimuladas.setIndisponivel(true);
        out.println("   provedor fora do ar → última cotação válida (recente): " + app.cotacoes.consultar(login(ANA), "PETR4").cotacao().preco());
        relogio.avancar(Duration.ofMinutes(3));
        out.println("   3 min depois → marcada DESATUALIZADA: " + app.cotacoes.consultar(login(ANA), "PETR4").desatualizada());
        enviar(login(ANA), SolicitacaoOrdem("COMPRA", "PETR4", "10", "38.50"));
        app.cotacoesSimuladas.setIndisponivel(false);

        secao("12. Mercado fechado");
        relogio.definir(Instant.parse("2026-11-24T22:00:00Z"));   // 19:00 BRT
        enviar(login(ANA), SolicitacaoOrdem("COMPRA", "PETR4", "10", "38.50"));

        secao("13. Privacidade e controle de acesso (RNF08)");
        relogio.definir(Instant.parse("2026-11-25T14:00:00Z")); DadosDemonstracao.definirCotacoesIniciais(app);
        String id = app.ordens.historico(login(ANA)).get(app.ordens.historico(login(ANA)).size() - 1).id().toString();
        String b = login(BRUNO);
        tenta("Bruno consulta ordem da Ana", () -> app.ordens.consultarOrdem(b, id));
        tenta("Bruno cancela ordem da Ana", () -> app.ordens.cancelarOrdem(b, id));
        tenta("ADMINISTRADOR tenta enviar ordem", () -> app.ordens.enviarOrdem(login("admin@orion.example"), SolicitacaoOrdem("COMPRA", "PETR4", "1", "38.50")));

        secao("14. Entradas maliciosas (allowlist no servidor)");
        String a2 = login(ANA);
        for (String ruim : new String[]{"PETR4'; DROP TABLE ordens;--", "<script>alert(1)</script>", "../../etc/passwd"})
            tenta("ativo = " + ruim, () -> app.ordens.enviarOrdem(a2, new SolicitacaoOrdem("COMPRA", ruim, "1", "10", UUID.randomUUID().toString(), false)));
        tenta("quantidade = -5", () -> app.ordens.enviarOrdem(a2, new SolicitacaoOrdem("COMPRA", "PETR4", "-5", "10", UUID.randomUUID().toString(), false)));

        secao("15. Auditoria imutável com hash encadeado (RF10/RNF03)");
        String adm = login("admin@orion.example");
        out.println("   " + app.admin.verificarIntegridadeAuditoria(adm));
        List<EventoAuditoria> todos = app.persistencia.auditoria().listar(0, 100_000);
        todos.stream().skip(Math.max(0, todos.size() - 6)).forEach(e -> out.println("   #" + e.seq() + " " + e.tipo() + " " + e.entidade() + " | " + e.detalhes() + " | hash " + e.hash().substring(0, 12) + "…"));
        List<EventoAuditoria> adulterada = new ArrayList<>(todos);
        EventoAuditoria alvo = adulterada.get(todos.size() / 2);
        adulterada.set(todos.size() / 2, new EventoAuditoria(alvo.seq(), alvo.instante(), alvo.tipo(), alvo.usuarioId(), alvo.entidade(), alvo.entidadeId(),
                alvo.detalhes() + " [ADULTERADO]", alvo.hashAnterior(), alvo.hash()));
        out.println("   simulando adulteração do evento #" + alvo.seq() + " → " + AuditoriaService.verificarCadeia(adulterada));
        List<EventoAuditoria> semUm = new ArrayList<>(todos); semUm.remove(todos.size() / 3);
        out.println("   simulando remoção de um evento → " + AuditoriaService.verificarCadeia(semUm));
        out.println("   total de eventos auditados: " + todos.size());

        secao("Resumo final da carteira de Ana");
        carteira(login(ANA));
        out.println("\nFim da demonstração.");
    }

    // ---------------------------------------------------------------- auxiliares
    private static int ProcessadorOrdens_MAX() { return br.orion.sentineltrade.aplicacao.servico.ProcessadorOrdens.MAX_TENTATIVAS_ENVIO; }
    private static SolicitacaoOrdem SolicitacaoOrdem(String lado, String t, String q, String p) { return new SolicitacaoOrdem(lado, t, q, p, UUID.randomUUID().toString(), false); }
    private void secao(String t) { out.println("\n━━ " + t + " ━━"); }
    private String senha(String email) { return creds.stream().filter(c -> c.email().equals(email)).findFirst().orElseThrow().senha(); }
    private String id(String email) { return creds.stream().filter(c -> c.email().equals(email)).findFirst().orElseThrow().usuarioId().toString(); }
    private final Map<String, String> sessoes = new java.util.HashMap<>();

    /** Reaproveita a sessão enquanto válida (como um usuário real); só refaz o login se ela expirou. */
    private String login(String email) {
        String existente = sessoes.get(email);
        if (existente != null) {
            try { app.auth.validarToken(existente); return existente; } catch (RuntimeException expirou) { /* novo login abaixo */ }
        }
        String novo = app.auth.confirmarMfa(app.auth.iniciarLogin(email, senha(email)), DadosDemonstracao.codigoMfaAtual(app, email));
        sessoes.put(email, novo);
        return novo;
    }
    private void refrescar() { app.cotacoesSimuladas.atualizarInstantes(); }
    private String codigoErrado(String email) {
        var u = app.persistencia.usuarios().buscarPorEmail(email).orElseThrow();
        for (int i = 0; ; i++) { String c = String.format("%06d", (i * 7919) % 1_000_000); if (!app.mfa.verificar(u.segredoMfa(), c, relogio.instant())) return c; }
    }

    private Ordem enviar(String token, SolicitacaoOrdem s) {
        var r = app.ordens.enviarOrdem(token, s);
        out.println("   " + s.lado() + " " + s.ticker() + " " + s.quantidade() + " @ " + s.preco() + "  → " + r.ordem().status()
                + (r.ordem().motivoRejeicao() != null ? " [" + r.ordem().motivoRejeicao() + "] " + r.ordem().motivoRejeicao().descricao() : ""));
        return r.ordem();
    }

    private void saldo(String token) {
        var s = app.carteira.consultarSaldo(token);
        out.println("   saldo disponível " + s.disponivel().formatado() + " | bloqueado (reserva da ordem) " + s.bloqueado().formatado());
    }

    private void carteira(String token) {
        var r = app.carteira.consultarCarteira(token);
        out.println("   saldo " + r.saldo().disponivel().formatado() + " (bloqueado " + r.saldo().bloqueado().formatado() + ")");
        r.itens().forEach(i -> out.printf("   %-7s qtd %5d  PM %8s  valor %10s%n", i.ticker(), i.quantidade(), i.precoMedio(), i.valorEstimado()));
    }

    private void tenta(String descricao, Callable<?> acao) {
        try { acao.call(); out.println("   ✘ " + descricao + ": DEVERIA TER FALHADO"); }
        catch (SentinelException e) { out.println("   ✔ " + descricao + " → recusado: " + e.getMessage()); }
        catch (Exception e) { out.println("   ✘ " + descricao + ": erro inesperado " + e); }
    }

    private boolean tentaSilencioso(Callable<?> acao) {
        try { acao.call(); return false; } catch (Exception e) { return true; }
    }
}
