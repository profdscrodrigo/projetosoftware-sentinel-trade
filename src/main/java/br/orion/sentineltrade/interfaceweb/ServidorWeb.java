package br.orion.sentineltrade.interfaceweb;

import br.orion.sentineltrade.Aplicacao;
import br.orion.sentineltrade.DadosDemonstracao;
import br.orion.sentineltrade.aplicacao.servico.CarteiraService.ItemCarteira;
import br.orion.sentineltrade.aplicacao.servico.CarteiraService.ResumoCarteira;
import br.orion.sentineltrade.aplicacao.servico.CotacaoService.CotacaoConsultada;
import br.orion.sentineltrade.aplicacao.servico.Log;
import br.orion.sentineltrade.aplicacao.servico.OrdemService.ResultadoAlteracao;
import br.orion.sentineltrade.aplicacao.servico.OrdemService.ResultadoEnvio;
import br.orion.sentineltrade.aplicacao.servico.Sanitizador;
import br.orion.sentineltrade.aplicacao.servico.SolicitacaoOrdem;
import br.orion.sentineltrade.aplicacao.servico.Validacoes;
import br.orion.sentineltrade.dominio.*;
import br.orion.sentineltrade.dominio.Excecoes.*;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Interface web: API REST (JSON) + front-end estático, sobre o servidor HTTP embutido do JDK.
 * É uma camada FINA — como o console: só traduz HTTP ↔ serviços de aplicação. Toda regra de negócio, validação e
 * autorização continuam nos serviços (o servidor NÃO confia no navegador).
 *
 * Controles de segurança desta camada:
 *  - autenticação por token em {@code Authorization: Bearer} (não é cookie ⇒ não sujeita a CSRF);
 *  - corpo limitado a 16 KB, Content-Type exigido, apenas objetos JSON planos com campos em allowlist;
 *  - métodos limitados a GET/POST; sem CORS (mesma origem);
 *  - CSP restritiva, nosniff, no-store, frame-ancestors 'none';
 *  - mensagens de erro seguras; erro inesperado devolve só uma referência (detalhe vai ao log);
 *  - por padrão escuta somente em 127.0.0.1.
 */
public final class ServidorWeb {
    private static final int MAX_CORPO = 16 * 1024;
    private static final Set<String> ARQUIVOS = Set.of("index.html", "app.js", "estilo.css");
    private static final String CSP = "default-src 'none'; script-src 'self'; style-src 'self'; connect-src 'self'; img-src 'self' data:; "
            + "base-uri 'none'; form-action 'none'; frame-ancestors 'none'";

    private final Aplicacao app;
    private final boolean autenticadorSimulado;
    private HttpServer servidor;
    private ExecutorService pool;

    /** @param autenticadorSimulado expõe /api/demo/autenticador (código TOTP do momento) — SOMENTE para demonstração. */
    public ServidorWeb(Aplicacao app, boolean autenticadorSimulado) { this.app = app; this.autenticadorSimulado = autenticadorSimulado; }

    /** @return a porta efetivamente usada (use 0 para uma porta livre). */
    public int iniciar(String endereco, int porta) throws IOException {
        System.setProperty("sun.net.httpserver.maxReqTime", "30");           // limita o tempo de leitura da requisição
        System.setProperty("sun.net.httpserver.maxRspTime", "60");
        servidor = HttpServer.create(new InetSocketAddress(endereco, porta), 0);
        pool = Executors.newFixedThreadPool(8, r -> { Thread t = new Thread(r, "http"); t.setDaemon(true); return t; });
        servidor.setExecutor(pool);
        servidor.createContext("/api/", this::api);
        servidor.createContext("/", this::estatico);
        servidor.start();
        return servidor.getAddress().getPort();
    }

    public void parar() { if (servidor != null) servidor.stop(0); if (pool != null) pool.shutdownNow(); }

