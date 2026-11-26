# Requisitos do SentinelTrade

> **Aviso:** o SentinelTrade opera exclusivamente com dados e operações simuladas.

## 1. Visão do sistema

O SentinelTrade é uma plataforma simulada de negociação de ativos financeiros. Ela permite que investidores autenticados acompanhem sua carteira, consultem cotações e realizem operações de compra e venda, respeitando saldo, posição disponível, limites de risco e regras de segurança.

O sistema deverá garantir que as ordens sejam rastreáveis, que não sejam executadas de forma duplicada e que todos os eventos relevantes sejam registrados em auditoria.

## 2. Atores

| Ator | Responsabilidade |
|---|---|
| Investidor | Consulta cotações, carteira, saldo, histórico e envia ou cancela ordens. |
| Administrador | Gerencia usuários, ativos, limites e parâmetros operacionais. |
| Analista de Risco | Consulta e ajusta limites de risco, acompanha ordens bloqueadas ou rejeitadas. |
| Corretora/Bolsa Simulada | Recebe ordens válidas e retorna informações sobre execução, rejeição ou falha. |
| Provedor de Cotações | Fornece valores atualizados ou simulados dos ativos. |
| Serviço de Notificação | Informa o investidor sobre eventos relevantes. |
| Serviço de Auditoria | Mantém o registro de ações, decisões e alterações de estado. |

## 3. Requisitos funcionais

Os requisitos funcionais representam as funcionalidades que o SentinelTrade deve realizar.

| ID | Requisito | Descrição | Critério de aceitação |
|---|---|---|---|
| RF-01 | Autenticação | O sistema deve permitir a autenticação dos usuários utilizando MFA. | Usuário válido acessa o sistema; usuário inválido é bloqueado. |
| RF-02 | Gerenciamento de Contas | O sistema deve permitir manter os dados das contas dos investidores. | Dados da conta são gravados e acessíveis somente ao investidor e a usuários autorizados. |
| RF-03 | Gerenciamento de Carteiras | O sistema deve permitir manter os dados das carteiras dos investidores. | Carteira apresenta ativos, quantidades, preço médio, valor estimado e saldo disponível. |
| RF-04 | Gerenciamento de Ativos | O sistema deve permitir manter os dados dos ativos disponíveis para negociação. | Ativo cadastrado ou desativado; ativo desativado não aceita novas ordens; alteração é auditada. |
| RF-05 | Gerenciamento de Limites | O sistema deve permitir manter os limites financeiros dos investidores. | Limite alterado é registrado na auditoria e aplicado na validação das ordens. |
| RF-06 | Consulta de Cotações | O sistema deve permitir consultar as cotações dos ativos. | Sistema apresenta preço, horário da cotação e variação simulada. |
| RF-07 | Recebimento de Cotações | O sistema deve receber cotações de um provedor externo. | Cotação é gravada com horário; se o provedor falhar, o último valor é mantido e marcado como desatualizado. |
| RF-08 | Envio de Ordem | O sistema deve permitir o envio de ordens de compra e venda. | Compra é aceita somente com saldo e limite disponíveis; venda somente com quantidade disponível do ativo. |
| RF-09 | Cancelamento de Ordem | O sistema deve permitir solicitar o cancelamento de ordens. | Cancelamento é aceito apenas para ordens não executadas; ordem executada é recusada. |
| RF-10 | Consulta de Ordem | O sistema deve permitir consultar as ordens realizadas. | Sistema apresenta dados e estado atual de cada ordem do investidor. |
| RF-11 | Validação de Ordem | O sistema deve validar saldo, posição em carteira, limite de risco e situação do mercado antes de transmitir uma ordem. | Dados inválidos, saldo insuficiente, limite excedido ou duplicidade impedem o envio. |
| RF-12 | Transmissão de Ordem | O sistema deve transmitir ordens válidas para a Bolsa/Corretora simulada. | Ordem válida é enviada com chave de idempotência; o retorno atualiza o estado da ordem. |
| RF-13 | Acompanhamento de Ordem | O sistema deve permitir acompanhar o ciclo de vida das ordens. | Investidor visualiza o status atualizado e o histórico de estados de cada ordem. |
| RF-14 | Histórico de Operações | O sistema deve permitir consultar o histórico das operações. | Lista operações e eventos do período, somente do investidor autenticado. |
| RF-15 | Auditoria | O sistema deve registrar eventos relevantes das operações para auditoria. | Criação, validação, rejeição, cancelamento e execução geram registro com usuário, data e evento. |
| RF-16 | Notificações | O sistema deve notificar o investidor sobre execução, rejeição, cancelamento ou falha. | Notificação é gerada em execução, rejeição, cancelamento ou falha. |
| RF-17 | Prevenção de Duplicidade | O sistema deve impedir o processamento duplicado de ordens. | Reenvio com a mesma chave de idempotência não cria nova ordem e retorna a ordem original. |

#### Acrescentados na implementação

