# 01 — Requisitos do SentinelTrade

Projeto: plataforma de trade de alta criticidade da corretora fictícia **Orion Capital** (ativos, contas e cotações simulados).
Padrão adotado: o mesmo do gabarito "Falcão Sombrio" (ID, nome, descrição), com metas mensuráveis nos requisitos não funcionais.

> Os valores numéricos (tempos, percentuais, prazos) são **metas propostas pela equipe** e podem ser ajustados após discussão com o professor.

---

## 1. Requisitos funcionais (RF)

| ID | Requisito | Descrição |
|---|---|---|
| RF-01 | Autenticação com MFA | O sistema deve autenticar todos os usuários com credenciais e um segundo fator de autenticação, bloqueando a conta temporariamente após tentativas inválidas consecutivas. |
| RF-02 | Autorização por perfil | O sistema deve restringir cada funcionalidade aos perfis autorizados: Investidor, Operador de Risco, Auditor e Administrador. |
| RF-03 | Cadastro e gestão de investidores | O sistema deve permitir cadastrar, consultar, alterar e desativar investidores, com suas contas, carteiras e limites financeiros. |
| RF-04 | Cadastro de ativos | O sistema deve manter o catálogo de ativos negociáveis (ações, ETFs e FIIs) com código de negociação, lote padrão e situação de negociação. |
| RF-05 | Consulta de cotações | O sistema deve exibir ao investidor preço, variação e volume dos ativos, com indicação do horário da última atualização. |
| RF-06 | Recepção de cotações externas | O sistema deve receber cotações em tempo quase real de um provedor externo e descartar cotações inválidas ou desatualizadas. |
| RF-07 | Consulta de carteira | O sistema deve exibir ao investidor saldo, posição por ativo, preço médio e resultado financeiro. |
| RF-08 | Envio de ordens | O investidor deve poder enviar ordens de compra e venda, a mercado ou limitadas, informando ativo, quantidade e, quando aplicável, preço. |
| RF-09 | Validação pré-negociação | Antes de transmitir uma ordem, o sistema deve validar saldo, quantidade disponível em carteira, preço, lote, limite de risco e situação do mercado. |
| RF-10 | Cancelamento de ordem | O investidor deve poder cancelar ordens que ainda não foram totalmente executadas. |
| RF-11 | Alteração de ordem | O investidor deve poder alterar preço ou quantidade de ordens ainda não totalmente executadas, repetindo a validação pré-negociação. |
| RF-12 | Integração com Bolsa/Corretora simulada | O sistema deve transmitir ordens validadas à Bolsa simulada e receber confirmações de execução total, execução parcial, rejeição e cancelamento. |
| RF-13 | Acompanhamento do status da ordem | O sistema deve controlar e exibir o estado de cada ordem: Criada, Validada, Enviada, Parcialmente Executada, Executada, Rejeitada, Cancelada ou Falha. |
| RF-14 | Registro de operações e posição | O sistema deve registrar cada execução e recalcular saldo e posição do investidor de forma consistente. |
| RF-15 | Notificações | O sistema deve notificar o investidor sobre execução, rejeição, cancelamento e falha de ordens. |
| RF-16 | Consulta de histórico | O investidor deve poder consultar o histórico de suas ordens e operações, filtrando por período, ativo e status. |
| RF-17 | Auditoria | O sistema deve registrar em log de auditoria imutável todo acesso, comando e mudança de estado relevante, com usuário, data/hora e origem. |
| RF-18 | Gestão de limites de risco | O Operador de Risco deve poder definir e ajustar limites financeiros por investidor, por ordem e por dia. |
| RF-19 | Indisponibilidade controlada | O sistema deve permitir acionar e encerrar um modo de indisponibilidade segura, no qual novas ordens são recusadas com mensagem clara, enquanto consultas continuam disponíveis. |
| RF-20 | Prevenção de duplicidade | O sistema deve impedir que uma mesma ordem seja processada mais de uma vez, mesmo em caso de reenvio, repetição de clique ou nova tentativa automática. |
| RF-21 | Reprocessamento de pendências | O sistema deve reprocessar automaticamente ordens em estado Falha ou sem confirmação da Bolsa, respeitando o número máximo de tentativas. |

---

## 2. Requisitos não funcionais (RNF)

### 2.1 Segurança

| ID | Requisito | Descrição |
|---|---|---|
| RNF-01 | Senhas com hash | Senhas devem ser armazenadas apenas como hash com sal (bcrypt, Argon2 ou PBKDF2); nunca em texto claro ou criptografia reversível. |
| RNF-02 | MFA | O segundo fator deve ser obrigatório para todos os perfis, com código de uso único válido por no máximo 60 segundos. |
| RNF-03 | Controle de acesso por perfil | Toda requisição deve ser autorizada no servidor conforme o perfil, seguindo o princípio do menor privilégio. |
| RNF-04 | Encapsulamento | Atributos sensíveis das classes (saldo, senha, limites, chaves) devem ser privados e acessados apenas por métodos que aplicam as regras de negócio. |
| RNF-05 | Validação de entradas | Toda entrada (formulário, API, mensagem de fila, resposta de sistema externo) deve ser validada quanto a tipo, tamanho, formato e faixa de valores. |
| RNF-06 | Tratamento seguro de exceções | Erros devem ser tratados sem expor detalhes internos ao usuário (stack trace, SQL, caminhos); o detalhe técnico vai apenas para o log protegido. |
| RNF-07 | Prevenção de injeção | O acesso a dados deve usar consultas parametrizadas ou ORM; é proibido concatenar entradas em comandos SQL, de sistema ou de consulta. |
| RNF-08 | Proteção contra reenvio | Cada ordem deve carregar chave de idempotência única e o sistema deve rejeitar repetições dentro da janela de validade da chave. |
| RNF-09 | Sem segredos no repositório | Senhas, tokens, chaves de API e certificados devem ficar fora do Git (variáveis de ambiente), com `.env.example` apenas com valores fictícios. |
| RNF-10 | Criptografia | Dados em trânsito devem usar TLS 1.2 ou superior e dados sensíveis em repouso devem ser criptografados. |

