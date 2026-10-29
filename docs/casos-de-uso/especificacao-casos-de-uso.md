# Especificação dos Casos de Uso — SentinelTrade

## Atores

| Ator | Tipo | Descrição |
|---|---|---|
| Investidor | Principal | Usuário autorizado que consulta informações e realiza operações. |
| Administrador | Principal | Gerencia ativos e parâmetros operacionais. |
| Analista de Risco | Principal | Ajusta limites de risco e acompanha ordens bloqueadas ou rejeitadas. |
| Bolsa/Corretora Simulada | Externo | Recebe ordens válidas e retorna eventos de processamento. |
| Provedor de Cotações | Externo | Fornece cotações simuladas. |
| Serviço de Notificação | Sistema de apoio | Informa o investidor sobre eventos relevantes. |
| Serviço de Auditoria | Sistema de apoio | Registra ações, decisões e alterações de estado. |

## Resumo dos casos de uso

| ID | Nome | Ator principal | Requisitos |
|---|---|---|---|
| UC-01 | Autenticar com MFA | Investidor | RF-01, RF-02, RF-19 |
| UC-02 | Consultar cotações | Investidor | RF-05 |
| UC-03 | Consultar saldo | Investidor | RF-03 |
| UC-04 | Consultar carteira | Investidor | RF-04 |
| UC-05 | Enviar ordem de compra | Investidor | RF-07, RF-09, RF-10, RF-16, RF-17, RF-18 |
| UC-06 | Enviar ordem de venda | Investidor | RF-08, RF-09, RF-10, RF-16, RF-17, RF-18 |
| UC-07 | Cancelar ordem | Investidor | RF-11, RF-16, RF-17 |
| UC-08 | Consultar e acompanhar ordem | Investidor | RF-12 |
| UC-09 | Consultar histórico | Investidor | RF-13 |
| UC-10 | Validar ordem | Sistema (incluído) | RF-09, RF-18 |
| UC-11 | Transmitir ordem à Bolsa/Corretora | Sistema (incluído) | RF-10 |
| UC-12 | Gerenciar ativos | Administrador | RF-14, RF-19 |
| UC-13 | Gerenciar limites de risco | Analista de Risco | RF-15, RF-19 |
| UC-14 | Registrar auditoria | Serviço de Auditoria | RF-16 |
| UC-15 | Enviar notificação | Serviço de Notificação | RF-17 |
| UC-16 | Receber cotações do provedor | Provedor de Cotações | RF-06 |

---

## UC-01 — Autenticar com MFA

**Ator principal:** Investidor
**Objetivo:** Permitir acesso ao SentinelTrade somente a usuário autorizado, com segundo fator.
**Pré-condições:** O investidor possui credenciais válidas.

**Fluxo principal:**
1. O investidor informa usuário e senha.
2. O sistema valida as credenciais.
3. O sistema solicita o segundo fator.
4. O investidor informa o código.
5. O sistema valida o código e libera o acesso.

**Fluxos alternativos:**
- A1 — Credencial inválida: o acesso não é liberado e a tentativa é registrada (UC-14).
- A2 — Código inválido: após 3 tentativas, a conta é bloqueada temporariamente.

**Pós-condições:** Sessão autenticada criada, ou tentativa negada e auditada.
**Requisitos:** RF-01, RF-02, RF-19, RNF-01.

---

## UC-02 — Consultar Cotações

**Ator principal:** Investidor
**Atores externos:** Provedor de Cotações (via UC-16)
**Objetivo:** Visualizar a cotação atual de um ativo.
**Pré-condições:** Investidor autenticado.

**Fluxo principal:**
1. O investidor seleciona um ativo.
2. O sistema consulta a última cotação recebida.
3. O sistema apresenta preço, horário e variação simulada.

**Fluxo alternativo:** A1 — Provedor indisponível: o sistema apresenta o último valor marcado como desatualizado.
**Requisitos:** RF-05, RF-06, RNF-05.

---

## UC-03 — Consultar Saldo

**Ator principal:** Investidor
**Objetivo:** Consultar saldo disponível, valor reservado e limite financeiro.
**Pré-condições:** Investidor autenticado.

**Fluxo principal:**
1. O investidor solicita o saldo.
2. O sistema consulta a conta do próprio investidor.
3. O sistema apresenta saldo disponível, valor reservado e limite.

**Requisitos:** RF-03, RN-08, RNF-08.

---

## UC-04 — Consultar Carteira

