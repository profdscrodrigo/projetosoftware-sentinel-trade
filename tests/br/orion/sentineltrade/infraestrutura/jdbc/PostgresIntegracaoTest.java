package br.orion.sentineltrade.infraestrutura.jdbc;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import br.orion.sentineltrade.Aplicacao;
import br.orion.sentineltrade.Config;
import br.orion.sentineltrade.DadosDemonstracao;
import br.orion.sentineltrade.aplicacao.servico.OrdemService.ResultadoAlteracao;
import br.orion.sentineltrade.aplicacao.servico.SolicitacaoOrdem;
import br.orion.sentineltrade.dominio.*;
import br.orion.sentineltrade.dominio.Excecoes.ConcorrenciaException;
import br.orion.sentineltrade.dominio.Excecoes.ConflitoException;
import br.orion.sentineltrade.infraestrutura.integracao.RelogioAjustavel;
import br.orion.sentineltrade.infraestrutura.seguranca.CifradorSegredo;
import java.time.Instant;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;

/**
 * Teste de INTEGRAÇÃO contra um PostgreSQL REAL — a prova de que a camada JDBC funciona de ponta a ponta.
 * É IGNORADO quando as variáveis abaixo não existem (o build comum não precisa de banco).
 *
 * Como executar:
 *   docker compose up -d                              (cria o banco com schema.sql e seed.sql)
 *   export SENTINEL_IT_DB_URL=jdbc:postgresql://localhost:5432/sentineltrade
 *   export SENTINEL_IT_DB_USER=...   SENTINEL_IT_DB_PASSWORD=...      (os mesmos do .env)
 *   mvn test -Dtest=PostgresIntegracaoTest
 *
 * Use um banco DESCARTÁVEL: o teste cria usuários de teste (e-mails únicos) e ordens simuladas.
 */
class PostgresIntegracaoTest {
    private static final String URL = System.getenv("SENTINEL_IT_DB_URL");
    private static final String USUARIO = System.getenv("SENTINEL_IT_DB_USER");
    private static final String SENHA_DB = System.getenv("SENTINEL_IT_DB_PASSWORD");

    private final RelogioAjustavel relogio = new RelogioAjustavel(Instant.parse("2026-11-24T14:00:00Z"), ZoneId.of("America/Sao_Paulo"));
    private final String chaveMfa = CifradorSegredo.gerarChaveBase64();
    private final String senha = "Aa1!" + UUID.randomUUID();
    private final String sufixo = UUID.randomUUID().toString().substring(0, 8);

    private Aplicacao novaAplicacao() {
        Config cfg = Config.de(Map.of("SENTINEL_STORAGE", "jdbc", "DB_URL", URL, "DB_USER", USUARIO == null ? "" : USUARIO,
                "DB_PASSWORD", SENHA_DB == null ? "" : SENHA_DB, "PBKDF2_ITERATIONS", "1000", "MFA_ENCRYPTION_KEY", chaveMfa));
        Aplicacao app = Aplicacao.comJdbc(relogio, cfg, null);
        DadosDemonstracao.definirCotacoesIniciais(app);
        return app;
    }

    private String login(Aplicacao app, String email) {
        String pendente = app.auth.iniciarLogin(email, senha);
        return app.auth.confirmarMfa(pendente, DadosDemonstracao.codigoMfaAtual(app, email));
    }

