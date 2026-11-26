package br.orion.sentineltrade;

import br.orion.sentineltrade.DadosDemonstracao.Credencial;
import br.orion.sentineltrade.aplicacao.servico.SolicitacaoOrdem;
import br.orion.sentineltrade.dominio.Papel;
import br.orion.sentineltrade.infraestrutura.integracao.RelogioAjustavel;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Fixture dos testes: aplicação completa em memória, relógio controlável (terça-feira 11:00 em Brasília,
 * mercado aberto), dados simulados e senhas ALEATÓRIAS geradas a cada execução (nenhum segredo no código).
 * Ana: R$ 100.000 + 200 VALE3. Bruno: R$ 100.000 + 500 PETR4. Cotações: PETR4 38,50; VALE3 62,40.
 */
public final class Cenario {
    public final RelogioAjustavel relogio = new RelogioAjustavel(Instant.parse("2026-11-24T14:00:00Z"), ZoneId.of("America/Sao_Paulo"));
    public final Aplicacao app;
    public final List<Credencial> credenciais;

    public Cenario() {
        Config cfg = Config.de(Map.of("PBKDF2_ITERATIONS", "1000", "BROKER_TIMEOUT_MS", "2000"));
        app = Aplicacao.emMemoria(relogio, cfg, null);
        credenciais = DadosDemonstracao.carregar(app, Map.of());
    }

    public Credencial cred(String email) {
        return credenciais.stream().filter(c -> c.email().equals(email)).findFirst().orElseThrow();
    }
    public UUID id(String email) { return cred(email).usuarioId(); }
    public UUID anaId() { return id("ana@orion.example"); }
    public UUID brunoId() { return id("bruno@orion.example"); }

    /** Login completo (senha + TOTP). */
    public String login(String email) {
        Credencial c = cred(email);
        String pendente = app.auth.iniciarLogin(email, c.senha());
        return app.auth.confirmarMfa(pendente, DadosDemonstracao.codigoMfaAtual(app, email));
    }
    public String ana() { return login("ana@orion.example"); }
    public String bruno() { return login("bruno@orion.example"); }
    public String admin() { return login("admin@orion.example"); }
    public String risco() { return login("risco@orion.example"); }

    /** Um código de 6 dígitos garantidamente inválido para o usuário no instante atual. */
    public String codigoMfaErrado(String email) {
        var u = app.persistencia.usuarios().buscarPorEmail(email).orElseThrow();
        for (int i = 0; ; i++) {
            String candidato = String.format("%06d", (i * 7919) % 1_000_000);
            if (!app.mfa.verificar(u.segredoMfa(), candidato, relogio.instant())) return candidato;
        }
    }

    public static String chave() { return UUID.randomUUID().toString(); }

    public static SolicitacaoOrdem compra(String ticker, int qtd, String preco) {
        return new SolicitacaoOrdem("COMPRA", ticker, String.valueOf(qtd), preco, chave(), false);
    }
    public static SolicitacaoOrdem venda(String ticker, int qtd, String preco) {
        return new SolicitacaoOrdem("VENDA", ticker, String.valueOf(qtd), preco, chave(), false);
    }
    public static SolicitacaoOrdem confirmada(SolicitacaoOrdem s) {
        return new SolicitacaoOrdem(s.lado(), s.ticker(), s.quantidade(), s.preco(), s.chaveIdempotencia(), true);
    }

    public Papel papel(String email) { return cred(email).papel(); }
}
