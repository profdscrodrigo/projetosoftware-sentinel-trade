-- =====================================================================================
-- SentinelTrade — esquema relacional (PostgreSQL 14+).
-- Escrito em SQL padrão (VARCHAR, NUMERIC, TIMESTAMP WITH TIME ZONE, CHECK, FK) para
-- rodar também em H2/SQLite nos testes de esquema. Dados 100% simulados.
--
-- Decisões:
--  * Dinheiro: NUMERIC(18,2) (nunca FLOAT). Datas: TIMESTAMP WITH TIME ZONE (UTC).
--  * Controle de concorrência OTIMISTA: coluna "versao" (UPDATE ... WHERE versao = ?).
--  * Idempotência: UNIQUE (investidor_id, chave_idempotencia) em ordens; PK id_execucao em execucoes.
--  * Integridade: CHECK constraints impedem saldo negativo, quantidade inválida e estados fora da lista.
--  * Auditoria: tabela append-only com hash encadeado (ver hardening-postgres.sql p/ bloquear UPDATE/DELETE).
--  * Segredos: nenhuma senha em claro — apenas hash PBKDF2 (senha_hash) e segredo MFA.
-- =====================================================================================

CREATE TABLE usuarios (
    id             VARCHAR(36)  PRIMARY KEY,
    nome           VARCHAR(100) NOT NULL,
    email          VARCHAR(254) NOT NULL,
    papel          VARCHAR(20)  NOT NULL CHECK (papel IN ('INVESTIDOR', 'ADMINISTRADOR', 'ANALISTA_RISCO')),
    senha_hash     VARCHAR(255) NOT NULL,
    segredo_mfa    VARCHAR(255) NOT NULL,
    ativo          BOOLEAN      NOT NULL DEFAULT TRUE,
    falhas_login   INTEGER      NOT NULL DEFAULT 0 CHECK (falhas_login >= 0),
    bloqueado_ate  TIMESTAMP WITH TIME ZONE,
    criado_em      TIMESTAMP WITH TIME ZONE NOT NULL,
    versao         BIGINT       NOT NULL DEFAULT 1,
    CONSTRAINT uq_usuarios_email UNIQUE (email)
);

CREATE TABLE sessoes (
    id               VARCHAR(36) PRIMARY KEY,
    usuario_id       VARCHAR(36) NOT NULL REFERENCES usuarios (id),
    papel            VARCHAR(20) NOT NULL,
    estado           VARCHAR(12) NOT NULL CHECK (estado IN ('PENDENTE_MFA', 'ATIVA', 'REVOGADA')),
    token_hash       CHAR(64)    NOT NULL,   -- SHA-256 do token; o token em claro nunca é gravado
    criada_em        TIMESTAMP WITH TIME ZONE NOT NULL,
    expira_em        TIMESTAMP WITH TIME ZONE NOT NULL,
    tentativas_mfa   INTEGER     NOT NULL DEFAULT 0 CHECK (tentativas_mfa >= 0),
    CONSTRAINT uq_sessoes_token UNIQUE (token_hash)
);

CREATE TABLE contas (
    id                VARCHAR(36)   PRIMARY KEY,
    investidor_id     VARCHAR(36)   NOT NULL REFERENCES usuarios (id),
    saldo_disponivel  NUMERIC(18,2) NOT NULL DEFAULT 0 CHECK (saldo_disponivel >= 0),
    saldo_bloqueado   NUMERIC(18,2) NOT NULL DEFAULT 0 CHECK (saldo_bloqueado >= 0),
    versao            BIGINT        NOT NULL DEFAULT 1,
    CONSTRAINT uq_contas_investidor UNIQUE (investidor_id)
);

CREATE TABLE ativos (
    ticker   VARCHAR(6)   PRIMARY KEY CHECK (ticker = UPPER(ticker)),
    nome     VARCHAR(100) NOT NULL,
    tipo     VARCHAR(5)   NOT NULL CHECK (tipo IN ('ACAO', 'ETF', 'FII')),
    status   VARCHAR(8)   NOT NULL CHECK (status IN ('ATIVO', 'SUSPENSO')),
    versao   BIGINT       NOT NULL DEFAULT 1
);

CREATE TABLE posicoes (
    investidor_id         VARCHAR(36)   NOT NULL REFERENCES usuarios (id),
    ticker                VARCHAR(6)    NOT NULL REFERENCES ativos (ticker),
    quantidade            INTEGER       NOT NULL DEFAULT 0 CHECK (quantidade >= 0),
    quantidade_bloqueada  INTEGER       NOT NULL DEFAULT 0 CHECK (quantidade_bloqueada >= 0),
    custo_total           NUMERIC(18,2) NOT NULL DEFAULT 0 CHECK (custo_total >= 0),
    versao                BIGINT        NOT NULL DEFAULT 1,
    PRIMARY KEY (investidor_id, ticker),
    CHECK (quantidade_bloqueada <= quantidade)
);

CREATE TABLE limites_risco (
    investidor_id           VARCHAR(36)   PRIMARY KEY REFERENCES usuarios (id),
    valor_maximo_por_ordem  NUMERIC(18,2) NOT NULL CHECK (valor_maximo_por_ordem > 0),
    limite_diario           NUMERIC(18,2) NOT NULL CHECK (limite_diario > 0),
    valor_confirmacao       NUMERIC(18,2) NOT NULL CHECK (valor_confirmacao > 0),
    versao                  BIGINT        NOT NULL DEFAULT 1,
    CHECK (limite_diario >= valor_maximo_por_ordem),
    CHECK (valor_confirmacao <= valor_maximo_por_ordem)
);

