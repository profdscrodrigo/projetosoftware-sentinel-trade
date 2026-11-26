#!/usr/bin/env python3
"""Gera os diagramas de classes de ANÁLISE e de SERVIÇOS (projeto) em Graphviz, com colunas por camada.
Uso (na raiz do repositório):  python3 docs/uml/gerar_diagramas_graphviz.py
Requer: Graphviz (dot).  Saídas: .dot em docs/uml/... e .png em diagrams/."""
import html, subprocess, pathlib

RAIZ = pathlib.Path(__file__).resolve().parents[2]

def classe(id_, nome, estereotipo=None, attrs=(), metodos=(), cor="#FFFFFF", borda="#333333", abstrato=False):
    n = f"<I>{html.escape(nome)}</I>" if abstrato else f"<B>{html.escape(nome)}</B>"
    est = f'<TR><TD><FONT POINT-SIZE="9"><I>&#171;{html.escape(estereotipo)}&#187;</I></FONT></TD></TR>' if estereotipo else ""
    def bloco(itens):
        if not itens: return ""
        linhas = "".join(f'{html.escape(i)}<BR ALIGN="LEFT"/>' for i in itens)
        return f'<HR/><TR><TD ALIGN="LEFT"><FONT POINT-SIZE="10">{linhas}</FONT></TD></TR>'
    return (f'  {id_} [shape=plain, label=<<TABLE BORDER="1" COLOR="{borda}" CELLBORDER="0" CELLSPACING="0" CELLPADDING="4" BGCOLOR="{cor}">'
            f'{est}<TR><TD>{n}</TD></TR>{bloco(attrs)}{bloco(metodos)}</TABLE>>];')

def salvar(nome_dot, png, linhas):
    dot = RAIZ / nome_dot
    dot.write_text("\n".join(linhas), encoding="utf-8")
    subprocess.run(["dot", "-Tpng", "-Gdpi=110", str(dot), "-o", str(RAIZ / "diagrams" / png)], check=True)

# ------------------------------------------------------------------------------------------------ ANÁLISE
FR, CT, EN = "#E8F1FF", "#FFF4D6", "#E7F6E7"
L = ['digraph analise { rankdir=LR; splines=true; nodesep=0.25; ranksep=1.1; fontname="Helvetica"; labelloc=t; fontsize=22;',
     ' label="Diagrama de Classes de Análise — SentinelTrade (fronteira · controle · entidade)\\n ";',
     ' node [fontname="Helvetica"]; edge [fontname="Helvetica", fontsize=9, color="#444444"];']
L.append(' subgraph cluster_f { label="FRONTEIRA (boundary)"; style=dashed; color="#7A9CC6"; fontsize=14;')
for i, n in [("tl", "TelaLogin"), ("to", "TelaOrdem (console e web)"), ("tc", "TelaCarteira"), ("tcot", "TelaCotacoes"), ("ta", "TelaAdministracao"), ("tr", "TelaRisco"),
             ("ic", "InterfaceCorretora"), ("in", "InterfaceNotificacao")]:
    L.append(classe(i, n, "boundary", cor=FR, borda="#2B4C7E"))
L.append(' }')
L.append(' subgraph cluster_c { label="CONTROLE (control)"; style=dashed; color="#C9A227"; fontsize=14;')
for i, n in [("cau", "ControleAutenticacao"), ("cor", "ControleOrdens"), ("cri", "ControleRisco"), ("cli", "ControleLiquidacao"),
             ("cco", "ControleCotacoes"), ("cad", "ControleAdministracao"), ("cno", "ControleNotificacao"), ("cau2", "ControleAuditoria")]:
    L.append(classe(i, n, "control", cor=CT, borda="#B07A00"))
