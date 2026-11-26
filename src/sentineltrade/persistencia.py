# CAMADA DE PERSISTÊNCIA: conexão com o PostgreSQL (SQLAlchemy) e todos os comandos SQL.
# Os valores vão separados do SQL (:nome) -> consulta parametrizada, sem SQL injection.
import os
from contextlib import contextmanager

from sqlalchemy import create_engine, text

from .dominio import Conta, Posicao, criar_ordem, criar_usuario

PASTA_DATABASE = os.path.join(os.path.dirname(__file__), "..", "..", "database")


class Banco:
    def __init__(self, url, schema=None):
        # o SQLAlchemy usa o psycopg por baixo para falar com o PostgreSQL
        url = url.replace("postgresql://", "postgresql+psycopg://", 1)
        self.engine = create_engine(url)
        self.conexao = self.engine.connect()
        self.em_transacao = False
        self.schema = schema
        if schema:  # usado só nos testes, para cada teste ter as suas tabelas
            self.conexao.exec_driver_sql(f'CREATE SCHEMA IF NOT EXISTS "{schema}"')
            self.conexao.exec_driver_sql(f'SET search_path TO "{schema}"')
            self.conexao.commit()

    def executar(self, sql, valores=None):
        resultado = self.conexao.execute(text(sql), valores or {})
        if not self.em_transacao:
            self.conexao.commit()
        return resultado

    def um(self, sql, valores=None):
        return self.executar(sql, valores).mappings().first()

    def todos(self, sql, valores=None):
        return self.executar(sql, valores).mappings().all()

    @contextmanager
    def transacao(self):
        # with banco.transacao(): ... -> se der erro no meio, desfaz tudo (rollback)
        self.em_transacao = True
        try:
            yield
            self.conexao.commit()
        except Exception:
            self.conexao.rollback()
            raise
        finally:
            self.em_transacao = False

    def criar_tabelas(self):
        if self.um("SELECT to_regclass('usuarios') AS tabela")["tabela"]:
            return  # já existe
        for arquivo in ["schema.sql", "seed.sql"]:
            with open(os.path.join(PASTA_DATABASE, arquivo), encoding="utf-8") as f:
                self.conexao.exec_driver_sql(f.read())  # arquivo do próprio projeto, sem dados do usuário
        self.conexao.commit()

    def apagar_schema(self):
        if self.schema:
            self.conexao.rollback()
            self.conexao.exec_driver_sql(f'DROP SCHEMA "{self.schema}" CASCADE')
            self.conexao.commit()

    def fechar(self):
        self.conexao.close()
        self.engine.dispose()


