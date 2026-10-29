##         REQUISITOS FUNCIONAIS

| ID | Requisito | Descrição |
|---|---|---|
| RF01 | Autenticação com MFA | O sistema deve autenticar usuários com múltiplos fatores. |
| RF02 | Gestão de dados cadastrais | Manter dados de investidores, contas, carteiras, ativos e limites financeiros. |
| RF03 | Cotações em tempo quase real | Receber e exibir cotações de um provedor externo. |
| RF04 | Enviar ordem de compra/venda | Permitir que o investidor envie ordens de compra e venda. |
| RF05 | Cancelar ordem | Permitir o cancelamento de ordens ainda não executadas. |
| RF06 | Consultar ordens | Permitir consultar ordens e o histórico de operações. |
| RF07 | Validar ordem | Validar saldo, posição em carteira, limite de risco e situação do mercado antes de transmitir. |
| RF08 | Integração com Bolsa/Corretora simulada | Transmitir ordens e receber retornos da bolsa simulada. |
| RF09 | Ciclo de vida da ordem | Acompanhar os estados da ordem (recebida, validada, enviada, executada, rejeitada, cancelada, falha). |
| RF10 | Log de auditoria imutável | Registrar todas as operações relevantes em logs que não possam ser alterados. |
| RF11 | Consultar logs de auditoria | Permitir ao administrador consultar os logs. |
| RF12 | Notificações | Notificar o investidor sobre execução, rejeição, cancelamento ou falha. |
| RF13 | Carteira de ativos | Manter e atualizar posições e saldo após cada execução. |


##       REQUISITOS NÃO FUNCIONAIS 

| ID | Categoria | Requisito |
|---|---|---|
| RNF01 | Segurança | MFA obrigatório, criptografia em trânsito (TLS) e em repouso, controle de acesso por perfil (investidor, administrador). |
| RNF02 | Integridade | Logs de auditoria imutáveis (append-only, com hash/assinatura). |
| RNF03 | Auditabilidade / Rastreabilidade | Toda ordem deve ser rastreável de ponta a ponta, com identificador único. |
| RNF04 | Disponibilidade | Alta disponibilidade, com indisponibilidade controlada e planejada. |
| RNF05 | Tolerância a falhas | Redundância e mecanismos de recuperação (retry, circuit breaker, failover). |
| RNF06 | Idempotência | Prevenção de duplicidade de ordens (chave de idempotência). |
| RNF07 | Desempenho | Baixa latência para cotações (quase real) e para validação e envio de ordens. |
| RNF08 | Escalabilidade | Arquitetura distribuída que escale horizontalmente em picos de acesso. |
| RNF09 | Consistência | Consistência forte nas operações financeiras (saldo, posição, limites). |
| RNF10 | Conformidade | Atender requisitos regulatórios e de proteção de dados (ex.: LGPD). |
| RNF11 | Confiabilidade | Nenhuma ordem pode ser perdida; recuperação do estado após falhas. |
| RNF12 | Usabilidade | Interface clara para acompanhar cotações, carteira e status das ordens. |
| RNF13 | Manutenibilidade | Componentes desacoplados, com baixo acoplamento entre serviços e integrações. |