    // ================================================================================== estático
    private void estatico(HttpExchange x) throws IOException {
        try {
            if (!x.getRequestMethod().equals("GET")) { enviar(x, 405, "text/plain; charset=utf-8", "Método não permitido".getBytes(StandardCharsets.UTF_8)); return; }
            String caminho = x.getRequestURI().getPath();
            String nome = caminho.equals("/") ? "index.html" : caminho.substring(1);
            if (!ARQUIVOS.contains(nome)) { enviar(x, 404, "text/plain; charset=utf-8", "Não encontrado".getBytes(StandardCharsets.UTF_8)); return; }
            try (InputStream in = ServidorWeb.class.getResourceAsStream("/web/" + nome)) {
                if (in == null) { enviar(x, 404, "text/plain; charset=utf-8", "Não encontrado".getBytes(StandardCharsets.UTF_8)); return; }
                String tipo = nome.endsWith(".html") ? "text/html; charset=utf-8" : nome.endsWith(".js") ? "text/javascript; charset=utf-8" : "text/css; charset=utf-8";
                enviar(x, 200, tipo, in.readAllBytes());
            }
        } finally { x.close(); }
    }

    // ================================================================================== API
    private void api(HttpExchange x) throws IOException {
        int status;
        Object corpo;
        try {
            String metodo = x.getRequestMethod();
            if (!metodo.equals("GET") && !metodo.equals("POST")) throw new HttpErro(405, "METODO_NAO_PERMITIDO", "Método não permitido");
            String[] p = caminho(x);
            String token = token(x);
            Map<String, Object> entrada = Map.of();
            if (metodo.equals("POST")) entrada = corpo(x);
            Resposta r = rotear(metodo, p, token, entrada, query(x));
            status = r.status;
            corpo = r.corpo;
        } catch (HttpErro e) {
            status = e.status; corpo = erro(e.codigo, e.getMessage());
        } catch (ConfirmacaoNecessariaException e) {
            status = 409; corpo = erro("CONFIRMACAO_NECESSARIA", e.getMessage());
        } catch (RegraNegocioException e) {
            status = 422; corpo = erro(e.codigo().name(), e.getMessage());
        } catch (ValidacaoException e) {
            status = 400; corpo = erro("VALIDACAO", e.getMessage());
        } catch (AutenticacaoException e) {
            status = 401; corpo = erro("NAO_AUTENTICADO", e.getMessage());
        } catch (AcessoNegadoException e) {
            status = 403; corpo = erro("ACESSO_NEGADO", e.getMessage());
        } catch (NaoEncontradoException e) {
            status = 404; corpo = erro("NAO_ENCONTRADO", e.getMessage());
        } catch (ConflitoException e) {
            status = 409; corpo = erro("CONFLITO", e.getMessage());
        } catch (LimiteTaxaException e) {
            status = 429; corpo = erro("MUITAS_REQUISICOES", e.getMessage());
        } catch (IntegracaoIndisponivelException | TimeoutIntegracaoException e) {
            status = 503; corpo = erro("INTEGRACAO_INDISPONIVEL", e.getMessage());
        } catch (RuntimeException e) {                                           // erro inesperado: só uma referência ao cliente
            String ref = UUID.randomUUID().toString().substring(0, 8);
            Log.erro("Erro interno na API ref=" + ref, e);
            status = 500; corpo = erro("ERRO_INTERNO", "Erro interno. Informe a referência " + ref + " ao suporte.");
        }
        try { enviar(x, status, "application/json; charset=utf-8", Json.escrever(corpo).getBytes(StandardCharsets.UTF_8)); }
        finally { x.close(); }
    }

    private record Resposta(int status, Object corpo) {}
    private static Resposta ok(Object o) { return new Resposta(200, o); }
    private static Resposta criado(Object o) { return new Resposta(201, o); }

