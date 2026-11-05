-- Senha de todos os usuários de teste.
-- Hash bcrypt (a senha nunca é gravada).

INSERT INTO usuarios (nome, email, senha_hash, perfil) VALUES
 ('Admin Teste',    'admin@sentineltrade.sim', '$2b$12$9IxyvtU5tv7QxlN8fHZ29egsbPH1zJJY2DngE0fDCUBOLPjYwnWPC', 'ADMINISTRADOR'),
 ('Analista Teste', 'risco@sentineltrade.sim', '$2b$12$eBSGCSCJogwW7I8C7Esi6O/eRWuVvNO28aQHHihpgGhn6/44o8hlu', 'ANALISTA_RISCO'),
 ('Ana Exemplo',    'ana@sentineltrade.sim',   '$2b$12$e8IFR9DWf7pH/HydkHbyHuVcMog7AcGPXr5nxHAehbV3D/gt7RE6y', 'INVESTIDOR'),
 ('Bruno Exemplo',  'bruno@sentineltrade.sim', '$2b$12$szcgECjz7GAGWKAZUn0fSOfXJBzwbScuikf93ayEoaOSMYDxiUafi', 'INVESTIDOR');

-- ids: 1 admin, 2 analista, 3 Ana, 4 Bruno
INSERT INTO contas (usuario_id, saldo, limite_por_ordem) VALUES
 (3, 50000.00, 20000.00),
 (4, 10000.00,  5000.00);

INSERT INTO ativos (ticker, nome, tipo, preco_referencia, ativo) VALUES
 ('SENT3',  'Sentinel Energia (fictícia)', 'ACAO',  20.50, TRUE),
 ('AURA4',  'Aura Mineração (fictícia)',   'ACAO',  36.20, TRUE),
 ('VRDE3',  'Verde Agro (fictícia)',       'ACAO',  12.80, TRUE),
 ('TECH11', 'ETF Tech (fictício)',         'ETF',   98.40, TRUE),
 ('ORIO11', 'Orion Lajes FII (fictício)',  'FII',  105.00, TRUE),
 ('BLOQ3',  'Empresa Suspensa (fictícia)', 'ACAO',   5.00, FALSE);

INSERT INTO posicoes (usuario_id, ticker, quantidade, preco_medio) VALUES
 (3, 'SENT3', 100, 20.00),
 (3, 'AURA4',  50, 35.00);

INSERT INTO parametros (chave, valor) VALUES
 ('mercado_aberto', 'SIM'),
 ('limite_confirmacao', '10000.00');