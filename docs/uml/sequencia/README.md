# UML — Diagramas de sequência

Fontes em Mermaid (`*.mmd`); exportações em `diagrams/`.

## 1. Login com MFA (UC01)
![](../../../diagrams/sequencia-login-mfa.png)
Fonte: [`login-mfa.mmd`](login-mfa.mmd)

## 2. Enviar ordem — compra ou venda (UC05/UC06)
Observe: a consulta ao provedor de cotações acontece **antes** de abrir a transação, e ordem, reserva, fila e auditoria confirmam **juntas**.

![](../../../diagrams/sequencia-enviar-ordem.png)
Fonte: [`enviar-ordem.mmd`](enviar-ordem.mmd)

## 3. Processar transmissão e execuções (UC12/UC17)
Entrega "pelo menos uma vez" da corretora + consumidor idempotente.

![](../../../diagrams/sequencia-processar-execucao.png)
Fonte: [`processar-execucao.mmd`](processar-execucao.mmd)

## 4. Falha da corretora com retentativa (RN07, RN19)
![](../../../diagrams/sequencia-falha-corretora.png)
Fonte: [`falha-corretora.mmd`](falha-corretora.mmd)

## 5. Cancelar ordem (UC13)
![](../../../diagrams/sequencia-cancelar-ordem.png)
Fonte: [`cancelar-ordem.mmd`](cancelar-ordem.mmd)

## 6. Alterar ordem — cancelar e substituir (UC25)
![](../../../diagrams/sequencia-alterar-ordem.png)
Fonte: [`alterar-ordem.mmd`](alterar-ordem.mmd)
