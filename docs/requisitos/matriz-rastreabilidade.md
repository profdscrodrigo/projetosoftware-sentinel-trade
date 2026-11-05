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

## Requisito → código → teste

| Requisito | Código | Teste |
|---|---|---|
| RF-01 Autenticação | `servicos.py`, `api.py` | `test_sistema.py` (login, MFA, bloqueio), `test_sem_login_nao_entra` |
| RF-08 Envio de Ordem | `servicos.py`, `dominio.py` | `test_compra_executada`, `test_venda_executada` |
| RF-09 Cancelamento | `ServicoOrdens.cancelar` | `test_cancelar_ordem_em_falha`, `test_ordem_executada_nao_cancela` |
| RF-11 Validação | `ServicoOrdens._validar`, `dominio.py` (Pydantic) | `test_dominio.py`, `test_dados_invalidos_retornam_422`, `test_limite_de_risco`, `test_mercado_fechado` |
| RF-12 Transmissão | `ServicoOrdens._transmitir`, `integracoes.py` | `test_bolsa_fora_do_ar`, `test_bolsa_rejeita_preco_muito_diferente` |
| RF-13 Acompanhamento | `dominio.py` (estados) | `test_dominio.py` |
| RF-15 Auditoria | `servicos.py` + trigger | `test_historico_e_auditoria`, `test_auditoria_nao_pode_ser_apagada` |
| RF-16 Notificações | `servicos.py` (`Notificacoes`) | `test_notificacao` |
| RF-17 Duplicidade | `ServicoOrdens.enviar` | `test_mesma_chave_nao_duplica` |
| RNF-01 Segurança | `servicos.py`, Pydantic, SQLAlchemy | `test_hash_da_senha`, `test_investidor_nao_acessa_admin`, bandit e pip-audit |
| RNF-10 Confiabilidade | chave de idempotência | `test_mesma_chave_nao_duplica` |
