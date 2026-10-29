# Regras de Negócio do SentinelTrade

| ID | Regra | Requisitos relacionados | Casos de uso |
|---|---|---|---|
| RN-01 | Uma ordem de compra exige saldo disponível compatível com a quantidade × preço informado. | RF-07, RF-09 | UC-05, UC-10 |
| RN-02 | Uma ordem de venda exige posição disponível do ativo na carteira. | RF-08, RF-09 | UC-06, UC-10 |
| RN-03 | A mesma ordem não pode ser processada nem executada mais de uma vez. | RF-18, RNF-06 | UC-05, UC-06, UC-10 |
| RN-04 | Uma ordem executada, rejeitada ou cancelada não pode retornar ao estado inicial. | RF-12 | UC-05, UC-06, UC-07 |
| RN-05 | Toda mudança de estado da ordem deve gerar registro de auditoria. | RF-16, RNF-03 | UC-05, UC-06, UC-07, UC-14 |
| RN-06 | O acesso administrativo é restrito a usuários com perfil autorizado. | RF-14, RF-15, RF-19, RNF-01 | UC-12, UC-13 |
| RN-07 | A indisponibilidade da Bolsa/Corretora Simulada não pode apagar a ordem nem alterar indevidamente a carteira. | RF-10, RNF-04 | UC-05, UC-06, UC-07 |
| RN-08 | O investidor somente pode consultar suas próprias informações. | RF-19, RNF-08 | UC-03, UC-04, UC-08, UC-09 |
| RN-09 | Ordem executada não pode ser cancelada. | RF-11 | UC-07 |
| RN-10 | Ordem acima do limite de risco vigente é rejeitada; quando o limite permitir, exige confirmação adicional. | RF-15, RF-09 | UC-05, UC-06, UC-10 |
| RN-11 | Ordem só é transmitida se a situação do mercado estiver disponível e o ativo estiver ativo para negociação. | RF-09, RF-14 | UC-05, UC-06, UC-11 |
| RN-12 | Toda ordem recebe uma chave de idempotência no momento do envio. | RF-18, RNF-06 | UC-05, UC-06 |
