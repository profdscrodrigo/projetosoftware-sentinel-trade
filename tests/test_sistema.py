# Testes do sistema com o banco PostgreSQL: ordens (UC-05, UC-06, UC-10), segurança e API.
# Cada teste cria um "schema" próprio no PostgreSQL e apaga no final.
import unittest
import uuid
from decimal import Decimal

from fastapi.testclient import TestClient
from sqlalchemy.exc import DBAPIError

from tests import SENHA, URL_TESTE
from sentineltrade.api import Sistema, criar_api
from sentineltrade.dominio import AcessoNegado, DadoInvalido, LoginInvalido, OrdemRecusada
from sentineltrade.servicos import PrecisaConfirmar, conferir_senha, gerar_hash


@unittest.skipUnless(URL_TESTE, "configure SENTINEL_TEST_DATABASE_URL para rodar este teste")
class TesteComBanco(unittest.TestCase):
    def setUp(self):
        self.codigos = []
        self.s = Sistema(URL_TESTE, schema="teste_" + uuid.uuid4().hex[:8],
                         enviar_codigo=lambda usuario, codigo: self.codigos.append(codigo))
        self.ana = self.s.repo.buscar_usuario(3)
        self.bruno = self.s.repo.buscar_usuario(4)
        self.admin = self.s.repo.buscar_usuario(1)
        self.analista = self.s.repo.buscar_usuario(2)

    def tearDown(self):
        self.s.banco.apagar_schema()
        self.s.banco.fechar()

    def comprar(self, usuario, ticker, quantidade, preco, **extra):
        return self.s.ordens.enviar(usuario, "COMPRA", ticker, quantidade, preco, str(uuid.uuid4()), **extra)

    def vender(self, usuario, ticker, quantidade, preco):
        return self.s.ordens.enviar(usuario, "VENDA", ticker, quantidade, preco, str(uuid.uuid4()))


