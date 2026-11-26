package br.orion.sentineltrade;

import br.orion.sentineltrade.aplicacao.servico.Validacoes;
import br.orion.sentineltrade.dominio.*;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Carga de dados SIMULADOS para demonstração (ativos, cotações, usuários, saldo e carteira inicial).
 * As senhas NUNCA ficam no repositório: vêm de variáveis de ambiente (DEMO_PASSWORD_*) ou são geradas
 * aleatoriamente a cada execução. Os e-mails usam o domínio reservado .example (não são dados pessoais).
 */
public final class DadosDemonstracao {
    public record Credencial(String email, String senha, Papel papel, UUID usuarioId) {}

    private static final String CARACTERES = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%&*?";
    private static final SecureRandom RND = new SecureRandom();

    private DadosDemonstracao() {}

    private static final String[][] ATIVOS = {{"PETR4", "Petrobras PN", "ACAO", "38.50", "38.10"}, {"VALE3", "Vale ON", "ACAO", "62.40", "62.90"},
            {"ITUB4", "Itau Unibanco PN", "ACAO", "33.20", "33.00"}, {"BOVA11", "iShares Ibovespa", "ETF", "128.70", "128.00"},
            {"MXRF11", "Maxi Renda FII", "FII", "10.35", "10.30"}};

    /** Cotações simuladas iniciais (o provedor simulado não persiste nada). */
    public static void definirCotacoesIniciais(Aplicacao app) {
        for (String[] a : ATIVOS) app.cotacoesSimuladas.definir(a[0], a[3], a[4]);
    }

    public static List<Credencial> carregar(Aplicacao app, Map<String, String> env) {
        definirCotacoesIniciais(app);
        for (String[] a : ATIVOS) {
            if (app.persistencia.ativos().buscar(Ticker.de(a[0])).isEmpty())
                app.persistencia.ativos().salvar(new Ativo(Ticker.de(a[0]), a[1], TipoAtivo.valueOf(a[2]), StatusAtivo.ATIVO, 0));
        }

        List<Credencial> creds = new ArrayList<>();
        creds.add(criar(app, env, "DEMO_PASSWORD_ADMIN", "Administrador Demo", "admin@orion.example", Papel.ADMINISTRADOR));
        creds.add(criar(app, env, "DEMO_PASSWORD_RISCO", "Analista de Risco Demo", "risco@orion.example", Papel.ANALISTA_RISCO));
        Credencial ana = criar(app, env, "DEMO_PASSWORD_INVESTIDOR_A", "Ana Investidora", "ana@orion.example", Papel.INVESTIDOR);
        Credencial bruno = criar(app, env, "DEMO_PASSWORD_INVESTIDOR_B", "Bruno Investidor", "bruno@orion.example", Papel.INVESTIDOR);
        creds.add(ana);
        creds.add(bruno);

        // saldo inicial simulado e carteira inicial
        for (Credencial c : List.of(ana, bruno)) {
            var conta = app.persistencia.contas().buscarPorInvestidor(c.usuarioId()).orElseThrow();
            conta.depositar(Dinheiro.de("100000"));
            app.persistencia.contas().atualizar(conta);
        }
        app.persistencia.posicoes().salvar(new Posicao(ana.usuarioId(), Ticker.de("VALE3"), 200, 0, Dinheiro.de("12000.00"), 0));
        app.persistencia.posicoes().salvar(new Posicao(bruno.usuarioId(), Ticker.de("PETR4"), 500, 0, Dinheiro.de("18000.00"), 0));
        return creds;
    }

    private static Credencial criar(Aplicacao app, Map<String, String> env, String variavel, String nome, String email, Papel papel) {
        String senha = env.get(variavel);
        if (senha == null || senha.isBlank()) senha = senhaAleatoria(email);
        Usuario u = app.admin.provisionar(null, nome, email, senha, papel);
        return new Credencial(email, senha, papel, u.id());
    }

    static String senhaAleatoria(String email) {
        while (true) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 16; i++) sb.append(CARACTERES.charAt(RND.nextInt(CARACTERES.length())));
            try { Validacoes.senha(sb.toString(), email); return sb.toString(); }
            catch (Excecoes.ValidacaoException e) { /* tenta de novo até cumprir a política */ }
        }
    }

    /** "Aplicativo autenticador" simulado: gera o código TOTP atual do usuário (somente na demonstração). */
    public static String codigoMfaAtual(Aplicacao app, String email) {
        Usuario u = app.persistencia.usuarios().buscarPorEmail(email).orElseThrow();
        return app.mfa.codigoAtual(u.segredoMfa(), app.relogio.instant());
    }
}