    private Resposta rotear(String m, String[] p, String token, Map<String, Object> in, Map<String, String> q) {
        String a = p.length > 0 ? p[0] : "";
        // ---- público
        if (m.equals("GET") && is(p, "saude")) return ok(mapa("status", "ok", "circuitoCorretora", app.corretoraResiliente.estado()));
        if (m.equals("GET") && is(p, "config")) return ok(mapa("autenticadorSimulado", autenticadorSimulado));
        if (m.equals("GET") && is(p, "demo", "autenticador")) {
            if (!autenticadorSimulado) throw new HttpErro(404, "NAO_ENCONTRADO", "Recurso inexistente");
            String email = Validacoes.email(q.get("email"));
            if (app.persistencia.usuarios().buscarPorEmail(email).isEmpty()) throw new HttpErro(404, "NAO_ENCONTRADO", "Recurso inexistente");
            return ok(mapa("codigo", DadosDemonstracao.codigoMfaAtual(app, email)));
        }
        // ---- autenticação
        if (m.equals("POST") && is(p, "auth", "login")) {
            campos(in, "email", "senha");
            return ok(mapa("tokenPendente", app.auth.iniciarLogin(Json.texto(in, "email"), Json.texto(in, "senha"))));
        }
        if (m.equals("POST") && is(p, "auth", "mfa")) {
            campos(in, "tokenPendente", "codigo");
            String tk = app.auth.confirmarMfa(Json.texto(in, "tokenPendente"), Json.texto(in, "codigo"));
            Sessao s = app.auth.validarToken(tk);
            return ok(mapa("token", tk, "papel", s.papel(), "expiraEm", s.expiraEm()));
        }
        if (m.equals("POST") && is(p, "auth", "logout")) { app.auth.encerrar(token == null ? "" : token); return ok(mapa("ok", true)); }

        // ---- consultas comuns (qualquer usuário autenticado)
        if (m.equals("GET") && is(p, "ativos")) {
            List<Object> l = new ArrayList<>();
            for (Ativo at : app.admin.listarAtivos(token)) l.add(dto(at));
            return ok(mapa("ativos", l));
        }
        if (m.equals("GET") && is(p, "cotacoes")) {
            List<Object> l = new ArrayList<>();
            for (CotacaoConsultada c : app.cotacoes.listar(token)) l.add(dto(c));
            return ok(mapa("cotacoes", l));
        }
        if (m.equals("GET") && p.length == 2 && a.equals("cotacoes")) return ok(dto(app.cotacoes.consultar(token, p[1])));

        // ---- investidor
        if (m.equals("GET") && is(p, "saldo")) { var s = app.carteira.consultarSaldo(token); return ok(mapa("disponivel", s.disponivel(), "bloqueado", s.bloqueado())); }
        if (m.equals("GET") && is(p, "carteira")) return ok(dto(app.carteira.consultarCarteira(token)));
        if (m.equals("GET") && is(p, "notificacoes")) {
            List<Object> l = new ArrayList<>();
            for (Notificacao n : app.notificacoes.minhasNotificacoes(token))
                l.add(mapa("tipo", n.tipo(), "mensagem", n.mensagem(), "criadaEm", n.criadaEm()));
            return ok(mapa("notificacoes", l));
        }
        if (a.equals("ordens")) return ordens(m, p, token, in);

        // ---- administrador
        if (a.equals("admin")) return admin(m, p, token, in, q);
        // ---- analista de risco
        if (a.equals("risco")) return risco(m, p, token, in);
        throw new HttpErro(404, "NAO_ENCONTRADO", "Recurso inexistente");
    }

