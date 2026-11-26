-- Criação das tabelas (PostgreSQL)

CREATE TABLE usuarios (
    id                SERIAL PRIMARY KEY,
    nome              VARCHAR(100) NOT NULL,
    email             VARCHAR(120) NOT NULL UNIQUE,
    senha_hash        VARCHAR(255) NOT NULL,   -- nunca guardamos a senha, só o hash
    perfil            VARCHAR(20)  NOT NULL CHECK (perfil IN ('INVESTIDOR', 'ADMINISTRADOR', 'ANALISTA_RISCO')),
    bloqueado         BOOLEAN      NOT NULL DEFAULT FALSE,
    tentativas_erradas INTEGER     NOT NULL DEFAULT 0
);

CREATE TABLE contas (
    id                SERIAL PRIMARY KEY,
    usuario_id        INTEGER       NOT NULL UNIQUE REFERENCES usuarios(id),
    saldo             NUMERIC(15,2) NOT NULL CHECK (saldo >= 0),
    limite_por_ordem  NUMERIC(15,2) NOT NULL CHECK (limite_por_ordem > 0)
);

CREATE TABLE ativos (
    ticker            VARCHAR(6)    PRIMARY KEY,
    nome              VARCHAR(100)  NOT NULL,
    tipo              VARCHAR(4)    NOT NULL CHECK (tipo IN ('ACAO', 'ETF', 'FII')),
    preco_referencia  NUMERIC(15,2) NOT NULL CHECK (preco_referencia > 0),
    ultimo_preco      NUMERIC(15,2),           -- última cotação recebida do provedor
    cotado_em         TIMESTAMP,
    ativo             BOOLEAN       NOT NULL DEFAULT TRUE
);

CREATE TABLE posicoes (
    id           SERIAL PRIMARY KEY,
    usuario_id   INTEGER       NOT NULL REFERENCES usuarios(id),
    ticker       VARCHAR(6)    NOT NULL REFERENCES ativos(ticker),
    quantidade   INTEGER       NOT NULL CHECK (quantidade >= 0),
    preco_medio  NUMERIC(15,2) NOT NULL DEFAULT 0,
    UNIQUE (usuario_id, ticker)
);

CREATE TABLE ordens (
    id                  SERIAL PRIMARY KEY,
    chave_idempotencia  VARCHAR(64)   NOT NULL UNIQUE,  -- impede ordem duplicada
    usuario_id          INTEGER       NOT NULL REFERENCES usuarios(id),
    ticker              VARCHAR(6)    NOT NULL REFERENCES ativos(ticker),
    tipo                VARCHAR(6)    NOT NULL CHECK (tipo IN ('COMPRA', 'VENDA')),
    quantidade          INTEGER       NOT NULL CHECK (quantidade > 0),
    preco               NUMERIC(15,2) NOT NULL CHECK (preco > 0),
    status              VARCHAR(25)   NOT NULL CHECK (status IN ('CRIADA', 'VALIDADA', 'ENVIADA',
                            'PARCIALMENTE_EXECUTADA', 'EXECUTADA', 'CANCELADA', 'REJEITADA', 'FALHA')),
    motivo              VARCHAR(255),
    criada_em           TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- cada mudança de status da ordem vira uma linha (ciclo de vida)
CREATE TABLE historico_ordens (
    id         SERIAL PRIMARY KEY,
    ordem_id   INTEGER     NOT NULL REFERENCES ordens(id),
    status     VARCHAR(25) NOT NULL,
    motivo     VARCHAR(255),
    data_hora  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE auditoria (
    id          SERIAL PRIMARY KEY,
    usuario_id  INTEGER,
    acao        VARCHAR(50)  NOT NULL,
    detalhes    VARCHAR(255),
    data_hora   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- a auditoria não pode ser alterada nem apagada (só inserir)
CREATE FUNCTION proteger_auditoria() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'A auditoria nao pode ser alterada nem apagada';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER auditoria_protegida
BEFORE UPDATE OR DELETE ON auditoria
FOR EACH ROW EXECUTE FUNCTION proteger_auditoria();

CREATE TABLE notificacoes (
    id          SERIAL PRIMARY KEY,
    usuario_id  INTEGER      NOT NULL REFERENCES usuarios(id),
    mensagem    VARCHAR(255) NOT NULL,
    data_hora   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE parametros (
    chave  VARCHAR(50) PRIMARY KEY,
    valor  VARCHAR(50) NOT NULL
);