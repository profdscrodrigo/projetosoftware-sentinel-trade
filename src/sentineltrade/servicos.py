# CAMADA DE SERVIÇOS (regras de negócio): login com MFA, permissões, auditoria,
# notificações, carteira, ordens e funções do administrador/analista.
import hashlib
import hmac
import logging
import secrets
import time
from decimal import Decimal

import bcrypt
from sqlalchemy.exc import IntegrityError

from . import dominio
from .dominio import (AcessoNegado, BolsaIndisponivel, DadoInvalido, DadosAlteracao, DadosAtivo, DadosInvestidor,
                      DadosLimite, DadosOrdem, LoginInvalido, OrdemRecusada, criar_ordem, validar)

log = logging.getLogger("sentineltrade")


# Segurança: hash de senha, login com MFA, sessões e permissões (UC-01, RF-01).
# UMLsec «secureAuthentication» e «leastPrivilege».


MAX_TENTATIVAS = 5          # senhas erradas antes de bloquear (proteção contra força bruta)
VALIDADE_CODIGO = 300       # código MFA vale 5 minutos
VALIDADE_SESSAO = 30 * 60   # sessão curta: 30 minutos


def gerar_hash(senha):
    return bcrypt.hashpw(senha.encode(), bcrypt.gensalt()).decode()


def conferir_senha(senha, hash_salvo):
    try:
        return bcrypt.checkpw(str(senha).encode(), hash_salvo.encode())
    except ValueError:
        return False


def exigir(usuario, acao):
    """Menor privilégio: confere no servidor, a cada pedido, se o perfil pode fazer a ação."""
    if usuario is None or not usuario.pode(acao):
        raise AcessoNegado("Você não tem permissão para fazer isso.")


def mostrar_codigo(usuario, codigo):
    # simula o envio de um SMS (o código NÃO vai para o log)
    print(f"\n  [SMS SIMULADO] Código para {usuario.nome}: {codigo}\n")


class Autenticacao:
    def __init__(self, repo, auditoria, chave_secreta, enviar_codigo=mostrar_codigo):
        self.repo = repo
        self.auditoria = auditoria
        self.chave = chave_secreta.encode()   # vem do .env, nunca do código
        self.enviar_codigo = enviar_codigo
        self.pendentes = {}   # email -> (usuario_id, codigo, hora)
        self.sessoes = {}     # id da sessão -> (usuario_id, hora em que expira)

    # ---------- passo 1: e-mail e senha ----------
    def entrar(self, email, senha):
        email = str(email).strip().lower()
        usuario = self.repo.buscar_usuario_por_email(email)
        # a mensagem é sempre a mesma, para não revelar se o e-mail existe ou se foi a senha
        if usuario is None or usuario.bloqueado:
            self.auditoria.registrar(None, "LOGIN_FALHOU", "usuário inexistente ou bloqueado")
            log.warning("Login recusado: usuário inexistente ou bloqueado")
            raise LoginInvalido("E-mail ou senha inválidos, ou usuário bloqueado.")
        if not conferir_senha(senha, usuario.senha_hash):
            usuario.tentativas_erradas += 1
            if usuario.tentativas_erradas >= MAX_TENTATIVAS:
                usuario.bloqueado = True
                self.auditoria.registrar(usuario.id, "USUARIO_BLOQUEADO", "5 senhas erradas")
            self.repo.salvar_tentativas(usuario)
            self.auditoria.registrar(usuario.id, "LOGIN_FALHOU", "senha errada")
            log.warning("Senha errada para o usuário %s", usuario.id)  # nunca registramos a senha
            raise LoginInvalido("E-mail ou senha inválidos, ou usuário bloqueado.")

        usuario.tentativas_erradas = 0
        self.repo.salvar_tentativas(usuario)
        codigo = f"{secrets.randbelow(1000000):06d}"
        self.pendentes[email] = (usuario.id, codigo, time.time())
        self.enviar_codigo(usuario, codigo)

    # ---------- passo 2: código MFA ----------
    def confirmar_codigo(self, email, codigo):
        """Confere o código (vale uma vez só, por 5 minutos) e devolve um token de sessão assinado."""
        pendente = self.pendentes.pop(str(email).strip().lower(), None)
        if pendente is None:
            raise LoginInvalido("Faça o login primeiro.")
        usuario_id, codigo_certo, hora = pendente
        if time.time() - hora > VALIDADE_CODIGO:
            raise LoginInvalido("Código expirado. Faça o login de novo.")
        if not secrets.compare_digest(str(codigo).strip(), codigo_certo):
            self.auditoria.registrar(usuario_id, "MFA_FALHOU", "código errado")
            raise LoginInvalido("Código incorreto. Faça o login de novo.")
        self.auditoria.registrar(usuario_id, "LOGIN_OK", "")
        log.info("Login do usuário %s", usuario_id)
        return self.criar_sessao(usuario_id), self.repo.buscar_usuario(usuario_id)

    # ---------- sessões ----------
    def _assinar(self, id_sessao):
        return hmac.new(self.chave, id_sessao.encode(), hashlib.sha256).hexdigest()

    def criar_sessao(self, usuario_id):
        id_sessao = secrets.token_hex(16)
        self.sessoes[id_sessao] = (usuario_id, time.time() + VALIDADE_SESSAO)
        return f"{id_sessao}.{self._assinar(id_sessao)}"   # token = id + assinatura

    def usuario_da_sessao(self, token):
        id_sessao, _, assinatura = str(token).partition(".")
        sessao = self.sessoes.get(id_sessao)
        if sessao is None or not hmac.compare_digest(assinatura, self._assinar(id_sessao)):
            raise LoginInvalido("Faça o login para usar o sistema.")
        usuario_id, expira = sessao
        if time.time() > expira:
            del self.sessoes[id_sessao]
            raise LoginInvalido("Sessão expirada. Faça o login de novo.")
        usuario = self.repo.buscar_usuario(usuario_id)
        if usuario is None or usuario.bloqueado:
            raise LoginInvalido("Faça o login para usar o sistema.")
        return usuario

    def sair(self, token):
        self.sessoes.pop(str(token).partition(".")[0], None)