    private Resposta ordens(String m, String[] p, String token, Map<String, Object> in) {
        if (m.equals("GET") && p.length == 1) {
            List<Object> l = new ArrayList<>();
            for (Ordem o : app.ordens.historico(token)) l.add(dto(o));
            return ok(mapa("ordens", l));
        }
        if (m.equals("POST") && p.length == 1) {
            campos(in, "lado", "tipo", "ticker", "quantidade", "preco", "chaveIdempotencia", "confirmada");
            ResultadoEnvio r = app.ordens.enviarOrdem(token, new SolicitacaoOrdem(Json.texto(in, "lado"), Json.texto(in, "tipo"),
                    Json.texto(in, "ticker"), Json.texto(in, "quantidade"), Json.texto(in, "preco"),
                    Json.texto(in, "chaveIdempotencia"), Json.booleano(in, "confirmada")));
            Object corpo = mapa("ordem", dto(r.ordem()), "duplicada", r.duplicada());
            return r.duplicada() ? ok(corpo) : criado(corpo);
        }
        if (m.equals("GET") && p.length == 2) return ok(dto(app.ordens.consultarOrdem(token, p[1])));
        if (m.equals("GET") && p.length == 3 && p[2].equals("execucoes")) {
            List<Object> l = new ArrayList<>();
            for (Execucao e : app.ordens.execucoesDaOrdem(token, p[1]))
                l.add(mapa("idExecucao", e.idExecucao(), "quantidade", e.quantidade(), "preco", e.preco(), "executadaEm", e.executadaEm()));
            return ok(mapa("execucoes", l));
        }
        if (m.equals("POST") && p.length == 3 && p[2].equals("cancelar")) return ok(mapa("ordem", dto(app.ordens.cancelarOrdem(token, p[1]))));
        if (m.equals("POST") && p.length == 3 && p[2].equals("alterar")) {
            campos(in, "quantidade", "preco", "chaveIdempotencia", "confirmada");
            ResultadoAlteracao r = app.ordens.alterarOrdem(token, p[1], Json.texto(in, "quantidade"), Json.texto(in, "preco"),
                    Json.texto(in, "chaveIdempotencia"), Json.booleano(in, "confirmada"));
            Object corpo = mapa("substituida", dto(r.substituida()), "nova", dto(r.nova()), "duplicada", r.duplicada());
            return r.duplicada() ? ok(corpo) : criado(corpo);
        }
        throw new HttpErro(404, "NAO_ENCONTRADO", "Recurso inexistente");
    }

    private Resposta admin(String m, String[] p, String token, Map<String, Object> in, Map<String, String> q) {
        if (m.equals("POST") && is(p, "admin", "investidores")) {
            campos(in, "nome", "email", "senha");
            return criado(mapa("id", app.admin.cadastrarInvestidor(token, Json.texto(in, "nome"), Json.texto(in, "email"), Json.texto(in, "senha"))));
        }
        if (m.equals("POST") && is(p, "admin", "depositos")) {
            campos(in, "investidorId", "valor");
            app.admin.depositarSimulado(token, Json.texto(in, "investidorId"), Json.texto(in, "valor"));
            return ok(mapa("ok", true));
        }
        if (m.equals("POST") && is(p, "admin", "ativos")) {
            campos(in, "ticker", "nome", "tipo");
            app.admin.cadastrarAtivo(token, Json.texto(in, "ticker"), Json.texto(in, "nome"), Json.texto(in, "tipo"));
            return criado(mapa("ok", true));
        }
        if (m.equals("POST") && p.length == 4 && p[1].equals("ativos") && p[3].equals("status")) {
            campos(in, "status");
            app.admin.alterarStatusAtivo(token, p[2], Json.texto(in, "status"));
            return ok(mapa("ok", true));
        }
        if (m.equals("GET") && is(p, "admin", "parametros")) return ok(mapa("parametros", new TreeMap<>(app.admin.listarParametros(token))));
        if (m.equals("POST") && is(p, "admin", "parametros")) {
            campos(in, "chave", "valor");
            app.admin.definirParametro(token, Json.texto(in, "chave"), Json.texto(in, "valor"));
            return ok(mapa("ok", true));
        }
        if (m.equals("GET") && is(p, "admin", "auditoria")) {
            List<Object> l = new ArrayList<>();
            for (EventoAuditoria e : app.admin.consultarAuditoria(token, inteiro(q.get("apos"), 0), inteiro(q.get("limite"), 50)))
                l.add(mapa("seq", e.seq(), "instante", e.instante(), "tipo", e.tipo(), "entidade", e.entidade(), "entidadeId", e.entidadeId(),
                        "detalhes", e.detalhes(), "hash", e.hash()));
            return ok(mapa("eventos", l));
        }
        if (m.equals("GET") && is(p, "admin", "auditoria", "verificacao")) {
            var v = app.admin.verificarIntegridadeAuditoria(token);
            return ok(mapa("integra", v.integra(), "seqProblema", v.seqProblema(), "mensagem", v.motivo()));
        }
        throw new HttpErro(404, "NAO_ENCONTRADO", "Recurso inexistente");
    }

