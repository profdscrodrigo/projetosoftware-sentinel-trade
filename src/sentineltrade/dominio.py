# CAMADA DE DOMÍNIO: erros, validação das entradas (Pydantic) e as classes
# Usuário, Conta, Posição e Ordem
import re
from abc import ABC, abstractmethod
from decimal import Decimal
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field, ValidationError, field_validator


# Erros do sistema. Todos herdam de ErroSentinel, assim a API
# consegue tratar qualquer um deles com um único "except".


class ErroSentinel(Exception):
    pass


class DadoInvalido(ErroSentinel):
    pass


class LoginInvalido(ErroSentinel):
    pass


class AcessoNegado(ErroSentinel):
    pass


class OrdemRecusada(ErroSentinel):
    # saldo insuficiente, sem posição, limite excedido, mercado fechado...
    pass


class BolsaIndisponivel(ErroSentinel):
    pass


# Validação dos dados de entrada com Pydantic (RNF-01).
# Os mesmos modelos são usados pela API (FastAPI) e pelos serviços.


class Modelo(BaseModel):
    # tira os espaços do começo e do fim dos textos antes de validar
    model_config = ConfigDict(str_strip_whitespace=True)


class DadosOrdem(Modelo):
    tipo: Literal["COMPRA", "VENDA"]
    ticker: str = Field(min_length=5, max_length=6)
    quantidade: int = Field(gt=0, le=100000)
    preco: Decimal = Field(gt=0, le=1000000, decimal_places=2)

    @field_validator("ticker")
    @classmethod
    def validar_ticker(cls, valor):
        valor = valor.strip().upper()
        if not re.fullmatch(r"[A-Z]{4}[0-9]{1,2}", valor):
            raise ValueError("Código de ativo inválido (exemplo: SENT3)")
        return valor


class DadosAlteracao(Modelo):
    quantidade: int = Field(gt=0, le=100000)
    preco: Decimal = Field(gt=0, le=1000000, decimal_places=2)


class DadosInvestidor(Modelo):
    nome: str = Field(min_length=3, max_length=100)
    email: str = Field(max_length=120)
    senha: str = Field(min_length=8, max_length=64)
    saldo: Decimal = Field(ge=0, decimal_places=2)

    @field_validator("email")
    @classmethod
    def validar_email(cls, valor):
        valor = valor.strip().lower()
        if not re.fullmatch(r"[a-z0-9._-]+@[a-z0-9-]+(\.[a-z0-9-]+)+", valor):
            raise ValueError("E-mail inválido")
        return valor

    @field_validator("senha")
    @classmethod
    def validar_senha(cls, valor):
        # precisa ter letra maiúscula, minúscula, número e símbolo
        if valor.lower() == valor or valor.upper() == valor or not any(c.isdigit() for c in valor) or valor.isalnum():
            raise ValueError("A senha precisa ter maiúscula, minúscula, número e símbolo")
        return valor


class DadosAtivo(Modelo):
    ticker: str = Field(min_length=5, max_length=6)
    nome: str = Field(min_length=3, max_length=100)
    tipo: Literal["ACAO", "ETF", "FII"]
    preco: Decimal = Field(gt=0, le=1000000, decimal_places=2)

    @field_validator("ticker")
    @classmethod
    def validar_ticker(cls, valor):
        return DadosOrdem.validar_ticker(valor)


class DadosLimite(Modelo):
    limite: Decimal = Field(gt=0, le=10000000, decimal_places=2)


def validar(modelo, **dados):
    """Cria o modelo do Pydantic e transforma o erro dele numa mensagem simples."""
    try:
        return modelo(**dados)
    except ValidationError as erro:
        primeiro = erro.errors()[0]
        campo = primeiro["loc"][0] if primeiro["loc"] else "dado"
        raise DadoInvalido(f"{campo}: {primeiro['msg']}")


# Usuários do sistema.
# Usuario é uma classe ABSTRATA: só existem Investidor, Administrador e AnalistaRisco (HERANÇA).


class Usuario(ABC):
    def __init__(self, id, nome, email, senha_hash, bloqueado=False, tentativas_erradas=0):
        self.id = id
        self.nome = nome
        self.email = email
        self.__senha_hash = senha_hash  # atributo privado (ENCAPSULAMENTO)
        self.bloqueado = bloqueado
        self.tentativas_erradas = tentativas_erradas

    @property
    def senha_hash(self):
        return self.__senha_hash

    @property
    @abstractmethod
    def perfil(self):
        pass

    @property
    @abstractmethod
    def permissoes(self):
        pass

    def pode(self, acao):
        return not self.bloqueado and acao in self.permissoes


class Investidor(Usuario):
    perfil = "INVESTIDOR"
    permissoes = {"ver_carteira", "ver_cotacoes", "enviar_ordem", "ver_ordens", "alterar_ordem", "cancelar_ordem"}


class Administrador(Usuario):
    perfil = "ADMINISTRADOR"
    permissoes = {"gerenciar_ativos", "gerenciar_usuarios", "gerenciar_mercado", "ver_auditoria", "ver_cotacoes"}


class AnalistaRisco(Usuario):
    perfil = "ANALISTA_RISCO"
    permissoes = {"gerenciar_limites", "ver_ordens_rejeitadas", "ver_cotacoes"}