### 2.2 Resiliência

| ID | Requisito | Descrição |
|---|---|---|
| RNF-11 | Time-out de integração | Toda chamada ao provedor de cotações e à Bolsa deve ter time-out máximo de 2 segundos. |
| RNF-12 | Retentativa controlada | Após falha transitória, o sistema deve repetir a chamada no máximo 3 vezes, com espera crescente entre tentativas, e só para operações idempotentes. |
| RNF-13 | Idempotência | Reenviar a mesma operação com a mesma chave deve produzir o mesmo resultado, sem efeito colateral adicional. |
| RNF-14 | Fila de mensagens | O envio de ordens à Bolsa e o envio de notificações devem ocorrer de forma assíncrona por fila de mensagens durável, com fila de mensagens mortas para falhas definitivas. |
| RNF-15 | Indisponibilidade segura | Diante de falha grave ou decisão do Operador de Risco, o sistema deve bloquear novas ordens (fail-safe) em vez de aceitá-las sem garantia de validação. |
| RNF-16 | Recuperação de falhas | Após queda de um componente, o sistema deve recuperar o estado das ordens em até 30 segundos, sem perda de ordens já aceitas. |
| RNF-17 | Consistência de dados | Atualizações de saldo, posição e status da ordem devem ser transacionais (tudo ou nada), sem saldo negativo nem ordem em dois estados simultâneos. |

### 2.3 Qualidade

| ID | Requisito | Descrição |
|---|---|---|
| RNF-18 | Auditabilidade | Todo evento de auditoria deve registrar quem, o quê, quando e resultado, em formato append-only e com verificação de integridade (encadeamento por hash). |
| RNF-19 | Confidencialidade | Dados pessoais e financeiros só podem ser vistos pelo próprio investidor e pelos perfis autorizados; logs não podem conter senhas, tokens ou dados completos de conta. |
| RNF-20 | Integridade | Nenhum dado de ordem, execução ou auditoria pode ser alterado sem registro; alterações indevidas devem ser detectáveis. |
| RNF-21 | Disponibilidade | O sistema deve ter disponibilidade de 99,9% no horário de pregão, com componentes críticos replicados. |
| RNF-22 | Desempenho | A validação e o envio de uma ordem à fila devem levar no máximo 500 ms no percentil 95, em condições normais. |
| RNF-23 | Rastreabilidade | Cada ordem deve ter um identificador de correlação que permita seguir seu caminho por todos os componentes e logs. |
| RNF-24 | Manutenibilidade | O código deve ser modular e com testes automatizados cobrindo ao menos 70% das regras de negócio críticas. |
| RNF-25 | Retenção de logs | Logs de auditoria devem ser guardados por no mínimo 5 anos, protegidos contra alteração e exclusão. |

---

## 3. Regras de negócio (RN)

| ID | Regra |
|---|---|
| RN01 | Uma ordem de compra só pode ser aceita se o saldo disponível cobrir o valor total estimado da ordem. |
| RN02 | Uma ordem de venda só pode ser aceita se a quantidade disponível em carteira cobrir a quantidade vendida (não há venda a descoberto). |
| RN03 | O valor de cada ordem e o total negociado no dia não podem ultrapassar os limites de risco do investidor. |
| RN04 | Ordens só podem ser enviadas com o mercado aberto, conforme horário de pregão simulado e situação do ativo (negociável ou suspenso). |
| RN05 | A quantidade de uma ordem deve ser múltiplo do lote padrão do ativo. |
| RN06 | O preço de uma ordem limitada deve ser maior que zero e ficar dentro de uma faixa de variação em torno da última cotação, definida pela corretora. |
| RN07 | Ordens a mercado só podem ser aceitas se a última cotação do ativo tiver no máximo 5 segundos de idade. |
| RN08 | Duas ordens com a mesma chave de idempotência são a mesma ordem e só podem ser processadas uma vez. |
| RN09 | Só podem ser alteradas ou canceladas ordens nos estados Validada, Enviada ou Parcialmente Executada. |
| RN10 | A senha deve ter no mínimo 12 caracteres, combinando letras e números, e nunca pode ser exibida ou registrada em log. |
| RN11 | Após 5 tentativas consecutivas de autenticação inválidas, a conta é bloqueada por 15 minutos. |
| RN12 | Registros de auditoria não podem ser alterados nem excluídos por nenhum perfil, inclusive o Administrador. |
| RN13 | Um investidor só pode consultar e operar sobre suas próprias contas e carteiras. |

---

## 4. Matriz de rastreabilidade (requisito × caso de uso)

| Requisito | Casos de uso que o atendem |
|---|---|
| RF-01 | CSU01 |
| RF-02 | CSU11 |
| RF-03 | CSU09 |
| RF-04 | CSU10 |
| RF-05 | CSU02 |
| RF-06 | CSU15 |
| RF-07 | CSU03 |
| RF-08, RF-09, RF-20 | CSU04 |
| RF-10 | CSU06 |
| RF-11 | CSU05 |
| RF-12, RF-14 | CSU04, CSU16 |
| RF-13 | CSU07, CSU16 |
| RF-15 | CSU17 |
| RF-16 | CSU08 |
| RF-17 | CSU14 (consulta) e pós-condição de todos os demais |
| RF-18 | CSU12 |
| RF-19 | CSU13 |
| RF-21 | CSU18 |