# Auditoria (RF-15) e notificações (RF-16).


class Auditoria:
    def __init__(self, repo):
        self.repo = repo

    def registrar(self, usuario_id, acao, detalhes=""):
        self.repo.inserir_auditoria(usuario_id, acao, detalhes[:255])

    def listar(self, usuario):
        exigir(usuario, "ver_auditoria")
        return self.repo.listar_auditoria()


class Notificacoes:
    def __init__(self, repo):
        self.repo = repo

    def notificar(self, usuario_id, mensagem):
        self.repo.inserir_notificacao(usuario_id, mensagem[:255])

    def listar(self, usuario):
        return self.repo.listar_notificacoes(usuario.id)


# Consultas do investidor: saldo, carteira e cotações (UC-02, UC-03, UC-04).
# UMLsec «availability» nas cotações: validação da resposta, cache e circuit breaker.


FALHAS_PARA_ABRIR = 3   # depois de 3 falhas seguidas, para de chamar o provedor...
TEMPO_ABERTO = 60       # ...por 60 segundos (circuit breaker) e usa o último preço salvo


class ServicoCarteira:
    def __init__(self, repo, provedor):
        self.repo = repo
        self.provedor = provedor
        self.falhas_seguidas = 0
        self.pausado_ate = 0

    def saldo(self, usuario):
        exigir(usuario, "ver_carteira")
        return self.repo.buscar_conta(usuario.id)  # só a conta do próprio usuário (RN-08)

    def carteira(self, usuario):
        exigir(usuario, "ver_carteira")
        itens = []
        for p in self.repo.listar_posicoes(usuario.id):
            preco_atual = p["ultimo_preco"] or p["preco_referencia"]
            itens.append({"ticker": p["ticker"], "quantidade": p["quantidade"], "preco_medio": p["preco_medio"],
                          "preco_atual": preco_atual, "valor": preco_atual * p["quantidade"]})
        return itens

    def _buscar_no_provedor(self, ativo):
        if time.time() < self.pausado_ate:
            return None  # circuit breaker aberto: nem tenta
        preco = self.provedor.buscar_preco(ativo["preco_referencia"])
        # validação da resposta: preço precisa existir e estar perto do preço de referência
        if preco is None or preco <= 0 or abs(preco - ativo["preco_referencia"]) > ativo["preco_referencia"] / 2:
            self.falhas_seguidas += 1
            if self.falhas_seguidas >= FALHAS_PARA_ABRIR:
                self.pausado_ate = time.time() + TEMPO_ABERTO
            return None
        self.falhas_seguidas = 0
        return preco

    def cotacoes(self, usuario):
        """Busca o preço no provedor. Se ele estiver fora do ar, mostra o último preço salvo (cache)."""
        exigir(usuario, "ver_cotacoes")
        resultado = []
        for ativo in self.repo.listar_ativos(so_ativos=True):
            preco = self._buscar_no_provedor(ativo)
            if preco is not None:
                self.repo.salvar_cotacao(ativo["ticker"], preco)
                resultado.append((ativo["ticker"], preco, "atualizada"))
            else:
                ultimo = ativo["ultimo_preco"] or ativo["preco_referencia"]
                resultado.append((ativo["ticker"], ultimo, "DESATUALIZADA (provedor fora do ar)"))
        return resultado


