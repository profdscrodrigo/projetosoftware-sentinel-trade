package br.orion.sentineltrade.interfaceweb;

import static org.junit.jupiter.api.Assertions.*;

import br.orion.sentineltrade.Cenario;
import br.orion.sentineltrade.DadosDemonstracao;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** API REST e front-end estático, contra um servidor real em porta livre (127.0.0.1). */
class ServidorWebTest {
    private record R(int status, String corpo, java.net.http.HttpHeaders cab) {
        String campo(String nome) {
            Matcher m = Pattern.compile("\"" + nome + "\":\"([^\"]*)\"").matcher(corpo);
            return m.find() ? m.group(1) : null;
        }
    }

    private final Cenario c = new Cenario();
    private final ServidorWeb web = new ServidorWeb(c.app, true);
    private final HttpClient http = HttpClient.newHttpClient();
    private String base;

    private void iniciar() throws Exception { base = "http://127.0.0.1:" + web.iniciar("127.0.0.1", 0); }

    private R chamar(String metodo, String caminho, String token, String corpo) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base + caminho));
        if (token != null) b.header("Authorization", "Bearer " + token);
        HttpRequest.BodyPublisher bp = corpo == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(corpo);
        if (corpo != null) b.header("Content-Type", "application/json");
        HttpResponse<String> r = http.send(b.method(metodo, bp).build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return new R(r.statusCode(), r.body(), r.headers());
    }

    private String login(String email) throws Exception {
        String senha = c.cred(email).senha();
        R l = chamar("POST", "/api/auth/login", null, "{\"email\":\"" + email + "\",\"senha\":\"" + senha + "\"}");
        assertEquals(200, l.status());
        R m = chamar("POST", "/api/auth/mfa", null, "{\"tokenPendente\":\"" + l.campo("tokenPendente") + "\",\"codigo\":\"" + DadosDemonstracao.codigoMfaAtual(c.app, email) + "\"}");
        assertEquals(200, m.status());
        return m.campo("token");
    }

    private static String ordemJson(String lado, String tipo, String ticker, String qtd, String preco, String chave, boolean conf) {
        return "{\"lado\":\"" + lado + "\",\"tipo\":\"" + tipo + "\",\"ticker\":\"" + ticker + "\",\"quantidade\":\"" + qtd + "\""
                + (preco == null ? "" : ",\"preco\":\"" + preco + "\"") + ",\"chaveIdempotencia\":\"" + chave + "\",\"confirmada\":" + conf + "}";
    }

    @Test void saudeEPublicaERespostasTemCabecalhosDeSeguranca() throws Exception {
        iniciar();
        try {
            R r = chamar("GET", "/api/saude", null, null);
            assertEquals(200, r.status());
            assertTrue(r.corpo().contains("\"status\":\"ok\""));
            assertTrue(r.cab().firstValue("Content-Security-Policy").orElse("").contains("default-src 'none'"));
            assertEquals("nosniff", r.cab().firstValue("X-Content-Type-Options").orElse(""));
            assertEquals("no-store", r.cab().firstValue("Cache-Control").orElse(""));
            assertTrue(r.cab().firstValue("Content-Security-Policy").orElse("").contains("frame-ancestors 'none'"));
            assertTrue(r.cab().firstValue("Access-Control-Allow-Origin").isEmpty(), "sem CORS: apenas mesma origem");
        } finally { web.parar(); }
    }

    @Test void loginComMfaEConsultasDoInvestidor() throws Exception {
        iniciar();
        try {
            String t = login("ana@orion.example");
            R carteira = chamar("GET", "/api/carteira", t, null);
            assertEquals(200, carteira.status());
            assertTrue(carteira.corpo().contains("\"disponivel\":\"100000.00\""));
            assertTrue(carteira.corpo().contains("VALE3"));
            assertEquals(200, chamar("GET", "/api/cotacoes", t, null).status());
            assertEquals(200, chamar("GET", "/api/cotacoes/PETR4", t, null).status());
            assertEquals(200, chamar("GET", "/api/saldo", t, null).status());
            assertEquals(200, chamar("GET", "/api/ativos", t, null).status());
            assertEquals(200, chamar("POST", "/api/auth/logout", t, null).status());
            assertEquals(401, chamar("GET", "/api/carteira", t, null).status(), "token encerrado");
        } finally { web.parar(); }
    }

    @Test void semTokenOuComTokenInvalidoRetorna401EPerfilErradoRetorna403() throws Exception {
        iniciar();
        try {
            assertEquals(401, chamar("GET", "/api/carteira", null, null).status());
            assertEquals(401, chamar("GET", "/api/carteira", "token-forjado", null).status());
            assertEquals(401, chamar("POST", "/api/ordens", null, ordemJson("COMPRA", "LIMITE", "PETR4", "1", "38.50", Cenario.chave(), false)).status());
            String ana = login("ana@orion.example");
            assertEquals(403, chamar("GET", "/api/admin/parametros", ana, null).status());
            assertEquals(403, chamar("GET", "/api/risco/investidores", ana, null).status());
            String adm = login("admin@orion.example");
            assertEquals(403, chamar("GET", "/api/carteira", adm, null).status());
            assertEquals(200, chamar("GET", "/api/admin/parametros", adm, null).status());
        } finally { web.parar(); }
    }

    @Test void credenciaisInvalidasRecebemMesmaRespostaSemEnumerarUsuarios() throws Exception {
        iniciar();
        try {
            R a = chamar("POST", "/api/auth/login", null, "{\"email\":\"ana@orion.example\",\"senha\":\"errada\"}");
            R b = chamar("POST", "/api/auth/login", null, "{\"email\":\"ninguem@orion.example\",\"senha\":\"errada\"}");
            assertEquals(401, a.status());
            assertEquals(a.corpo(), b.corpo());
        } finally { web.parar(); }
    }

    @Test void enviarOrdemReenvioIdempotenteAlterarECancelarPelaApi() throws Exception {
        iniciar();
        try {
            String t = login("ana@orion.example");
            String chave = Cenario.chave();
            R a = chamar("POST", "/api/ordens", t, ordemJson("COMPRA", "LIMITE", "PETR4", "100", "36.00", chave, false));
            assertEquals(201, a.status());
            assertTrue(a.corpo().contains("\"status\":\"VALIDADA\""));
            R b = chamar("POST", "/api/ordens", t, ordemJson("COMPRA", "LIMITE", "PETR4", "100", "36.00", chave, false));
            assertEquals(200, b.status(), "reenvio com a mesma chave");
            assertTrue(b.corpo().contains("\"duplicada\":true"));
            String id = a.campo("id");

            R alt = chamar("POST", "/api/ordens/" + id + "/alterar", t,
                    "{\"quantidade\":\"50\",\"preco\":\"35.00\",\"chaveIdempotencia\":\"" + Cenario.chave() + "\",\"confirmada\":false}");
            assertEquals(201, alt.status(), alt.corpo());
            assertTrue(alt.corpo().contains("\"substituida\""));
            assertTrue(alt.corpo().contains("\"status\":\"CANCELADA\""));

            R lista = chamar("GET", "/api/ordens", t, null);
            assertTrue(lista.corpo().contains("\"status\":\"VALIDADA\""));
            String idNova = Pattern.compile("\"nova\":\\{\"id\":\"([^\"]+)\"").matcher(alt.corpo()).results().findFirst().orElseThrow().group(1);
            R canc = chamar("POST", "/api/ordens/" + idNova + "/cancelar", t, null);
            assertEquals(200, canc.status());
            assertTrue(canc.corpo().contains("\"status\":\"CANCELADA\""));
            assertEquals(422, chamar("POST", "/api/ordens/" + idNova + "/alterar", t,
                    "{\"quantidade\":\"5\",\"preco\":\"35.00\",\"chaveIdempotencia\":\"" + Cenario.chave() + "\"}").status(), "ordem cancelada não é alterável");
        } finally { web.parar(); }
    }

    @Test void ordemAMercadoEExecucaoAtravesDoWorker() throws Exception {
        iniciar();
        try {
            String t = login("ana@orion.example");
            R a = chamar("POST", "/api/ordens", t, ordemJson("COMPRA", "MERCADO", "PETR4", "100", null, Cenario.chave(), false));
            assertEquals(201, a.status(), a.corpo());
            assertTrue(a.corpo().contains("\"tipo\":\"MERCADO\"") && a.corpo().contains("\"precoLimite\":\"42.35\""));
            c.app.processador.ciclo();
            R o = chamar("GET", "/api/ordens/" + a.campo("id"), t, null);
            assertTrue(o.corpo().contains("\"status\":\"EXECUTADA\""));
            assertTrue(chamar("GET", "/api/ordens/" + a.campo("id") + "/execucoes", t, null).corpo().contains("\"preco\":\"38.50\""));
            assertEquals(400, chamar("POST", "/api/ordens", t, ordemJson("COMPRA", "MERCADO", "PETR4", "1", "38.50", Cenario.chave(), false)).status());
        } finally { web.parar(); }
    }

    @Test void ordemDeAltoValorRetorna409ComCodigoEAceitaConfirmacao() throws Exception {
        iniciar();
        try {
            String t = login("ana@orion.example");
            String chave = Cenario.chave();
            R a = chamar("POST", "/api/ordens", t, ordemJson("COMPRA", "LIMITE", "PETR4", "700", "38.50", chave, false));
            assertEquals(409, a.status());
            assertTrue(a.corpo().contains("CONFIRMACAO_NECESSARIA"));
            assertEquals(201, chamar("POST", "/api/ordens", t, ordemJson("COMPRA", "LIMITE", "PETR4", "700", "38.50", chave, true)).status());
        } finally { web.parar(); }
    }

    @Test void regraDeNegocioViolada_RetornaOrdemRejeitadaRegistrada() throws Exception {
        iniciar();
        try {
            String t = login("ana@orion.example");
            R r = chamar("POST", "/api/ordens", t, ordemJson("VENDA", "LIMITE", "PETR4", "10", "38.50", Cenario.chave(), false));
            assertEquals(201, r.status());
            assertTrue(r.corpo().contains("\"status\":\"REJEITADA\"") && r.corpo().contains("POSICAO_INSUFICIENTE"));
        } finally { web.parar(); }
    }

    @Test void entradaMaliciosaOuMalFormadaRetorna4xxSemVazarDetalhes() throws Exception {
        iniciar();
        try {
            String t = login("ana@orion.example");
            String k = Cenario.chave();
            for (String ruim : new String[]{"PETR4'; DROP TABLE ordens;--", "<script>alert(1)</script>", "../../etc/passwd", "PETR4\\u0000"}) {
                R r = chamar("POST", "/api/ordens", t, ordemJson("COMPRA", "LIMITE", ruim, "1", "38.50", Cenario.chave(), false));
                assertEquals(400, r.status(), ruim);
                assertFalse(r.corpo().contains("Exception") || r.corpo().contains("at br.orion"), "sem stack trace");
                assertFalse(r.corpo().contains("<script>"), "entrada nunca é ecoada sem escape");
            }
            String[] json = {"{", "[]", "{\"lado\":{\"a\":1}}", "{\"lado\":[1]}", "{\"lado\":\"COMPRA\",\"lado\":\"VENDA\"}", "{\"admin\":true}",
                    "{\"lado\":\"COMPRA\"} lixo", "texto", "{\"lado\":\"COMPRA\",}", "{\"lado\":'COMPRA'}"};
            for (String j : json) assertEquals(400, chamar("POST", "/api/ordens", t, j).status(), "deveria recusar: " + j);
            assertEquals(0, c.app.ordens.historico(t).size(), "nada foi criado");
            assertTrue(k.length() > 0);
        } finally { web.parar(); }
    }

    @Test void limitesDeProtocoloCorpoTipoDeConteudoMetodoECaminho() throws Exception {
        iniciar();
        try {
            String t = login("ana@orion.example");
            assertEquals(413, chamar("POST", "/api/ordens", t, "{\"lado\":\"" + "x".repeat(20_000) + "\"}").status());
            HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create(base + "/api/ordens")).header("Authorization", "Bearer " + t)
                    .header("Content-Type", "text/plain").POST(HttpRequest.BodyPublishers.ofString("{}")).build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(415, r.statusCode());
            assertEquals(405, chamar("PUT", "/api/ordens", t, "{}").status());
            assertEquals(405, chamar("DELETE", "/api/ordens/" + UUID.randomUUID(), t, null).status());
            assertEquals(404, chamar("GET", "/api/nao-existe", t, null).status());
            assertEquals(400, chamar("GET", "/api/ordens/..%2f..%2fetc", t, null).status());
        } finally { web.parar(); }
    }

    @Test void investidorNaoAcessaOrdemDeOutroPelaApi() throws Exception {
        iniciar();
        try {
            String ana = login("ana@orion.example"), bruno = login("bruno@orion.example");
            String id = chamar("POST", "/api/ordens", ana, ordemJson("COMPRA", "LIMITE", "PETR4", "10", "38.50", Cenario.chave(), false)).campo("id");
            assertEquals(404, chamar("GET", "/api/ordens/" + id, bruno, null).status());
            assertEquals(404, chamar("POST", "/api/ordens/" + id + "/cancelar", bruno, null).status());
            assertEquals(404, chamar("POST", "/api/ordens/" + id + "/alterar", bruno,
                    "{\"quantidade\":\"5\",\"preco\":\"38.00\",\"chaveIdempotencia\":\"" + Cenario.chave() + "\"}").status());
            assertEquals(200, chamar("GET", "/api/ordens/" + id, ana, null).status());
        } finally { web.parar(); }
    }

    @Test void funcoesAdministrativasEDoAnalistaPelaApi() throws Exception {
        iniciar();
        try {
            String adm = login("admin@orion.example"), ris = login("risco@orion.example");
            R inv = chamar("POST", "/api/admin/investidores", adm, "{\"nome\":\"Carla Nova\",\"email\":\"carla@orion.example\",\"senha\":\"Aa1!" + UUID.randomUUID() + "\"}");
            assertEquals(201, inv.status(), inv.corpo());
            assertEquals(409, chamar("POST", "/api/admin/investidores", adm, "{\"nome\":\"Carla Nova\",\"email\":\"carla@orion.example\",\"senha\":\"Aa1!" + UUID.randomUUID() + "\"}").status());
            assertEquals(400, chamar("POST", "/api/admin/investidores", adm, "{\"nome\":\"X Y\",\"email\":\"x@orion.example\",\"senha\":\"fraca\"}").status());
            assertEquals(200, chamar("POST", "/api/admin/depositos", adm, "{\"investidorId\":\"" + inv.campo("id") + "\",\"valor\":\"500\"}").status());
            assertEquals(200, chamar("POST", "/api/admin/parametros", adm, "{\"chave\":\"ordem.faixa_preco_percentual\",\"valor\":\"5\"}").status());
            assertEquals(400, chamar("POST", "/api/admin/parametros", adm, "{\"chave\":\"qualquer.coisa\",\"valor\":\"1\"}").status());
            assertEquals(200, chamar("POST", "/api/admin/ativos/PETR4/status", adm, "{\"status\":\"SUSPENSO\"}").status());
            R v = chamar("GET", "/api/admin/auditoria/verificacao", adm, null);
            assertTrue(v.corpo().contains("\"integra\":true"));
            assertEquals(200, chamar("GET", "/api/admin/auditoria?limite=5", adm, null).status());
            assertEquals(400, chamar("GET", "/api/admin/auditoria?limite=abc", adm, null).status());

            assertEquals(200, chamar("GET", "/api/risco/investidores", ris, null).status());
            String anaId = c.anaId().toString();
            assertEquals(200, chamar("POST", "/api/risco/limites/" + anaId, ris,
                    "{\"valorMaximoPorOrdem\":\"1000\",\"limiteDiario\":\"5000\",\"valorConfirmacao\":\"500\"}").status());
            assertTrue(chamar("GET", "/api/risco/limites/" + anaId, ris, null).corpo().contains("\"valorMaximoPorOrdem\":\"1000.00\""));
            assertEquals(200, chamar("GET", "/api/risco/ordens-rejeitadas", ris, null).status());
        } finally { web.parar(); }
    }

    @Test void frontEndEstaticoSoServeArquivosPermitidos() throws Exception {
        iniciar();
        try {
            R idx = chamar("GET", "/", null, null);
            assertEquals(200, idx.status());
            assertTrue(idx.corpo().contains("SentinelTrade"));
            assertFalse(idx.corpo().contains("<script>"), "nenhum script inline (a CSP proíbe)");
            assertTrue(idx.cab().firstValue("Content-Type").orElse("").startsWith("text/html"));
            R js = chamar("GET", "/app.js", null, null);
            assertEquals(200, js.status());
            assertTrue(js.cab().firstValue("Content-Type").orElse("").startsWith("text/javascript"));
            assertFalse(js.corpo().contains("innerHTML"), "o front-end nunca injeta dados como HTML");
            assertEquals(200, chamar("GET", "/estilo.css", null, null).status());
            for (String ruim : new String[]{"/../pom.xml", "/Main.class", "/web/index.html", "/.env", "/%2e%2e/etc/passwd", "/app.js/../../x"})
                assertEquals(404, chamar("GET", ruim, null, null).status(), ruim);
            assertEquals(405, chamar("POST", "/", null, "{}").status());
        } finally { web.parar(); }
    }

    @Test void autenticadorSimuladoSoExisteQuandoHabilitado() throws Exception {
        iniciar();
        try {
            assertTrue(chamar("GET", "/api/config", null, null).corpo().contains("\"autenticadorSimulado\":true"));
            R r = chamar("GET", "/api/demo/autenticador?email=ana@orion.example", null, null);
            assertEquals(200, r.status());
            assertTrue(r.campo("codigo").matches("[0-9]{6}"));
            assertEquals(404, chamar("GET", "/api/demo/autenticador?email=ninguem@orion.example", null, null).status());
        } finally { web.parar(); }
        ServidorWeb producao = new ServidorWeb(c.app, false);
        base = "http://127.0.0.1:" + producao.iniciar("127.0.0.1", 0);
        try {
            assertEquals(404, chamar("GET", "/api/demo/autenticador?email=ana@orion.example", null, null).status());
            assertTrue(chamar("GET", "/api/config", null, null).corpo().contains("\"autenticadorSimulado\":false"));
        } finally { producao.parar(); }
    }
}
