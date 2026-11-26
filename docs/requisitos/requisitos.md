# Requisitos do SentinelTrade

> **Projeto acadêmico.** Opera exclusivamente com dados, contas, ativos, cotações e corretora **simulados**.
> Nenhum dinheiro, bolsa ou dado financeiro real é utilizado.

## 1. Visão do sistema

O **SentinelTrade** é uma plataforma simulada de negociação de ativos financeiros (ações, ETFs e FIIs) da corretora fictícia
**Orion Capital**. Ela permite que investidores autenticados (senha + segundo fator) acompanhem cotações e carteira e enviem
ordens de compra e venda, **respeitando saldo, posição disponível, limites de risco, situação do mercado e regras de segurança**.

O sistema garante que as ordens sejam **rastreáveis**, **nunca executadas em duplicidade** e que **todo evento relevante**
fique em uma trilha de auditoria à prova de adulteração. Falhas de sistemas externos (corretora, provedor de cotações) não
podem corromper ordens, saldos ou posições.

**Para quem existe:** o Investidor (usuário final), o Administrador (operação), o Analista de Risco (controle) e os sistemas
externos que se integram (Corretora/Bolsa, Provedor de Cotações, Serviços de Notificação e de Auditoria).

## 2. Atores

| Ator | Tipo | Responsabilidade |
|---|---|---|
| **Investidor** | Humano | Consulta cotações, saldo, carteira e histórico; envia e cancela ordens; recebe notificações. |
| **Administrador** | Humano | Gerencia investidores, ativos, parâmetros operacionais, limites de risco e depósitos simulados; consulta e verifica a auditoria. |
| **Analista de Risco** | Humano | Consulta e ajusta limites de risco; acompanha ordens rejeitadas. (O ajuste de limites também é permitido ao Administrador, conforme a tabela de atores do enunciado.) |
| **Corretora/Bolsa Simulada** | Sistema externo | Recebe ordens válidas; devolve aceite, execuções (totais/parciais) ou rejeição. |
| **Provedor de Cotações** | Sistema externo | Fornece preços atualizados (simulados). Dado **não confiável**: é revalidado. |
| **Serviço de Notificação** | Sistema | Informa o investidor sobre execução, rejeição, cancelamento ou falha. |
| **Serviço de Auditoria** | Sistema | Mantém o registro imutável de ações, decisões e mudanças de estado. |

## 3. Requisitos funcionais

Cada critério de aceitação é verificável; a coluna **Verificação** aponta o teste automatizado ou a evidência.
A numeração **RF01–RF12 segue o enunciado**; acrescentamos RF13–RF23 para cobrir outros pontos exigidos no próprio
enunciado (cadastro de investidores, histórico, limites, transmissão assíncrona, **alteração de ordem**, interface etc.).

