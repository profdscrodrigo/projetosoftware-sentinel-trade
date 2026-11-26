-- =====================================================================
-- SentinelTrade — Estrutura do banco de dados (SQLite)
-- Projeto acadêmico: TODOS os dados e operações são SIMULADOS.
--
-- Como criar o banco (na raiz do projeto):
--   sqlite3 database/sentineltrade.db < database/schema.sql
--   sqlite3 database/sentineltrade.db < database/seed.sql
--
-- Requisitos atendidos: RF-01 a RF-18, RNF-01, RNF-02, RNF-03, RNF-06, RNF-07
-- Regras de negócio: ver docs/requisitos/regras-de-negocio.md (RN-01 a RN-12)
-- =====================================================================

PRAGMA foreign_keys = ON;

-- ---------------------------------------------------------------------
-- USUÁRIOS E PERFIS (RF-01, RF-02, RNF-01, RN-06)
-- A senha NUNCA é guardada em texto puro: apenas o hash (RNF-01).
-- O segredo do MFA fica fora do banco (variável MFA_SECRET_KEY no .env).
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS usuario (
    id                 INTEGER PRIMARY KEY AUTOINCREMENT,
    nome               TEXT    NOT NULL,
    email              TEXT    NOT NULL UNIQUE,
    senha_hash         TEXT    NOT NULL,
    perfil             TEXT    NOT NULL CHECK (perfil IN ('INVESTIDOR', 'ADMINISTRADOR', 'ANALISTA_RISCO')),
    mfa_habilitado     INTEGER NOT NULL DEFAULT 1 CHECK (mfa_habilitado IN (0, 1)),
    ativo              INTEGER NOT NULL DEFAULT 1 CHECK (ativo IN (0, 1)),
    tentativas_login   INTEGER NOT NULL DEFAULT 0,
    bloqueado_ate      TEXT,                                   -- bloqueio temporário (UC01 FA03)
    criado_em          TEXT    NOT NULL DEFAULT (datetime('now'))
);

-- Dados do investidor (RF-02)
CREATE TABLE IF NOT EXISTS investidor (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    usuario_id  INTEGER NOT NULL UNIQUE REFERENCES usuario(id),
    cpf         TEXT    NOT NULL UNIQUE,                       -- fictício
    telefone    TEXT
);

