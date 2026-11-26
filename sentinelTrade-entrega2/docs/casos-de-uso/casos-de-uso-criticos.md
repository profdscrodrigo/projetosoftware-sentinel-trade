# Casos de Uso Críticos — Descrição Textual

Descrição detalhada dos três casos de uso mais importantes do sistema, no formato: Nome, Ator principal, Objetivo, Pré-condições, Fluxo principal, Fluxos alternativos, Pós-condições, Regras de negócio e Requisitos relacionados.

Eles detalham o **UC03 – Gerenciar ordens** (e seus «include» UC04, UC05, UC06 e UC09) descrito em [casos-de-uso.md](./casos-de-uso.md). Regras citadas: [regras-de-negocio.md](../requisitos/regras-de-negocio.md).

---

## CUC-01 — Enviar ordem de compra

| Campo | Conteúdo |
| :--- | :--- |
| **Nome** | Enviar ordem de compra |
| **Ator principal** | Investidor |
| **Atores secundários** | Corretora/Bolsa Simulada, Serviço de Auditoria, Serviço de Notificação |
| **Objetivo** | Comprar uma quantidade de um ativo por um preço informado, com a ordem validada e registrada. |
| **Pré-condições** | Investidor autenticado com MFA (UC01); conta ativa; ativo cadastrado. |
| **Fluxo principal** | 1. Investidor escolhe "Nova ordem de compra".<br>2. Informa ativo, quantidade e preço.<br>3. Sistema gera um identificador único para a ordem (status **CRIADA**).<br>4. Sistema valida a ordem (**«include» Validar ordem**): não duplicada, saldo suficiente, dentro dos limites e mercado aberto.<br>5. Sistema mostra o resumo (valor total) e o investidor confirma.<br>6. Sistema transmite a ordem à Bolsa simulada (**«include» Transmitir ordem**) — status **ENVIADA**.<br>7. Bolsa executa a ordem — status **EXECUTADA**; saldo diminui e a quantidade do ativo aumenta na carteira.<br>8. Sistema registra cada mudança de status (**«include» Registrar auditoria**).<br>9. Sistema notifica o investidor (**«include» Enviar notificação**). |
| **Fluxos alternativos** | **FA01 – Saldo insuficiente (passo 4):** ordem **REJEITADA** com o motivo; nada é enviado à Bolsa.<br>**FA02 – Ativo indisponível ou mercado fechado (passo 4):** ordem **REJEITADA** com o motivo.<br>**FA03 – Limite excedido (passo 4):** ordem **REJEITADA**; se passar só do limite de alto risco, o sistema pede confirmação adicional (**«extend» UC19**).<br>**FA04 – Ordem duplicada (passo 4):** a tentativa é bloqueada e registrada; nenhuma ordem nova é criada.<br>**FA05 – Falha de comunicação (passo 6):** reenvio com o mesmo identificador (até 3 vezes); sem resposta, a ordem fica **FALHA** e vai para recuperação (UC14), sem alterar saldo nem carteira.<br>**FA06 – Bolsa rejeita (passo 7):** ordem **REJEITADA** com o motivo informado pela Bolsa. |
| **Pós-condições** | Ordem em estado final (**EXECUTADA** ou **REJEITADA**) ou **FALHA** aguardando recuperação; saldo e carteira atualizados só se executada; todos os eventos auditados; investidor notificado. |
| **Regras de negócio** | RN-01, RN-03, RN-04, RN-05, RN-07, RN-09, RN-10, RN-12 |
| **Requisitos relacionados** | RF-07, RF-08, RF-09, RF-10, RF-11, RF-12, RF-14 · RNF-02, RNF-03, RNF-04, RNF-06, RNF-07 |

---

## CUC-02 — Enviar ordem de venda

