# Regras de Negócio — SentinelTrade

Regras que o sistema deve respeitar em **toda** operação. Elas complementam os [requisitos](./requisitos.md) e são verificadas nos [casos de uso](../casos-de-uso/casos-de-uso.md) e no [banco de dados](../../database/schema.sql).

> Todo o ambiente é simulado: ativos, contas, saldos, cotações e a Bolsa/Corretora são fictícios.

## Regras principais

| ID | Regra de negócio | Requisitos | Casos de uso | Onde é garantida |
| :--- | :--- | :--- | :--- | :--- |
| RN-01 | Uma ordem de **compra** exige saldo disponível compatível com a quantidade × preço informados. | RF-07, RF-08 | UC03, UC04 | Validação de Ordens |
| RN-02 | Uma ordem de **venda** exige posição disponível do ativo na carteira (quantidade suficiente). | RF-07, RF-08 | UC03, UC04 | Validação de Ordens |
| RN-03 | A mesma ordem **não pode ser processada ou executada duas vezes**. Cada ordem tem um identificador único (chave de idempotência) e os reenvios usam o mesmo identificador. | RF-14, RNF-06 | UC05, UC06 | Controle de Duplicidade + `UNIQUE` em `ordem.chave_idempotencia` |
| RN-04 | Uma ordem **executada, rejeitada ou cancelada não pode voltar** ao estado inicial nem mudar de status. | RF-10, RNF-03 | UC03, UC07 | Gatilho `trg_ordem_estado_final` |
| RN-05 | **Toda mudança de estado da ordem gera auditoria** (usuário/sistema, ação, ordem e data/hora). | RF-10, RF-11, RNF-02, RNF-07 | UC09 | Gatilhos `trg_ordem_criada` e `trg_ordem_status_alterado` |
| RN-06 | O **acesso administrativo** é restrito a usuários com perfil autorizado (Administrador ou Analista de Risco, conforme a função). | RF-01, RNF-01 | UC01, UC10–UC14, UC17, UC18 | Autenticação MFA + perfil do usuário |
| RN-07 | Uma **indisponibilidade da corretora simulada** não pode apagar a ordem nem alterar indevidamente a carteira: a ordem fica com status `FALHA` e vai para recuperação. | RF-09, RF-13, RNF-04 | UC06, UC14 | Gateway Bolsa + Recuperação de Operações |
| RN-08 | O **investidor só pode consultar as próprias informações** (carteira, saldo, ordens, histórico e notificações). | RNF-01, RNF-09 | UC02, UC07, UC15, UC16 | Controle de acesso por usuário |

## Regras adicionais

| ID | Regra de negócio | Requisitos | Casos de uso |
| :--- | :--- | :--- | :--- |
| RN-09 | Uma ordem só é aceita se o **valor total** (quantidade × preço) estiver dentro do **limite por ordem** e do **limite diário** da conta. | RF-05, RF-08 | UC04, UC13 |
| RN-10 | Só podem ser negociados ativos marcados como **negociáveis** e com o **mercado aberto**. | RF-06, RF-08, RF-17 | UC04, UC17 |
| RN-11 | Após **3 tentativas de login inválidas**, o usuário fica bloqueado temporariamente (parâmetro `MAX_TENTATIVAS_LOGIN`). | RF-01, RNF-01 | UC01 |
| RN-12 | Ordens acima do **limite de alto risco** da conta exigem uma **confirmação adicional** do investidor antes do envio («extend» de Enviar ordem). | RF-05, RF-08 | UC03, UC19 |
| RN-13 | A quantidade de um ativo na carteira **nunca pode ficar negativa**, e o saldo da conta nunca pode ser negativo. | RF-04, RNF-03 | UC06, UC12 |
| RN-14 | Uma ordem só pode ser **cancelada** enquanto não estiver executada (status `CRIADA`, `VALIDADA` ou `ENVIADA`). | RF-07 | UC03 |
| RN-15 | Os registros de auditoria **não podem ser editados nem apagados**. | RF-11, RNF-07 | UC09 |

## Ciclo de vida (estados) da ordem

```
CRIADA → VALIDADA → ENVIADA → EXECUTADA
   │         │          ├────→ REJEITADA
   │         │          └────→ FALHA ──(recuperação UC14)──→ ENVIADA / EXECUTADA
   └─────────┴──→ REJEITADA (validação reprovada)
CRIADA / VALIDADA / ENVIADA → CANCELADA (pedido do investidor)
```

Estados finais (não mudam mais — RN-04): **EXECUTADA**, **REJEITADA**, **CANCELADA**.