| ID | Requisito | Critério de aceitação | Prior. | Verificação |
|---|---|---|---|---|
| **RF01** | O sistema deve autenticar usuários por e-mail e senha. | **Dado** um usuário válido, **quando** informa a senha correta, **então** recebe um token *pendente* (sem acesso ainda). **Dado** credencial inválida ou conta bloqueada, **então** o acesso é negado com mensagem única (sem revelar se o e-mail existe). Após 5 falhas seguidas a conta fica bloqueada por 15 min. | Alta | `AutenticacaoServiceTest` |
| **RF02** | O sistema deve exigir um segundo fator (TOTP simulado). | O acesso só é liberado após código de 6 dígitos válido (janela de 30 s ±1). 3 códigos errados invalidam a pendência. O token pendente não dá acesso e só pode ser usado uma vez. | Alta | `AutenticacaoServiceTest`, `SegurancaCriptograficaTest` (vetores da RFC 6238) |
| **RF03** | O investidor deve consultar saldo, carteira e posição por ativo. | A carteira mostra, por ativo: quantidade, quantidade bloqueada, preço médio, cotação atual e valor estimado; e o saldo mostra disponível e bloqueado. Só dados do próprio investidor. | Alta | `ProcessadorOrdensTest`, `PrivacidadeEAcessoTest` |
| **RF04** | O investidor deve consultar cotações dos ativos. | Cada cotação mostra preço, horário e variação percentual; indica se está **desatualizada** (> 60 s). | Alta | `CotacaoServiceTest` |
| **RF05** | O investidor deve enviar ordem de **compra** (a limite ou a mercado, RF22). | A ordem só é aceita (VALIDADA) com saldo disponível ≥ qtd × preço, dentro dos limites de risco, mercado aberto, ativo ativo, cotação recente e preço na faixa. O valor fica **reservado** (saldo bloqueado). Caso contrário, fica REJEITADA com o motivo registrado. | Alta | `OrdemServiceTest`, `OrdemTest` |
| **RF06** | O investidor deve enviar ordem de **venda** (a limite ou a mercado, RF22). | Só é aceita com quantidade **disponível** do ativo (descontadas as já reservadas por outras vendas). A quantidade fica reservada. | Alta | `OrdemServiceTest`, `OrdemTest` |
| **RF07** | O investidor deve cancelar uma ordem ainda não executada. | Ordens CRIADA/VALIDADA/ENVIADA/PARCIALMENTE_EXECUTADA podem ser canceladas e **devolvem a reserva restante**; EXECUTADA/REJEITADA **não** podem. Cancelamento é idempotente. Se a corretora estiver fora do ar, a ordem **permanece inalterada**. | Alta | `OrdemServiceTest` |
| **RF08** | O sistema deve validar ordens antes da transmissão. | Dados inválidos, saldo/posição insuficiente, limite excedido, mercado fechado, ativo suspenso, cotação desatualizada ou preço fora da faixa **impedem o envio**; a rejeição é registrada e auditada. | Alta | `OrdemServiceTest`, `ValidacoesTest` |
| **RF09** | O sistema deve registrar o ciclo de vida da ordem. | O investidor consulta o status atualizado: CRIADA → VALIDADA → ENVIADA → PARCIALMENTE_EXECUTADA → EXECUTADA, ou REJEITADA / CANCELADA. Transições fora da tabela são recusadas. | Alta | `StatusOrdemTest`, `OrdemTest` |
| **RF10** | O sistema deve registrar eventos de auditoria. | Cada criação, validação, rejeição, envio, execução, cancelamento, login, acesso negado e alteração administrativa gera evento encadeado por hash; adulteração/remoção é detectável. | Alta | `AuditoriaServiceTest` |
| **RF11** | O sistema deve notificar o investidor sobre eventos críticos. | Há notificação em execução (total/parcial), rejeição, cancelamento e falha de integração; o envio ocorre fora da transação e é reenviado se falhar. | Média | `ProcessadorOrdensTest`, `PrivacidadeEAcessoTest` |
| **RF12** | O administrador deve gerenciar ativos e parâmetros do sistema. | Cadastro/suspensão de ativos e alteração de parâmetros (horário de mercado, idade máxima da cotação, faixa de preço, frequência máx.) só por chaves permitidas e valores válidos; cada alteração é auditada. | Média | `AdministracaoRiscoTest` |
| **RF13** | O administrador deve cadastrar e gerenciar investidores. | Cria usuário com senha forte (hash), segredo MFA, conta e limites padrão; recusa e-mail duplicado, senha fraca e entradas inválidas. | Alta | `AdministracaoRiscoTest` |
| **RF14** | O investidor deve consultar o histórico de operações. | Lista todas as ordens próprias (todos os status), mais recentes primeiro, e as execuções de cada ordem. | Média | `OrdemServiceTest.historicoLista…` |
| **RF15** | O analista de risco (e o administrador) devem consultar e ajustar limites. | Define limite por ordem, limite diário e valor de confirmação (coerentes entre si); vale para a próxima ordem; alteração auditada. Investidores não têm acesso. | Média | `AdministracaoRiscoTest` |
| **RF16** | O sistema deve transmitir ordens validadas à corretora de forma **assíncrona** e resiliente. | Ordem VALIDADA entra em fila durável; envio com *timeout*, *circuit breaker*, até 5 tentativas com recuo exponencial; reenvio é idempotente na corretora. Esgotadas as tentativas: REJEITADA (falha de integração) e reserva devolvida. | Alta | `ProcessadorOrdensTest`, `CorretoraResilienteTest` |
| **RF17** | O sistema deve processar execuções e liquidar saldo/carteira. | Cada execução (parcial/total) atualiza ordem, saldo e posição **na mesma transação**; execuções repetidas são ignoradas (idempotência); preço melhor que o limite devolve a diferença. | Alta | `ProcessadorOrdensTest`, `OrdemTest` |
| **RF18** | O sistema deve exigir confirmação adicional para ordens de alto valor. | Ordem acima do *valor de confirmação* (e dentro do máximo por ordem) só é criada após confirmação explícita; sem ela, nada é gravado. *(«extend» do caso de uso)* | Média | `OrdemServiceTest` |
| **RF19** | O analista de risco deve acompanhar ordens rejeitadas. | Lista ordens REJEITADAS com o motivo. | Baixa | `AdministracaoRiscoTest` |
| **RF20** | O administrador deve registrar depósitos simulados. | Credita saldo (valor positivo, ≤ R$ 1.000.000,00), auditado. Necessário porque o dinheiro é simulado. | Média | `AdministracaoRiscoTest` |
| **RF21** | O investidor deve **alterar** uma ordem ainda não executada (atualização de ordem). | Quantidade e/ou preço de ordem VALIDADA ou ENVIADA **sem nenhuma execução** podem ser alterados. A alteração é *cancelar e substituir*: a original vira CANCELADA (devolvendo a reserva) e nasce uma nova ordem, com id próprio, ligada à original. Tudo que pode recusar a alteração (mercado, faixa, limites, saldo/posição, confirmação, corretora fora do ar) é verificado **antes** de cancelar; se falhar, a original permanece intacta. Reenvio da mesma alteração (mesma chave) não cria outra ordem. | Alta | `OrdemMercadoEAlteracaoTest` |
| **RF22** | O investidor deve poder enviar ordem **a mercado**. | Sem informar preço: o sistema usa um **preço de proteção** (cotação ± faixa permitida) como limite, de modo que reserva de saldo, limites de risco e faixa de preço continuam valendo. Executa ao preço de mercado e devolve a diferença da reserva. Sem cotação válida e recente a ordem é recusada na entrada. | Média | `OrdemMercadoEAlteracaoTest` |
| **RF23** | O sistema deve oferecer **API REST e interface web**, além do console. | Todas as funções do investidor, do administrador e do analista de risco disponíveis via HTTP/JSON e por uma tela web, com as **mesmas** regras (a interface é fina). Token em `Authorization: Bearer`; corpo ≤ 16 KB; só objetos JSON planos com campos permitidos; sem CORS; CSP restritiva; erros sem detalhes internos. | Média | `ServidorWebTest`, `JsonTest`, `docs/evidencias/teste-interface-web.txt` |