L.append(' }')
L.append(' subgraph cluster_e { label="ENTIDADE (entity)"; style=dashed; color="#5E9E5E"; fontsize=14;')
ent = [("usu", "Usuario", ["nome", "email", "papel"]), ("inv", "Investidor", []), ("adm", "Administrador", []), ("ana", "AnalistaRisco", []),
       ("ses", "Sessao", ["estado", "expiraEm"]), ("con", "Conta", ["saldoDisponivel", "saldoBloqueado"]),
       ("pos", "Posicao", ["quantidade", "quantidadeBloqueada", "custoTotal"]), ("ati", "Ativo", ["ticker", "nome", "tipo", "status"]),
       ("cot", "Cotacao", ["preco", "fechamentoAnterior", "instante"]),
       ("ord", "Ordem", ["lado", "tipo (limite | mercado)", "quantidade", "precoLimite", "status", "chaveIdempotencia", "substituiOrdemId"]),
       ("oc", "OrdemCompra", []), ("ov", "OrdemVenda", []), ("exe", "Execucao", ["idExecucao", "quantidade", "preco"]),
       ("lim", "LimiteRisco", ["porOrdem", "diario", "confirmacao"]), ("not", "Notificacao", ["tipo", "mensagem"]),
       ("aud", "EventoAuditoria", ["seq", "tipo", "hash", "hashAnterior"])]
for i, n, a in ent: L.append(classe(i, n, "entity", attrs=a, cor=EN, borda="#2E7D32", abstrato=(n == "Ordem")))
L.append(' }')
# fronteira -> controle
for a, b in [("tl", "cau"), ("to", "cor"), ("tc", "cor"), ("tcot", "cco"), ("ta", "cad"), ("tr", "cri"), ("ic", "cli"), ("in", "cno")]:
    L.append(f' {a} -> {b} [arrowhead=open];')
L.append(' cor -> ic [arrowhead=open, style=dashed, constraint=false];')
# controle -> entidade
for a, b in [("cau", "usu"), ("cau", "ses"), ("cor", "ord"), ("cor", "con"), ("cor", "pos"), ("cri", "lim"), ("cco", "cot"), ("cli", "exe"),
             ("cli", "ord"), ("cad", "ati"), ("cad", "usu"), ("cno", "not"), ("cau2", "aud")]:
    L.append(f' {a} -> {b} [arrowhead=open, style=dashed];')
for a, b in [("cor", "cri"), ("cor", "cau2"), ("cli", "cau2"), ("cau", "cau2"), ("cli", "cno"), ("cri", "cco")]:
    L.append(f' {a} -> {b} [arrowhead=open, color="#B07A00", constraint=false];')
# entidade <-> entidade
for a, b in [("inv", "usu"), ("adm", "usu"), ("ana", "usu"), ("oc", "ord"), ("ov", "ord")]:
    L.append(f' {a} -> {b} [arrowhead=empty, constraint=false];')
for a, b, ta, tb, lb in [("usu", "ses", "1", "*", ""), ("inv", "con", "1", "1", ""), ("inv", "pos", "1", "*", "carteira"), ("inv", "lim", "1", "1", ""),
                         ("inv", "ord", "1", "*", ""), ("inv", "not", "1", "*", ""), ("ord", "ati", "*", "1", ""), ("pos", "ati", "*", "1", ""),
                         ("ord", "exe", "1", "*", ""), ("ati", "cot", "1", "*", "")]:
    L.append(f' {a} -> {b} [arrowhead=none, taillabel="{ta}", headlabel="{tb}", label="{lb}", constraint=false];')
L.append('}')
(RAIZ / "docs/uml/classes-analise").mkdir(parents=True, exist_ok=True)
salvar("docs/uml/classes-analise/classes-analise.dot", "classes-analise.png", L)

# ------------------------------------------------------------------------------------------------ SERVIÇOS (projeto)
UC, AP, TR = "#E8F1FF", "#FFF4D6", "#F0E8FF"
S = ['digraph servicos { rankdir=LR; splines=true; nodesep=0.25; ranksep=1.2; fontname="Helvetica"; labelloc=t; fontsize=22;',
     ' label="Diagrama de Classes de Projeto — Serviços da camada de aplicação (pacote aplicacao.servico)\\n ";',
     ' node [fontname="Helvetica"]; edge [fontname="Helvetica", fontsize=9, color="#444444", arrowhead=open];']
