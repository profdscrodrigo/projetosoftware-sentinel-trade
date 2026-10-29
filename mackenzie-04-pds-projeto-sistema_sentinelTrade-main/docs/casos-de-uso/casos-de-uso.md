# Casos de Uso — SentinelTrade

## Visão geral

| UC | Nome | Ator(es) | RF |
|---|---|---|---|
| UC01 | Autenticar com MFA | Usuário (Investidor, Administrador) | RF-01 |
| UC02 | Consultar cotações | Investidor, Bolsa/Corretora | RF-06 |
| UC03 | Gerenciar ordens | Investidor | RF-07 |
| UC04 | Validar ordens | — (incluído) | RF-08 |
| UC05 | Prevenir duplicidade de ordens | — (incluído) | RF-14 |
| UC06 | Integrar com Bolsa/Corretora simulada | Bolsa/Corretora | RF-09 |
| UC07 | Acompanhar ciclo de vida das ordens | Investidor | RF-10 |
| UC08 | Notificar investidores | — (extensão) | RF-12 |
| UC09 | Registrar logs de auditoria | — (incluído) | RF-11 |
| UC10 | Gerenciar investidores | Administrador | RF-02 |
| UC11 | Gerenciar contas | Administrador | RF-03 |
| UC12 | Gerenciar carteiras e ativos | Administrador | RF-04 |
| UC13 | Gerenciar limites financeiros | Administrador | RF-05 |
| UC14 | Recuperar operações | Administrador | RF-13 |

---

## UC01 – Autenticar com MFA

**Ator:** Usuário (Investidor ou Administrador)
**Pré-condição:** Usuário cadastrado e ativo.

**Fluxo principal:**
1. Usuário informa login e senha.
2. Sistema valida as credenciais.
3. Sistema envia/solicita o segundo fator (código temporário).
4. Usuário informa o código.
5. Sistema valida o código e libera o acesso conforme o perfil.

**Fluxos alternativos:**
- **FA01 (passo 2):** credenciais inválidas → sistema exibe erro e permite nova tentativa.
- **FA02 (passo 5):** código inválido ou expirado → sistema solicita novo código.
- **FA03:** excesso de tentativas → conta bloqueada temporariamente.

**Pós-condição:** Sessão autenticada criada.
**RNF:** RNF-01, RNF-02.

---

## UC02 – Consultar cotações

**Atores:** Investidor (principal), Bolsa/Corretora simulada (secundário)
**Pré-condição:** Investidor autenticado (UC01).

**Fluxo principal:**
1. Investidor seleciona ou busca um ativo.
2. Sistema solicita a cotação à Bolsa/Corretora simulada.
3. Bolsa retorna a cotação atualizada.
4. Sistema exibe preço, variação e horário da cotação.

**Fluxos alternativos:**
- **FA01 (passo 1):** ativo não encontrado → sistema informa.
- **FA02 (passo 3):** provedor indisponível → sistema exibe a última cotação conhecida com aviso de desatualização.

**Pós-condição:** Cotação exibida.
**RNF:** RNF-04, RNF-05.

---

## UC03 – Gerenciar ordens

**Ator:** Investidor
**Pré-condição:** Investidor autenticado e com conta ativa.
**Inclui:** UC04, UC06, UC09

**Fluxo principal (criar ordem):**
1. Investidor escolhe criar ordem de compra ou venda.
2. Informa ativo, quantidade, preço e tipo da ordem.
3. Sistema valida a ordem (**«include» UC04**).
4. Investidor confirma.
5. Sistema envia a ordem à Bolsa (**«include» UC06**).
6. Sistema registra a operação (**«include» UC09**) e define o status como "Enviada".

**Fluxos alternativos:**
- **FA01 – Consultar ordens:** investidor filtra por período/status e o sistema lista as ordens.
- **FA02 – Cancelar ordem:** investidor seleciona uma ordem pendente, confirma, o sistema envia o cancelamento à Bolsa e registra o log.
- **FA03 (passo 3):** validação reprovada → sistema exibe o motivo e não envia a ordem.
- **FA04 (FA02):** ordem já executada → cancelamento não permitido.

**Pós-condição:** Ordem criada, consultada ou cancelada, com registro em log.
**RNF:** RNF-02, RNF-03, RNF-06.

---

## UC04 – Validar ordens

**Ator:** Sistema (incluído por UC03)
**Inclui:** UC05

**Fluxo principal:**
1. Sistema verifica duplicidade (**«include» UC05**).
2. Verifica saldo disponível (compra) ou posição em carteira (venda).
3. Verifica limites financeiros e de risco da conta.
4. Verifica condições do mercado (ativo negociável, mercado aberto).
5. Sistema aprova a ordem.

**Fluxo de exceção:**
- **FE01 (passos 1–4):** qualquer verificação falha → ordem rejeitada com o motivo registrado.

**Pós-condição:** Ordem aprovada ou rejeitada.
**RNF:** RNF-03, RNF-06.

---

## UC05 – Prevenir duplicidade de ordens

**Ator:** Sistema (incluído por UC04)

**Fluxo principal:**
1. Sistema gera/recebe um identificador único da ordem.
2. Verifica se esse identificador já foi processado ou transmitido.
3. Não havendo duplicata, libera a ordem para as demais validações.

**Fluxo de exceção:**
- **FE01 (passo 2):** ordem duplicada → sistema bloqueia e registra a tentativa.

**Pós-condição:** Garantia de que a ordem será processada uma única vez.
**RNF:** RNF-03, RNF-06.

---

## UC06 – Integrar com Bolsa/Corretora simulada

**Ator:** Bolsa/Corretora simulada

