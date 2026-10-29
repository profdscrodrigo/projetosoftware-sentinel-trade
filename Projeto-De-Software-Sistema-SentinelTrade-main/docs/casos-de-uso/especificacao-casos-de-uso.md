# Especificação dos Casos de Uso

## Atores

### Investidor
Usuário autorizado que acessa o SentinelTrade para consultar informações e realizar operações.

### Provedor de Cotações
Sistema externo responsável por fornecer cotações simuladas ao SentinelTrade.

### Bolsa/Corretora Simulada
Sistema externo que recebe ordens válidas e retorna eventos relacionados ao processamento da ordem.

---

## UC-01 — Autenticar Usuário

**Ator:** Investidor

**Objetivo:** Permitir acesso ao SentinelTrade por um usuário autorizado.

**Pré-condição:** O investidor possui credenciais válidas.

**Fluxo principal:**
1. O investidor informa suas credenciais.
2. O sistema solicita o segundo fator de autenticação.
3. O investidor informa o segundo fator.
4. O sistema valida as informações.
5. O sistema libera o acesso.

**Fluxo alternativo:** Se a autenticação falhar, o acesso não é liberado e o evento é registrado para auditoria.

---

## UC-02 — Consultar Cotações

**Ator:** Investidor

**Ator externo relacionado:** Provedor de Cotações

**Objetivo:** Permitir consulta de cotações simuladas.

**Fluxo principal:**
1. O investidor solicita a cotação de um ativo.
2. O SentinelTrade consulta os dados disponíveis.
3. O sistema apresenta a cotação ao investidor.

---

## UC-03 — Consultar Saldo

**Ator:** Investidor

**Objetivo:** Consultar saldo e limites financeiros da conta.

**Fluxo principal:**
1. O investidor solicita os dados da conta.
2. O sistema consulta saldo e limites.
3. O sistema apresenta as informações.

---

## UC-04 — Consultar Carteira

**Ator:** Investidor

**Objetivo:** Consultar posições dos ativos mantidos na carteira.

**Fluxo principal:**
1. O investidor solicita a carteira.
2. O sistema consulta as posições.
3. O sistema apresenta os ativos e respectivas posições.

---

## UC-05 — Enviar Ordem

**Ator:** Investidor

**Ator externo relacionado:** Bolsa/Corretora Simulada

**Objetivo:** Permitir o envio de uma ordem de compra ou venda.

**Pré-condições:**
- Investidor autenticado.
- Conta disponível.
- Ativo disponível para negociação.

**Fluxo principal:**
1. O investidor seleciona o ativo.
2. Escolhe compra ou venda.
3. Informa quantidade e preço.
4. O sistema valida os dados.
5. O sistema verifica saldo.
6. O sistema verifica a posição em carteira.
7. O sistema verifica o limite de risco.
8. O sistema verifica a situação do mercado.
9. O sistema verifica duplicidade.
10. O sistema registra a ordem.
11. O sistema transmite a ordem à Bolsa/Corretora Simulada.
12. O sistema atualiza o estado da ordem.
13. O sistema registra o evento de auditoria.
14. O sistema notifica o investidor.

**Fluxos alternativos:**
- Saldo insuficiente → ordem rejeitada.
- Posição insuficiente para venda → ordem rejeitada.
- Limite de risco excedido → ordem rejeitada.
- Mercado indisponível → ordem não transmitida.
- Ordem duplicada → processamento duplicado impedido.
- Falha na Bolsa/Corretora Simulada → falha registrada e investidor notificado.

---

## UC-06 — Cancelar Ordem

**Ator:** Investidor

**Objetivo:** Solicitar o cancelamento de uma ordem que ainda possa ser cancelada.

**Fluxo principal:**
1. O investidor seleciona uma ordem.
2. Solicita o cancelamento.
3. O sistema verifica o estado da ordem.
4. Se o cancelamento for possível, o sistema envia a solicitação à Bolsa/Corretora Simulada.
5. O estado da ordem é atualizado.
6. O evento é registrado para auditoria.
7. O investidor é notificado.

---

## UC-07 — Consultar Ordem

**Ator:** Investidor

**Objetivo:** Consultar os dados e o estado atual de uma ordem.

---

## UC-08 — Acompanhar Ordem

**Ator:** Investidor

**Objetivo:** Acompanhar o ciclo de vida da ordem.

**Estados relevantes:** Criada, Validada, Enviada, Parcialmente Executada, Executada, Cancelada, Rejeitada e Falha.

---

## UC-09 — Consultar Histórico

**Ator:** Investidor

**Objetivo:** Consultar operações e eventos históricos relacionados à conta.