# Regras das ordens: enviar (UC-05/UC-10), consultar (UC-07/08/09), alterar e cancelar (UC-06).


TENTATIVAS_BOLSA = 3


class PrecisaConfirmar(Exception):
    """Ordem acima do limite de confirmação: a API pede que o investidor confirme (<<extend>>)."""


class ServicoOrdens:
    def __init__(self, banco, repo, bolsa, auditoria, notificacoes):
        self.banco = banco
        self.repo = repo
        self.bolsa = bolsa
        self.auditoria = auditoria
        self.notificacoes = notificacoes

    # ---------------- enviar ordem ----------------
    def enviar(self, usuario, tipo, ticker, quantidade, preco, chave, confirmado=False):
        exigir(usuario, "enviar_ordem")
        dados = validar(DadosOrdem, tipo=tipo, ticker=ticker, quantidade=quantidade, preco=preco)
        tipo, ticker, quantidade, preco = dados.tipo, dados.ticker, dados.quantidade, dados.preco

        # verificar duplicidade (RF-17): a mesma chave devolve a ordem que já existe
        existente = self.repo.buscar_ordem_por_chave(chave)
        if existente:
            return existente

        ativo = self.repo.buscar_ativo(ticker)
        if ativo is None:
            raise DadoInvalido(f"O ativo {ticker} não existe.")

        ordem = criar_ordem(tipo, usuario_id=usuario.id, ticker=ticker, quantidade=quantidade, preco=preco, chave=chave)
        limite_confirmacao = Decimal(self.repo.buscar_parametro("limite_confirmacao"))
        if ordem.valor_total > limite_confirmacao and not confirmado:
            raise PrecisaConfirmar(f"Ordem de R$ {ordem.valor_total}. Confirma?")

        try:
            with self.banco.transacao():
                self.repo.inserir_ordem(ordem)
                self.repo.inserir_historico(ordem)
                self.auditoria.registrar(usuario.id, "ORDEM_CRIADA", f"#{ordem.id} {tipo} {quantidade} {ticker} a {preco}")
                self._validar(ordem, ativo)
        except IntegrityError:
            return self.repo.buscar_ordem_por_chave(chave)  # chegaram duas iguais ao mesmo tempo

        if ordem.status == dominio.VALIDADA:
            self._transmitir(ordem)
        return ordem

    def _validar(self, ordem, ativo):
        """Validar ordem (RF-11): saldo ou posição, limite de risco e situação do mercado."""
        conta = self.repo.buscar_conta(ordem.usuario_id)
        posicao = self.repo.buscar_posicao(ordem.usuario_id, ordem.ticker)
        try:
            ordem.verificar(conta, posicao)  # compra: saldo / venda: posição
            if ordem.valor_total > conta.limite_por_ordem:
                raise OrdemRecusada(f"Limite de risco excedido: limite por ordem é R$ {conta.limite_por_ordem}.")
            if self.repo.buscar_parametro("mercado_aberto") != "SIM":
                raise OrdemRecusada("Mercado fechado: ordem não transmitida.")
            if not ativo["ativo"]:
                raise OrdemRecusada(f"O ativo {ativo['ticker']} está suspenso.")
        except OrdemRecusada as erro:
            self._mudar(ordem, dominio.REJEITADA, str(erro))
            self.notificacoes.notificar(ordem.usuario_id, f"Ordem #{ordem.id} rejeitada: {erro}")
            return
        self._mudar(ordem, dominio.VALIDADA, "Validada")

    def _transmitir(self, ordem):
        """Envia para a bolsa. Tenta 3 vezes; se não conseguir, a ordem fica em FALHA (RN-07)."""
        self._mudar(ordem, dominio.ENVIADA, "Enviada para a bolsa")
        ativo = self.repo.buscar_ativo(ordem.ticker)
        resposta = None
        for tentativa in range(TENTATIVAS_BOLSA):
            try:
                resposta = self.bolsa.enviar(ordem, ativo["preco_referencia"])
                break
            except BolsaIndisponivel:
                log.warning("Bolsa não respondeu (ordem %s, tentativa %s)", ordem.id, tentativa + 1)
        if resposta is None:
            # a ordem NÃO é apagada e a carteira NÃO muda
            self._mudar(ordem, dominio.FALHA, "Bolsa fora do ar")
            self.notificacoes.notificar(ordem.usuario_id, f"Ordem #{ordem.id} em FALHA: bolsa fora do ar.")
            return

        executou, mensagem = resposta
        if not executou:
            self._mudar(ordem, dominio.REJEITADA, mensagem)
            self.notificacoes.notificar(ordem.usuario_id, f"Ordem #{ordem.id} rejeitada pela bolsa: {mensagem}")
            return

        with self.banco.transacao():  # saldo, carteira e ordem mudam juntos
            conta = self.repo.buscar_conta(ordem.usuario_id)
            posicao = self.repo.buscar_posicao(ordem.usuario_id, ordem.ticker)
            try:
                ordem.executar(conta, posicao)
            except OrdemRecusada as erro:  # o saldo ou as ações acabaram enquanto a ordem esperava
                self._mudar(ordem, dominio.REJEITADA, str(erro))
                self.notificacoes.notificar(ordem.usuario_id, f"Ordem #{ordem.id} rejeitada: {erro}")
                return
            self.repo.salvar_conta(conta)
            self.repo.salvar_posicao(posicao)
            self._mudar(ordem, dominio.EXECUTADA, mensagem)
            self.notificacoes.notificar(ordem.usuario_id, f"Ordem #{ordem.id} EXECUTADA.")

    def reenviar_falhas(self, admin):
        """Administrador: reenvia as ordens que ficaram em FALHA (recuperação)."""
        exigir(admin, "gerenciar_mercado")
        ordens = self.repo.listar_ordens_por_status(dominio.FALHA)
        for ordem in ordens:
            self._transmitir(ordem)
        self.auditoria.registrar(admin.id, "REENVIO_FALHAS", f"{len(ordens)} ordem(ns)")
        return [self.repo.buscar_ordem(o.id) for o in ordens]

    # ---------------- consultar ----------------
    def minhas_ordens(self, usuario):
        exigir(usuario, "ver_ordens")
        return self.repo.listar_ordens(usuario.id)

    def consultar(self, usuario, ordem_id):
        exigir(usuario, "ver_ordens")
        ordem = self._ordem_do_usuario(usuario, ordem_id)
        return ordem, self.repo.listar_historico(ordem.id)

    # ---------------- alterar e cancelar ----------------
    def alterar(self, usuario, ordem_id, quantidade, preco):
        exigir(usuario, "alterar_ordem")
        ordem = self._ordem_do_usuario(usuario, ordem_id)
        if not ordem.pode_alterar():
            raise OrdemRecusada(f"Ordem {ordem.status} não pode ser alterada (só ordem em FALHA).")
        dados = validar(DadosAlteracao, quantidade=quantidade, preco=preco)
        quantidade, preco = dados.quantidade, dados.preco
        conta = self.repo.buscar_conta(usuario.id)
        posicao = self.repo.buscar_posicao(usuario.id, ordem.ticker)
        antes = f"{ordem.quantidade} x {ordem.preco}"
        ordem.quantidade, ordem.preco = quantidade, preco
        ordem.verificar(conta, posicao)  # valida de novo com os valores novos
        if ordem.valor_total > conta.limite_por_ordem:
            raise OrdemRecusada("Limite de risco excedido.")
        self.repo.salvar_ordem(ordem)
        self.auditoria.registrar(usuario.id, "ORDEM_ALTERADA", f"#{ordem.id}: {antes} -> {quantidade} x {preco}")
        return ordem

    def cancelar(self, usuario, ordem_id):
        exigir(usuario, "cancelar_ordem")
        ordem = self._ordem_do_usuario(usuario, ordem_id)
        if not ordem.pode_cancelar():
            raise OrdemRecusada(f"Ordem {ordem.status} não pode ser cancelada.")  # RN-09
        self._mudar(ordem, dominio.CANCELADA, "Cancelada pelo investidor")
        self.notificacoes.notificar(usuario.id, f"Ordem #{ordem.id} cancelada.")
        return ordem

    # ---------------- auxiliares ----------------
    def _ordem_do_usuario(self, usuario, ordem_id):
        if not str(ordem_id).strip().isdigit():
            raise DadoInvalido("Número da ordem inválido.")
        ordem = self.repo.buscar_ordem(int(ordem_id))
        if ordem is None:
            raise DadoInvalido("Ordem não encontrada.")
        if ordem.usuario_id != usuario.id:  # RN-08
            self.auditoria.registrar(usuario.id, "ACESSO_NEGADO", f"tentou ver a ordem #{ordem.id}")
            raise AcessoNegado("Essa ordem não é sua.")
        return ordem

    def _mudar(self, ordem, novo_status, motivo):
        """Toda mudança de status passa por aqui: grava histórico e auditoria (RN-05)."""
        ordem.mudar_status(novo_status, motivo)
        log.info("Ordem %s -> %s", ordem.id, novo_status)
        self.repo.salvar_ordem(ordem)
        self.repo.inserir_historico(ordem)
        self.auditoria.registrar(ordem.usuario_id, f"ORDEM_{novo_status}", f"#{ordem.id}: {motivo}")


