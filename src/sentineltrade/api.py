# CAMADA DE INTERFACE: API do SentinelTrade com FastAPI.
# Abrindo http://127.0.0.1:8000/docs aparece uma página para testar cada função.
# A API só recebe os dados e chama os serviços; as regras ficam nos serviços.
import logging
import os
import secrets
import uuid

from fastapi import Depends, FastAPI, Request
from fastapi.responses import JSONResponse
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from pydantic import BaseModel, Field

from .dominio import AcessoNegado, DadosAlteracao, DadosAtivo, DadosInvestidor, DadosLimite, DadosOrdem, ErroSentinel, LoginInvalido
from .integracoes import BolsaSimulada, ProvedorCotacoes
from .persistencia import Banco, Repositorio
from .servicos import (Auditoria, Autenticacao, Notificacoes, PrecisaConfirmar, ServicoAdmin, ServicoCarteira,
                       ServicoOrdens, mostrar_codigo)

log = logging.getLogger("sentineltrade")


def ler_env():
    """Lê o arquivo .env (que NÃO vai para o GitHub) e guarda nas variáveis de ambiente."""
    caminho = os.path.join(os.path.dirname(__file__), "..", "..", ".env")
    if os.path.exists(caminho):
        with open(caminho, encoding="utf-8") as arquivo:
            for linha in arquivo:
                if "=" in linha and not linha.startswith("#"):
                    chave, valor = linha.strip().split("=", 1)
                    if valor:
                        os.environ.setdefault(chave, valor)


class Sistema:
    def __init__(self, url_banco, schema=None, enviar_codigo=mostrar_codigo):
        """Junta todas as partes do sistema."""
        # chave que assina os tokens de sessão; se não estiver no .env, cria uma nova a cada execução
        chave = os.environ.get("SENTINEL_CHAVE_SECRETA") or secrets.token_hex(32)
        self.banco = Banco(url_banco, schema)
        self.banco.criar_tabelas()
        self.repo = Repositorio(self.banco)
        self.bolsa = BolsaSimulada()
        self.auditoria = Auditoria(self.repo)
        self.notificacoes = Notificacoes(self.repo)
        self.login = Autenticacao(self.repo, self.auditoria, chave, enviar_codigo)
        self.carteira = ServicoCarteira(self.repo, ProvedorCotacoes())
        self.ordens = ServicoOrdens(self.banco, self.repo, self.bolsa, self.auditoria, self.notificacoes)
        self.admin = ServicoAdmin(self.repo, self.auditoria, self.bolsa)


# ----- modelos das requisições (validados pelo Pydantic) -----
class DadosLogin(BaseModel):
    email: str = Field(max_length=120)
    senha: str = Field(max_length=64)


class DadosCodigo(BaseModel):
    email: str = Field(max_length=120)
    codigo: str = Field(min_length=6, max_length=6)


class PedidoOrdem(DadosOrdem):
    # chave que identifica o envio: se a mesma chave chegar de novo, não cria outra ordem
    chave: str = Field(default_factory=lambda: str(uuid.uuid4()), min_length=8, max_length=64)
    confirmado: bool = False


def ordem_json(ordem):
    return {"id": ordem.id, "tipo": ordem.tipo, "ticker": ordem.ticker, "quantidade": ordem.quantidade,
            "preco": ordem.preco, "status": ordem.status, "motivo": ordem.motivo}


