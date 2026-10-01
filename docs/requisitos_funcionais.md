# Especificação de Requisitos — SentinelTrade

## 1. Requisitos Funcionais 

* **Gestão de Contas e Carteiras:** Manter e gerenciar dados cadastrais de investidores, contas ativas, carteiras de investimentos, ativos em posse e limites financeiros.
* **Ingestão de Cotações:** Receber e processar cotações de ativos em tempo quase real por meio da integração com um provedor de dados externo.
* **Operação de Ativos:** Permitir a submissão de ordens de trade (compra e venda), bem como o cancelamento de ordens pendentes e a consulta de operações.
* **Motor de Validação (Risco):** Validar saldo disponível, posição atualizada na carteira, limites de risco do investidor e situação de abertura/fechamento do mercado antes de transmitir qualquer ordem.
* **Integração de Roteamento:** Conectar-se e integrar o fluxo de ordens a uma Bolsa/Corretora simulada.
* **Acompanhamento de Ciclo de Vida:** Rastrear e atualizar o status das ordens (ex: criada, validada, enviada, executada, rejeitada).
* **Sistema de Notificação:** Alertar ativamente o investidor sobre o desfecho de suas ações operacionais, como execução, rejeição, cancelamento ou falha.
* **Autenticação via MFA:** Garantir um controle de acesso rigoroso validado por autenticação multifator (MFA)

---

## 2. Requisitos não funcionais

### Explícitos
* **Consistência (Integridade de Dados):** Prevenção rigorosa de duplicidade de ordens e garantia matemática nas validações de saldo e limites. 
* **Auditabilidade:** Geração de logs de auditoria imutáveis para todas as transações e eventos.
* **Performance (Baixa Latência):** Processamento de fluxos de dados e roteamento de ordens em tempo quase real.
* **Segurança:** Controle de acesso e proteção de dados financeiros sensíveis.
* **Resiliência e Recuperabilidade:** Implementação de mecanismos de indisponibilidade controlada e recuperação automática após falhas.

### Implícitas 
* **Confiabilidade:** Garantia de que as mensagens e ordens roteadas não sejam perdidas em trânsito, mantendo o comportamento do sistema previsível para garantir a confiança do investidor e mitigar o impacto reputacional.
* **Elasticidade:** Capacidade de escalar instâncias automaticamente durante picos repentinos de volumetria de usuários e requisições, característicos dos horários de abertura e fechamento do mercado.
* **Agilidade (Testabilidade e Implantabilidade):** Estruturação que permita evolução contínua, testes automatizados confiáveis e deploys seguros sem interrupção prolongada da plataforma de negociação.
* **Modularidade:** Alta coesão e baixo acoplamento entre os domínios (Risco, Ordens, Identidade), facilitando a distribuição dos serviços e o isolamento de falhas.