## 4. Requisitos não funcionais

**RNF01–RNF08 seguem o enunciado**; RNF09–RNF12 vêm da devolutiva da Fase 1 e do README original.

| ID | Requisito | Critério de aceitação mensurável | Como é atendido | Verificação |
|---|---|---|---|---|
| **RNF01** | Segurança | Senhas somente como hash PBKDF2-HMAC-SHA256 (600.000 iterações, salt de 16 bytes por senha); MFA obrigatório e **segredo MFA cifrado em repouso com AES-256-GCM** (chave só por variável de ambiente; com PostgreSQL o sistema não inicia sem ela); controle de acesso por perfil no servidor; toda entrada validada por *allowlist*; SQL 100% parametrizado; nenhum segredo no repositório; erros ao usuário sem detalhes técnicos. | `Pbkdf2Hasher`, `TotpMfa` + `CifradorSegredo`, `AutenticacaoService.exigir`, `Validacoes`, `Jdbc` (PreparedStatement), `.env.example` | `SegurancaCriptograficaTest`, `CifradorSegredoTest`, `ValidacoesTest`, `AutenticacaoServiceTest`, `ServidorWebTest`, varredura de segredos |
| **RNF02** | Integridade | Toda ordem tem investidor, ativo, lado, quantidade > 0 e preço > 0 válidos; saldo e quantidades nunca ficam negativos (no código **e** no banco por `CHECK`). | Invariantes em `Ordem`/`Conta`/`Posicao`; `CHECK` em `schema.sql` | `OrdemTest`, `validacao-sql.txt` |
| **RNF03** | Auditabilidade | 100% das mudanças de estado e ações sensíveis geram evento; a trilha é *append-only*, com hash encadeado SHA-256; qualquer alteração/remoção/reordenação é detectada; no PostgreSQL, `UPDATE/DELETE/TRUNCATE` são bloqueados por gatilho e privilégio. | `AuditoriaService`, `hardening-postgres.sql` | `AuditoriaServiceTest` |
| **RNF04** | Disponibilidade | Falha da corretora **não** corrompe ordens/posições: a ordem continua na fila e, se esgotar as tentativas, é rejeitada com devolução integral da reserva. Falha do provedor de cotações: usa a última cotação válida recente; se antiga, **bloqueia** novas ordens (nunca negocia com preço velho). | Fila durável, `CorretoraResiliente`, `CotacaoService` | `ProcessadorOrdensTest`, `OrdemServiceTest`, `CotacaoServiceTest` |
| **RNF05** | Desempenho | Em ambiente simulado (em memória), consulta de carteira e de cotações com p95 < 5 ms e envio de ordem (validação + reserva + auditoria) com p95 < 50 ms, excluindo a latência do banco e da corretora. **Resultado do ensaio registrado:** carteira p95 0,03 ms; cotações p95 0,01 ms; envio de ordem p95 0,79 ms (n = 2.000). O ensaio foi feito em memória, sem latência de banco ou rede. | Validação de mercado fora da transação; consultas indexadas | `docs/evidencias/desempenho.txt` |
| **RNF06** | Confiabilidade | Reenvios e falhas de comunicação não duplicam ordens: mesma chave de idempotência → mesma ordem; execução repetida → aplicada uma única vez; reenvio à corretora → ela mantém uma única ordem. | `UNIQUE(investidor_id, chave_idempotencia)`, `PK id_execucao`, `idCliente` na corretora | `OrdemServiceTest`, `ProcessadorOrdensTest` |
| **RNF07** | Manutenibilidade | Código em camadas (interface / aplicação / portas / infraestrutura) sem dependência de framework no núcleo; documentação, SQL e diagramas versionados; suíte de testes das regras críticas. | Arquitetura hexagonal; ver `README.md` | 169 testes executados (incluindo 2 de integração com PostgreSQL; execução de 26/11/2026 em `resultado-testes-maven-postgresql-2026-11-26.txt`) |
| **RNF08** | Privacidade | Um investidor só acessa os próprios dados: o investidor vem **da sessão**, nunca de parâmetro; ordem de terceiros é indistinguível de inexistente. | `OrdemService.doInvestidor`, `CarteiraService` | `PrivacidadeEAcessoTest` |
| **RNF09** | Escalabilidade | Serviços sem estado de sessão em memória (sessão no banco); transmissão desacoplada por fila (workers adicionáveis, pois o reenvio é idempotente); concorrência otimista por coluna `versao`. | Fila durável; `UPDATE … WHERE versao = ?` | Revisão de projeto; limite documentado em §6 |
| **RNF10** | Recuperabilidade | Após queda em qualquer ponto, nenhuma ordem é perdida nem duplicada: o estado vive no banco; mensagens não concluídas são reprocessadas; relatórios não confirmados são reentregues. | Padrão *outbox*; entrega "pelo menos uma vez" + consumidor idempotente | `ProcessadorOrdensTest.reenvioParaACorretora…` |
| **RNF11** | Consistência de dados | Ordem, reserva de saldo/posição, fila e auditoria confirmam **juntos ou nenhum** (transação); atualizações concorrentes são detectadas e repetidas. | `GerenciadorTransacao`, `TransacaoJdbc`, `Retentativa` | `OrdemServiceTest.envioSimultaneo…`; conservação financeira na demo |
| **RNF12** | Rastreabilidade/observabilidade | Cada ordem tem id, chave e histórico de eventos; logs não contêm senha, token, segredo ou hash e têm quebras de linha neutralizadas. | `Sanitizador`, `Log`, auditoria | `AuditoriaServiceTest.detalhesSaoSanitizados…` |