**Ator principal:** Investidor
**Objetivo:** Consultar as posições por ativo.
**Pré-condições:** Investidor autenticado.

**Fluxo principal:**
1. O investidor solicita a carteira.
2. O sistema consulta as posições do próprio investidor.
3. O sistema apresenta ativo, quantidade, preço médio e valor estimado.

**Requisitos:** RF-04, RN-08, RNF-08.

---

## UC-05 — Enviar Ordem de Compra

**Nome:** Enviar ordem de compra
**Ator principal:** Investidor
**Atores secundários:** Bolsa/Corretora Simulada, Serviço de Notificação, Serviço de Auditoria
**Objetivo:** Enviar uma ordem para comprar quantidade de um ativo a um preço informado.

**Pré-condições:**
- Investidor autenticado com MFA.
- Ativo ativo para negociação.
- Cotação disponível para o ativo.

**Fluxo principal:**
1. O investidor seleciona o ativo e informa tipo "compra", quantidade e preço.
2. O sistema valida os campos obrigatórios (quantidade e preço positivos, ativo ativo).
3. O sistema gera a chave de idempotência e registra a ordem no estado **Criada**.
4. O sistema executa **<<include>> Validar ordem (UC-10)**: saldo, limite de risco, situação do mercado e duplicidade.
5. A ordem passa para o estado **Validada**.
6. O sistema executa **<<include>> Transmitir ordem (UC-11)**. A ordem passa para **Enviada**.
7. Ao receber o retorno da Bolsa/Corretora, o sistema atualiza o estado para **Executada**.
8. O sistema executa **<<include>> Registrar auditoria (UC-14)** a cada mudança de estado.
9. O sistema executa **<<include>> Enviar notificação (UC-15)**.

**Fluxos alternativos:**
- **A1 — Saldo insuficiente (passo 4):** a ordem é marcada como **Rejeitada**, o motivo é informado e o fluxo segue para os passos 8 e 9.
- **A2 — Limite de risco excedido (passo 4):** a ordem é marcada como **Rejeitada**. Se o limite permitir, o sistema solicita confirmação adicional (**<<extend>> Confirmar operação acima do limite**).
- **A3 — Mercado indisponível (passo 4):** a ordem não é transmitida e recebe o estado **Falha**, com motivo registrado.
- **A4 — Ordem duplicada (passo 4):** o sistema impede novo processamento e retorna o status da ordem original.
- **A5 — Dados inválidos (passo 2):** a ordem não é registrada e o sistema exibe a mensagem de erro.
- **A6 — Falha da Bolsa/Corretora (passo 6 ou 7):** a ordem recebe o estado **Falha**, não é apagada, a carteira não é alterada e o investidor é notificado.

**Pós-condições:**
- A ordem está registrada com estado final (Executada, Rejeitada ou Falha), rastreável pelo identificador.
- A carteira e o saldo só são alterados quando a ordem é executada.
- Os eventos estão registrados na auditoria e o investidor foi notificado.

**Regras de negócio:** RN-01, RN-03, RN-04, RN-05, RN-07, RN-10, RN-11, RN-12.
**Requisitos relacionados:** RF-07, RF-09, RF-10, RF-16, RF-17, RF-18, RNF-02, RNF-03, RNF-04, RNF-06.

---

## UC-06 — Enviar Ordem de Venda

**Nome:** Enviar ordem de venda
**Ator principal:** Investidor
**Atores secundários:** Bolsa/Corretora Simulada, Serviço de Notificação, Serviço de Auditoria
**Objetivo:** Enviar uma ordem para vender quantidade de um ativo que o investidor possui.

**Pré-condições:**
- Investidor autenticado com MFA.
- Ativo ativo para negociação.
- Investidor possui posição do ativo na carteira.

**Fluxo principal:**
1. O investidor seleciona o ativo e informa tipo "venda", quantidade e preço.
2. O sistema valida os campos obrigatórios.
3. O sistema gera a chave de idempotência e registra a ordem no estado **Criada**.
4. O sistema executa **<<include>> Validar ordem (UC-10)**, verificando a posição disponível, o limite de risco, a situação do mercado e a duplicidade.
5. A ordem passa para **Validada** e, em seguida, **<<include>> Transmitir ordem (UC-11)**.
6. Ao receber o retorno, o sistema atualiza o estado para **Executada**.
7. O sistema executa **<<include>> Registrar auditoria (UC-14)** e **<<include>> Enviar notificação (UC-15)**.