class TestOrdens(TesteComBanco):
    def saldo(self, usuario):
        return self.s.repo.buscar_conta(usuario.id).saldo

    def quantidade(self, usuario, ticker):
        return self.s.repo.buscar_posicao(usuario.id, ticker).quantidade

    def test_compra_executada(self):
        ordem = self.comprar(self.ana, "VRDE3", 100, "12.80")
        self.assertEqual(ordem.status, "EXECUTADA")
        self.assertEqual(self.saldo(self.ana), Decimal("48720.00"))
        self.assertEqual(self.quantidade(self.ana, "VRDE3"), 100)

    def test_venda_executada(self):
        self.vender(self.ana, "SENT3", 40, "20.50")
        self.assertEqual(self.quantidade(self.ana, "SENT3"), 60)
        self.assertEqual(self.saldo(self.ana), Decimal("50820.00"))

    def test_compra_sem_saldo_rejeitada(self):
        self.s.admin.alterar_limite(self.analista, self.bruno.id, "20000")
        ordem = self.comprar(self.bruno, "TECH11", 120, "98.40", confirmado=True)
        self.assertEqual(ordem.status, "REJEITADA")
        self.assertEqual(self.saldo(self.bruno), Decimal("10000.00"))

    def test_venda_sem_posicao_rejeitada(self):
        self.assertEqual(self.vender(self.bruno, "SENT3", 1, "20.50").status, "REJEITADA")

    def test_limite_de_risco(self):
        ordem = self.comprar(self.bruno, "SENT3", 300, "20.50")  # R$ 6.150 > limite R$ 5.000
        self.assertEqual(ordem.status, "REJEITADA")
        self.assertIn("Limite", ordem.motivo)

    def test_mercado_fechado(self):
        self.s.admin.abrir_ou_fechar_mercado(self.admin, False)
        self.assertEqual(self.comprar(self.ana, "SENT3", 10, "20.50").status, "REJEITADA")

    def test_ativo_suspenso(self):
        self.assertEqual(self.comprar(self.ana, "BLOQ3", 10, "5.00").status, "REJEITADA")

    def test_dados_invalidos(self):
        with self.assertRaises(DadoInvalido):
            self.comprar(self.ana, "SENT3", -5, "20.50")
        self.assertEqual(self.s.ordens.minhas_ordens(self.ana), [])

    def test_confirmacao_acima_de_10_mil(self):
        with self.assertRaises(PrecisaConfirmar):
            self.comprar(self.ana, "TECH11", 150, "98.40")
        self.assertEqual(self.comprar(self.ana, "TECH11", 150, "98.40", confirmado=True).status, "EXECUTADA")

    def test_mesma_chave_nao_duplica(self):
        # RF-17 / RN-03
        primeira = self.s.ordens.enviar(self.ana, "COMPRA", "SENT3", 10, "20.50", "chave-repetida")
        segunda = self.s.ordens.enviar(self.ana, "COMPRA", "SENT3", 10, "20.50", "chave-repetida")
        self.assertEqual(primeira.id, segunda.id)
        self.assertEqual(self.quantidade(self.ana, "SENT3"), 110)  # comprou uma vez só

    def test_bolsa_fora_do_ar(self):
        # RN-07: a ordem não é apagada e a carteira não muda
        self.s.admin.ligar_ou_desligar_bolsa(self.admin, False)
        ordem = self.comprar(self.ana, "SENT3", 10, "20.50")
        self.assertEqual(ordem.status, "FALHA")
        self.assertEqual(self.saldo(self.ana), Decimal("50000.00"))
        # bolsa volta e o admin reenvia
        self.s.admin.ligar_ou_desligar_bolsa(self.admin, True)
        self.s.ordens.reenviar_falhas(self.admin)
        self.assertEqual(self.s.repo.buscar_ordem(ordem.id).status, "EXECUTADA")
        self.assertEqual(self.quantidade(self.ana, "SENT3"), 110)

    def test_bolsa_rejeita_preco_muito_diferente(self):
        self.assertEqual(self.comprar(self.ana, "SENT3", 10, "30.00").status, "REJEITADA")

    def test_cancelar_ordem_em_falha(self):
        self.s.admin.ligar_ou_desligar_bolsa(self.admin, False)
        ordem = self.comprar(self.ana, "SENT3", 10, "20.50")
        self.s.ordens.cancelar(self.ana, ordem.id)
        self.assertEqual(self.s.repo.buscar_ordem(ordem.id).status, "CANCELADA")

    def test_ordem_executada_nao_cancela(self):
        ordem = self.comprar(self.ana, "SENT3", 10, "20.50")
        with self.assertRaises(OrdemRecusada):
            self.s.ordens.cancelar(self.ana, ordem.id)

    def test_alterar_ordem_em_falha(self):
        self.s.admin.ligar_ou_desligar_bolsa(self.admin, False)
        ordem = self.comprar(self.ana, "SENT3", 10, "20.50")
        self.s.ordens.alterar(self.ana, ordem.id, 20, "20.00")
        self.assertEqual(self.s.repo.buscar_ordem(ordem.id).quantidade, 20)

    def test_nao_mexe_na_ordem_de_outro(self):
        # RN-08
        ordem = self.comprar(self.ana, "SENT3", 10, "20.50")
        with self.assertRaises(AcessoNegado):
            self.s.ordens.consultar(self.bruno, ordem.id)

    def test_historico_e_auditoria(self):
        # RN-05: toda mudança de status fica registrada
        ordem = self.comprar(self.ana, "SENT3", 10, "20.50")
        _, historico = self.s.ordens.consultar(self.ana, ordem.id)
        self.assertEqual([h["status"] for h in historico], ["CRIADA", "VALIDADA", "ENVIADA", "EXECUTADA"])
        acoes = [r["acao"] for r in self.s.auditoria.listar(self.admin)]
        self.assertIn("ORDEM_EXECUTADA", acoes)

    def test_notificacao(self):
        self.comprar(self.ana, "SENT3", 10, "20.50")
        mensagens = [n["mensagem"] for n in self.s.notificacoes.listar(self.ana)]
        self.assertTrue(any("EXECUTADA" in m for m in mensagens))

    def test_circuit_breaker_das_cotacoes(self):
        # RNF-03: provedor fora do ar -> usa o último preço; depois de 3 falhas para de chamar
        self.s.carteira.provedor.disponivel = False
        cotacoes = self.s.carteira.cotacoes(self.ana)
        self.assertTrue(all("DESATUALIZADA" in situacao for _, _, situacao in cotacoes))
        self.assertGreater(self.s.carteira.pausado_ate, 0)