## 5. Mapeamento dos requisitos originais (README da Fase 1)

| Original | Novo(s) | Observação |
|---|---|---|
| RF-01 Cadastro de investidores, contas, carteiras, ativos e limites | RF13, RF12, RF15, RF20 | Dividido por ator/responsabilidade; conta e carteira nascem com o investidor. |
| RF-02 Consulta (carteira, conta, limites) | RF03, RF15 | |
| RF-03 Cotações em tempo quase real | RF04 | |
| RF-04 Envio de ordens (compra, venda, cancelamento, consulta) | RF05, RF06, RF07, RF09 | |
| RF-05 Validação antes de transmitir | RF08, RF18 | Detalhada nas regras RN01–RN15. |
| RF-06 Integração com Bolsa/Corretora simulada | RF16, RF17 | |
| RF-07 Ciclo de vida das ordens | RF09 | |
| RF-08 Logs de auditoria imutáveis | RF10, RNF03 | |
| RF-09 Notificações | RF11 | |
| RF-10 Histórico | RF14 | |
| RNF-01 / RNF-10 MFA e proteção de senhas | RNF01 | |
| RNF-02 Recuperação, indisponibilidade controlada, duplicidade | RNF04, RNF06, RNF10 | Separado em três critérios mensuráveis. |
| RNF-03 Integridade | RNF02 | |
| RNF-04 Desempenho | RNF05 | Critérios numéricos e resultados do ensaio registrados em `desempenho.txt`. |
| RNF-05 Escalabilidade | RNF09 | |
| RNF-06 Segurança de dados | RNF01, RNF08 | |
| RNF-07 Recuperação de falhas | RNF10 | |
| RNF-08 Consistência | RNF11 | |
| RNF-09 Disponibilidade | RNF04 | |

