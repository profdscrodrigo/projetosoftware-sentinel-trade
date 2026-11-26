package br.orion.sentineltrade.interfacecli;

import br.orion.sentineltrade.Aplicacao;
import br.orion.sentineltrade.DadosDemonstracao;
import br.orion.sentineltrade.DadosDemonstracao.Credencial;
import br.orion.sentineltrade.aplicacao.servico.CarteiraService.ResumoCarteira;
import br.orion.sentineltrade.aplicacao.servico.OrdemService;
import br.orion.sentineltrade.aplicacao.servico.OrdemService.ResultadoEnvio;
import br.orion.sentineltrade.aplicacao.servico.Sanitizador;
import br.orion.sentineltrade.aplicacao.servico.SolicitacaoOrdem;
import br.orion.sentineltrade.dominio.*;
import br.orion.sentineltrade.dominio.Excecoes.ConfirmacaoNecessariaException;
import br.orion.sentineltrade.dominio.Excecoes.SentinelException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.Scanner;
import java.util.UUID;

/**
 * Interface de console. Camada FINA: só lê texto, chama os serviços e imprime. Nenhuma regra de negócio mora aqui
 * e todo texto de origem externa é sanitizado antes de ser impresso (sem injeção de sequências de terminal).
 */
public final class CliApp {
    private final Aplicacao app;
    private final List<Credencial> credenciaisDemo;
    private final Scanner in;
    private final PrintStream out;

    public CliApp(Aplicacao app, List<Credencial> credenciaisDemo, InputStream in, PrintStream out) {
        this.app = app; this.credenciaisDemo = credenciaisDemo; this.in = new Scanner(in); this.out = out;
    }

    public void executar() {
        out.println("==========================================================");
        out.println(" SentinelTrade — Orion Capital   (SISTEMA 100% SIMULADO)");
        out.println(" Nenhum dinheiro, ativo, bolsa ou corretora reais.");
        out.println("==========================================================");
        if (!credenciaisDemo.isEmpty()) {
            out.println("Contas de demonstração (senhas geradas aleatoriamente nesta execução):");
            for (Credencial c : credenciaisDemo) out.printf("  %-14s %-22s senha: %s%n", c.papel(), c.email(), c.senha());
            out.println("O código MFA é exibido pelo 'autenticador simulado' no passo de verificação.\n");
        }
        while (true) {
            out.print("\n1) Entrar   0) Sair > ");
            String op = in.hasNextLine() ? in.nextLine().trim() : "0";
            if (op.equals("0")) return;
            if (op.equals("1")) {
                try { String token = login(); if (token != null) menu(token); }
                catch (SentinelException e) { out.println("✘ " + Sanitizador.paraLog(e.getMessage())); }
            }
        }
    }

    private String login() {
        String email = ler("E-mail: ");
        String senha = lerSenha();
        String pendente = app.auth.iniciarLogin(email, senha);
        if (!credenciaisDemo.isEmpty())
            out.println("   [autenticador simulado] código atual: " + DadosDemonstracao.codigoMfaAtual(app, email.trim().toLowerCase()));
        String token = app.auth.confirmarMfa(pendente, ler("Código MFA (6 dígitos): "));
        out.println("✔ Autenticado.");
        return token;
    }

    private void menu(String token) {
        Papel papel = app.auth.validarToken(token).papel();
        while (true) {
            try {
                switch (papel) {
                    case INVESTIDOR -> { if (!menuInvestidor(token)) return; }
                    case ADMINISTRADOR -> { if (!menuAdmin(token)) return; }
                    case ANALISTA_RISCO -> { if (!menuRisco(token)) return; }
                }
            } catch (SentinelException e) {
                out.println("✘ " + Sanitizador.paraLog(e.getMessage()));
                if (e instanceof Excecoes.AutenticacaoException) return;      // sessão expirada
            } catch (RuntimeException e) {
                String ref = UUID.randomUUID().toString().substring(0, 8);
                System.err.println("Erro interno ref=" + ref + ": " + e);        // detalhe só no log
                out.println("✘ Erro interno. Informe a referência " + ref + " ao suporte.");
            }
        }
    }