class TestSeguranca(TesteComBanco):
    def test_hash_da_senha(self):
        hash_senha = gerar_hash("Teste@123")
        self.assertNotIn("Teste@123", hash_senha)  # a senha não aparece no hash
        self.assertTrue(hash_senha.startswith("$2b$"))  # bcrypt
        self.assertTrue(conferir_senha("Teste@123", hash_senha))
        self.assertFalse(conferir_senha("errada", hash_senha))

    def test_login_com_mfa(self):
        self.s.login.entrar("ana@sentineltrade.sim", SENHA)
        token, usuario = self.s.login.confirmar_codigo("ana@sentineltrade.sim", self.codigos[-1])
        self.assertEqual(usuario.nome, "Ana Exemplo")
        self.assertEqual(self.s.login.usuario_da_sessao(token).id, usuario.id)

    def test_senha_errada(self):
        with self.assertRaises(LoginInvalido):
            self.s.login.entrar("ana@sentineltrade.sim", "Errada@123")

    def test_codigo_usado_uma_vez(self):
        self.s.login.entrar("ana@sentineltrade.sim", SENHA)
        self.s.login.confirmar_codigo("ana@sentineltrade.sim", self.codigos[-1])
        with self.assertRaises(LoginInvalido):
            self.s.login.confirmar_codigo("ana@sentineltrade.sim", self.codigos[-1])

    def test_token_alterado_ou_expirado(self):
        self.s.login.entrar("ana@sentineltrade.sim", SENHA)
        token, _ = self.s.login.confirmar_codigo("ana@sentineltrade.sim", self.codigos[-1])
        with self.assertRaises(LoginInvalido):  # assinatura alterada
            self.s.login.usuario_da_sessao(token[:-1] + ("0" if token[-1] != "0" else "1"))
        id_sessao = token.split(".")[0]
        usuario_id, _ = self.s.login.sessoes[id_sessao]
        self.s.login.sessoes[id_sessao] = (usuario_id, 0)  # simula que passaram os 30 minutos
        with self.assertRaises(LoginInvalido):
            self.s.login.usuario_da_sessao(token)

    def test_codigo_mfa_errado(self):
        self.s.login.entrar("ana@sentineltrade.sim", SENHA)
        errado = "000000" if self.codigos[-1] != "000000" else "111111"
        with self.assertRaises(LoginInvalido):
            self.s.login.confirmar_codigo("ana@sentineltrade.sim", errado)

    def test_bloqueio_depois_de_5_erros(self):
        for _ in range(5):
            with self.assertRaises(LoginInvalido):
                self.s.login.entrar("bruno@sentineltrade.sim", "Errada@123")
        with self.assertRaises(LoginInvalido):  # nem a senha certa funciona mais
            self.s.login.entrar("bruno@sentineltrade.sim", SENHA)

    def test_sql_injection_no_login(self):
        with self.assertRaises(LoginInvalido):
            self.s.login.entrar("' OR '1'='1", "' OR '1'='1")

    def test_investidor_nao_acessa_funcoes_de_admin(self):
        with self.assertRaises(AcessoNegado):
            self.s.admin.cadastrar_ativo(self.ana, "NOVO3", "Novo", "ACAO", "10")
        with self.assertRaises(AcessoNegado):
            self.s.auditoria.listar(self.ana)

    def test_admin_nao_envia_ordem(self):
        with self.assertRaises(AcessoNegado):
            self.comprar(self.admin, "SENT3", 1, "20.50")

    def test_auditoria_nao_pode_ser_apagada(self):
        self.comprar(self.ana, "SENT3", 1, "20.50")
        with self.assertRaises(DBAPIError):
            self.s.banco.executar("DELETE FROM auditoria")
        self.s.banco.conexao.rollback()
        with self.assertRaises(DBAPIError):
            self.s.banco.executar("UPDATE auditoria SET detalhes = 'alterado'")
        self.s.banco.conexao.rollback()


class TestApi(TesteComBanco):
    def setUp(self):
        super().setUp()
        self.cliente = TestClient(criar_api(self.s))

    def entrar(self, email):
        self.cliente.post("/login", json={"email": email, "senha": SENHA})
        resposta = self.cliente.post("/login/codigo", json={"email": email, "codigo": self.codigos[-1]})
        return {"Authorization": f"Bearer {resposta.json()['token']}"}

    def test_login_e_compra(self):
        ana = self.entrar("ana@sentineltrade.sim")
        resposta = self.cliente.post("/ordens", headers=ana,
                                     json={"tipo": "COMPRA", "ticker": "SENT3", "quantidade": 10, "preco": 20.50})
        self.assertEqual(resposta.status_code, 200)
        self.assertEqual(resposta.json()["status"], "EXECUTADA")

    def test_sem_login_nao_entra(self):
        self.assertEqual(self.cliente.get("/saldo").status_code, 401)
        self.assertEqual(self.cliente.get("/saldo", headers={"Authorization": "Bearer inventado"}).status_code, 401)

    def test_senha_errada(self):
        resposta = self.cliente.post("/login", json={"email": "ana@sentineltrade.sim", "senha": "Errada@123"})
        self.assertEqual(resposta.status_code, 401)

    def test_dados_invalidos_retornam_422(self):
        ana = self.entrar("ana@sentineltrade.sim")
        resposta = self.cliente.post("/ordens", headers=ana,
                                     json={"tipo": "COMPRA", "ticker": "X'; DROP TABLE ordens;--", "quantidade": -1, "preco": 0})
        self.assertEqual(resposta.status_code, 422)

    def test_investidor_nao_acessa_admin(self):
        ana = self.entrar("ana@sentineltrade.sim")
        self.assertEqual(self.cliente.get("/admin/auditoria", headers=ana).status_code, 403)

    def test_confirmacao_acima_do_limite(self):
        ana = self.entrar("ana@sentineltrade.sim")
        pedido = {"tipo": "COMPRA", "ticker": "TECH11", "quantidade": 150, "preco": 98.40}
        self.assertEqual(self.cliente.post("/ordens", headers=ana, json=pedido).status_code, 409)
        resposta = self.cliente.post("/ordens", headers=ana, json={**pedido, "confirmado": True})
        self.assertEqual(resposta.json()["status"], "EXECUTADA")


if __name__ == "__main__":
    unittest.main()