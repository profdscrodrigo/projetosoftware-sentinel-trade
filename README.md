# SentinelTrade

Sistema de Trade Financeiro de Alta Criticidade, desenvolvido como exercício integrador acadêmico para a corretora fictícia Orion Capital.

> Projeto acadêmico. Não opera com dinheiro real, bolsa real ou dados financeiros reais — todos os ativos, contas e cotações são simulados.

---

## Sobre o projeto
A Orion Capital é uma corretora fictícia que negocia ações, ETFs e fundos imobiliários. Hoje suas operações dependem de sistemas pouco integrados, o que dificulta a rastreabilidade das ordens, o controle de risco e a auditoria.

O SentinelTrade vem para resolver isso: uma plataforma distribuída, segura, escalável e tolerante a falhas, que permite a investidores autorizados:
- acompanharem cotações em tempo quase real;
- manter uma carteira de ativos;
- enviar ordens de compra e venda;
- consultar o histórico de operações.

Como o impacto de falhas aqui é financeiro, regulatório e reputacional, o sistema é tratado como de alta criticidade, cada decisão de arquitetura leva em conta segurança, auditabilidade e recuperação de falhas.

## Requisitos do sistema
Requisitos Funcionais(RF):
RF-01: Cadastro de investidores, contas, carteiras, ativos e limites financeiros
RF-02: Consulta de informações (carteira, conta, limites)
RF-03: Recebimento de cotações em tempo quase real (provedor externo simulado)
RF-04: Envio de ordens: compra, venda, cancelamento e consulta
RF-05: Validação de saldo, posição em carteira, limite de risco e situação do mercado antes de transmitir a ordem
RF-06: Integração com Bolsa/Corretora simulada
RF-07: Acompanhamento do ciclo de vida das ordens
RF-08: Logs de auditoria imutáveis
RF-09: Notificação ao investidor (execução, rejeição, cancelamento, falha)
RF-10: Consulta de histórico

Requisitos Não Funcionais(RNF):
RNF-01: Autenticação de usuários com MFA (múltiplo fator)
RNF-02: Mecanismos de recuperação, indisponibilidade controlada e prevenção de duplicidade de ordens
RNF-03: Integridade dos dados
RNF-04: Desempenho
RNF-05: Escalabilidade sem comprometer o sistema
RNF-06: Segurança de dados e operações
RNF-07: Recuperação no caso e falhas
RNF-08: Consistência dos dados
RNF-09: Disponibilidade
RNF-10: Proteção de senhas

## Segurança
O SentinelTrade considera segurança como requisito fundamental devido à criticidade financeira das operações. É previsto no sistema:
-Autenticação multifator (MFA);
-Proteção de senhas por hash;
-Controle de acesso por perfil;
-Validação de entrada;
-Tratamento seguro de exceções;
-Prevenção contra duplicidade de ordens;
-Proteção contra ataques de injeção;
-Proteção de segredos e credenciais;
-Registros de auditoria.

## Resiliência
Para conseguir lidar com falhas e indisponibilidade, é previsto no SentinelTrade:
-Time-out nas integrações;
-Retentativas controladas;
-Idempotência das ordens;
-Fila de mensagens para processamento assíncrono;
-Indisponibilidade segura;
-Mecanismos de recuperação;
-Manutenção da consistência dos dados.

## Pensamento Sistêmico
O SentinelTrade considera o relacionamento entre seus diferentes componentes, incluindo frontend, backend, autenticação, motor de ordens, banco de dados, provedor de cotações, bolsa/corretora simulada e notificações.
Uma falha que acontece em um componente pode acabar afetando os demais. Por isso, o sistema considera não apenas o funcionamento normal, mas também situações de falha, indisponibilidade, reenvio de ordens, recuperação e consistência dos dados.

## UML (Casos de Uso) do Sistema
<img width="1536" height="1024" alt="ChatGPT Image 30 de set  de 2026, 17_43_33" src="https://github.com/user-attachments/assets/c386b512-ad1a-4fa4-89b9-0321c75b047a" />



## Arquitetura (visão geral)

O sistema é dividido em componentes que conversam entre si, cada um com uma responsabilidade clara:

```
[Investidor] ──> [App/Frontend] ──> [SentinelTrade - Backend]
                                          │
                    ┌─────────────────────┼─────────────────────┐
                    ▼                     ▼                     ▼
         [Autenticação/MFA]      [Motor de Ordens]     [Banco de Dados]
                                          │
                                          ▼
                              [Bolsa/Corretora Simulada]
                                          ▲
                                          │
                         [Provedor de Cotações Simulado]
```

- Provedor de cotações: componente externo (simulado) que envia preços atualizados continuamente.
- Motor de ordens: valida saldo, risco e situação do mercado antes de enviar a ordem para a bolsa simulada.
- Banco de dados: guarda histórico, carteiras e logs de auditoria (imutáveis).
- Notificações: avisa o investidor sobre o que aconteceu com sua ordem.

## Tecnologias 
Linguagem : Python 3.11 ou superior
Git/Github para versionamento
// Adicionar banco de dados e resto assim que for definido


## Equipe

| Leonardo Aquino Cruz |10445016
| Victor Esteves Gallo Birello|10737139


