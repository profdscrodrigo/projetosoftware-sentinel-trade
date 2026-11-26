-- =====================================================================================
-- SentinelTrade — dados de referência SIMULADOS (sem dados pessoais, sem credenciais).
-- Usuários, saldos e carteiras de demonstração NÃO ficam aqui: são criados em tempo de execução
-- por DadosDemonstracao (SENTINEL_DEMO=true), com senhas vindas de variáveis de ambiente ou geradas
-- aleatoriamente — assim nenhum segredo (nem mesmo hash) é versionado.
-- =====================================================================================

INSERT INTO ativos (ticker, nome, tipo, status, versao) VALUES ('PETR4',  'Petrobras PN',      'ACAO', 'ATIVO', 1);
INSERT INTO ativos (ticker, nome, tipo, status, versao) VALUES ('VALE3',  'Vale ON',           'ACAO', 'ATIVO', 1);
INSERT INTO ativos (ticker, nome, tipo, status, versao) VALUES ('ITUB4',  'Itau Unibanco PN',  'ACAO', 'ATIVO', 1);
INSERT INTO ativos (ticker, nome, tipo, status, versao) VALUES ('BOVA11', 'iShares Ibovespa',  'ETF',  'ATIVO', 1);
INSERT INTO ativos (ticker, nome, tipo, status, versao) VALUES ('MXRF11', 'Maxi Renda FII',    'FII',  'ATIVO', 1);

INSERT INTO parametros (chave, valor) VALUES ('mercado.abertura', '10:00');
INSERT INTO parametros (chave, valor) VALUES ('mercado.fechamento', '17:55');
INSERT INTO parametros (chave, valor) VALUES ('mercado.forcar_aberto', 'false');
INSERT INTO parametros (chave, valor) VALUES ('cotacao.idade_maxima_segundos', '60');
INSERT INTO parametros (chave, valor) VALUES ('ordem.faixa_preco_percentual', '10');
INSERT INTO parametros (chave, valor) VALUES ('ordem.max_por_minuto', '30');