# Funções do Administrador e do Analista de Risco.


class ServicoAdmin:
    def __init__(self, repo, auditoria, bolsa):
        self.repo = repo
        self.auditoria = auditoria
        self.bolsa = bolsa

    # ----- administrador -----
    def listar_usuarios(self, admin):
        exigir(admin, "gerenciar_usuarios")
        return self.repo.listar_usuarios()

    def listar_ativos(self, admin):
        exigir(admin, "gerenciar_ativos")
        return self.repo.listar_ativos()

    def cadastrar_investidor(self, admin, nome, email, senha, saldo):
        exigir(admin, "gerenciar_usuarios")
        dados = validar(DadosInvestidor, nome=nome, email=email, senha=senha, saldo=saldo)
        if self.repo.buscar_usuario_por_email(dados.email):
            raise DadoInvalido("Já existe usuário com esse e-mail.")
        novo_id = self.repo.inserir_usuario(dados.nome.strip(), dados.email, gerar_hash(dados.senha), "INVESTIDOR")
        self.repo.inserir_conta(novo_id, dados.saldo, 5000)
        self.auditoria.registrar(admin.id, "USUARIO_CRIADO", f"#{novo_id} {dados.email}")
        return novo_id

    def desbloquear(self, admin, usuario_id):
        exigir(admin, "gerenciar_usuarios")
        usuario = self.repo.buscar_usuario(usuario_id)
        if usuario is None:
            raise DadoInvalido("Usuário não encontrado.")
        usuario.bloqueado, usuario.tentativas_erradas = False, 0
        self.repo.salvar_tentativas(usuario)
        self.auditoria.registrar(admin.id, "USUARIO_DESBLOQUEADO", f"#{usuario_id}")

    def cadastrar_ativo(self, admin, ticker, nome, tipo, preco):
        exigir(admin, "gerenciar_ativos")
        dados = validar(DadosAtivo, ticker=ticker, nome=nome, tipo=tipo, preco=preco)
        if self.repo.buscar_ativo(dados.ticker):
            raise DadoInvalido("Esse ativo já existe.")
        self.repo.inserir_ativo(dados.ticker, dados.nome.strip(), dados.tipo, dados.preco)
        self.auditoria.registrar(admin.id, "ATIVO_CRIADO", f"{dados.ticker} {dados.tipo} R$ {dados.preco}")

    def suspender_ou_liberar_ativo(self, admin, ticker, liberar):
        exigir(admin, "gerenciar_ativos")
        ticker = str(ticker).strip().upper()
        if not self.repo.buscar_ativo(ticker):
            raise DadoInvalido("Ativo não encontrado.")
        self.repo.mudar_situacao_ativo(ticker, liberar)
        self.auditoria.registrar(admin.id, "ATIVO_LIBERADO" if liberar else "ATIVO_SUSPENSO", ticker)

    def abrir_ou_fechar_mercado(self, admin, abrir):
        exigir(admin, "gerenciar_mercado")
        self.repo.salvar_parametro("mercado_aberto", "SIM" if abrir else "NAO")
        self.auditoria.registrar(admin.id, "MERCADO_ABERTO" if abrir else "MERCADO_FECHADO", "")

    def ligar_ou_desligar_bolsa(self, admin, ligar):
        exigir(admin, "gerenciar_mercado")
        self.bolsa.disponivel = ligar
        self.auditoria.registrar(admin.id, "BOLSA_LIGADA" if ligar else "BOLSA_DESLIGADA", "")

    # ----- analista de risco -----
    def alterar_limite(self, analista, usuario_id, novo_limite):
        exigir(analista, "gerenciar_limites")
        conta = self.repo.buscar_conta(usuario_id)
        if conta is None:
            raise DadoInvalido("Investidor não encontrado.")
        antes = conta.limite_por_ordem
        conta.limite_por_ordem = validar(DadosLimite, limite=novo_limite).limite
        self.repo.salvar_conta(conta)
        self.auditoria.registrar(analista.id, "LIMITE_ALTERADO", f"investidor #{usuario_id}: {antes} -> {novo_limite}")

    def ordens_rejeitadas(self, analista):
        exigir(analista, "ver_ordens_rejeitadas")
        return self.repo.listar_ordens_por_status(dominio.REJEITADA) + \
            self.repo.listar_ordens_por_status(dominio.FALHA)