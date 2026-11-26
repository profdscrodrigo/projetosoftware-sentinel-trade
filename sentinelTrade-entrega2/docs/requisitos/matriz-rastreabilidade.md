# Matriz de Rastreabilidade — SentinelTrade

Liga cada requisito ao caso de uso, aos diagramas, à parte do sistema e ao controle que o garante, e depois ao **código** e ao **teste** que comprovam que ele funciona.

**Siglas dos diagramas** (todos em [`docs/diagramas/`](../diagramas/)):

| Sigla | Diagrama | Sigla | Diagrama |
| :--- | :--- | :--- | :--- |
| CU | Casos de Uso | CL | Classes |
| CS | Casos de Uso com segurança (UMLsec) | ES | Estados |
| CO | Componentes | AT | Atividades |
| SQ | Sequência | IM | Implantação |

## Requisitos funcionais

| Requisito | Caso de uso associado | Diagramas | Componente | Controle técnico | Regras |
| :--- | :--- | :--- | :--- | :--- | :--- |
| RF-01 — Autenticação com MFA | UC01 Autenticar com MFA | CU, CS, CO, SQ, IM | Autenticação MFA | Senha com hash + código TOTP; bloqueio após tentativas | RN-06, RN-11 |
| RF-02 — Gerenciar investidores | UC10 Gerenciar investidores | CU, CO, CL | Gestão de Investidores | CPF único; validação de dados; auditoria | RN-06 |
| RF-03 — Gerenciar contas | UC11 Gerenciar contas | CU, CO, CL | Gestão de Contas | Conta vinculada a investidor ativo (chave estrangeira) | RN-06 |
| RF-04 — Carteiras e ativos | UC12 Gerenciar carteiras e ativos | CU, CO, CL | Carteiras e Ativos | `CHECK quantidade >= 0` | RN-13 |
| RF-05 — Limites financeiros | UC13 Gerenciar limites, UC18 | CU, CO | Limites Financeiros | Limite por ordem, diário e de alto risco por conta | RN-09, RN-12 |
| RF-06 — Cotações | UC02 Consultar cotações | CU, CO, IM | Cotações + Gateway Bolsa | Última cotação conhecida quando o provedor cai | RN-10 |
| RF-07 — Gerenciar ordens (compra, venda, cancelar) | UC03 Gerenciar ordens | CU, CO, SQ, AT, ES | Gestão de Ordens | Cancelamento só antes da execução | RN-01, RN-02, RN-14 |
| RF-08 — Validar ordem | UC04 Validar ordens | CU, CO, SQ, AT | Validação de Ordens | Serviço de risco e regras de negócio (saldo, posição, limite, mercado) | RN-01, RN-02, RN-09, RN-10 |
| RF-09 — Integração com a Bolsa simulada | UC06 Integrar com Bolsa | CU, CO, SQ, IM | Gateway Bolsa | Reenvio com o mesmo ID; tratamento de falha | RN-03, RN-07 |
| RF-10 — Ciclo de vida da ordem | UC07 Acompanhar ciclo de vida | CU, CO, ES | Gestão de Ordens | Tabela `historico_status_ordem`; estados finais travados | RN-04, RN-05 |
| RF-11 — Auditoria | UC09 Registrar logs de auditoria | CU, CO, SQ, IM | Auditoria | Log persistente e trilha de auditoria imutável (gatilhos) | RN-05, RN-15 |
| RF-12 — Notificar investidores | UC08 Notificar investidores | CU, CO | Notificação | Notificação em execução, rejeição, cancelamento ou falha | — |
| RF-13 — Recuperar operações | UC14 Recuperar operações | CU, CO, IM | Recuperação de Operações | Reprocessa sem duplicar; sincroniza status com a Bolsa | RN-03, RN-07 |
| RF-14 — Prevenção de duplicidade | UC05 Prevenir duplicidade | CU, CO, SQ | Controle de Duplicidade | Chave de idempotência (`UNIQUE`) | RN-03 |
| RF-15 — Consultar saldo, carteira e posição | UC15 Consultar carteira e saldo | CU, CO | Carteiras e Ativos + Gestão de Contas | Consulta apenas da própria conta | RN-08 |
| RF-16 — Consultar histórico de operações | UC16 Consultar histórico | CU, CO | Gestão de Ordens | Filtro por período e status; apenas ordens do próprio investidor | RN-08 |
| RF-17 — Gerenciar ativos e parâmetros | UC17 Gerenciar ativos e parâmetros | CU, CO | Carteiras e Ativos (Administração) | Tabela `parametro_sistema`; auditoria das alterações | RN-06, RN-10 |
| RF-18 — Acompanhar ordens bloqueadas/rejeitadas | UC18 Acompanhar ordens bloqueadas | CU, CO | Limites Financeiros + Gestão de Ordens | Acesso restrito ao perfil Analista de Risco | RN-06 |

