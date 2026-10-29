# Requisitos do SentinelTrade

> **Aviso:** o SentinelTrade opera exclusivamente com dados e operações simuladas.

## 1. Visão do sistema

O SentinelTrade é uma plataforma simulada de negociação de ativos financeiros. Ela permite que investidores autenticados acompanhem sua carteira, consultem cotações e realizem operações de compra e venda, respeitando saldo, posição disponível, limites de risco e regras de segurança.

O sistema deverá garantir que as ordens sejam rastreáveis, que não sejam executadas de forma duplicada e que todos os eventos relevantes sejam registrados em auditoria.

## 2. Atores

| Ator | Responsabilidade |
|---|---|
| Investidor | Consulta cotações, carteira, saldo, histórico e envia ou cancela ordens. |
| Administrador | Gerencia usuários, ativos, limites e parâmetros operacionais. |
| Analista de Risco | Consulta e ajusta limites de risco, acompanha ordens bloqueadas ou rejeitadas. |
| Corretora/Bolsa Simulada | Recebe ordens válidas e retorna informações sobre execução, rejeição ou falha. |
| Provedor de Cotações | Fornece valores atualizados ou simulados dos ativos. |
| Serviço de Notificação | Informa o investidor sobre eventos relevantes. |
| Serviço de Auditoria | Mantém o registro de ações, decisões e alterações de estado. |

## 3. Requisitos

- Funcionais: [requisitos-funcionais.md](requisitos-funcionais.md)
- Não funcionais: [requisitos-nao-funcionais.md](requisitos-nao-funcionais.md)
- Regras de negócio: [regras-de-negocio.md](regras-de-negocio.md)
- Matriz de rastreabilidade: [matriz-rastreabilidade.md](matriz-rastreabilidade.md)