    // ------------------------------------------------------------------------------------ investidor
    private boolean menuInvestidor(String t) {
        out.print("\n[Investidor] 1) Saldo/carteira 2) Cotações 3) Comprar 4) Vender 5) Cancelar ordem 6) Status da ordem 7) Histórico 8) Notificações 9) Alterar ordem 0) Sair > ");
        switch (ler("")) {
            case "1" -> carteira(t);
            case "2" -> app.cotacoes.listar(t).forEach(q -> out.printf("  %-7s %10s  var %6s%%  %s%s%n", q.cotacao().ticker(), q.cotacao().preco(),
                    q.cotacao().variacaoPercentual(), q.cotacao().instante(), q.desatualizada() ? "  (DESATUALIZADA)" : ""));
            case "3" -> enviar(t, "COMPRA");
            case "4" -> enviar(t, "VENDA");
            case "5" -> out.println("  → " + descrever(app.ordens.cancelarOrdem(t, ler("ID da ordem: "))));
            case "6" -> out.println("  → " + descrever(app.ordens.consultarOrdem(t, ler("ID da ordem: "))));
            case "7" -> app.ordens.historico(t).forEach(o -> out.println("  " + o.id() + "  " + descrever(o)));
            case "8" -> app.notificacoes.minhasNotificacoes(t).forEach(n -> out.println("  [" + n.tipo() + "] " + Sanitizador.paraLog(n.mensagem())));
            case "9" -> alterar(t);
            case "0" -> { app.auth.encerrar(t); return false; }
            default -> out.println("Opção inválida.");
        }
        return true;
    }

    private void alterar(String t) {
        String id = ler("ID da ordem: "), qtd = ler("Nova quantidade: "), preco = ler("Novo preço limite (vazio mantém; ordem a mercado: deixe vazio): ");
        String chave = UUID.randomUUID().toString();
        try {
            mostrarAlteracao(app.ordens.alterarOrdem(t, id, qtd, preco, chave, false));
        } catch (ConfirmacaoNecessariaException e) {
            out.println("  ⚠ " + e.getMessage());
            if (ler("Confirmar a alteração? (s/n): ").equalsIgnoreCase("s")) mostrarAlteracao(app.ordens.alterarOrdem(t, id, qtd, preco, chave, true));
            else out.println("  Alteração não confirmada; a ordem original permanece.");
        }
    }

    private void mostrarAlteracao(OrdemService.ResultadoAlteracao r) {
        out.println("  → original: " + descrever(r.substituida()));
        out.println("  → nova (ID " + r.nova().id() + "): " + descrever(r.nova()));
    }

    private void carteira(String t) {
        ResumoCarteira r = app.carteira.consultarCarteira(t);
        out.println("  Saldo disponível: " + r.saldo().disponivel().formatado() + " | bloqueado: " + r.saldo().bloqueado().formatado());
        r.itens().forEach(i -> out.printf("  %-7s qtd %6d (bloq. %d)  PM %s  cotação %s  valor %s%n", i.ticker(), i.quantidade(),
                i.quantidadeBloqueada(), i.precoMedio(), i.cotacaoAtual() == null ? "n/d" : i.cotacaoAtual(), i.valorEstimado()));
        out.println("  Patrimônio estimado: " + r.patrimonioEstimado().formatado());
    }

    private void enviar(String t, String lado) {
        String chave = UUID.randomUUID().toString();            // uma chave POR intenção do usuário: protege contra reenvio
        String ativo = ler("Ativo (ex.: PETR4): "), quantidade = ler("Quantidade: ");
        String tipo = ler("Execução (LIMITE/MERCADO) [LIMITE]: ");
        String preco = tipo.equalsIgnoreCase("MERCADO") ? "" : ler("Preço limite: ");
        SolicitacaoOrdem s = new SolicitacaoOrdem(lado, tipo, ativo, quantidade, preco, chave, false);
        try {
            mostrar(app.ordens.enviarOrdem(t, s));
        } catch (ConfirmacaoNecessariaException e) {
            out.println("  ⚠ " + e.getMessage());
            if (ler("Confirmar a operação? (s/n): ").equalsIgnoreCase("s"))
                mostrar(app.ordens.enviarOrdem(t, new SolicitacaoOrdem(s.lado(), s.tipo(), s.ticker(), s.quantidade(), s.preco(), chave, true)));
            else out.println("  Operação não confirmada; nada foi enviado.");
        }
    }

    private void mostrar(ResultadoEnvio r) {
        out.println("  → " + (r.duplicada() ? "(reenvio reconhecido; ordem já existente) " : "") + "ID " + r.ordem().id() + "  " + descrever(r.ordem()));
    }