    private Resposta risco(String m, String[] p, String token, Map<String, Object> in) {
        if (m.equals("GET") && is(p, "risco", "investidores")) {
            List<Object> l = new ArrayList<>();
            for (Usuario u : app.risco.listarInvestidores(token)) l.add(mapa("id", u.id(), "nome", u.nome(), "email", u.email()));
            return ok(mapa("investidores", l));
        }
        if (m.equals("GET") && p.length == 3 && p[1].equals("limites")) return ok(dto(app.risco.consultarLimites(token, p[2])));
        if (m.equals("POST") && p.length == 3 && p[1].equals("limites")) {
            campos(in, "valorMaximoPorOrdem", "limiteDiario", "valorConfirmacao");
            app.risco.definirLimites(token, p[2], Json.texto(in, "valorMaximoPorOrdem"), Json.texto(in, "limiteDiario"), Json.texto(in, "valorConfirmacao"));
            return ok(mapa("ok", true));
        }
        if (m.equals("GET") && is(p, "risco", "ordens-rejeitadas")) {
            List<Object> l = new ArrayList<>();
            for (Ordem o : app.risco.ordensRejeitadas(token)) l.add(dto(o));
            return ok(mapa("ordens", l));
        }
        throw new HttpErro(404, "NAO_ENCONTRADO", "Recurso inexistente");
    }

    // ================================================================================== apresentação (DTOs)
    private static Map<String, Object> dto(Ordem o) {
        return mapa("id", o.id(), "lado", o.lado(), "tipo", o.tipo(), "ticker", o.ticker(), "quantidade", o.quantidade(),
                "precoLimite", o.precoLimite(), "status", o.status(), "quantidadeExecutada", o.quantidadeExecutada(),
                "valorExecutado", o.valorExecutado(), "motivoRejeicao", o.motivoRejeicao(),
                "motivoDescricao", o.motivoRejeicao() == null ? null : o.motivoRejeicao().descricao(),
                "criadaEm", o.criadaEm(), "atualizadaEm", o.atualizadaEm(), "substituiOrdemId", o.substituiOrdemId(),
                "cancelavel", !o.status().terminal(), "alteravel", o.alteravel());
    }
    private static Map<String, Object> dto(CotacaoConsultada c) {
        return mapa("ticker", c.cotacao().ticker(), "preco", c.cotacao().preco(), "variacaoPercentual", c.cotacao().variacaoPercentual(),
                "instante", c.cotacao().instante(), "desatualizada", c.desatualizada());
    }
    private static Map<String, Object> dto(Ativo a) { return mapa("ticker", a.ticker(), "nome", a.nome(), "tipo", a.tipo(), "status", a.status()); }
    private static Map<String, Object> dto(LimiteRisco l) {
        return mapa("valorMaximoPorOrdem", l.valorMaximoPorOrdem(), "limiteDiario", l.limiteDiario(), "valorConfirmacao", l.valorConfirmacao());
    }
    private static Map<String, Object> dto(ResumoCarteira r) {
        List<Object> itens = new ArrayList<>();
        for (ItemCarteira i : r.itens())
            itens.add(mapa("ticker", i.ticker(), "quantidade", i.quantidade(), "quantidadeBloqueada", i.quantidadeBloqueada(), "precoMedio", i.precoMedio(),
                    "cotacaoAtual", i.cotacaoAtual(), "cotacaoDesatualizada", i.cotacaoDesatualizada(), "valorEstimado", i.valorEstimado()));
        return mapa("disponivel", r.saldo().disponivel(), "bloqueado", r.saldo().bloqueado(), "itens", itens, "patrimonioEstimado", r.patrimonioEstimado());
    }

    // ================================================================================== utilitários HTTP
    private static final class HttpErro extends RuntimeException {
        final int status; final String codigo;
        HttpErro(int status, String codigo, String msg) { super(msg); this.status = status; this.codigo = codigo; }
    }