-- ---------------------------------------------------------------------
-- CONTAS, LIMITES E CARTEIRAS (RF-03, RF-04, RF-05, RF-15)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS conta (
    id                INTEGER PRIMARY KEY AUTOINCREMENT,
    investidor_id     INTEGER NOT NULL REFERENCES investidor(id),
    numero            TEXT    NOT NULL UNIQUE,
    saldo_disponivel  REAL    NOT NULL DEFAULT 0 CHECK (saldo_disponivel >= 0),
    status            TEXT    NOT NULL DEFAULT 'ATIVA' CHECK (status IN ('ATIVA', 'INATIVA', 'BLOQUEADA')),
    criado_em         TEXT    NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS limite_risco (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    conta_id            INTEGER NOT NULL UNIQUE REFERENCES conta(id),
    limite_por_ordem    REAL    NOT NULL CHECK (limite_por_ordem > 0),   -- valor máximo de uma ordem
    limite_diario       REAL    NOT NULL CHECK (limite_diario > 0),      -- soma máxima de ordens no dia
    limite_alto_risco   REAL    NOT NULL CHECK (limite_alto_risco > 0),  -- acima disso pede confirmação extra (RN-12)
    atualizado_por      INTEGER REFERENCES usuario(id),
    atualizado_em       TEXT    NOT NULL DEFAULT (datetime('now'))
);

-- ---------------------------------------------------------------------
-- ATIVOS E COTAÇÕES (RF-06, RF-17)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ativo (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    codigo      TEXT    NOT NULL UNIQUE,                       -- código fictício
    nome        TEXT    NOT NULL,
    tipo        TEXT    NOT NULL CHECK (tipo IN ('ACAO', 'FII', 'ETF')),
    negociavel  INTEGER NOT NULL DEFAULT 1 CHECK (negociavel IN (0, 1))
);

CREATE TABLE IF NOT EXISTS cotacao (
    id                   INTEGER PRIMARY KEY AUTOINCREMENT,
    ativo_id             INTEGER NOT NULL REFERENCES ativo(id),
    preco                REAL    NOT NULL CHECK (preco > 0),
    variacao_percentual  REAL    NOT NULL DEFAULT 0,
    data_hora            TEXT    NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS carteira (
    id        INTEGER PRIMARY KEY AUTOINCREMENT,
    conta_id  INTEGER NOT NULL UNIQUE REFERENCES conta(id)
);

-- Posição por ativo: quantidade nunca negativa (UC12 FA01, RNF-03)
CREATE TABLE IF NOT EXISTS posicao (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    carteira_id  INTEGER NOT NULL REFERENCES carteira(id),
    ativo_id     INTEGER NOT NULL REFERENCES ativo(id),
    quantidade   INTEGER NOT NULL CHECK (quantidade >= 0),
    preco_medio  REAL    NOT NULL CHECK (preco_medio >= 0),
    UNIQUE (carteira_id, ativo_id)
);

-- Parâmetros operacionais gerenciados pelo administrador (RF-17)
CREATE TABLE IF NOT EXISTS parametro_sistema (
    chave          TEXT PRIMARY KEY,
    valor          TEXT NOT NULL,
    descricao      TEXT,
    atualizado_em  TEXT NOT NULL DEFAULT (datetime('now'))
);

-- ---------------------------------------------------------------------
-- ORDENS E CICLO DE VIDA (RF-07, RF-08, RF-09, RF-10, RF-14)
-- chave_idempotencia UNIQUE: a mesma ordem nunca é gravada duas vezes (RF-14, RN-03)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ordem (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    chave_idempotencia  TEXT    NOT NULL UNIQUE,
    conta_id            INTEGER NOT NULL REFERENCES conta(id),
    ativo_id            INTEGER NOT NULL REFERENCES ativo(id),
    tipo                TEXT    NOT NULL CHECK (tipo IN ('COMPRA', 'VENDA')),
    quantidade          INTEGER NOT NULL CHECK (quantidade > 0),       -- RNF-03 Integridade: dados válidos
    preco               REAL    NOT NULL CHECK (preco > 0),
    status              TEXT    NOT NULL DEFAULT 'CRIADA' CHECK (status IN
                        ('CRIADA', 'VALIDADA', 'ENVIADA', 'EXECUTADA', 'REJEITADA', 'CANCELADA', 'FALHA')),
    motivo              TEXT,                                          -- motivo de rejeição/falha
    tentativas_envio    INTEGER NOT NULL DEFAULT 0,
    criado_em           TEXT    NOT NULL DEFAULT (datetime('now')),
    atualizado_em       TEXT    NOT NULL DEFAULT (datetime('now'))
);

-- Histórico de mudanças de status (RF-10: o investidor vê cada etapa com data e hora)
CREATE TABLE IF NOT EXISTS historico_status_ordem (
    id               INTEGER PRIMARY KEY AUTOINCREMENT,
    ordem_id         INTEGER NOT NULL REFERENCES ordem(id),
    status_anterior  TEXT,
    status_novo      TEXT    NOT NULL,
    motivo           TEXT,
    data_hora        TEXT    NOT NULL DEFAULT (datetime('now'))
);

-- ---------------------------------------------------------------------
-- AUDITORIA (RF-11, RNF-02, RNF-07, RN-05)
-- Registro imutável: os gatilhos abaixo bloqueiam UPDATE e DELETE.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS auditoria (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    usuario_id   INTEGER REFERENCES usuario(id),               -- NULL = ação do próprio sistema
    acao         TEXT    NOT NULL,                             -- ex.: ORDEM_CRIADA, LIMITE_ALTERADO
    entidade     TEXT    NOT NULL,                             -- ex.: ordem, conta, limite_risco
    entidade_id  INTEGER,
    detalhes     TEXT,
    data_hora    TEXT    NOT NULL DEFAULT (datetime('now'))
);

CREATE TRIGGER IF NOT EXISTS trg_auditoria_sem_update
BEFORE UPDATE ON auditoria
BEGIN
    SELECT RAISE(ABORT, 'Auditoria é imutável: alteração não permitida (RNF-07)');
END;

CREATE TRIGGER IF NOT EXISTS trg_auditoria_sem_delete
BEFORE DELETE ON auditoria
BEGIN
    SELECT RAISE(ABORT, 'Auditoria é imutável: exclusão não permitida (RNF-07)');
END;

-- ---------------------------------------------------------------------
-- NOTIFICAÇÕES (RF-12)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS notificacao (
    id             INTEGER PRIMARY KEY AUTOINCREMENT,
    investidor_id  INTEGER NOT NULL REFERENCES investidor(id),
    ordem_id       INTEGER REFERENCES ordem(id),
    mensagem       TEXT    NOT NULL,
    status         TEXT    NOT NULL DEFAULT 'PENDENTE' CHECK (status IN ('PENDENTE', 'ENVIADA', 'LIDA')),
    criado_em      TEXT    NOT NULL DEFAULT (datetime('now'))
);

-- ---------------------------------------------------------------------
-- GATILHOS DE REGRAS DE NEGÓCIO DA ORDEM
-- ---------------------------------------------------------------------

-- RN-04: ordem EXECUTADA, REJEITADA ou CANCELADA não volta para nenhum outro estado
CREATE TRIGGER IF NOT EXISTS trg_ordem_estado_final
BEFORE UPDATE OF status ON ordem
WHEN OLD.status IN ('EXECUTADA', 'REJEITADA', 'CANCELADA') AND NEW.status <> OLD.status
BEGIN
    SELECT RAISE(ABORT, 'Ordem em estado final não pode mudar de status (RN-04)');
END;

-- RN-05: criação da ordem gera histórico e auditoria
CREATE TRIGGER IF NOT EXISTS trg_ordem_criada
AFTER INSERT ON ordem
BEGIN
    INSERT INTO historico_status_ordem (ordem_id, status_anterior, status_novo)
    VALUES (NEW.id, NULL, NEW.status);
    INSERT INTO auditoria (acao, entidade, entidade_id, detalhes)
    VALUES ('ORDEM_CRIADA', 'ordem', NEW.id, NEW.tipo || ' ' || NEW.quantidade || ' x ' || NEW.preco);
END;

-- RN-05: toda mudança de status gera histórico e auditoria
CREATE TRIGGER IF NOT EXISTS trg_ordem_status_alterado
AFTER UPDATE OF status ON ordem
WHEN NEW.status <> OLD.status
BEGIN
    INSERT INTO historico_status_ordem (ordem_id, status_anterior, status_novo, motivo)
    VALUES (NEW.id, OLD.status, NEW.status, NEW.motivo);
    INSERT INTO auditoria (acao, entidade, entidade_id, detalhes)
    VALUES ('ORDEM_' || NEW.status, 'ordem', NEW.id, OLD.status || ' -> ' || NEW.status);
END;

-- ---------------------------------------------------------------------
-- ÍNDICES (RNF-05 Desempenho)
-- ---------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_ordem_conta      ON ordem (conta_id, criado_em);
CREATE INDEX IF NOT EXISTS idx_ordem_status     ON ordem (status);
CREATE INDEX IF NOT EXISTS idx_cotacao_ativo    ON cotacao (ativo_id, data_hora);
CREATE INDEX IF NOT EXISTS idx_auditoria_data   ON auditoria (data_hora);
CREATE INDEX IF NOT EXISTS idx_historico_ordem  ON historico_status_ordem (ordem_id);