def criar_usuario(perfil, **dados):
    classes = {"INVESTIDOR": Investidor, "ADMINISTRADOR": Administrador, "ANALISTA_RISCO": AnalistaRisco}
    return classes[perfil](**dados)


# Conta (saldo) e Posição (quantidade de um ativo na carteira).


class Conta:
    def __init__(self, usuario_id, saldo, limite_por_ordem):
        self.usuario_id = usuario_id
        self.__saldo = Decimal(saldo)  # privado: só muda por debitar/creditar
        self.limite_por_ordem = Decimal(limite_por_ordem)

    @property
    def saldo(self):
        return self.__saldo

    def debitar(self, valor):
        if valor > self.__saldo:
            raise OrdemRecusada(f"Saldo insuficiente: precisa de R$ {valor}, tem R$ {self.__saldo}.")
        self.__saldo -= valor

    def creditar(self, valor):
        self.__saldo += valor


class Posicao:
    def __init__(self, usuario_id, ticker, quantidade=0, preco_medio=0):
        self.usuario_id = usuario_id
        self.ticker = ticker
        self.__quantidade = quantidade
        self.preco_medio = Decimal(preco_medio)

    @property
    def quantidade(self):
        return self.__quantidade

    def adicionar(self, quantidade, preco):
        # recalcula o preço médio: (total antigo + total novo) / quantidade nova
        total = self.preco_medio * self.__quantidade + preco * quantidade
        self.__quantidade += quantidade
        self.preco_medio = (total / self.__quantidade).quantize(Decimal("0.01"))

    def remover(self, quantidade):
        if quantidade > self.__quantidade:
            raise OrdemRecusada(f"Posição insuficiente em {self.ticker}: tem {self.__quantidade}.")
        self.__quantidade -= quantidade


# Ordem de compra/venda e seus estados (UC-08).
# Ordem é ABSTRATA; OrdemCompra e OrdemVenda fazem coisas diferentes (POLIMORFISMO).


CRIADA = "CRIADA"
VALIDADA = "VALIDADA"
ENVIADA = "ENVIADA"
PARCIALMENTE_EXECUTADA = "PARCIALMENTE_EXECUTADA"
EXECUTADA = "EXECUTADA"
CANCELADA = "CANCELADA"
REJEITADA = "REJEITADA"
FALHA = "FALHA"

# para onde cada estado pode ir (RN-04: estados finais não voltam)
TRANSICOES = {
    CRIADA: [VALIDADA, REJEITADA],
    VALIDADA: [ENVIADA, CANCELADA],
    ENVIADA: [EXECUTADA, PARCIALMENTE_EXECUTADA, REJEITADA, FALHA],
    PARCIALMENTE_EXECUTADA: [EXECUTADA, CANCELADA],
    FALHA: [ENVIADA, CANCELADA],
    EXECUTADA: [],
    REJEITADA: [],
    CANCELADA: [],
}


class Ordem(ABC):
    def __init__(self, usuario_id, ticker, quantidade, preco, chave, id=None, status=CRIADA, motivo=None):
        self.id = id
        self.usuario_id = usuario_id
        self.ticker = ticker
        self.quantidade = quantidade
        self.preco = Decimal(preco)
        self.chave = chave
        self.__status = status
        self.motivo = motivo

    @property
    def status(self):
        return self.__status

    @property
    def valor_total(self):
        return self.preco * self.quantidade

    def mudar_status(self, novo_status, motivo=None):
        if novo_status not in TRANSICOES[self.__status]:
            raise OrdemRecusada(f"Não é possível mudar a ordem de {self.__status} para {novo_status}.")
        self.__status = novo_status
        self.motivo = motivo

    def pode_cancelar(self):
        return CANCELADA in TRANSICOES[self.__status]

    def pode_alterar(self):
        return self.__status == FALHA  # só ordem que ainda não chegou na bolsa

    @property
    @abstractmethod
    def tipo(self):
        pass

    @abstractmethod
    def verificar(self, conta, posicao):
        """Confere se tem saldo (compra) ou ações (venda). Lança OrdemRecusada se não tiver."""

    @abstractmethod
    def executar(self, conta, posicao):
        """Atualiza saldo e carteira depois que a bolsa executou."""


class OrdemCompra(Ordem):
    tipo = "COMPRA"

    def verificar(self, conta, posicao):
        if self.valor_total > conta.saldo:
            raise OrdemRecusada(f"Saldo insuficiente: precisa de R$ {self.valor_total}, tem R$ {conta.saldo}.")

    def executar(self, conta, posicao):
        conta.debitar(self.valor_total)
        posicao.adicionar(self.quantidade, self.preco)


class OrdemVenda(Ordem):
    tipo = "VENDA"

    def verificar(self, conta, posicao):
        if self.quantidade > posicao.quantidade:
            raise OrdemRecusada(f"Posição insuficiente em {self.ticker}: tem {posicao.quantidade}.")

    def executar(self, conta, posicao):
        posicao.remover(self.quantidade)
        conta.creditar(self.valor_total)


def criar_ordem(tipo, **dados):
    if tipo == "COMPRA":
        return OrdemCompra(**dados)
    return OrdemVenda(**dados)