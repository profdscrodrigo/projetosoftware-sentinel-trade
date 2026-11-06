# Testes do domínio: validação das entradas (Pydantic) e estados da ordem. Não precisam de banco.
import unittest
from decimal import Decimal

import tests  # noqa: F401  (ajusta o caminho do código)
from sentineltrade import dominio as e
from sentineltrade.dominio import (Conta, DadoInvalido, DadosAtivo, DadosInvestidor, DadosOrdem, OrdemCompra,
                                   OrdemRecusada, OrdemVenda, Posicao, validar)


def ordem(**mudancas):
    dados = {"tipo": "COMPRA", "ticker": "SENT3", "quantidade": 10, "preco": "20.50"}
    dados.update(mudancas)
    return validar(DadosOrdem, **dados)


class TestValidacoes(unittest.TestCase):
    def test_ordem_valida(self):
        dados = ordem(ticker=" sent3 ")
        self.assertEqual(dados.ticker, "SENT3")
        self.assertEqual(dados.preco, Decimal("20.50"))

    def test_ticker_invalido(self):
        for errado in ["", "ABC", "12345", "SENT3'; DROP TABLE ordens;--"]:
            with self.assertRaises(DadoInvalido):
                ordem(ticker=errado)

    def test_quantidade_invalida(self):
        for errado in [0, -5, "abc", 1.5, 200000]:
            with self.assertRaises(DadoInvalido):
                ordem(quantidade=errado)

    def test_preco_invalido(self):
        for errado in ["0", "-1", "abc", "10.123"]:
            with self.assertRaises(DadoInvalido):
                ordem(preco=errado)

    def test_tipo_invalido(self):
        with self.assertRaises(DadoInvalido):
            ordem(tipo="ALUGUEL")

    def test_investidor(self):
        base = {"nome": "Carla Teste", "email": "Carla@Teste.com", "senha": "Forte@123", "saldo": "100"}
        self.assertEqual(validar(DadosInvestidor, **base).email, "carla@teste.com")
        for campo, errado in [("email", "carla@"), ("senha", "fraca123"), ("senha", "SEMMINUSCULA1!"), ("nome", "A")]:
            with self.assertRaises(DadoInvalido):
                validar(DadosInvestidor, **{**base, campo: errado})

    def test_tipo_de_ativo(self):
        with self.assertRaises(DadoInvalido):
            validar(DadosAtivo, ticker="NOVO3", nome="Novo", tipo="CRIPTO", preco="10")


def nova_ordem(status=e.CRIADA):
    return OrdemCompra(usuario_id=1, ticker="SENT3", quantidade=10, preco="20.00", chave="teste", status=status)


class TestEstados(unittest.TestCase):
    def test_caminho_normal(self):
        ordem = nova_ordem()
        for status in [e.VALIDADA, e.ENVIADA, e.EXECUTADA]:
            ordem.mudar_status(status)
        self.assertEqual(ordem.status, e.EXECUTADA)

    def test_falha_e_reenvio(self):
        ordem = nova_ordem(e.ENVIADA)
        ordem.mudar_status(e.FALHA)
        ordem.mudar_status(e.ENVIADA)
        ordem.mudar_status(e.EXECUTADA)
        self.assertEqual(ordem.status, e.EXECUTADA)

    def test_estados_finais_nao_mudam(self):
        # RN-04: executada, rejeitada ou cancelada não volta
        for final in [e.EXECUTADA, e.REJEITADA, e.CANCELADA]:
            for outro in e.TRANSICOES:
                with self.assertRaises(OrdemRecusada):
                    nova_ordem(final).mudar_status(outro)

    def test_nao_pula_etapas(self):
        with self.assertRaises(OrdemRecusada):
            nova_ordem(e.CRIADA).mudar_status(e.EXECUTADA)

    def test_quem_pode_cancelar(self):
        self.assertTrue(nova_ordem(e.FALHA).pode_cancelar())
        self.assertTrue(nova_ordem(e.VALIDADA).pode_cancelar())
        self.assertFalse(nova_ordem(e.EXECUTADA).pode_cancelar())  # RN-09


class TestCompraEVenda(unittest.TestCase):
    def test_compra_atualiza_saldo_e_preco_medio(self):
        conta = Conta(1, "1000", "5000")
        posicao = Posicao(1, "SENT3", 10, "10.00")
        nova_ordem().executar(conta, posicao)
        self.assertEqual(conta.saldo, Decimal("800"))
        self.assertEqual(posicao.quantidade, 20)
        self.assertEqual(posicao.preco_medio, Decimal("15.00"))

    def test_compra_sem_saldo(self):
        with self.assertRaises(OrdemRecusada):
            nova_ordem().verificar(Conta(1, "100", "5000"), Posicao(1, "SENT3"))

    def test_venda_sem_posicao(self):
        venda = OrdemVenda(usuario_id=1, ticker="SENT3", quantidade=10, preco="20.00", chave="teste")
        with self.assertRaises(OrdemRecusada):
            venda.verificar(Conta(1, "0", "5000"), Posicao(1, "SENT3", 5))



if __name__ == "__main__":
    unittest.main()