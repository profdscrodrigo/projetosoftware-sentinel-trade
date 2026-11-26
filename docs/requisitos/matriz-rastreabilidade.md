# Matriz de rastreabilidade

Liga **requisito → caso de uso → diagrama → controle técnico/código → teste**, em ambos os sentidos.
Todos os nomes de classe e de teste abaixo existem no repositório.
Diagramas em `docs/uml/` (exportações em `diagrams/`).

## 1. Requisitos funcionais

| Requisito | Caso de uso | Diagramas | Controle técnico / classes principais | Tabelas SQL | Testes |
|---|---|---|---|---|---|
| **RF01** Autenticar | Autenticar-se com MFA | Casos de uso · Atividade *autenticação-mfa* · Sequência *login-mfa* · Estados *sessão* | `AutenticacaoService`, `Pbkdf2Hasher`, `LimitadorDeTaxa` | `usuarios`, `sessoes` | `AutenticacaoServiceTest` |
| **RF02** MFA | Autenticar-se com MFA | idem · Componentes | `TotpMfa`, `Sessao` | `sessoes` | `SegurancaCriptograficaTest`, `AutenticacaoServiceTest` |
| **RF03** Saldo e carteira | Consultar saldo · Consultar carteira | Casos de uso · Classes análise/projeto | `CarteiraService`, `Conta`, `Posicao` | `contas`, `posicoes` | `PrivacidadeEAcessoTest`, `ProcessadorOrdensTest` |
| **RF04** Cotações | Consultar cotação | Casos de uso · Componentes | `CotacaoService`, `ProvedorCotacoes`, `CotacoesSimuladas` | — | `CotacaoServiceTest` |
| **RF05** Compra | Enviar ordem de compra | Casos de uso · Atividade *validação* · Sequência *enviar-ordem* · Estados *ordem* | `OrdemService`, `OrdemCompra`, `ValidadorOrdem`, `Conta.bloquear` | `ordens`, `contas`, `fila_ordens` | `OrdemServiceTest`, `OrdemTest` |
| **RF06** Venda | Enviar ordem de venda | idem | `OrdemService`, `OrdemVenda`, `Posicao.bloquear` | `ordens`, `posicoes`, `fila_ordens` | `OrdemServiceTest`, `OrdemTest` |
| **RF07** Cancelar | Cancelar ordem | Casos de uso · Atividade *cancelar-ordem* · Sequência *cancelar-ordem* · Estados *ordem* | `OrdemService.cancelarOrdem`, `Ordem.cancelar` | `ordens` | `OrdemServiceTest` (cancel…) |
| **RF08** Validar | Validar ordem → Verificar saldo/posição, limites, mercado e cotação | Casos de uso · Atividade *validação* | `ValidadorOrdem`, `CalendarioMercado`, `Validacoes` | `ativos`, `limites_risco`, `parametros` | `OrdemServiceTest`, `ValidacoesTest` |
| **RF09** Ciclo de vida | Consultar status da ordem | Estados *ordem* · Sequência *processar-execução* | `StatusOrdem`, `Ordem` | `ordens.status` (`CHECK`) | `StatusOrdemTest`, `OrdemTest` |
| **RF10** Auditoria | Registrar auditoria | Casos de uso · Componentes · Classes | `AuditoriaService`, `AuditoriaRepositorio` | `auditoria` + `hardening-postgres.sql` | `AuditoriaServiceTest` |
| **RF11** Notificações | Enviar notificação | Casos de uso · Sequência *processar-execução* | `NotificacaoService` (outbox), `EnviadorNotificacao` | `notificacoes` | `ProcessadorOrdensTest`, `PrivacidadeEAcessoTest` |
| **RF12** Ativos e parâmetros | Gerenciar ativos e parâmetros | Casos de uso | `AdministracaoService`, `ParametrosSistema` | `ativos`, `parametros` | `AdministracaoRiscoTest` |
| **RF13** Investidores | Gerenciar investidores | Casos de uso | `AdministracaoService.provisionar` | `usuarios`, `contas`, `limites_risco` | `AdministracaoRiscoTest` |
| **RF14** Histórico | Consultar histórico de operações | Casos de uso | `OrdemService.historico`, `execucoesDaOrdem` | `ordens`, `execucoes` | `OrdemServiceTest.historico…` |
| **RF15** Limites | Gerenciar limites de risco | Casos de uso | `RiscoService`, `LimiteRisco` | `limites_risco` | `AdministracaoRiscoTest` |
| **RF16** Transmissão assíncrona | Transmitir ordem à corretora | Casos de uso · Atividade *transmissão-liquidação* · Sequências *processar-execução* e *falha-corretora* · Estados *circuit-breaker* | `ProcessadorOrdens`, `FilaOrdens`, `CorretoraResiliente` | `fila_ordens` | `ProcessadorOrdensTest`, `CorretoraResilienteTest` |
| **RF17** Execução e liquidação | Executar ou rejeitar ordem | Atividade *transmissão-liquidação* · Sequência *processar-execução* | `Ordem.aplicarExecucao`, `ProcessadorOrdens.aplicarRelatorio` | `execucoes`, `contas`, `posicoes` | `ProcessadorOrdensTest`, `OrdemTest` |
| **RF18** Alto valor | Confirmar operação de alto valor («extend») | Casos de uso · Atividade *validação* | `OrdemService.enviarOrdem`, `ConfirmacaoNecessariaException` | `limites_risco.valor_confirmacao` | `OrdemServiceTest` |
| **RF19** Ordens rejeitadas | Consultar ordens rejeitadas | Casos de uso | `RiscoService.ordensRejeitadas` | `ordens` | `AdministracaoRiscoTest` |
| **RF20** Depósito simulado | Gerenciar investidores | Casos de uso | `AdministracaoService.depositarSimulado` | `contas` | `AdministracaoRiscoTest` |
| **RF21** Alterar ordem | Alterar ordem (cancelar e substituir) | Casos de uso · Atividade *alterar-ordem* · Sequência *alterar-ordem* · Classes de projeto | `OrdemService.alterarOrdem`, `Ordem.alteravel` | `ordens.substitui_ordem_id` | `OrdemMercadoEAlteracaoTest` |
| **RF22** Ordem a mercado | Enviar ordem de compra/venda (a mercado) | Casos de uso · Classes de projeto | `ValidadorOrdem.precoDeProtecao`, `TipoOrdem` | `ordens.tipo_ordem` | `OrdemMercadoEAlteracaoTest` |
| **RF23** API REST e interface web | Todos (via HTTP) | Componentes · Classes de projeto (infraestrutura) | `ServidorWeb`, `Json`, `src/main/resources/web/` | — | `ServidorWebTest`, `JsonTest`, `teste-interface-web.txt` |

