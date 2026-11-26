# Testes (tests/)

Testes automáticos das regras críticas. Rodam com `mvn test` (ou pelo IntelliJ) e também na esteira DevSecOps.

| Arquivo | O que testa | Requisitos / Regras |
| :--- | :--- | :--- |
| `model/StatusOrdemTest` | Transições permitidas e proibidas entre os estados da ordem | RF-10, RN-04 |
| `model/OrdemTest` | Ordem nova começa CRIADA, dados inválidos são recusados, executada não cancela | RF-07, RNF-03 |
| `service/ValidadorOrdemTest` | Cada regra: saldo, posição, limite por ordem, limite diário, mercado e ativo | RF-08, RN-01, RN-02, RN-09, RN-10 |
| `service/OrdemServiceTest` | Ciclo completo com banco SQLite real: execução, ordem aguardando, duplicidade, alto risco, falha e recuperação, cancelamento, alteração de preço, auditoria imutável | RF-07 a RF-14, RN-03, RN-04, RN-07, RN-12, RNF-07 |
| `service/ContaServiceTest` | Saldo e carteira do investidor, histórico só da própria conta, aviso de execução, alteração de limites pelo analista | RF-05, RF-12, RF-15, RF-16, RF-18, RNF-09 |
| `seguranca/SegurancaBasicaTest` | Código MFA (certo, errado, expirado, 3 tentativas) e limpeza de texto para o log | RF-01, RNF-01 |
| `web/SegurancaWebTest` | Login obrigatório, perfil errado recebe 403, formulário sem CSRF é bloqueado, cabeçalhos de segurança | RNF-01, RN-06 |