**Fluxos alternativos:**
- **A1 — Posição insuficiente (passo 4):** a ordem é marcada como **Rejeitada** com motivo informado.
- **A2 — Limite de risco excedido (passo 4):** a ordem é marcada como **Rejeitada**.
- **A3 — Mercado indisponível (passo 4):** a ordem não é transmitida e recebe o estado **Falha**.
- **A4 — Ordem duplicada (passo 4):** processamento duplicado é impedido.
- **A5 — Falha da Bolsa/Corretora (passo 5):** a ordem recebe o estado **Falha**, a posição não é alterada e o investidor é notificado.

**Pós-condições:** Ordem com estado final registrado; posição alterada somente se executada; auditoria e notificação realizadas.
**Regras de negócio:** RN-02, RN-03, RN-04, RN-05, RN-07, RN-10, RN-11, RN-12.
**Requisitos relacionados:** RF-08, RF-09, RF-10, RF-16, RF-17, RF-18, RNF-02, RNF-03, RNF-04, RNF-06.

---

## UC-07 — Cancelar Ordem

**Nome:** Cancelar ordem
**Ator principal:** Investidor
**Atores secundários:** Bolsa/Corretora Simulada, Serviço de Notificação, Serviço de Auditoria
**Objetivo:** Solicitar o cancelamento de uma ordem ainda não executada.

**Pré-condições:**
- Investidor autenticado.
- A ordem pertence ao investidor.
- A ordem está em estado **Criada**, **Validada**, **Enviada** ou **Parcialmente Executada**.

**Fluxo principal:**
1. O investidor seleciona a ordem.
2. O investidor solicita o cancelamento.
3. O sistema verifica o estado atual da ordem.
4. Se o cancelamento for possível, o sistema envia a solicitação à Bolsa/Corretora Simulada.
5. A ordem passa para o estado **Cancelada**.
6. O sistema executa **<<include>> Registrar auditoria (UC-14)**.
7. O sistema executa **<<include>> Enviar notificação (UC-15)**.

**Fluxos alternativos:**
- **A1 — Ordem executada ou rejeitada (passo 3):** o cancelamento é recusado, com mensagem explicativa. A ordem não é alterada.
- **A2 — Parcialmente executada (passo 3):** o sistema cancela apenas a parte ainda não executada e registra a parte executada.
- **A3 — Falha da Bolsa/Corretora (passo 4):** a ordem mantém o estado atual, o cancelamento fica pendente, é registrada a falha e o investidor é notificado.
- **A4 — Ordem de outro investidor (passo 3):** o acesso é negado e a tentativa é registrada na auditoria.

**Pós-condições:** Ordem cancelada e registrada, ou estado inalterado com motivo registrado.
**Regras de negócio:** RN-04, RN-05, RN-07, RN-08, RN-09.
**Requisitos relacionados:** RF-11, RF-16, RF-17, RNF-03, RNF-08, RNF-10.

---

## UC-08 — Consultar e Acompanhar Ordem

**Ator principal:** Investidor
**Objetivo:** Consultar os dados e o estado atual de uma ordem, incluindo o histórico de mudanças.
**Pré-condições:** Investidor autenticado.

**Fluxo principal:**
1. O investidor seleciona uma ordem.
2. O sistema apresenta os dados da ordem e seu estado atual.
3. O sistema apresenta o histórico de estados: Criada, Validada, Enviada, Parcialmente Executada, Executada, Cancelada, Rejeitada ou Falha.

**Fluxo alternativo:** A1 — Ordem de outro investidor: acesso negado.
**Requisitos:** RF-12, RN-08, RNF-08.

---

## UC-09 — Consultar Histórico

**Ator principal:** Investidor
**Objetivo:** Consultar operações e eventos históricos da conta.
**Pré-condições:** Investidor autenticado.

**Fluxo principal:**
1. O investidor informa o período desejado.
2. O sistema lista as ordens e eventos da conta no período.
3. O sistema apresenta os resultados em ordem cronológica.

**Requisitos:** RF-13, RN-08, RNF-08.

---

## UC-10 — Validar Ordem

**Ator principal:** Sistema (incluído por UC-05 e UC-06)
**Objetivo:** Verificar se a ordem pode ser transmitida.

**Fluxo principal:**
1. O sistema executa **<<include>> Verificar saldo ou posição**.
2. O sistema executa **<<include>> Verificar limite de risco**.
3. O sistema executa **<<include>> Verificar situação do mercado**.
4. O sistema executa **<<include>> Verificar duplicidade** pela chave de idempotência.
5. Se todas as verificações forem aprovadas, a ordem é considerada válida.

