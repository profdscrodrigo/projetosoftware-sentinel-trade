# SentinelTrade

Plataforma distribuída de negociação de ativos (ações, ETFs e FIIs) de **alta criticidade**, projetada para a corretora fictícia **Orion Capital**.
Projeto da disciplina **Projeto de Software**.

>  Não há integração com bolsa real nem dinheiro real.

## Visão do projeto

Hoje a Orion Capital opera com sistemas pouco integrados, o que dificulta a rastreabilidade das ordens, o controle de risco e a auditoria. O SentinelTrade unifica o fluxo de negociação:

- autenticação com MFA e controle de acesso por perfil;
- cadastro de investidores, contas, carteiras, ativos e limites;
- cotações em tempo quase real vindas de provedor externo;
- envio, alteração e cancelamento de ordens, com validação de saldo, posição, limite de risco e situação do mercado;
- integração com Bolsa/Corretora simulada e acompanhamento do ciclo de vida da ordem;
- notificações ao investidor;
- auditoria imutável;
- resiliência: time-out, retentativa controlada, idempotência, fila de mensagens, indisponibilidade segura e recuperação de falhas.

## Integrantes

| Nome | RA | Responsabilidade |
|---|---|---|
| Enzo Kiyoshi | 10738185 | Requisitos e casos de uso |
| Pedro Vaiano | 10736306 | Diagramas e visão sistêmica |
| Vinicius Alves | 10399112 | Segurança e resiliência |

**Professor:** Prof. Dr. Rodrigo Silva
**Turma:** 04D - Manhã

## Tecnologias

| Camada | Escolha |
|---|---|
| Modelagem | UML (casos de uso, estados) e Mermaid |
| Documentação | Markdown versionado no Git |
| Linguagem / framework | A definir na etapa de implementação |
| Banco de dados | A definir |
| Fila de mensagens | A definir |

## Estrutura do repositório

| Caminho | Conteúdo |
|---|---|
| `README.md` | Apresentação do projeto, integrantes, tecnologias e instruções de execução. |
| `.gitignore` | Lista de arquivos que não devem ser versionados, como `.env` e chaves. |
| `.env.example` | Exemplo de variáveis de ambiente, apenas com valores fictícios. |
| `docs/` | Documentação do projeto. |
| `docs/01-requisitos.md` | Requisitos funcionais, não funcionais, regras de negócio e rastreabilidade. |
| `docs/02-casos-de-uso.md` | Atores, casos de uso e descrições expandidas. |
| `docs/03-visao-sistemica.md` | Análise sob a ótica do pensamento sistêmico, com cenários de falha. |
| `docs/diagramas/` | Diagrama de casos de uso (`.png`) e ciclo de vida da ordem. |
| `docs/evidencias/` | Evidências do trabalho: registros de reunião e revisões do grupo. |
| `src/` | Código-fonte, previsto para as próximas etapas. |
```

## Documentação

1. [Requisitos](docs/01-requisitos.md)
2. [Modelo de casos de uso](docs/02-casos-de-uso.md) e [diagrama](docs/diagramas/casos-de-uso.png)
3. [Visão sistêmica](docs/03-visao-sistemica.md)
4. [Ciclo de vida da ordem](docs/diagramas/ciclo-de-vida-ordem.md)
5. [Evidências de trabalho](docs/evidencias/README.md)

```

Nesta etapa o repositório contém a **especificação e a modelagem** do sistema. Ainda não há código executável.

Para consultar o projeto:

1. Leia os requisitos em `docs/01-requisitos.md`.
2. Veja os atores e casos de uso em `docs/02-casos-de-uso.md`.
3. Veja o diagrama em `docs/diagramas/casos-de-uso.png`.

Quando houver implementação, o procedimento será copiar `.env.example` para `.env`, preencher os valores e executar o sistema conforme as instruções que serão adicionadas aqui.

## Segurança do repositório

- **Nunca** versionar senhas, tokens, chaves de API, certificados ou dados reais. O `.gitignore` bloqueia `.env` e arquivos de chaves.
- O arquivo `.env.example` contém apenas valores fictícios.
- Se um segredo for commitado por engano, considere-o comprometido: troque-o imediatamente e remova-o do histórico.