## 2. Requisitos não funcionais

| Requisito | Controle técnico | Onde | Evidência |
|---|---|---|---|
| **RNF01** Segurança | PBKDF2 + salt; TOTP com segredo cifrado (AES-256-GCM); RBAC; allowlist; `PreparedStatement`; `.gitignore`/`.env.example`; erros genéricos; CSP e limites na API | `Pbkdf2Hasher`, `TotpMfa`, `CifradorSegredo`, `AutenticacaoService`, `Validacoes`, `Jdbc`, `ServidorWeb` | `SegurancaCriptograficaTest`, `CifradorSegredoTest`, `ValidacoesTest`, `ServidorWebTest`, `validacao-sql.txt` (0 concatenações), demo §1 e §14 |
| **RNF02** Integridade | Invariantes + `CHECK` no banco | `Ordem`, `Conta`, `Posicao`, `schema.sql` | `OrdemTest`, `validacao-sql.txt` |
| **RNF03** Auditabilidade | Hash encadeado SHA-256; *append-only*; gatilho PostgreSQL | `AuditoriaService`, `hardening-postgres.sql` | `AuditoriaServiceTest`, demo §15 |
| **RNF04** Disponibilidade | Fila durável, *timeout*, *circuit breaker*, cache de cotação com validade | `ProcessadorOrdens`, `CorretoraResiliente`, `CotacaoService` | `ProcessadorOrdensTest`, `CorretoraResilienteTest`, demo §10–11 |
| **RNF05** Desempenho | Validação externa fora da transação; índices | `OrdemService`, `schema.sql` | `desempenho.txt` |
| **RNF06** Confiabilidade | Chave de idempotência; `id_execucao` único; `idCliente` | `OrdemService`, `Execucoes`, `CorretoraSimulada` | `OrdemServiceTest`, `ProcessadorOrdensTest`, demo §4 |
| **RNF07** Manutenibilidade | Arquitetura hexagonal; docs e SQL versionados | Estrutura de pastas | `README.md`, `docs/evidencias/resultado-testes-maven-postgresql-2026-11-26.txt` |
| **RNF08** Privacidade | Identidade derivada da sessão; "não encontrado" para ordem alheia | `OrdemService`, `CarteiraService` | `PrivacidadeEAcessoTest`, demo §13 |
| **RNF09** Escalabilidade | Fila + workers idempotentes; `versao` otimista | `FilaOrdens`, `RepositoriosJdbc` | Diagrama de componentes |
| **RNF10** Recuperabilidade | *Outbox*; reentrega de relatórios; consumidor idempotente | `ProcessadorOrdens` | `ProcessadorOrdensTest.reenvioParaACorretora…` |
| **RNF11** Consistência | Transação única ordem+reserva+fila+auditoria; retentativa em conflito | `GerenciadorTransacao`, `Retentativa` | `OrdemServiceTest.envioSimultaneo…`; conservação na demo (§ resumo) |
| **RNF12** Rastreabilidade | Log sanitizado, sem segredos | `Sanitizador`, `Log` | `AuditoriaServiceTest.detalhesSaoSanitizados…` |

## 3. Regras de negócio ↔ requisitos

Ver a coluna **Req.** e **Teste** em [`regras-de-negocio.md`](regras-de-negocio.md) (RN01–RN26).

## 4. Matriz inicial do enunciado (continua válida)

| Requisito | Caso de uso associado | Diagrama | Controle técnico |
|---|---|---|---|
| RF05 — Enviar ordem de compra | Enviar ordem de compra | Atividade, sequência e estados ✔ | Validação de saldo, limite e idempotência ✔ |
| RF06 — Enviar ordem de venda | Enviar ordem de venda | Atividade, sequência e estados ✔ | Validação de posição disponível ✔ |
| RF08 — Validar ordem | Validar ordem | Atividade e sequência ✔ | Serviço de risco e regras de negócio (`ValidadorOrdem`) ✔ |
| RF10 — Auditar eventos | Registrar auditoria | Classes e componentes ✔ | Auditoria persistente com hash encadeado e proteção contra alteração/remoção ✔ |
| RNF01 — Segurança | Autenticar usuário | Casos de uso e componentes ✔ | MFA, hash, controle de acesso ✔ |
| RNF06 — Confiabilidade | Transmitir ordem | Sequência e estados ✔ | Chave de idempotência e tratamento de falha ✔ |