**Fluxos alternativos:** Qualquer verificação reprovada rejeita a ordem (ver UC-05 e UC-06).
**Regras de negócio:** RN-01, RN-02, RN-03, RN-10, RN-11, RN-12.
**Requisitos:** RF-09, RF-18, RNF-02, RNF-06.

---

## UC-11 — Transmitir Ordem à Bolsa/Corretora

**Ator principal:** Sistema (incluído por UC-05 e UC-06)
**Atores externos:** Bolsa/Corretora Simulada
**Objetivo:** Enviar a ordem validada à Bolsa/Corretora Simulada e receber o retorno.

**Fluxo principal:**
1. O sistema envia a ordem com sua chave de idempotência.
2. A Bolsa/Corretora retorna execução, rejeição ou falha.
3. O sistema atualiza o estado da ordem conforme o retorno.

**Fluxo alternativo:** A1 — Sem resposta: o sistema mantém a ordem gravada, marca o estado como **Falha** e permite reenvio com a mesma chave, sem duplicar a ordem.
**Regras de negócio:** RN-07, RN-12.
**Requisitos:** RF-10, RNF-04, RNF-06, RNF-10.

---

## UC-12 — Gerenciar Ativos

**Ator principal:** Administrador
**Objetivo:** Cadastrar, ativar ou desativar ativos disponíveis para negociação.
**Pré-condições:** Administrador autenticado com perfil autorizado.

**Fluxo principal:**
1. O Administrador acessa a lista de ativos.
2. O Administrador cadastra, ativa ou desativa um ativo.
3. O sistema valida os dados e grava a alteração.
4. O sistema executa **<<include>> Registrar auditoria (UC-14)**.

**Fluxos alternativos:**
- A1 — Perfil sem permissão: acesso negado e tentativa registrada.
- A2 — Dados inválidos: alteração não gravada.

**Regras de negócio:** RN-05, RN-06, RN-11.
**Requisitos:** RF-14, RF-19.

---

## UC-13 — Gerenciar Limites de Risco

**Ator principal:** Analista de Risco
**Objetivo:** Ajustar os limites de risco dos investidores e acompanhar ordens bloqueadas ou rejeitadas.
**Pré-condições:** Analista de Risco autenticado com perfil autorizado.

**Fluxo principal:**
1. O Analista de Risco consulta o investidor.
2. O Analista de Risco ajusta o limite.
3. O sistema valida o valor e grava a alteração.
4. O sistema executa **<<include>> Registrar auditoria (UC-14)**.

**Fluxos alternativos:**
- A1 — Perfil sem permissão: acesso negado.
- A2 — Valor inválido: alteração não gravada.

**Regras de negócio:** RN-05, RN-06, RN-10.
**Requisitos:** RF-15, RF-19.

---

## UC-14 — Registrar Auditoria

**Ator principal:** Serviço de Auditoria (incluído por diversos casos de uso)
**Objetivo:** Registrar, de forma rastreável, eventos relevantes do sistema.

**Fluxo principal:**
1. Um caso de uso gera um evento (criação, validação, rejeição, cancelamento, execução, alteração de ativo ou limite, tentativa de acesso).
2. O sistema grava o registro com usuário, data, hora e tipo do evento.
3. O registro é gravado apenas para inserção, sem alteração ou exclusão.

**Regras de negócio:** RN-05.
**Requisitos:** RF-16, RNF-03.

---

## UC-15 — Enviar Notificação

**Ator principal:** Serviço de Notificação (incluído por UC-05, UC-06 e UC-07)
**Objetivo:** Informar o investidor sobre eventos críticos.

**Fluxo principal:**
1. Um evento crítico ocorre: execução, rejeição, cancelamento ou falha.
2. O sistema gera a notificação com o status e o motivo, quando houver.
3. A notificação é entregue ao investidor.

**Requisitos:** RF-17.

---

## UC-16 — Receber Cotações do Provedor

**Ator principal:** Provedor de Cotações
**Objetivo:** Atualizar as cotações dos ativos no SentinelTrade.

**Fluxo principal:**
1. O Provedor de Cotações envia as cotações atualizadas.
2. O sistema grava o valor com o horário de recebimento.
3. As consultas passam a usar o valor mais recente.

**Fluxo alternativo:** A1 — Falha do provedor: o sistema mantém o último valor e o marca como desatualizado.
**Requisitos:** RF-06, RNF-04, RNF-11.
