# Nossa análise do pensamento sistêmico

O enunciado pede esta avaliação. Em nossa análise, pensamento sistêmico significa: olhar o **todo** e as **relações** entre as
partes (não cada módulo isolado); prever **como uma falha se propaga**; identificar **ciclos de realimentação**; e aceitar
**trade-offs** conscientes. Avaliamos com honestidade o que entregamos na Fase 1 (README, lista de requisitos e diagrama de casos de
uso) e o que mudou nesta versão.

## 1. Diagnóstico da Fase 1

| Critério | Evidência na Fase 1 | Avaliação |
|---|---|---|
| **Visão do todo** | Na Fase 1, nosso README tinha a seção "Pensamento Sistêmico" e um esboço de arquitetura com 7 componentes (Frontend, Backend, Autenticação, Motor de Ordens, Banco, Corretora, Provedor). | ✅ **Presente na intenção.** Percebemos que o sistema é um conjunto de partes interdependentes. |
| **Falhas que se propagam** | O texto afirma que "uma falha em um componente pode afetar os demais" e lista *timeout*, retentativa, idempotência e fila. | 🟡 **Declarado, não demonstrado.** Nenhum requisito, regra ou diagrama dizia *o que acontece* quando a corretora cai no meio de uma ordem. |
| **Fronteira do sistema e atores** | O diagrama de casos de uso tinha só 3 dos 7 atores (sem Administrador, Analista de Risco, Notificação, Auditoria). | 🔴 **Lacuna.** Ator ausente = fronteira mal desenhada: ninguém "dono" do risco, da auditoria ou dos parâmetros. |
| **Relações entre casos de uso** | «include» invertido: *Transmitir ordem* incluía *Acompanhar ciclo de vida*; Notificação ligada ao Investidor. | 🔴 **Causalidade confundida.** O ciclo de vida é *consequência* da execução, não parte da transmissão. |
| **Ciclos de realimentação** | Não mencionados. | 🔴 **Ausente.** Ex.: o que uma rejeição ou cancelamento devolve ao limite do dia? |
| **Requisitos mensuráveis** | RNFs como "Desempenho", "Disponibilidade", "Escalabilidade" sem critério. | 🔴 **Não verificáveis** (a devolutiva do professor aponta o mesmo). |
| **Coerência com o contexto** | README em Python; o enunciado exige Java; banco "a definir". | 🟡 Decisões de plataforma em aberto atrasam a integração das partes. |

**Conclusão sobre a Fase 1: parcialmente.** Havia *mentalidade* sistêmica (as intenções estavam certas), mas ainda sem *método*:
faltava transformar a intuição em análise de interdependências, modos de falha e regras verificáveis.

## 2. O que esta versão faz para concretizar o pensamento sistêmico

### 2.1 Propagação de falhas — para cada ponto, o que acontece e como é contido

| Se falhar… | Efeito sem tratamento | Contenção projetada | Resultado garantido | Evidência |
|---|---|---|---|---|
| **Corretora indisponível** ao enviar | Ordem perdida, ou saldo preso para sempre | Fila durável + recuo exponencial + *circuit breaker*; após 5 tentativas, rejeita **e devolve a reserva** | Ordem nunca some; saldo/carteira intactos | `ProcessadorOrdensTest.corretoraIndisponivel…`, demo §10 |
| **Corretora responde, mas a resposta se perde** | Ordem duplicada na bolsa | Reenvio com o mesmo `idCliente` (idempotente na corretora) | Uma única ordem na corretora | `…reenvioParaACorretoraNaoCriaSegundaOrdemLa` |
| **Relatório de execução chega duas vezes** | Saldo creditado em dobro | `idExecucao` único + consumidor idempotente | Liquida uma só vez | `…relatorioDuplicado…` |
| **Relatório inválido** (preço pior que o limite, quantidade maior) | Carteira corrompida por dado externo | Revalidação na `Ordem.aplicarExecucao`; descarta e audita | Saldo não muda | `…execucaoInconsistente…` |
| **Execução chega depois do cancelamento** | Saldo devolvido *e* ativo comprado | Estado terminal não aceita execução → anomalia auditada p/ conciliação | Sem alteração silenciosa | `…execucaoParaOrdemJaCancelada…` |
| **Provedor de cotações fora do ar / dado absurdo** | Negociar com preço errado | Revalida; guarda última cotação válida; **bloqueia** ordens se estiver velha | Nunca negocia a preço velho | `CotacaoServiceTest`, `OrdemServiceTest` |
| **Banco: dois processos alteram o mesmo saldo** | Dupla alocação do mesmo dinheiro | Reserva atômica + `versao` otimista + retentativa | Nenhum gasto duplo | `OrdemServiceTest.envioSimultaneo…` |
| **Queda no meio da operação** | Ordem criada sem reserva, ou reserva sem fila | Uma transação: ordem + reserva + fila + auditoria | Tudo ou nada | `GerenciadorTransacao` (atomicidade real no PostgreSQL) |
| **Serviço de notificação falha** | Ordem desfeita por causa de um e-mail | *Outbox*: grava na transação, envia depois e reenvia | Fato de negócio independe da notificação | `NotificacaoService` |
| **Usuário reenvia (duplo clique / timeout)** | Ordem dupla | Chave de idempotência por intenção | Uma ordem; mesmo resultado | `OrdemServiceTest.reenvio…` |
| **Alguém tenta apagar rastros** | Perda de rastreabilidade | Hash encadeado + `UPDATE/DELETE` bloqueados no banco | Adulteração detectável | `AuditoriaServiceTest` |
| **Alteração de ordem: falha entre cancelar a antiga e criar a nova** | Investidor fica sem ordem nenhuma | Tudo que pode recusar a alteração é verificado **antes** de cancelar; cancelamento + criação na mesma transação | Em falha prévia, a original fica intacta | `OrdemMercadoEAlteracaoTest.alteracaoImpossivelDeixaAOrdemOriginalIntacta` |
| **Alteração enquanto a corretora executa a ordem** | Execução da ordem antiga misturada aos parâmetros novos | Alteração = cancelar e **substituir** (nova ordem, novo id); só ordens sem execução; execuções em trânsito aplicadas antes | Nenhuma execução cai na ordem errada | `…naoAlteraOrdemExecutadaParcialmente…`, `…corretoraForaDoAr…` |
| **Segredo MFA vazado do banco** | Atacante gera códigos TOTP | AES-256-GCM com chave fora do banco; valor adulterado nunca autentica | Dump do banco isolado não basta | `CifradorSegredoTest` |
| **Cliente HTTP malicioso ou instável (API)** | Corpo gigante, JSON ambíguo, campos extras, injeção | Limite de 16 KB, JSON plano com allowlist de campos, Content-Type obrigatório, erros sem detalhes | Entrada recusada com 4xx; nada é criado | `ServidorWebTest`, `JsonTest` |

