# Regras de Negócio

As regras abaixo devem ser aplicadas durante as operações do SentinelTrade.

| ID | Regra | Requisitos relacionados | Casos de uso |
|---|---|---|---|
| RN-01 | Uma ordem de compra exige saldo disponível compatível com a quantidade e o preço informados. | RF-08, RF-11 | UC-05 |
| RN-02 | Uma ordem de venda exige posição disponível do ativo na carteira. | RF-08, RF-11 | UC-10 |
| RN-03 | A mesma ordem não pode ser processada nem executada duas vezes. | RF-17, RNF-10 | UC-05, UC-10 |
| RN-04 | Uma ordem executada, rejeitada ou cancelada não pode retornar ao estado inicial. | RF-13 | UC-05, UC-06, UC-10 |
| RN-05 | Toda mudança de estado da ordem deve gerar registro de auditoria. | RF-15, RNF-09 | UC-05, UC-06, UC-10 |
| RN-06 | O acesso administrativo é restrito a usuários com perfil autorizado. | RF-04, RF-05, RNF-01 | Casos administrativos |
| RN-07 | A indisponibilidade da Bolsa/Corretora simulada não pode apagar a ordem nem alterar indevidamente a carteira. | RF-12, RNF-03, RNF-05 | UC-05, UC-06, UC-10 |
| RN-08 | O investidor somente pode consultar as próprias informações. | RF-02, RF-03, RF-14, RNF-01 | UC-03, UC-04, UC-07, UC-08, UC-09 |
| RN-09 | Ordem executada não pode ser cancelada. | RF-09 | UC-06 |

### Acrescentadas na implementação

| ID | Regra | Casos de uso |
|---|---|---|
| RN-10 | A ordem não pode passar do limite por ordem do investidor. | UC-05, UC-10 |
| RN-11 | Com o mercado fechado ou o ativo suspenso, a ordem é rejeitada. | UC-05, UC-10 |
| RN-12 | Ordem acima de R$ 10.000 precisa de confirmação. | UC-05, UC-10 |
| RN-13 | 5 senhas erradas seguidas bloqueiam o usuário. | UC-01 |

## Estados da ordem

| Estado | Quando acontece | Pode ir para |
|---|---|---|
| CRIADA | A ordem foi registrada | VALIDADA, REJEITADA |
| VALIDADA | Passou nas verificações | ENVIADA, CANCELADA |
| ENVIADA | Foi mandada para a bolsa | EXECUTADA, PARCIALMENTE_EXECUTADA, REJEITADA, FALHA |
| PARCIALMENTE_EXECUTADA | A bolsa executou só uma parte (previsto; a bolsa simulada executa tudo de uma vez) | EXECUTADA, CANCELADA |
| FALHA | A bolsa não respondeu; a ordem fica guardada | ENVIADA (reenvio), CANCELADA |
| EXECUTADA, REJEITADA, CANCELADA | Estados finais | — |

## Onde cada regra está no código

| Regra | Onde |
|---|---|
| RN-01, RN-02 | `OrdemCompra.verificar` e `OrdemVenda.verificar` em `dominio.py` |
| RN-03 | coluna `chave_idempotencia UNIQUE` + `ServicoOrdens.enviar` |
| RN-04, RN-09 | `TRANSICOES` em `dominio.py` |
| RN-05 | `ServicoOrdens._mudar` (toda mudança de status grava histórico e auditoria) |
| RN-06 | `exigir()` em `servicos.py` |
| RN-07 | `ServicoOrdens._transmitir` (status FALHA) |
| RN-08 | `ServicoOrdens._ordem_do_usuario` |
| RN-10, RN-11 | `ServicoOrdens._validar` |
| RN-12 | `ServicoOrdens.enviar` (`PrecisaConfirmar`) |
| RN-13 | `Autenticacao.entrar` |