    // ------------------------------------------------------------------------------------ administrador
    private boolean menuAdmin(String t) {
        out.print("\n[Administrador] 1) Cadastrar investidor 2) Depósito simulado 3) Cadastrar ativo 4) Alterar status de ativo 5) Ativos 6) Parâmetros 7) Alterar parâmetro 8) Auditoria 9) Verificar integridade 0) Sair > ");
        switch (ler("")) {
            case "1" -> out.println("  ✔ Investidor criado: " + app.admin.cadastrarInvestidor(t, ler("Nome: "), ler("E-mail: "), lerSenha()));
            case "2" -> { app.admin.depositarSimulado(t, ler("ID do investidor: "), ler("Valor: ")); out.println("  ✔ Depósito simulado registrado."); }
            case "3" -> { app.admin.cadastrarAtivo(t, ler("Código: "), ler("Nome: "), ler("Tipo (ACAO/ETF/FII): ")); out.println("  ✔ Ativo cadastrado."); }
            case "4" -> { app.admin.alterarStatusAtivo(t, ler("Código: "), ler("Status (ATIVO/SUSPENSO): ")); out.println("  ✔ Status alterado."); }
            case "5" -> app.admin.listarAtivos(t).forEach(a -> out.println("  " + a.ticker() + "  " + Sanitizador.paraLog(a.nome()) + "  " + a.tipo() + "  " + a.status()));
            case "6" -> app.admin.listarParametros(t).forEach((k, v) -> out.println("  " + k + " = " + v));
            case "7" -> { app.admin.definirParametro(t, ler("Chave: "), ler("Valor: ")); out.println("  ✔ Parâmetro alterado."); }
            case "8" -> app.admin.consultarAuditoria(t, 0, 500).stream().skip(Math.max(0, 0)).forEach(e ->
                    out.println("  #" + e.seq() + " " + e.instante() + " " + e.tipo() + " " + e.entidade() + ":" + e.entidadeId() + " " + e.detalhes()));
            case "9" -> out.println("  " + app.admin.verificarIntegridadeAuditoria(t));
            case "0" -> { app.auth.encerrar(t); return false; }
            default -> out.println("Opção inválida.");
        }
        return true;
    }

    // ------------------------------------------------------------------------------------ analista de risco
    private boolean menuRisco(String t) {
        out.print("\n[Analista de Risco] 1) Investidores 2) Consultar limites 3) Definir limites 4) Ordens rejeitadas 0) Sair > ");
        switch (ler("")) {
            case "1" -> app.risco.listarInvestidores(t).forEach(u -> out.println("  " + u.id() + "  " + Sanitizador.paraLog(u.nome())));
            case "2" -> { LimiteRisco l = app.risco.consultarLimites(t, ler("ID do investidor: "));
                out.println("  por ordem " + l.valorMaximoPorOrdem() + " | diário " + l.limiteDiario() + " | confirmação acima de " + l.valorConfirmacao()); }
            case "3" -> { app.risco.definirLimites(t, ler("ID do investidor: "), ler("Máximo por ordem: "), ler("Limite diário: "), ler("Confirmação acima de: ")); out.println("  ✔ Limites atualizados."); }
            case "4" -> app.risco.ordensRejeitadas(t).forEach(o -> out.println("  " + o.id() + "  " + descrever(o)));
            case "0" -> { app.auth.encerrar(t); return false; }
            default -> out.println("Opção inválida.");
        }
        return true;
    }

    // ------------------------------------------------------------------------------------ utilidades
    static String descrever(Ordem o) {
        return o.lado() + (o.tipo() == TipoOrdem.MERCADO ? " A MERCADO" : "") + " " + o.ticker() + " " + o.quantidade() + " @ " + o.precoLimite()
                + (o.tipo() == TipoOrdem.MERCADO ? " (proteção)" : "") + " → " + o.status()
                + " (executado " + o.quantidadeExecutada() + "/" + o.quantidade() + ")"
                + (o.motivoRejeicao() != null ? " — " + o.motivoRejeicao().descricao() : "");
    }

    private String ler(String prompt) {
        if (!prompt.isEmpty()) out.print(prompt);
        return in.hasNextLine() ? in.nextLine().trim() : "0";
    }

    private String lerSenha() {
        java.io.Console c = System.console();
        if (c != null) { char[] p = c.readPassword("Senha: "); return p == null ? "" : new String(p); }
        return ler("Senha (atenção: será exibida): ");
    }
}
