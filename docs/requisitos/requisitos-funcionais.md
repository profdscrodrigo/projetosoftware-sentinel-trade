# Requisitos Funcionais

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