CREATE TABLE ordens (
    id                  VARCHAR(36)   PRIMARY KEY,
    investidor_id       VARCHAR(36)   NOT NULL REFERENCES usuarios (id),
    ticker              VARCHAR(6)    NOT NULL,
    lado                VARCHAR(6)    NOT NULL CHECK (lado IN ('COMPRA', 'VENDA')),
    tipo_ordem          VARCHAR(7)    NOT NULL DEFAULT 'LIMITE' CHECK (tipo_ordem IN ('LIMITE', 'MERCADO')),
    quantidade          INTEGER       NOT NULL CHECK (quantidade > 0),
    preco_limite        NUMERIC(18,2) NOT NULL CHECK (preco_limite > 0),
    chave_idempotencia  VARCHAR(64)   NOT NULL,
    impressao_digital   CHAR(64)      NOT NULL,
    status              VARCHAR(24)   NOT NULL CHECK (status IN ('CRIADA', 'VALIDADA', 'ENVIADA', 'PARCIALMENTE_EXECUTADA',
                                                                  'EXECUTADA', 'REJEITADA', 'CANCELADA')),
    quantidade_executada INTEGER      NOT NULL DEFAULT 0,
    valor_executado     NUMERIC(18,2) NOT NULL DEFAULT 0 CHECK (valor_executado >= 0),
    motivo_rejeicao     VARCHAR(40),
    substitui_ordem_id  VARCHAR(36)   REFERENCES ordens (id),   -- alteração = cancelar e substituir
    criada_em           TIMESTAMP WITH TIME ZONE NOT NULL,
    atualizada_em       TIMESTAMP WITH TIME ZONE NOT NULL,
    versao              BIGINT        NOT NULL DEFAULT 1,
    CONSTRAINT uq_ordens_idempotencia UNIQUE (investidor_id, chave_idempotencia),   -- RN03 / RNF06
    CHECK (quantidade_executada >= 0 AND quantidade_executada <= quantidade),
    CHECK ((status = 'REJEITADA') = (motivo_rejeicao IS NOT NULL))
);
CREATE INDEX idx_ordens_investidor_data ON ordens (investidor_id, criada_em);
CREATE INDEX idx_ordens_status ON ordens (status);

CREATE TABLE execucoes (
    id_execucao   VARCHAR(64)   PRIMARY KEY,         -- identificador da corretora: garante idempotência do consumo
    ordem_id      VARCHAR(36)   NOT NULL REFERENCES ordens (id),
    quantidade    INTEGER       NOT NULL CHECK (quantidade > 0),
    preco         NUMERIC(18,2) NOT NULL CHECK (preco > 0),
    executada_em  TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_execucoes_ordem ON execucoes (ordem_id);

-- Fila durável de transmissão (padrão outbox): gravada na mesma transação que valida a ordem.
CREATE TABLE fila_ordens (
    id                  VARCHAR(36) PRIMARY KEY,
    ordem_id            VARCHAR(36) NOT NULL REFERENCES ordens (id),
    tentativas          INTEGER     NOT NULL DEFAULT 0 CHECK (tentativas >= 0),
    proxima_tentativa   TIMESTAMP WITH TIME ZONE NOT NULL,
    status              VARCHAR(10) NOT NULL DEFAULT 'PENDENTE' CHECK (status IN ('PENDENTE', 'CONCLUIDA'))
);
CREATE INDEX idx_fila_pendentes ON fila_ordens (proxima_tentativa) WHERE status = 'PENDENTE';

-- Trilha de auditoria append-only com hash encadeado (RNF03). Sem FK de propósito: o registro deve
-- sobreviver a qualquer alteração em outras tabelas.
CREATE TABLE auditoria (
    seq            BIGINT       PRIMARY KEY,
    instante       TIMESTAMP WITH TIME ZONE NOT NULL,
    tipo           VARCHAR(40)  NOT NULL,
    usuario_id     VARCHAR(36),
    entidade       VARCHAR(30)  NOT NULL,
    entidade_id    VARCHAR(80)  NOT NULL,
    detalhes       VARCHAR(600) NOT NULL,
    hash_anterior  CHAR(64)     NOT NULL,
    hash           CHAR(64)     NOT NULL,
    CONSTRAINT uq_auditoria_hash UNIQUE (hash)
);
CREATE INDEX idx_auditoria_entidade ON auditoria (entidade, entidade_id);

CREATE TABLE notificacoes (
    id             VARCHAR(36)  PRIMARY KEY,
    investidor_id  VARCHAR(36)  NOT NULL REFERENCES usuarios (id),
    tipo           VARCHAR(20)  NOT NULL CHECK (tipo IN ('EXECUCAO', 'EXECUCAO_PARCIAL', 'REJEICAO', 'CANCELAMENTO', 'ALTERACAO', 'FALHA')),
    mensagem       VARCHAR(300) NOT NULL,
    criada_em      TIMESTAMP WITH TIME ZONE NOT NULL,
    enviada_em     TIMESTAMP WITH TIME ZONE
);
CREATE INDEX idx_notificacoes_investidor ON notificacoes (investidor_id, criada_em);
CREATE INDEX idx_notificacoes_pendentes ON notificacoes (criada_em) WHERE enviada_em IS NULL;

CREATE TABLE parametros (
    chave  VARCHAR(60)  PRIMARY KEY,
    valor  VARCHAR(100) NOT NULL
);
