# Matriz de Rastreabilidade

| Requisito | Caso de uso associado | Diagrama futuro | Controle técnico |
|---|---|---|---|
| RF-08 — Envio de Ordem | UC-05 Enviar Ordem de Compra; UC-10 Enviar Ordem de Venda | Atividade, sequência e estados | Validação de saldo, posição, limite e idempotência |
| RF-09 — Cancelamento de Ordem | UC-06 Cancelar Ordem | Atividade, sequência e estados | Verificação do estado antes do cancelamento |
| RF-11 — Validação de Ordem | UC-05, UC-10 | Atividade e sequência | Serviço de risco e regras de negócio |
| RF-15 — Auditoria | UC-05, UC-06, UC-10 | Classes, componentes e implantação | Log persistente e trilha de auditoria |
| RF-17 — Prevenção de Duplicidade | UC-05, UC-10 | Atividade e sequência | Chave de idempotência |
| RNF-01 — Segurança | UC-01 Autenticar Usuário | Casos de uso, componentes e implantação | MFA, hash, controle de acesso |
| RNF-10 — Confiabilidade | UC-05, UC-10 | Sequência, estados e implantação | Chave de idempotência e tratamento de falha |