class Repositorio:
    def __init__(self, banco):
        self.banco = banco

    # ---------- usuários ----------
    def _usuario(self, linha):
        if linha is None:
            return None
        return criar_usuario(linha["perfil"], id=linha["id"], nome=linha["nome"], email=linha["email"],
                             senha_hash=linha["senha_hash"], bloqueado=linha["bloqueado"],
                             tentativas_erradas=linha["tentativas_erradas"])

    def buscar_usuario_por_email(self, email):
        return self._usuario(self.banco.um("SELECT * FROM usuarios WHERE email = :email", {"email": email}))

    def buscar_usuario(self, usuario_id):
        return self._usuario(self.banco.um("SELECT * FROM usuarios WHERE id = :id", {"id": usuario_id}))

    def listar_usuarios(self):
        return [self._usuario(l) for l in self.banco.todos("SELECT * FROM usuarios ORDER BY id")]

    def salvar_tentativas(self, usuario):
        self.banco.executar("UPDATE usuarios SET bloqueado = :bloqueado, tentativas_erradas = :tentativas WHERE id = :id",
                            {"bloqueado": usuario.bloqueado, "tentativas": usuario.tentativas_erradas, "id": usuario.id})

    def inserir_usuario(self, nome, email, senha_hash, perfil):
        return self.banco.um("INSERT INTO usuarios (nome, email, senha_hash, perfil) "
                             "VALUES (:nome, :email, :senha_hash, :perfil) RETURNING id",
                             {"nome": nome, "email": email, "senha_hash": senha_hash, "perfil": perfil})["id"]

    # ---------- contas e posições ----------
    def buscar_conta(self, usuario_id):
        l = self.banco.um("SELECT * FROM contas WHERE usuario_id = :id", {"id": usuario_id})
        return Conta(l["usuario_id"], l["saldo"], l["limite_por_ordem"]) if l else None

    def inserir_conta(self, usuario_id, saldo, limite):
        self.banco.executar("INSERT INTO contas (usuario_id, saldo, limite_por_ordem) VALUES (:id, :saldo, :limite)",
                            {"id": usuario_id, "saldo": saldo, "limite": limite})

    def salvar_conta(self, conta):
        self.banco.executar("UPDATE contas SET saldo = :saldo, limite_por_ordem = :limite WHERE usuario_id = :id",
                            {"saldo": conta.saldo, "limite": conta.limite_por_ordem, "id": conta.usuario_id})

    def buscar_posicao(self, usuario_id, ticker):
        l = self.banco.um("SELECT * FROM posicoes WHERE usuario_id = :id AND ticker = :ticker",
                          {"id": usuario_id, "ticker": ticker})
        if l is None:
            return Posicao(usuario_id, ticker)  # ainda não tem esse ativo
        return Posicao(usuario_id, ticker, l["quantidade"], l["preco_medio"])

    def listar_posicoes(self, usuario_id):
        return self.banco.todos("SELECT p.*, a.ultimo_preco, a.preco_referencia FROM posicoes p "
                                "JOIN ativos a ON a.ticker = p.ticker "
                                "WHERE p.usuario_id = :id AND p.quantidade > 0 ORDER BY p.ticker", {"id": usuario_id})

    def salvar_posicao(self, posicao):
        self.banco.executar(
            "INSERT INTO posicoes (usuario_id, ticker, quantidade, preco_medio) VALUES (:id, :ticker, :qtd, :pm) "
            "ON CONFLICT (usuario_id, ticker) DO UPDATE SET quantidade = EXCLUDED.quantidade, "
            "preco_medio = EXCLUDED.preco_medio",
            {"id": posicao.usuario_id, "ticker": posicao.ticker, "qtd": posicao.quantidade, "pm": posicao.preco_medio})

    # ---------- ativos ----------
    def buscar_ativo(self, ticker):
        return self.banco.um("SELECT * FROM ativos WHERE ticker = :ticker", {"ticker": ticker})

    def listar_ativos(self, so_ativos=False):
        return self.banco.todos("SELECT * FROM ativos WHERE ativo OR NOT :so_ativos ORDER BY ticker",
                                {"so_ativos": so_ativos})

    def inserir_ativo(self, ticker, nome, tipo, preco):
        self.banco.executar("INSERT INTO ativos (ticker, nome, tipo, preco_referencia) VALUES (:ticker, :nome, :tipo, :preco)",
                            {"ticker": ticker, "nome": nome, "tipo": tipo, "preco": preco})

    def mudar_situacao_ativo(self, ticker, ativo):
        self.banco.executar("UPDATE ativos SET ativo = :ativo WHERE ticker = :ticker", {"ativo": ativo, "ticker": ticker})

    def salvar_cotacao(self, ticker, preco):
        self.banco.executar("UPDATE ativos SET ultimo_preco = :preco, cotado_em = CURRENT_TIMESTAMP WHERE ticker = :ticker",
                            {"preco": preco, "ticker": ticker})

    # ---------- ordens ----------
    def _ordem(self, l):
        if l is None:
            return None
        return criar_ordem(l["tipo"], id=l["id"], usuario_id=l["usuario_id"], ticker=l["ticker"],
                           quantidade=l["quantidade"], preco=l["preco"], chave=l["chave_idempotencia"],
                           status=l["status"], motivo=l["motivo"])

    def buscar_ordem(self, ordem_id):
        return self._ordem(self.banco.um("SELECT * FROM ordens WHERE id = :id", {"id": ordem_id}))

    def buscar_ordem_por_chave(self, chave):
        return self._ordem(self.banco.um("SELECT * FROM ordens WHERE chave_idempotencia = :chave", {"chave": chave}))

    def listar_ordens(self, usuario_id):
        return [self._ordem(l) for l in
                self.banco.todos("SELECT * FROM ordens WHERE usuario_id = :id ORDER BY id DESC", {"id": usuario_id})]

    def listar_ordens_por_status(self, status):
        return [self._ordem(l) for l in self.banco.todos("SELECT * FROM ordens WHERE status = :status ORDER BY id", {"status": status})]

    def inserir_ordem(self, ordem):
        ordem.id = self.banco.um(
            "INSERT INTO ordens (chave_idempotencia, usuario_id, ticker, tipo, quantidade, preco, status) "
            "VALUES (:chave, :usuario, :ticker, :tipo, :qtd, :preco, :status) RETURNING id",
            {"chave": ordem.chave, "usuario": ordem.usuario_id, "ticker": ordem.ticker, "tipo": ordem.tipo,
             "qtd": ordem.quantidade, "preco": ordem.preco, "status": ordem.status})["id"]

    def salvar_ordem(self, ordem):
        self.banco.executar("UPDATE ordens SET quantidade = :qtd, preco = :preco, status = :status, motivo = :motivo "
                            "WHERE id = :id", {"qtd": ordem.quantidade, "preco": ordem.preco, "status": ordem.status,
                                               "motivo": ordem.motivo, "id": ordem.id})

    def inserir_historico(self, ordem):
        self.banco.executar("INSERT INTO historico_ordens (ordem_id, status, motivo) VALUES (:id, :status, :motivo)",
                            {"id": ordem.id, "status": ordem.status, "motivo": ordem.motivo})

    def listar_historico(self, ordem_id):
        return self.banco.todos("SELECT * FROM historico_ordens WHERE ordem_id = :id ORDER BY id", {"id": ordem_id})

    # ---------- auditoria, notificações e parâmetros ----------
    def inserir_auditoria(self, usuario_id, acao, detalhes):
        self.banco.executar("INSERT INTO auditoria (usuario_id, acao, detalhes) VALUES (:id, :acao, :detalhes)",
                            {"id": usuario_id, "acao": acao, "detalhes": detalhes})

    def listar_auditoria(self, limite=30):
        return self.banco.todos("SELECT * FROM auditoria ORDER BY id DESC LIMIT :limite", {"limite": limite})

    def inserir_notificacao(self, usuario_id, mensagem):
        self.banco.executar("INSERT INTO notificacoes (usuario_id, mensagem) VALUES (:id, :mensagem)",
                            {"id": usuario_id, "mensagem": mensagem})

    def listar_notificacoes(self, usuario_id):
        return self.banco.todos("SELECT * FROM notificacoes WHERE usuario_id = :id ORDER BY id DESC", {"id": usuario_id})

    def buscar_parametro(self, chave):
        return self.banco.um("SELECT valor FROM parametros WHERE chave = :chave", {"chave": chave})["valor"]

    def salvar_parametro(self, chave, valor):
        self.banco.executar("UPDATE parametros SET valor = :valor WHERE chave = :chave", {"valor": valor, "chave": chave})