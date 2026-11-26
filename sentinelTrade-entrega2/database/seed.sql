-- =====================================================================
-- SentinelTrade — Dados iniciais para testes (SQLite)
-- ATENÇÃO: todos os nomes, CPFs, e-mails, contas, ativos e valores são
-- FICTÍCIOS. Não há dados pessoais reais nem senhas reais neste arquivo.
-- senha_hash: hash BCrypt da senha de DEMONSTRAÇÃO "Demo@1234", usada só
-- para entrar no ambiente simulado (nunca use em um sistema real).
--
-- Executar depois do schema.sql:
--   sqlite3 database/sentineltrade.db < database/seed.sql
-- =====================================================================

PRAGMA foreign_keys = ON;

-- Usuários (um de cada perfil + dois investidores)
INSERT INTO usuario (id, nome, email, senha_hash, perfil) VALUES
    (1, 'Admin Exemplo',        'admin@orioncapital.exemplo',    '$2a$10$i5jHQrVXA0QU.1hljMicoeVzp7h8Jk8O/sGWfLrwZjh9NHVJwSTpa', 'ADMINISTRADOR'),
    (2, 'Analista Exemplo',     'risco@orioncapital.exemplo',    '$2a$10$i5jHQrVXA0QU.1hljMicoeVzp7h8Jk8O/sGWfLrwZjh9NHVJwSTpa', 'ANALISTA_RISCO'),
    (3, 'Investidor Exemplo A', 'investidor.a@email.exemplo',    '$2a$10$i5jHQrVXA0QU.1hljMicoeVzp7h8Jk8O/sGWfLrwZjh9NHVJwSTpa', 'INVESTIDOR'),
    (4, 'Investidor Exemplo B', 'investidor.b@email.exemplo',    '$2a$10$i5jHQrVXA0QU.1hljMicoeVzp7h8Jk8O/sGWfLrwZjh9NHVJwSTpa', 'INVESTIDOR');

INSERT INTO investidor (id, usuario_id, cpf, telefone) VALUES
    (1, 3, '000.000.000-01', '(11) 90000-0001'),
    (2, 4, '000.000.000-02', '(11) 90000-0002');

-- Contas com saldo simulado
INSERT INTO conta (id, investidor_id, numero, saldo_disponivel, status) VALUES
    (1, 1, 'OC-0001', 10000.00, 'ATIVA'),
    (2, 2, 'OC-0002',  2500.00, 'ATIVA');

-- Limites de risco por conta
INSERT INTO limite_risco (conta_id, limite_por_ordem, limite_diario, limite_alto_risco, atualizado_por) VALUES
    (1, 5000.00, 20000.00, 3000.00, 2),
    (2, 1000.00,  3000.00,  800.00, 2);

-- Ativos fictícios
INSERT INTO ativo (id, codigo, nome, tipo, negociavel) VALUES
    (1, 'ORCP3', 'Orion Participações (fictício)',   'ACAO', 1),
    (2, 'STNL4', 'Sentinel Energia (fictício)',      'ACAO', 1),
    (3, 'FICT11','Fundo Imobiliário Fictício',       'FII',  1),
    (4, 'BLOQ3', 'Ativo Suspenso (fictício)',        'ACAO', 0);

-- Cotações simuladas
INSERT INTO cotacao (ativo_id, preco, variacao_percentual) VALUES
    (1, 25.40,  1.20),
    (2, 12.75, -0.85),
    (3, 98.10,  0.10),
    (4,  5.00,  0.00);

-- Carteiras e posições
INSERT INTO carteira (id, conta_id) VALUES (1, 1), (2, 2);
INSERT INTO posicao (carteira_id, ativo_id, quantidade, preco_medio) VALUES
    (1, 1, 100, 22.00),
    (1, 3,  10, 95.00),
    (2, 2,  50, 13.10);

-- Parâmetros do sistema
INSERT INTO parametro_sistema (chave, valor, descricao) VALUES
    ('MERCADO_ABERTO',          'true', 'Indica se a bolsa simulada aceita ordens'),
    ('MAX_TENTATIVAS_LOGIN',    '3',    'Tentativas antes do bloqueio temporário (UC01 FA03)'),
    ('MAX_TENTATIVAS_ENVIO',    '3',    'Reenvios à bolsa com o mesmo identificador (UC06 FA01)'),
    ('MINUTOS_BLOQUEIO_LOGIN',  '15',   'Tempo de bloqueio após exceder as tentativas'),
    ('BOLSA_DISPONIVEL',        'true', 'Simula a Bolsa no ar (true) ou fora do ar (false)');

-- Exemplos de ordens (os gatilhos geram histórico e auditoria automaticamente)
INSERT INTO ordem (chave_idempotencia, conta_id, ativo_id, tipo, quantidade, preco) VALUES
    ('ORD-EXEMPLO-0001', 1, 1, 'COMPRA', 10, 25.40),
    ('ORD-EXEMPLO-0002', 2, 2, 'VENDA',  20, 12.75);

UPDATE ordem SET status = 'VALIDADA'  WHERE chave_idempotencia = 'ORD-EXEMPLO-0001';
UPDATE ordem SET status = 'ENVIADA'   WHERE chave_idempotencia = 'ORD-EXEMPLO-0001';
UPDATE ordem SET status = 'EXECUTADA' WHERE chave_idempotencia = 'ORD-EXEMPLO-0001';

-- Exemplo de ordem recusada (aparece no painel do analista de risco)
UPDATE ordem SET status = 'REJEITADA', motivo = 'Mercado fechado no momento.'
WHERE chave_idempotencia = 'ORD-EXEMPLO-0002';

INSERT INTO notificacao (investidor_id, ordem_id, mensagem, status) VALUES
    (1, 1, 'Sua ordem de COMPRA de 10 ORCP3 foi EXECUTADA.', 'ENVIADA');