    @Test void fluxoCompletoContraPostgreSQL() throws Exception {
        assumeTrue(URL != null && !URL.isBlank(), "defina SENTINEL_IT_DB_URL (e _USER/_PASSWORD) para executar contra um PostgreSQL de teste");
        Aplicacao app = novaAplicacao();
        String emailAdm = "adm" + sufixo + "@orion.example", emailInv = "inv" + sufixo + "@orion.example";

        // 1) cadastro (usuário, conta, limites, segredo MFA cifrado) e unicidade de e-mail
        app.admin.provisionar(null, "Admin Teste", emailAdm, senha, Papel.ADMINISTRADOR);
        Usuario inv = app.admin.provisionar(null, "Investidor Teste", emailInv, senha, Papel.INVESTIDOR);
        assertThrows(ConflitoException.class, () -> app.admin.provisionar(null, "Outro Nome", emailInv, senha, Papel.INVESTIDOR));
        assertTrue(app.persistencia.usuarios().buscarPorEmail(emailInv).orElseThrow().segredoMfa().startsWith("v1:"), "segredo MFA cifrado no banco");

        // 2) depósito simulado, login com MFA e compra
        app.admin.depositarSimulado(login(app, emailAdm), inv.id().toString(), "100000");
        String t = login(app, emailInv);
        SolicitacaoOrdem compra = new SolicitacaoOrdem("COMPRA", "PETR4", "100", "38.50", UUID.randomUUID().toString(), false);
        var a = app.ordens.enviarOrdem(t, compra);
        assertEquals(StatusOrdem.VALIDADA, a.ordem().status());
        assertEquals(Dinheiro.de("3850"), app.carteira.consultarSaldo(t).bloqueado());

        // 3) idempotência garantida pelo UNIQUE do banco
        var b = app.ordens.enviarOrdem(t, compra);
        assertTrue(b.duplicada());
        assertEquals(a.ordem().id(), b.ordem().id());
        assertEquals(1, app.ordens.historico(t).size());

        // 4) execução pelo motor assíncrono, liquidação atômica e carteira
        app.processador.ciclo();
        assertEquals(StatusOrdem.EXECUTADA, app.ordens.consultarOrdem(t, a.ordem().id().toString()).status());
        assertEquals(Dinheiro.de("96150"), app.carteira.consultarSaldo(t).disponivel());
        assertEquals(100, app.carteira.consultarCarteira(t).itens().get(0).quantidade());

        // 5) ordem a mercado, alteração (cancelar e substituir) e cancelamento
        var base = app.ordens.enviarOrdem(t, new SolicitacaoOrdem("COMPRA", "PETR4", "100", "36.00", UUID.randomUUID().toString(), false)).ordem();
        app.processador.ciclo();
        ResultadoAlteracao alt = app.ordens.alterarOrdem(t, base.id().toString(), "50", "35.00", UUID.randomUUID().toString(), false);
        assertEquals(StatusOrdem.CANCELADA, alt.substituida().status());
        assertEquals(base.id(), app.persistencia.ordens().buscarPorId(alt.nova().id()).orElseThrow().substituiOrdemId());
        assertEquals(StatusOrdem.CANCELADA, app.ordens.cancelarOrdem(t, alt.nova().id().toString()).status());
        assertEquals(Dinheiro.ZERO, app.carteira.consultarSaldo(t).bloqueado(), "toda reserva devolvida");

        // 6) persistência real: uma NOVA instância (como após reiniciar) enxerga tudo, e o MFA cifrado continua decifrável
        Aplicacao reiniciada = novaAplicacao();
        String t2 = login(reiniciada, emailInv);
        assertEquals(3, reiniciada.ordens.historico(t2).size(), "compra, ordem original e substituta");
        assertEquals(Dinheiro.de("96150"), reiniciada.carteira.consultarSaldo(t2).disponivel());

        // 7) trilha de auditoria íntegra no banco
        assertTrue(app.auditoria.verificarIntegridade().integra(), app.auditoria.verificarIntegridade().motivo());
    }

    @Test void concorrenciaRealNoBancoNaoCriaGastoDuploNemOrdemDuplicada() throws Exception {
        assumeTrue(URL != null && !URL.isBlank(), "defina SENTINEL_IT_DB_URL (e _USER/_PASSWORD) para executar contra um PostgreSQL de teste");
        Aplicacao app = novaAplicacao();
        String emailAdm = "adm" + sufixo + "@orion.example", emailInv = "inv" + sufixo + "@orion.example";
        app.admin.provisionar(null, "Admin Teste", emailAdm, senha, Papel.ADMINISTRADOR);
        Usuario inv = app.admin.provisionar(null, "Investidor Teste", emailInv, senha, Papel.INVESTIDOR);
        app.admin.depositarSimulado(login(app, emailAdm), inv.id().toString(), "100000");
        String t = login(app, emailInv);
        app.admin.definirParametro(login(app, emailAdm), "ordem.max_por_minuto", "1000");

        ExecutorService pool = Executors.newFixedThreadPool(4);
        List<Future<?>> fs = new ArrayList<>();
        for (int th = 0; th < 4; th++) fs.add(pool.submit(() -> {
            for (int i = 0; i < 5; i++) {
                SolicitacaoOrdem s = new SolicitacaoOrdem("COMPRA", "MXRF11", "1", "10.35", UUID.randomUUID().toString(), false);
                for (int tentativa = 0; ; tentativa++) {                      // o cliente repete com a MESMA chave: a idempotência protege
                    try { app.ordens.enviarOrdem(t, s); break; }
                    catch (ConcorrenciaException e) { if (tentativa > 30) throw e; }
                }
            }
            return null;
        }));
        try {
            for (Future<?> f : fs) f.get(60, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }

        assertEquals(20, app.ordens.historico(t).size(), "20 ordens distintas, nenhuma duplicada");
        assertEquals(Dinheiro.de("207.00"), app.carteira.consultarSaldo(t).bloqueado(), "reserva exata: sem gasto duplo");
        assertEquals(Dinheiro.de("99793.00"), app.carteira.consultarSaldo(t).disponivel());
        assertTrue(app.auditoria.verificarIntegridade().integra());
    }
}