## 6. Restrições, premissas e limites assumidos

- **Simulação:** dinheiro, ativos, cotações, corretora e serviço de notificação são simulados; feriados não são modelados.
- **Moeda e precisão:** valores em R$ com 2 casas (`BigDecimal`/`NUMERIC(18,2)`, nunca ponto flutuante).
- **Tipos de ordem:** a **preço limite** e **a mercado** (esta, via preço de proteção). A execução ocorre ao preço de mercado quando ele cruza o limite. Não há ordens condicionais (*stop*) nem *day trade*.
- **Alteração = cancelar e substituir** (como nas bolsas), não edição no mesmo registro: evita misturar execuções em trânsito da ordem antiga com os novos parâmetros. Se, por concorrência, a nova ordem for rejeitada depois do cancelamento da original, a original permanece cancelada (a rejeição é registrada e notificada).
- **Sem fracionamento de risco por ativo:** os limites são por ordem e por dia (em valor), não por ativo.
- **Trilha única:** a cadeia de auditoria é global (serializa gravações); em alto volume, particionar por entidade é a evolução natural.
- **Um worker por vez** é o cenário testado; vários workers são seguros pela idempotência, mas a fila padrão não usa `SKIP LOCKED`.
- **Persistência verificada em duas etapas:** validamos os 45 comandos SQL do JDBC contra o esquema e executamos `PostgresIntegracaoTest` contra um PostgreSQL real, inclusive com concorrência. Os testes de integração dependem de `SENTINEL_IT_DB_URL`; em 26/11/2026, executamos os dois testes e registramos o resultado em `docs/evidencias/resultado-testes-maven-postgresql-2026-11-26.txt`.
- **Interface web simples:** HTTP sem TLS (escuta só em 127.0.0.1 por padrão). Em produção, colocar atrás de um proxy reverso com TLS.