def criar_api(sistema):
    api = FastAPI(title="SentinelTrade", description="Plataforma SIMULADA de negociação (nenhum dado é real).")
    bearer = HTTPBearer()

    def usuario_logado(credenciais: HTTPAuthorizationCredentials = Depends(bearer)):
        # a cada pedido o servidor confere o token (assinatura e validade de 30 minutos)
        return sistema.login.usuario_da_sessao(credenciais.credentials)

    # ----- tratamento de erros: mensagem simples, sem detalhes técnicos -----
    @api.exception_handler(ErroSentinel)
    def erro_do_sistema(request: Request, erro: ErroSentinel):
        codigo = 401 if isinstance(erro, LoginInvalido) else 403 if isinstance(erro, AcessoNegado) else 400
        return JSONResponse(status_code=codigo, content={"erro": str(erro)})

    @api.exception_handler(PrecisaConfirmar)
    def precisa_confirmar(request: Request, erro: PrecisaConfirmar):
        return JSONResponse(status_code=409, content={"erro": f"{erro} Envie de novo com confirmado = true."})

    @api.exception_handler(Exception)
    def erro_inesperado(request: Request, erro: Exception):
        log.exception("Erro inesperado")  # o detalhe vai só para o log
        return JSONResponse(status_code=500, content={"erro": "Erro inesperado. Tente de novo."})

    # ================= LOGIN (UC-01) =================
    @api.post("/login", tags=["Login"])
    def login(dados: DadosLogin):
        sistema.login.entrar(dados.email, dados.senha)
        return {"mensagem": "Senha correta. Digite o código enviado por SMS (simulado no terminal)."}

    @api.post("/login/codigo", tags=["Login"])
    def confirmar_codigo(dados: DadosCodigo):
        token, usuario = sistema.login.confirmar_codigo(dados.email, dados.codigo)
        return {"token": token, "perfil": usuario.perfil, "valido_por": "30 minutos",
                "mensagem": "Copie o token e clique em Authorize (cadeado) no topo da página."}

    @api.post("/logout", tags=["Login"])
    def logout(credenciais: HTTPAuthorizationCredentials = Depends(bearer)):
        sistema.login.sair(credenciais.credentials)
        return {"mensagem": "Sessão encerrada."}

    # ================= INVESTIDOR =================
    @api.get("/saldo", tags=["Investidor"])
    def ver_saldo(usuario=Depends(usuario_logado)):
        conta = sistema.carteira.saldo(usuario)
        return {"saldo": conta.saldo, "limite_por_ordem": conta.limite_por_ordem}

    @api.get("/carteira", tags=["Investidor"])
    def ver_carteira(usuario=Depends(usuario_logado)):
        return sistema.carteira.carteira(usuario)

    @api.get("/cotacoes", tags=["Investidor"])
    def ver_cotacoes(usuario=Depends(usuario_logado)):
        return [{"ticker": t, "preco": p, "situacao": s} for t, p, s in sistema.carteira.cotacoes(usuario)]

    @api.post("/ordens", tags=["Investidor"])
    def enviar_ordem(pedido: PedidoOrdem, usuario=Depends(usuario_logado)):
        ordem = sistema.ordens.enviar(usuario, pedido.tipo, pedido.ticker, pedido.quantidade, pedido.preco,
                                      pedido.chave, pedido.confirmado)
        return ordem_json(ordem)

    @api.get("/ordens", tags=["Investidor"])
    def minhas_ordens(usuario=Depends(usuario_logado)):
        return [ordem_json(o) for o in sistema.ordens.minhas_ordens(usuario)]

    @api.get("/ordens/{ordem_id}", tags=["Investidor"])
    def acompanhar_ordem(ordem_id: int, usuario=Depends(usuario_logado)):
        ordem, historico = sistema.ordens.consultar(usuario, ordem_id)
        return {**ordem_json(ordem), "historico": [dict(h) for h in historico]}

    @api.put("/ordens/{ordem_id}", tags=["Investidor"])
    def alterar_ordem(ordem_id: int, dados: DadosAlteracao, usuario=Depends(usuario_logado)):
        return ordem_json(sistema.ordens.alterar(usuario, ordem_id, dados.quantidade, dados.preco))

    @api.post("/ordens/{ordem_id}/cancelar", tags=["Investidor"])
    def cancelar_ordem(ordem_id: int, usuario=Depends(usuario_logado)):
        return ordem_json(sistema.ordens.cancelar(usuario, ordem_id))

    @api.get("/notificacoes", tags=["Investidor"])
    def notificacoes(usuario=Depends(usuario_logado)):
        return [dict(n) for n in sistema.notificacoes.listar(usuario)]

    # ================= ADMINISTRADOR =================
    @api.get("/admin/usuarios", tags=["Administrador"])
    def listar_usuarios(usuario=Depends(usuario_logado)):
        return [{"id": u.id, "nome": u.nome, "email": u.email, "perfil": u.perfil, "bloqueado": u.bloqueado}
                for u in sistema.admin.listar_usuarios(usuario)]

    @api.post("/admin/investidores", tags=["Administrador"])
    def cadastrar_investidor(dados: DadosInvestidor, usuario=Depends(usuario_logado)):
        novo = sistema.admin.cadastrar_investidor(usuario, dados.nome, dados.email, dados.senha, dados.saldo)
        return {"id": novo, "mensagem": "Investidor cadastrado."}

    @api.post("/admin/usuarios/{usuario_id}/desbloquear", tags=["Administrador"])
    def desbloquear(usuario_id: int, usuario=Depends(usuario_logado)):
        sistema.admin.desbloquear(usuario, usuario_id)
        return {"mensagem": "Usuário desbloqueado."}

    @api.get("/admin/ativos", tags=["Administrador"])
    def listar_ativos(usuario=Depends(usuario_logado)):
        return [dict(a) for a in sistema.admin.listar_ativos(usuario)]

    @api.post("/admin/ativos", tags=["Administrador"])
    def cadastrar_ativo(dados: DadosAtivo, usuario=Depends(usuario_logado)):
        sistema.admin.cadastrar_ativo(usuario, dados.ticker, dados.nome, dados.tipo, dados.preco)
        return {"mensagem": "Ativo cadastrado."}

    @api.post("/admin/ativos/{ticker}/suspender", tags=["Administrador"])
    def suspender_ativo(ticker: str, usuario=Depends(usuario_logado)):
        sistema.admin.suspender_ou_liberar_ativo(usuario, ticker, False)
        return {"mensagem": "Ativo suspenso."}

    @api.post("/admin/ativos/{ticker}/liberar", tags=["Administrador"])
    def liberar_ativo(ticker: str, usuario=Depends(usuario_logado)):
        sistema.admin.suspender_ou_liberar_ativo(usuario, ticker, True)
        return {"mensagem": "Ativo liberado."}

    @api.post("/admin/mercado/{acao}", tags=["Administrador"])
    def mercado(acao: str, usuario=Depends(usuario_logado)):
        """acao: abrir ou fechar"""
        sistema.admin.abrir_ou_fechar_mercado(usuario, acao == "abrir")
        return {"mensagem": "Mercado ABERTO." if acao == "abrir" else "Mercado FECHADO."}

    @api.post("/admin/bolsa/{acao}", tags=["Administrador"])
    def bolsa(acao: str, usuario=Depends(usuario_logado)):
        """acao: ligar ou desligar (para simular a bolsa fora do ar)"""
        sistema.admin.ligar_ou_desligar_bolsa(usuario, acao == "ligar")
        return {"mensagem": "Bolsa LIGADA." if acao == "ligar" else "Bolsa DESLIGADA."}

    @api.post("/admin/ordens/reenviar", tags=["Administrador"])
    def reenviar_falhas(usuario=Depends(usuario_logado)):
        return [ordem_json(o) for o in sistema.ordens.reenviar_falhas(usuario)]

    @api.get("/admin/auditoria", tags=["Administrador"])
    def auditoria(usuario=Depends(usuario_logado)):
        return [dict(r) for r in sistema.auditoria.listar(usuario)]

    # ================= ANALISTA DE RISCO =================
    @api.get("/risco/ordens-rejeitadas", tags=["Analista de Risco"])
    def ordens_rejeitadas(usuario=Depends(usuario_logado)):
        return [ordem_json(o) for o in sistema.admin.ordens_rejeitadas(usuario)]

    @api.put("/risco/limites/{usuario_id}", tags=["Analista de Risco"])
    def alterar_limite(usuario_id: int, dados: DadosLimite, usuario=Depends(usuario_logado)):
        sistema.admin.alterar_limite(usuario, usuario_id, dados.limite)
        return {"mensagem": "Limite alterado."}

    return api