    private static Map<String, Object> mapa(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }
    private static Map<String, Object> erro(String codigo, String mensagem) {
        return mapa("erro", mapa("codigo", codigo, "mensagem", Sanitizador.paraLog(mensagem, 300)));
    }
    private static boolean is(String[] p, String... esperado) { return Arrays.equals(p, esperado); }

    private static void campos(Map<String, Object> in, String... permitidos) {
        Set<String> ok = Set.of(permitidos);
        for (String k : in.keySet()) if (!ok.contains(k)) throw new ValidacaoException("Campo não permitido: " + Sanitizador.paraLog(k, 30));
    }

    private static int inteiro(String v, int padrao) {
        if (v == null || v.isBlank()) return padrao;
        if (!v.matches("[0-9]{1,9}")) throw new ValidacaoException("Parâmetro numérico inválido");
        return Integer.parseInt(v);
    }

    private static String[] caminho(HttpExchange x) {
        String raw = x.getRequestURI().getRawPath();
        if (raw.contains("%") || raw.contains("..") || raw.contains("//")) throw new HttpErro(400, "VALIDACAO", "Caminho inválido");
        String resto = raw.substring("/api/".length());
        if (resto.endsWith("/")) resto = resto.substring(0, resto.length() - 1);
        if (resto.isEmpty()) return new String[0];
        String[] p = resto.split("/", -1);
        if (p.length > 5) throw new HttpErro(404, "NAO_ENCONTRADO", "Recurso inexistente");
        for (String s : p) if (s.isEmpty() || s.length() > 64) throw new HttpErro(400, "VALIDACAO", "Caminho inválido");
        return p;
    }

    private static Map<String, String> query(HttpExchange x) {
        Map<String, String> m = new HashMap<>();
        String q = x.getRequestURI().getRawQuery();
        if (q == null || q.length() > 256) return m;
        for (String par : q.split("&")) {
            int i = par.indexOf('=');
            if (i > 0 && par.length() < 100) m.put(par.substring(0, i), java.net.URLDecoder.decode(par.substring(i + 1), StandardCharsets.UTF_8));
        }
        return m;
    }

    private static String token(HttpExchange x) {
        String h = x.getRequestHeaders().getFirst("Authorization");
        if (h == null || !h.startsWith("Bearer ")) return null;
        String t = h.substring(7).trim();
        return t.isEmpty() || t.length() > 128 ? null : t;
    }

    private static Map<String, Object> corpo(HttpExchange x) throws IOException {
        byte[] bytes;
        try (InputStream in = x.getRequestBody()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[2048];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                if (out.size() > MAX_CORPO) throw new HttpErro(413, "CORPO_GRANDE_DEMAIS", "Corpo da requisição acima do limite de 16 KB");
            }
            bytes = out.toByteArray();
        }
        if (bytes.length == 0) return Map.of();                               // POST sem corpo (ex.: cancelar, logout)
        String tipo = x.getRequestHeaders().getFirst("Content-Type");
        if (tipo == null || !tipo.toLowerCase(Locale.ROOT).startsWith("application/json"))
            throw new HttpErro(415, "TIPO_NAO_SUPORTADO", "Use Content-Type: application/json");
        return Json.lerObjetoPlano(new String(bytes, StandardCharsets.UTF_8), Set.of(
                "email", "senha", "tokenPendente", "codigo", "lado", "tipo", "ticker", "quantidade", "preco", "chaveIdempotencia", "confirmada",
                "nome", "investidorId", "valor", "status", "chave", "valorMaximoPorOrdem", "limiteDiario", "valorConfirmacao"));
    }

    private static void enviar(HttpExchange x, int status, String tipo, byte[] corpo) throws IOException {
        var h = x.getResponseHeaders();
        h.set("Content-Type", tipo);
        h.set("Cache-Control", "no-store");
        h.set("X-Content-Type-Options", "nosniff");
        h.set("Referrer-Policy", "no-referrer");
        h.set("Content-Security-Policy", CSP);
        h.set("Cross-Origin-Resource-Policy", "same-origin");
        x.sendResponseHeaders(status, corpo.length == 0 ? -1 : corpo.length);
        if (corpo.length > 0) x.getResponseBody().write(corpo);
    }
}