## Requisitos não funcionais

| Requisito | Caso de uso associado | Diagramas | Controle técnico |
| :--- | :--- | :--- | :--- |
| RNF-01 — Segurança | UC01 Autenticar usuário | CU, CS, CO, IM | MFA, hash de senha, controle de acesso por perfil, HTTPS, firewall |
| RNF-02 — Rastreabilidade | UC09 Registrar auditoria | CO, SQ | Usuário, ação, ordem e data/hora em cada registro |
| RNF-03 — Integridade | UC03, UC04, UC12 | CO, CL | Restrições `CHECK`, chaves estrangeiras e estados válidos |
| RNF-04 — Disponibilidade | UC06, UC14 | SQ, IM | Status "Falha" + recuperação; Bolsa em servidor separado |
| RNF-05 — Desempenho | UC02, UC15 | IM | Índices no banco; cotações com horário |
| RNF-06 — Confiabilidade | UC05, UC06 Transmitir ordem | SQ, IM, ES | Chave de idempotência e tratamento de falha |
| RNF-07 — Auditabilidade | UC09 | CO, IM | Auditoria append-only (sem UPDATE/DELETE) |
| RNF-08 — Manutenibilidade | — | todos | Estrutura de pastas, commits padronizados, documentação versionada |
| RNF-09 — Privacidade | UC15, UC16 | CO | Investidor acessa só os próprios dados; somente dados fictícios |

## Diagramas citados

| Diagrama | Arquivo |
| :--- | :--- |
| Casos de Uso | [SentinelTrade_UML_Casos_de_Uso_Completo](../diagramas/SentinelTrade_UML_Casos_de_Uso_Completo.drawio.png) |
| Casos de Uso com segurança (UMLsec) | [SentinelTrade_UML_Casos_de_Uso_Seguranca](../diagramas/SentinelTrade_UML_Casos_de_Uso_Seguranca.drawio.png) |
| Componentes | [SentinelTrade_UML_Componentes](../diagramas/SentinelTrade_UML_Componentes.drawio.png) |
| Classes | [SentinelTrade_UML_Classes](../diagramas/SentinelTrade_UML_Classes.drawio.png) |
| Estados | [SentinelTrade_UML_Estados](../diagramas/SentinelTrade_UML_Estados.drawio.png) |
| Atividades | [SentinelTrade_UML_Atividades](../diagramas/SentinelTrade_UML_Atividades.drawio.png) |
| Sequência | [SentinelTrade_UML_Sequencia](../diagramas/SentinelTrade_UML_Sequencia.drawio.png) |
| Implantação | [SentinelTrade_UML_Implantacao](../diagramas/SentinelTrade_UML_Implantacao.drawio.png) |

## Requisito → código → teste

Caminhos do código a partir de `src/main/java/br/mackenzie/sentineltrade/`. Os testes ficam em [`tests/`](../../tests/).

