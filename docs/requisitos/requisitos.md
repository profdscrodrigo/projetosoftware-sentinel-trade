# Requisitos do SentinelTrade

## 1. Visão do sistema

O SentinelTrade é uma plataforma **simulada** de negociação de ativos financeiros. Ela permite que investidores autenticados acompanhem sua carteira, consultem cotações e realizem operações de compra e venda, respeitando saldo, posição disponível, limites de risco e regras de segurança.

O sistema garante que as ordens sejam rastreáveis, que não sejam executadas de forma duplicada e que todos os eventos relevantes sejam registrados em auditoria.

> **Aviso:** o SentinelTrade opera exclusivamente com dados e operações simuladas. Não há movimentação de dinheiro real nem envio de ordens a bolsas reais.

## 2. Atores

| Ator | Responsabilidade |
|---|---|
| Investidor | Consulta cotações, carteira, saldo e histórico; envia ou cancela ordens. |
| Administrador | Gerencia ativos e parâmetros operacionais do sistema. |
| Analista de Risco | Consulta e ajusta limites de risco; acompanha ordens bloqueadas ou rejeitadas. |
| Corretora/Bolsa Simulada | Recebe ordens válidas e retorna informações sobre execução, rejeição ou falha. |
| Provedor de Cotações | Fornece valores atualizados ou simulados dos ativos. |
| Serviço de Notificação | Informa o investidor sobre eventos relevantes. |
| Serviço de Auditoria | Mantém o registro de ações, decisões e alterações de estado. |

## 3. Requisitos funcionais

| ID | Requisito | Descrição | Critério de aceitação |
|---|---|---|---|
| RF-01 | Autenticação de credenciais | O sistema deve validar usuário e senha. | Usuário válido avança para o segundo fator; credencial inválida é bloqueada e a tentativa é auditada. |
| RF-02 | Segundo fator (MFA simulado) | O sistema deve exigir um código adicional após a senha. | O acesso só é liberado após código válido; após 3 códigos inválidos, a conta fica bloqueada temporariamente. |
| RF-03 | Consultar saldo | O sistema deve apresentar o saldo do investidor. | Exibe saldo disponível, valor reservado por ordens pendentes e limite financeiro. |
| RF-04 | Consultar carteira | O sistema deve apresentar as posições por ativo. | Exibe ativo, quantidade, preço médio e valor estimado (quantidade × cotação atual). |
| RF-05 | Consultar cotações | O sistema deve apresentar as cotações dos ativos disponíveis. | Exibe preço, horário da cotação e variação simulada. |
| RF-06 | Receber cotações | O sistema deve receber cotações do Provedor de Cotações. | Cotação recebida é gravada com horário; se o provedor falhar, o último valor é mantido e marcado como desatualizado. |
| RF-07 | Enviar ordem de compra | O investidor deve enviar ordem de compra. | Ordem é aceita somente com saldo e limite disponíveis; caso contrário, é rejeitada com motivo informado. |
| RF-08 | Enviar ordem de venda | O investidor deve enviar ordem de venda. | Ordem é aceita somente com quantidade disponível do ativo na carteira. |
| RF-09 | Validar ordem | O sistema deve validar a ordem antes da transmissão. | Dados, saldo ou posição, limite de risco, situação do mercado e duplicidade são verificados antes do envio. |
| RF-10 | Transmitir ordem | O sistema deve transmitir ordens válidas à Bolsa/Corretora Simulada. | Ordem válida é enviada com chave de idempotência; o retorno atualiza o estado da ordem. |
| RF-11 | Cancelar ordem | O investidor deve solicitar o cancelamento de ordem. | Cancelamento é aceito apenas para ordens não executadas; ordem executada é recusada. |
| RF-12 | Acompanhar ordem | O sistema deve mostrar o ciclo de vida de cada ordem. | Status atualizado e histórico de mudanças de estado disponíveis para o investidor. |
| RF-13 | Consultar histórico | O sistema deve listar as operações e eventos da conta. | Lista ordens e eventos por período, somente do investidor autenticado. |
| RF-14 | Gerenciar ativos | O Administrador deve cadastrar, ativar ou desativar ativos. | Alterações ficam registradas na auditoria; ativo desativado não aceita novas ordens. |
| RF-15 | Gerenciar limites de risco | O Analista de Risco deve ajustar os limites dos investidores. | Alteração é registrada na auditoria; ordem acima do limite vigente é rejeitada. |
| RF-16 | Registrar auditoria | O sistema deve registrar eventos relevantes. | Cada criação, validação, rejeição, cancelamento, execução e alteração de ativo ou limite gera registro com usuário, data e evento. |
| RF-17 | Notificar investidor | O sistema deve notificar o investidor sobre eventos críticos. | Notificação é gerada em execução, rejeição, cancelamento ou falha. |
| RF-18 | Prevenir duplicidade | O sistema deve impedir o processamento duplicado de ordens. | Reenvio com a mesma chave de idempotência não cria nova ordem e retorna a ordem original. |
| RF-19 | Controlar acesso por perfil | O sistema deve restringir funções conforme o perfil do usuário. | Investidor acessa somente os próprios dados; funções administrativas são restritas a Administrador e Analista de Risco. |

## 4. Requisitos não funcionais

| ID | Categoria | Descrição | Aplicação prática |
|---|---|---|---|
| RNF-01 | Segurança | O sistema deve proteger os dados contra acessos não autorizados. | Senhas armazenadas com hash; MFA obrigatório; controle de acesso por perfil; validação de todas as entradas. |
| RNF-02 | Integridade | O sistema deve preservar a consistência de contas, carteiras e ordens. | Toda ordem possui investidor, ativo, tipo, quantidade e preço válidos antes de ser gravada. |
| RNF-03 | Auditabilidade | Eventos críticos devem gerar registros rastreáveis e protegidos contra alteração. | Registros de auditoria são apenas de inserção; alterações indevidas são detectáveis. |
| RNF-04 | Disponibilidade | O sistema deve lidar com indisponibilidade de componentes externos. | Falha da Bolsa/Corretora ou do provedor de cotações não apaga ordens nem altera posições. |
| RNF-05 | Desempenho | Consultas básicas devem responder em tempo adequado ao ambiente simulado. | Consultas de carteira e cotações respondem em até 2 segundos no ambiente de teste. |
| RNF-06 | Confiabilidade | O sistema deve evitar duplicidades e inconsistências no processamento. | Chave de idempotência em cada ordem; reenvios por falha de comunicação não geram ordens duplicadas. |
| RNF-07 | Manutenibilidade | Código, documentação, banco e diagramas devem estar organizados e versionados. | Estrutura de pastas definida no README; alterações feitas por commits com mensagens padronizadas. |
| RNF-08 | Privacidade | Os dados do investidor devem ser acessíveis somente a usuários autorizados. | Consultas filtram sempre pelo investidor autenticado; dados pessoais não são gravados no repositório. |
| RNF-09 | Escalabilidade | O sistema deve permitir expansão conforme o crescimento de usuários e operações. | Componentes separados (interface, regras, persistência e integrações) podem ser escalados de forma independente. |
| RNF-10 | Tolerância a falhas e recuperação | O sistema deve lidar com falhas de componentes e se recuperar delas. | Ordens em estado de falha podem ser reprocessadas sem perda de dados. |
| RNF-11 | Distribuição e comunicação | A solução deve ser organizada como plataforma distribuída, com comunicação protegida entre componentes. | Integrações com Provedor de Cotações e Bolsa/Corretora usam canais protegidos e contratos definidos. |