| Campo | Conteúdo |
| :--- | :--- |
| **Nome** | Enviar ordem de venda |
| **Ator principal** | Investidor |
| **Atores secundários** | Corretora/Bolsa Simulada, Serviço de Auditoria, Serviço de Notificação |
| **Objetivo** | Vender uma quantidade de um ativo que o investidor possui na carteira. |
| **Pré-condições** | Investidor autenticado com MFA (UC01); conta ativa; ativo presente na carteira. |
| **Fluxo principal** | 1. Investidor escolhe "Nova ordem de venda".<br>2. Informa ativo, quantidade e preço.<br>3. Sistema gera um identificador único para a ordem (status **CRIADA**).<br>4. Sistema valida a ordem (**«include» Validar ordem**): não duplicada, **quantidade disponível na carteira**, dentro dos limites e mercado aberto.<br>5. Investidor confirma o resumo.<br>6. Sistema transmite a ordem à Bolsa simulada — status **ENVIADA**.<br>7. Bolsa executa a ordem — status **EXECUTADA**; a quantidade do ativo diminui na carteira e o saldo aumenta.<br>8. Sistema registra a auditoria de cada mudança de status.<br>9. Sistema notifica o investidor. |
| **Fluxos alternativos** | **FA01 – Posição insuficiente (passo 4):** quantidade maior que a disponível → ordem **REJEITADA** com o motivo.<br>**FA02 – Ativo indisponível ou mercado fechado (passo 4):** ordem **REJEITADA**.<br>**FA03 – Limite excedido (passo 4):** ordem **REJEITADA** (ou confirmação adicional, «extend» UC19).<br>**FA04 – Ordem duplicada (passo 4):** tentativa bloqueada e registrada.<br>**FA05 – Falha de comunicação (passo 6):** reenvio com o mesmo identificador; sem resposta → **FALHA** e recuperação (UC14), sem alterar a carteira. |
| **Pós-condições** | Ordem **EXECUTADA**, **REJEITADA** ou **FALHA**; a quantidade do ativo nunca fica negativa; eventos auditados; investidor notificado. |
| **Regras de negócio** | RN-02, RN-03, RN-04, RN-05, RN-07, RN-09, RN-10, RN-13 |
| **Requisitos relacionados** | RF-04, RF-07, RF-08, RF-09, RF-10, RF-11, RF-12, RF-14 · RNF-03, RNF-04, RNF-06, RNF-07 |

---

## CUC-03 — Cancelar ordem

| Campo | Conteúdo |
| :--- | :--- |
| **Nome** | Cancelar ordem |
| **Ator principal** | Investidor |
| **Atores secundários** | Corretora/Bolsa Simulada, Serviço de Auditoria, Serviço de Notificação |
| **Objetivo** | Cancelar uma ordem que ainda não foi executada. |
| **Pré-condições** | Investidor autenticado com MFA (UC01); existe uma ordem do próprio investidor com status **CRIADA**, **VALIDADA** ou **ENVIADA**. |
| **Fluxo principal** | 1. Investidor acessa suas ordens e seleciona uma ordem pendente.<br>2. Escolhe "Cancelar" e confirma.<br>3. Se a ordem já foi enviada, o sistema pede o cancelamento à Bolsa simulada.<br>4. Bolsa confirma o cancelamento.<br>5. Sistema muda o status para **CANCELADA**.<br>6. Sistema registra a auditoria (**«include» Registrar auditoria**).<br>7. Sistema notifica o investidor (**«include» Enviar notificação**). |
| **Fluxos alternativos** | **FA01 – Ordem já executada (passo 1 ou 4):** cancelamento não permitido; sistema informa o motivo.<br>**FA02 – Ordem de outro investidor:** acesso negado (RN-08).<br>**FA03 – Falha de comunicação (passo 3):** o pedido é reenviado; se a Bolsa não responder, a ordem fica em recuperação (UC14) e o status real é sincronizado depois. |
| **Pós-condições** | Ordem **CANCELADA** (estado final, não volta a nenhum outro status); cancelamento auditado; investidor notificado. |
| **Regras de negócio** | RN-04, RN-05, RN-07, RN-08, RN-14 |
| **Requisitos relacionados** | RF-07, RF-09, RF-10, RF-11, RF-12, RF-13 · RNF-02, RNF-07, RNF-09 |