| Requisito | Onde está no código | Onde aparece no site | Teste automático |
| :--- | :--- | :--- | :--- |
| RF-01 Autenticação com MFA | `seguranca/AutenticacaoService`, `seguranca/DesafioMfa`, `controller/LoginController` | Telas de login e de código MFA | `SegurancaBasicaTest`, `SegurancaWebTest` |
| RF-02 Gerenciar investidores | Tabela `investidor` (CPF `UNIQUE`) em `database/schema.sql`; dados fictícios em `seed.sql` | Nome do investidor no painel | Sem tela de cadastro nesta versão: dados prontos no `seed.sql` |
| RF-03 Gerenciar contas | Tabela `conta` (ligada ao investidor por chave estrangeira), `repository/ContaRepository` | Número da conta e saldo no painel | Sem tela de cadastro nesta versão: dados prontos no `seed.sql` |
| RF-04 Carteiras e ativos | `repository/CarteiraRepository` (preço médio), `CHECK quantidade >= 0` | Tabela "Minha carteira" | `OrdemServiceTest` (compra aumenta a carteira; venda sem posição é rejeitada) |
| RF-05 Limites financeiros | `service/regras/RegraLimitePorOrdem`, `RegraLimiteDiario`, `service/ContaService` | Painel do analista de risco | `ValidadorOrdemTest`, `ContaServiceTest` |
| RF-06 Cotações | `service/MercadoService`, `repository/AtivoRepository` | Tabela de cotações; admin altera a cotação | `OrdemServiceTest` (ordem aguardando executa quando a cotação cai) |
| RF-07 Gerenciar ordens | `service/OrdemService` (`criar`, `alterarPreco`, `cancelar`), `model/Ordem`, `controller/InvestidorController` | Nova ordem; Minhas ordens (alterar preço e cancelar) | `OrdemServiceTest`, `OrdemTest` |
| RF-08 Validar ordem | `service/regras/ValidadorOrdem` + 6 regras (mercado, ativo, saldo, posição, limite por ordem, limite diário) | Mensagem com o motivo da rejeição | `ValidadorOrdemTest` |
| RF-09 Bolsa simulada | `integracao/BolsaGateway`, `integracao/BolsaSimulada`, `integracao/RespostaBolsa` | Status "Enviada" / "Executada" | `OrdemServiceTest` |
| RF-10 Ciclo de vida | `model/StatusOrdem`, `model/Ordem`, tabela `historico_status_ordem` e gatilhos do `schema.sql` | Histórico de cada ordem em Minhas ordens | `StatusOrdemTest`, `OrdemTest`, `OrdemServiceTest` |
| RF-11 Auditoria | `auditoria/AuditoriaService`, `repository/AuditoriaRepository`, gatilhos do `schema.sql` | Tela Auditoria (administrador) | `OrdemServiceTest` (auditoria não pode ser alterada nem apagada) |
| RF-12 Notificações | `notificacao/NotificacaoService`, `repository/NotificacaoRepository` | Quadro "Notificações" no painel do investidor | `ContaServiceTest` |
| RF-13 Recuperar operações | `OrdemService.recuperarFalhas()`, `controller/AdminController` | Botão "Recuperar ordens" (administrador) | `OrdemServiceTest` (falha e recuperação) |
| RF-14 Prevenção de duplicidade | `OrdemService.criar()` + `UNIQUE` em `ordem.chave_idempotencia` | Reenvio do mesmo formulário é bloqueado | `OrdemServiceTest` |
| RF-15 Saldo, carteira e posição | `service/ContaService` (`saldo`, `carteira`), `controller/InvestidorController` | Painel do investidor | `ContaServiceTest` |
| RF-16 Histórico de operações | `OrdemService.listarDaConta()` e `historicoDaConta()` | Minhas ordens, com filtro por status | `ContaServiceTest` |
| RF-17 Ativos e parâmetros | `service/MercadoService`, `controller/AdminController` | Painel do administrador (cotações, mercado e Bolsa) | `OrdemServiceTest`, `ValidadorOrdemTest` (mercado fechado) |
| RF-18 Ordens bloqueadas/rejeitadas | `controller/RiscoController`, `OrdemService.listarRejeitadas()`, `ContaService.alterarLimites()` | Painel do analista de risco | `ContaServiceTest` |
| RNF-01 Segurança | `seguranca/SegurancaConfig` (perfis, CSRF, cabeçalhos), senhas BCrypt, formulários com validação | Cada perfil só abre as próprias telas | `SegurancaWebTest`, `SegurancaBasicaTest` |
| RNF-02 Rastreabilidade | `auditoria/AuditoriaService` (usuário, ação, ordem e data/hora) | Tela Auditoria | `OrdemServiceTest` |
| RNF-03 Integridade | Validação em `model/Ordem`, restrições `CHECK` e chaves estrangeiras, `@Transactional` nos serviços | Dados inválidos são recusados | `OrdemTest`, `OrdemServiceTest` |
| RNF-04 Disponibilidade | Status `FALHA` + `recuperarFalhas()`; `BolsaIndisponivelException` | Botões "Simular queda" / "Restabelecer" | `OrdemServiceTest` (Bolsa fora do ar não mexe no saldo) |
| RNF-05 Desempenho | Índices no `schema.sql`; cotação guardada com data e hora | Telas carregam direto do banco local | Sem teste automático (verificado ao usar o site) |
| RNF-06 Confiabilidade | Chave de idempotência e transações em `OrdemService` | Mesma ordem nunca é executada duas vezes | `OrdemServiceTest` |
| RNF-07 Auditabilidade | Gatilhos `trg_auditoria_sem_update` e `trg_auditoria_sem_delete` | Tela Auditoria | `OrdemServiceTest` |
| RNF-08 Manutenibilidade | Camadas separadas (controller, service, repository, model), documentação em `docs/` | — | Esteira DevSecOps roda todos os testes a cada envio |
| RNF-09 Privacidade | Conferência do dono da ordem em `OrdemService`; `seguranca/Sanitizador` limpa o log; só dados fictícios | Investidor vê só a própria conta | `OrdemServiceTest` (só o dono cancela), `ContaServiceTest` |
