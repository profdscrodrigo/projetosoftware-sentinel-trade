# Matriz de Rastreabilidade — SentinelTrade

Relaciona requisitos, casos de uso, diagramas e controles técnicos.

| Requisito | Descrição resumida | Caso de uso associado | Diagrama | Controle técnico |
|---|---|---|---|---|
| RF-01 | Autenticação de credenciais | UC-01 Autenticar com MFA | Casos de uso | Hash de senha; bloqueio após tentativas |
| RF-02 | Segundo fator (MFA simulado) | UC-01 Autenticar com MFA | Casos de uso | Código de segundo fator controlado |
| RF-03 | Consultar saldo | UC-03 Consultar saldo | Casos de uso | Filtro por investidor autenticado |
| RF-04 | Consultar carteira | UC-04 Consultar carteira | Casos de uso | Filtro por investidor autenticado |
| RF-05 | Consultar cotações | UC-02 Consultar cotações | Casos de uso | Leitura do último valor recebido |
| RF-06 | Receber cotações | UC-16 Receber cotações do provedor | Casos de uso | Registro de horário; tratamento de falha do provedor |
| RF-07 | Enviar ordem de compra | UC-05 Enviar ordem de compra | Atividade, sequência e estados | Validação de saldo e limite; chave de idempotência |
| RF-08 | Enviar ordem de venda | UC-06 Enviar ordem de venda | Atividade, sequência e estados | Validação de posição disponível |
| RF-09 | Validar ordem | UC-10 Validar ordem | Atividade e sequência | Serviço de risco e regras de negócio |
| RF-10 | Transmitir ordem | UC-11 Transmitir ordem à Bolsa/Corretora | Sequência, estados e implantação | Tratamento de falha e retentativa controlada |
| RF-11 | Cancelar ordem | UC-07 Cancelar ordem | Atividade, sequência e estados | Verificação de estado antes do cancelamento |
| RF-12 | Acompanhar ordem | UC-08 Consultar e acompanhar ordem | Estados | Histórico de mudanças de estado |
| RF-13 | Consultar histórico | UC-09 Consultar histórico | Casos de uso | Filtro por investidor e período |
| RF-14 | Gerenciar ativos | UC-12 Gerenciar ativos | Casos de uso | Controle de acesso por perfil; auditoria |
| RF-15 | Gerenciar limites de risco | UC-13 Gerenciar limites de risco | Casos de uso | Controle de acesso por perfil; auditoria |
| RF-16 | Registrar auditoria | UC-14 Registrar auditoria | Classes, componentes e implantação | Log persistente e trilha de auditoria |
| RF-17 | Notificar investidor | UC-15 Enviar notificação | Sequência | Notificação em execução, rejeição, cancelamento e falha |
| RF-18 | Prevenir duplicidade | UC-10 Validar ordem | Atividade e sequência | Chave de idempotência |
| RF-19 | Controlar acesso por perfil | UC-01, UC-12, UC-13 | Casos de uso, componentes | Autorização por perfil em cada operação |
| RNF-01 | Segurança | UC-01 Autenticar com MFA | Casos de uso, componentes e implantação | MFA, hash, controle de acesso |
| RNF-02 | Integridade | UC-05, UC-06 | Classes | Validação de campos obrigatórios |
| RNF-03 | Auditabilidade | UC-14 Registrar auditoria | Classes e implantação | Log somente de inserção |
| RNF-04 | Disponibilidade | UC-11 Transmitir ordem | Implantação | Estados de falha sem apagar a ordem |
| RNF-05 | Desempenho | UC-03, UC-04, UC-02 | Implantação | Consultas indexadas |
| RNF-06 | Confiabilidade | UC-05, UC-06, UC-11 | Sequência e estados | Chave de idempotência |
| RNF-07 | Manutenibilidade | Todos | Componentes | Estrutura de pastas e commits padronizados |
| RNF-08 | Privacidade | UC-03, UC-04, UC-08, UC-09 | Componentes | Filtro por investidor autenticado |
| RNF-09 | Escalabilidade | Todos | Implantação | Componentes separados |
| RNF-10 | Tolerância a falhas e recuperação | UC-11, UC-07 | Implantação | Reprocessamento de ordens em falha |
| RNF-11 | Distribuição e comunicação | UC-02, UC-11, UC-16 | Implantação | Integrações com contratos definidos |
