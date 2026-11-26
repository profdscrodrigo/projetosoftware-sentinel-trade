# CAMADA DE INTEGRAÇÕES: sistemas externos SIMULADOS (Bolsa/Corretora e Provedor de Cotações).
import random
from abc import ABC, abstractmethod
from decimal import Decimal

from .dominio import BolsaIndisponivel


class BolsaBase(ABC):
    """Interface da bolsa: se um dia existir uma bolsa real, basta criar outra classe filha."""

    @abstractmethod
    def enviar(self, ordem, preco_referencia):
        """Devolve (True, mensagem) se executou ou (False, motivo) se rejeitou."""


class BolsaSimulada(BolsaBase):
    def __init__(self):
        self.disponivel = True       # o admin pode "desligar" a bolsa para testar falhas
        self.ordens_executadas = set()

    def enviar(self, ordem, preco_referencia):
        if not self.disponivel:
            raise BolsaIndisponivel("A bolsa não respondeu (fora do ar).")
        if ordem.id in self.ordens_executadas:
            return True, "Ordem já tinha sido executada (não executa de novo)."
        # a bolsa rejeita preço mais de 10% longe do preço de referência
        if abs(ordem.preco - preco_referencia) > preco_referencia * 10 / 100:
            return False, "Preço muito diferente do preço de mercado (mais de 10%)."
        self.ordens_executadas.add(ordem.id)
        return True, "Executada pela bolsa simulada."


class ProvedorCotacoes:
    """Provedor de cotações simulado: o preço varia até 2% para cima ou para baixo."""

    def __init__(self):
        self.disponivel = True

    def buscar_preco(self, preco_referencia):
        if not self.disponivel:
            return None
        # random serve aqui só para simular o mercado (não é usado para segurança)
        variacao = Decimal(str(round(random.uniform(-2, 2), 2)))  # nosec B311
        return (preco_referencia * (1 + variacao / 100)).quantize(Decimal("0.01"))