### 2.2 Ciclos de realimentação identificados

| Ciclo | Tipo | Como é tratado |
|---|---|---|
| Reserva → saldo disponível → novas ordens | **Equilíbrio** (estabiliza o gasto) | A reserva reduz o disponível *imediatamente*, impedindo que ordens concorrentes gastem o mesmo valor. |
| Ordem cancelada/rejeitada → libera limite diário | **Equilíbrio** | O limite diário só soma ordens que consomem risco (VALIDADA, ENVIADA, PARCIAL, EXECUTADA). |
| Falhas da corretora → mais retentativas → mais carga na corretora | **Reforço perigoso** ("tempestade de retentativas") | Recuo exponencial **e** *circuit breaker*: ao abrir, para de chamar e dá tempo de recuperação. |
| Falhas de login → bloqueio → menos tentativas | **Equilíbrio** | Bloqueio de 15 min + limite de tentativas/min. |
| Cotação antiga → ordens rejeitadas → investidor tenta de novo → mais rejeições | Efeito colateral aceito | É preferível rejeitar a negociar a preço velho (decisão de risco: RN14). |

### 2.3 Efeitos de segunda ordem e trade-offs assumidos (honestidade técnica)

| Decisão | Ganho | Custo / limite assumido |
|---|---|---|
| Trilha de auditoria **global** com hash encadeado | Integridade verificável de ponta a ponta | Serializa as gravações de auditoria; em alto volume, particionar a cadeia por entidade. |
| Validação de mercado **fora** da transação | Não segura conexão de banco esperando sistema externo | A cotação pode mudar entre validar e gravar (mitigado pela faixa de preço e pelo preço de execução vir da corretora). |
| Mensagem de login **única** | Impede enumeração de usuários | Menos informativa para quem erra o e-mail. |
| Entrega "pelo menos uma vez" + consumidor idempotente | Nunca perde execução | Exige idempotência em todo consumidor (custo de projeto). |
| PBKDF2 com 600.000 iterações | Resiste a força bruta offline | Login levou 224 ms no ensaio registrado — é o custo de desempenho que aceitamos para esse nível de proteção. |
| Atomicidade real só no JDBC | Regras testáveis sem banco | O adaptador em memória **não desfaz** alterações em caso de erro (documentado). |

### 2.4 Pontos de alavanca (onde pequenas escolhas protegem o todo)

1. **A tabela de transições de `StatusOrdem`:** uma única definição impede, em todos os caminhos de código, que uma ordem encerrada volte a operar.
2. **A reserva de saldo/posição:** transforma uma verificação que seria "otimista" em um recurso consumido, eliminando o gasto duplo.
3. **A chave de idempotência (cliente) + `idExecucao` (corretora):** são as duas pontas que tornam repetições inofensivas.
4. **O princípio "nenhuma chamada externa dentro de transação":** evita que a lentidão de um parceiro derrube o banco.

## 3. Veredito atual

| | Fase 1 | Agora |
|---|---|---|
| Intenção sistêmica | ✅ | ✅ |
| Análise de interdependências e falhas | 🔴 | ✅ tabela §2.1 com teste por linha |
| Ciclos de realimentação | 🔴 | ✅ §2.2 |
| Fronteira e atores | 🔴 | ✅ 7 atores, 25 casos de uso |
| Trade-offs explícitos | 🔴 | ✅ §2.3 |
| Verificável | 🔴 | ✅ 169 testes (incluindo PostgreSQL) + roteiro de demonstração + teste de interface em navegador |

**Já considerávamos o sistema como um conjunto de partes interdependentes; nesta versão, transformamos essa visão em análise e evidências.** Cada linha da tabela §2.1 aponta para o teste correspondente. Para a defesa, organizamos a apresentação de modo que nós dois consigamos explicar, de ponta a ponta, pelo menos três cenários (da falha ao teste).
