## Requisitos Funcionais

| ID | Requisito | Descrição |
|---|---|---|
| RF-01 | Autenticação dos usuários | O sistema deve permitir a autenticação dos usuários utilizando MFA. |
| RF-02 | API | O sistema deve receber cotações em tempo real por meio de uma API externa. |
| RF-03 | Gestão de contas | O sistema deve manter os dados das contas, carteiras, investidores e ativos. |
| RF-04 | Ordens | O sistema deve permitir o envio, cancelamento e consulta de ordens de compra e venda. |
| RF-05 | Validação de operações | O sistema deve validar o saldo e os limites antes de processar uma operação. |
| RF-06 | Histórico, notificações e auditoria | O sistema deve permitir a consulta do histórico de operações, o acompanhamento de ordens, o envio de notificações e o registro de eventos para auditoria. |

## Requisitos Não Funcionais

| ID | Requisito | Descrição |
|---|---|---|
| RNF-01 | Segurança | O sistema deve proteger as senhas e os dados dos usuários. |
| RNF-02 | Resiliência | O sistema deve realizar novas tentativas em caso de falha na comunicação com as APIs. |
| RNF-03 | Consistência | O sistema deve evitar o processamento duplicado de ordens e manter os dados atualizados. |
| RNF-04 | Disponibilidade | O sistema deve continuar funcionando de forma consistente mesmo quando ocorrerem falhas em algum serviço. |
| RNF-05 | Auditoria | O sistema deve registrar as operações realizadas pelos usuários para permitir o acompanhamento e a verificação das ações. |