| ID | Requisito | Descrição | Critério de aceitação |
|---|---|---|---|
| RF-18 | Alteração de Ordem | O investidor pode alterar quantidade e preço de uma ordem que ficou em Falha. | A ordem é validada de novo; a alteração fica na auditoria. |
| RF-19 | Confirmação de Ordem Alta | Ordens acima de R$ 10.000 pedem confirmação. | Sem confirmação, a ordem não é criada. |

#### Onde está no código

| Requisito | Onde (`src/sentineltrade/`) |
|---|---|
| RF-01 | `servicos.py` → `Autenticacao` |
| RF-02, RF-04, RF-05 | `servicos.py` → `ServicoAdmin` |
| RF-03, RF-06, RF-07 | `servicos.py` → `ServicoCarteira`; `integracoes.py` → `ProvedorCotacoes` |
| RF-08 a RF-11, RF-13, RF-14, RF-17 a RF-19 | `servicos.py` → `ServicoOrdens`; `dominio.py` → `Ordem` |
| RF-12 | `servicos.py` → `ServicoOrdens._transmitir`; `integracoes.py` → `BolsaSimulada` |
| RF-15, RF-16 | `servicos.py` → `Auditoria`, `Notificacoes` |
| Entrada de todos pela API | `api.py` |

## 4. Requisitos não funcionais

Os requisitos não funcionais representam características de qualidade e restrições da solução.

| ID | Categoria | Descrição | Como fizemos |
|---|---|---|---|
| RNF-01 | Segurança | O sistema deve proteger os dados contra acessos não autorizados. | Senha com hash **bcrypt**, login com código (MFA) e token assinado com validade de 30 minutos, permissões por perfil, validação das entradas com **Pydantic**, SQL com parâmetros pelo **SQLAlchemy** (contra injeção), código verificado com **bandit** e bibliotecas com **pip-audit**. |
| RNF-02 | Comunicação | As comunicações entre os componentes devem ser protegidas. | A senha do banco fica no `.env`, fora do GitHub. |
| RNF-03 | Disponibilidade | O sistema deve possuir mecanismos para lidar com indisponibilidade de componentes. | Se a bolsa cai, a ordem fica em FALHA e pode ser reenviada depois. Se o provedor de cotações cai, mostramos o último preço. |
| RNF-04 | Escalabilidade | O sistema deve permitir expansão para suportar o crescimento de usuários e operações. | Banco PostgreSQL e código separado em camadas. |
| RNF-05 | Tolerância a Falhas | O sistema deve possuir mecanismos para lidar com falhas de componentes. | Tentamos enviar à bolsa 3 vezes antes de marcar FALHA. |
| RNF-06 | Recuperação | O sistema deve possuir mecanismos de recuperação após falhas. | O administrador reenvia as ordens em FALHA. |
| RNF-07 | Integridade | O sistema deve preservar a consistência dos dados de contas, carteiras e ordens. | Saldo, carteira e ordem mudam juntos numa transação; o banco tem regras (CHECK, chaves estrangeiras). |
| RNF-08 | Rastreabilidade | As operações relevantes devem poder ser rastreadas. | Histórico de status de cada ordem e log do sistema com **logging** (sem registrar senhas nem códigos). |
| RNF-09 | Auditoria | Os registros de auditoria devem ser protegidos contra alterações indevidas. | Uma trigger no banco impede alterar ou apagar a auditoria. |
| RNF-10 | Confiabilidade | O sistema deve evitar duplicidades e inconsistências durante o processamento das ordens. | Cada envio tem uma chave única; a mesma chave não cria outra ordem. |
| RNF-11 | Distribuição | A solução deve ser organizada como uma plataforma distribuída, permitindo a comunicação entre seus componentes. | As partes são separadas (bolsa e cotações atrás de classes próprias). Nesta versão tudo roda num programa só. |

#### Acrescentados (lista do professor)

| ID | Categoria | Como fizemos |
|---|---|---|
| RNF-12 | Desempenho | Consultas simples; o sistema responde na hora no ambiente simulado. |
| RNF-13 | Manutenibilidade | Código em camadas, testes automáticos e tudo versionado no Git. |
| RNF-14 | Privacidade | O investidor só vê os próprios dados; os dados de exemplo são fictícios. |

#### Bibliotecas de segurança recomendadas

| Biblioteca | Situação |
|---|---|
| FastAPI | Usada na API |
| Pydantic | Usada na validação das entradas |
| SQLAlchemy | Usada no acesso ao banco |
| logging | Usada no log do sistema |
| bandit | Rodado: nenhum problema encontrado ([resultado](../evidencias/verificacoes-seguranca.txt)) |
| pip-audit | Rodado: nenhuma vulnerabilidade encontrada ([resultado](../evidencias/verificacoes-seguranca.txt)) |
| bleach | Não usado: o sistema não recebe HTML |

## 5. Outros documentos

- Regras de negócio: [regras-de-negocio.md](regras-de-negocio.md)
- Matriz de rastreabilidade: [matriz-rastreabilidade.md](matriz-rastreabilidade.md)
- Casos de uso: [../casos-de-uso/especificacao-casos-de-uso.md](../casos-de-uso/especificacao-casos-de-uso.md)