S.append(' subgraph cluster_a { label="Casos de uso (expostos à interface; recebem o token da sessão)"; style=dashed; color="#7A9CC6"; fontsize=13;')
S += [classe("os", "OrdemService", None, [], ["enviarOrdem(token, SolicitacaoOrdem) : ResultadoEnvio", "alterarOrdem(token, id, qtd, preco, chave, confirmada) : ResultadoAlteracao", "cancelarOrdem(token, idOrdem) : Ordem", "consultarOrdem(token, idOrdem) : Ordem",
                                             "historico(token) : List<Ordem>", "execucoesDaOrdem(token, idOrdem) : List<Execucao>"], UC),
      classe("cs", "CarteiraService", None, [], ["consultarSaldo(token) : SaldoConta", "consultarCarteira(token) : ResumoCarteira"], UC),
      classe("cot", "CotacaoService", None, [], ["obter(Ticker) : Optional<CotacaoConsultada>", "consultar(token, ticker)", "listar(token)"], UC),
      classe("ads", "AdministracaoService", None, [], ["cadastrarInvestidor(token, nome, email, senha) : UUID", "depositarSimulado(token, id, valor)",
                                                      "cadastrarAtivo(token, ticker, nome, tipo)", "alterarStatusAtivo(token, ticker, status)",
                                                      "definirParametro(token, chave, valor)", "consultarAuditoria(token, aposSeq, limite)",
                                                      "verificarIntegridadeAuditoria(token)"], UC),
      classe("rs", "RiscoService", None, [], ["definirLimites(token, id, max, diario, confirmacao)", "consultarLimites(token, id)", "ordensRejeitadas(token)"], UC),
      classe("ns", "NotificacaoService", None, [], ["criar(UUID, TipoNotificacao, String)", "despacharPendentes() : int", "minhasNotificacoes(token)"], UC)]
S.append(' }')
S.append(' subgraph cluster_m { label="Motor assíncrono"; style=dashed; color="#C9A227"; fontsize=13;')
S.append(classe("po", "ProcessadorOrdens", None, [], ["ciclo() : int", "processarFila() : int", "coletarExecucoes() : int", "aplicarRelatorio(RelatorioExecucao)"], AP))
S.append(' }')
S.append(' subgraph cluster_t { label="Serviços de apoio e transversais"; style=dashed; color="#8E6BBF"; fontsize=13;')
S += [classe("vo", "ValidadorOrdem", None, [], ["validarMercado(Ticker, Dinheiro, Instant)", "validarRisco(Ordem, LimiteRisco, Dinheiro)", "precoDeProtecao(Ticker, Lado) : Dinheiro  // ordem a mercado"], TR),
      classe("cm", "CalendarioMercado", None, [], ["aberto(Instant) : boolean"], TR),
      classe("ps", "ParametrosSistema", None, [], ["definir(chave, valor)  // allowlist de chaves"], TR),
      classe("au", "AutenticacaoService", None, [], ["iniciarLogin(email, senha) : String", "confirmarMfa(tokenPendente, codigo) : String",
                                                    "exigir(token, Papel...) : Sessao", "encerrar(token)"], TR),
      classe("ad", "AuditoriaService", None, [], ["registrar(TipoEvento, ...) : EventoAuditoria", "verificarIntegridade() : Verificacao",
                                                  "verificarCadeia(List<EventoAuditoria>) : Verificacao  // estático"], TR),
      classe("lt", "LimitadorDeTaxa", None, [], ["registrar(chave, maximo, Duration)"], TR),
      classe("va", "Validacoes", None, [], ["ordem(SolicitacaoOrdem) : OrdemValidada  // estático", "senha(), email(), nome(), ..."], TR),
      classe("sa", "Sanitizador", None, [], ["paraLog(String) : String  // estático"], TR),
      classe("re", "Retentativa", None, [], ["comConflito(max, Supplier<T>) : T  // estático"], TR)]
S.append(' }')
for a, b in [("os", "au"), ("os", "vo"), ("os", "po"), ("os", "ad"), ("os", "ns"), ("os", "lt"), ("os", "va"), ("os", "ps"), ("os", "re"),
             ("cs", "au"), ("cs", "cot"), ("ads", "au"), ("ads", "ad"), ("ads", "ps"), ("rs", "au"), ("rs", "ad"), ("ns", "au"),
             ("po", "ad"), ("po", "ns"), ("po", "re"), ("vo", "cot"), ("vo", "cm"), ("vo", "ps"), ("cm", "ps"), ("cot", "ps"), ("cot", "au"),
             ("au", "ad"), ("au", "lt"), ("au", "va"), ("au", "re"), ("ad", "sa"), ("ns", "sa")]:
    S.append(f' {a} -> {b};')
S.append('}')
salvar("docs/uml/classes-projeto/servicos.dot", "classes-projeto-servicos.png", S)
print("ok")