**Fluxo principal:**
1. Sistema envia a ordem aprovada à Bolsa.
2. Bolsa confirma o recebimento.
3. Bolsa processa e retorna o resultado (executada, parcialmente executada ou rejeitada).
4. Sistema atualiza o status da ordem e a carteira/saldo.

**Fluxos alternativos:**
- **FA01 (passo 2):** sem resposta → sistema reenvia com o mesmo identificador (sem duplicar, UC05).
- **FA02:** Bolsa indisponível → ordem marcada como "Falha" e enviada para recuperação (UC14).

**Pós-condição:** Ordem com status atualizado.
**RNF:** RNF-04, RNF-05, RNF-06.

---

## UC07 – Acompanhar ciclo de vida das ordens

**Ator:** Investidor
**Pré-condição:** Investidor autenticado e com ao menos uma ordem.
**Ponto de extensão:** mudança de status da ordem → UC08

**Fluxo principal:**
1. Investidor acessa suas ordens.
2. Seleciona uma ordem.
3. Sistema exibe o histórico de status: criada → validada → enviada → executada/rejeitada/cancelada/falha, com data e hora.

**Fluxo alternativo:**
- **FA01:** nenhuma ordem encontrada → sistema informa.

**Pós-condição:** Status exibido ao investidor.
**RNF:** RNF-02.

---

## UC08 – Notificar investidores

**Ator:** Sistema (estende UC07)
**Condição de extensão:** ordem executada, rejeitada, cancelada ou com falha.

**Fluxo principal:**
1. Sistema detecta a mudança de status.
2. Gera a notificação com ordem, novo status e motivo (se houver).
3. Envia a notificação ao investidor.

**Fluxo alternativo:**
- **FA01:** falha no envio → sistema tenta novamente e mantém a notificação pendente.

**Pós-condição:** Investidor informado.

---

## UC09 – Registrar logs de auditoria

**Ator:** Sistema (incluído por UC03, UC10–UC14)

**Fluxo principal:**
1. Sistema captura usuário, ação, ordem/entidade envolvida e data/hora.
2. Grava o registro em armazenamento imutável (sem edição ou exclusão).

**Fluxo de exceção:**
- **FE01:** falha na gravação → a operação não é concluída até o log ser registrado.

**Pós-condição:** Operação rastreável.
**RNF:** RNF-02, RNF-03, RNF-07.

---

## UC10 – Gerenciar investidores

**Ator:** Administrador
**Pré-condição:** Administrador autenticado.
**Inclui:** UC09

**Fluxo principal:**
1. Administrador acessa o módulo de investidores.
2. Escolhe cadastrar, consultar ou atualizar.
3. Informa ou edita os dados (nome, CPF, e-mail etc.).
4. Sistema valida os dados e salva.
5. Sistema registra o log (**«include» UC09**).

**Fluxos alternativos:**
- **FA01 (passo 4):** dados inválidos ou CPF já cadastrado → sistema exibe erro.
- **FA02:** investidor não encontrado na consulta → sistema informa.

**Pós-condição:** Dados do investidor atualizados.
**RNF:** RNF-01, RNF-03.

---

## UC11 – Gerenciar contas

**Ator:** Administrador
**Inclui:** UC09

**Fluxo principal:**
1. Administrador seleciona um investidor.
2. Escolhe cadastrar ou consultar conta.
3. Informa os dados da conta (no cadastro).
4. Sistema vincula a conta ao investidor e salva.
5. Sistema registra o log.

**Fluxo alternativo:**
- **FA01:** investidor inexistente ou inativo → cadastro bloqueado.

**Pós-condição:** Conta cadastrada/consultada.
**RNF:** RNF-03.

---

## UC12 – Gerenciar carteiras e ativos

**Ator:** Administrador
**Inclui:** UC09

**Fluxo principal:**
1. Administrador seleciona a conta do investidor.
2. Sistema exibe a carteira e seus ativos (quantidade, preço médio).
3. Administrador cria a carteira ou ajusta ativos.
4. Sistema valida e salva.
5. Sistema registra o log.

**Fluxo alternativo:**
- **FA01:** ajuste deixaria quantidade negativa → sistema rejeita.

**Pós-condição:** Carteira atualizada.
**RNF:** RNF-03.

---

## UC13 – Gerenciar limites financeiros

**Ator:** Administrador
**Inclui:** UC09

**Fluxo principal:**
1. Administrador seleciona a conta.
2. Sistema exibe os limites atuais (financeiro, por ordem, de risco).
3. Administrador define ou altera os limites.
4. Sistema valida e salva.
5. Sistema registra o log.

**Fluxo alternativo:**
- **FA01:** valor inválido (negativo ou fora da política) → sistema exibe erro.

**Pós-condição:** Novos limites aplicados às próximas validações (UC04).
**RNF:** RNF-03.

---

## UC14 – Recuperar operações

**Ator:** Administrador
**Inclui:** UC09
**Pré-condição:** Existência de operações com status "Falha" ou interrompidas.

**Fluxo principal:**
1. Administrador acessa as operações pendentes/com falha.
2. Sistema lista as operações e o ponto onde pararam.
3. Administrador seleciona uma operação e aciona a recuperação.
4. Sistema consulta a Bolsa para confirmar o estado real da ordem.
5. Sistema reprocessa ou conclui a operação sem duplicá-la (UC05).
6. Sistema registra o log.

**Fluxos alternativos:**
- **FA01 (passo 4):** ordem já executada na Bolsa → sistema apenas sincroniza o status.
- **FA02:** recuperação falha novamente → operação mantida pendente com alerta.

**Pós-condição:** Operação concluída ou sincronizada.
**RNF:** RNF-04, RNF-06.
