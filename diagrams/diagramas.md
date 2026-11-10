# Diagramas do SentinelTrade

- **Casos de uso (UMLsec):** imagem em [`../docs/uml/diagrama-casos-de-uso.png`](../docs/uml/diagrama-casos-de-uso.png), código PlantUML em [`diagrama-casos-de-uso.puml`](diagrama-casos-de-uso.puml).
- Os diagramas abaixo usam **Mermaid**

## 1. Estados da Ordem
```mermaid
stateDiagram-v2
    [*] --> CRIADA
    CRIADA --> VALIDADA : passou nas verificações
    CRIADA --> REJEITADA : sem saldo, sem posição, limite ou mercado fechado
    VALIDADA --> ENVIADA : mandada para a bolsa
    VALIDADA --> CANCELADA
    ENVIADA --> EXECUTADA : bolsa executou
    ENVIADA --> PARCIALMENTE_EXECUTADA : bolsa executou uma parte
    ENVIADA --> REJEITADA : bolsa recusou
    ENVIADA --> FALHA : bolsa fora do ar
    PARCIALMENTE_EXECUTADA --> EXECUTADA
    PARCIALMENTE_EXECUTADA --> CANCELADA
    FALHA --> ENVIADA : admin reenvia
    FALHA --> CANCELADA : investidor cancela
    EXECUTADA --> [*]
    REJEITADA --> [*]
    CANCELADA --> [*]
```

Código: `TRANSICOES` em `src/sentineltrade/dominio.py`. EXECUTADA, REJEITADA e CANCELADA não mudam mais (RN-04).

## 2. Sequência — Enviar Ordem de Compra (UC-05)
```mermaid
sequenceDiagram
    actor I as Investidor
    participant M as API (FastAPI)
    participant S as ServicoOrdens
    participant DB as Banco
    participant B as Bolsa Simulada

    I->>M: POST /ordens (ativo, quantidade, preço)
    M->>M: Pydantic valida os dados
    M->>S: enviar(...)
    S->>DB: já existe ordem com essa chave?
    DB-->>S: não
    S->>DB: grava ordem CRIADA + auditoria
    S->>S: verifica saldo, limite e mercado
    S->>DB: ordem VALIDADA
    S->>DB: ordem ENVIADA
    loop até 3 tentativas
        S->>B: enviar(ordem)
    end
    alt bolsa respondeu
        B-->>S: executada
        S->>DB: debita saldo, soma na carteira, ordem EXECUTADA
    else bolsa fora do ar
        S->>DB: ordem FALHA (carteira não muda)
    end
    S->>DB: notificação para o investidor
    S-->>M: ordem com o status final
    M-->>I: mostra o resultado
```

## 3. Classes
```mermaid
classDiagram
    class Usuario {
        <<abstract>>
        +id
        +nome
        +email
        -__senha_hash
        +bloqueado
        +perfil()*
        +permissoes()*
        +pode(acao) bool
    }
    Usuario <|-- Investidor
    Usuario <|-- Administrador
    Usuario <|-- AnalistaRisco

    class Ordem {
        <<abstract>>
        +id
        +ticker
        +quantidade
        +preco
        +chave
        -__status
        +mudar_status(novo, motivo)
        +pode_cancelar() bool
        +verificar(conta, posicao)*
        +executar(conta, posicao)*
    }
    Ordem <|-- OrdemCompra
    Ordem <|-- OrdemVenda

    class Conta {
        -__saldo
        +limite_por_ordem
        +debitar(valor)
        +creditar(valor)
    }
    class Posicao {
        +ticker
        -__quantidade
        +preco_medio
        +adicionar(qtd, preco)
        +remover(qtd)
    }

    class BolsaBase {
        <<interface>>
        +enviar(ordem, preco_referencia)*
    }
    BolsaBase <|.. BolsaSimulada

    class ServicoOrdens {
        +enviar(...)
        +consultar(...)
        +alterar(...)
        +cancelar(...)
        +reenviar_falhas(admin)
    }
    ServicoOrdens --> Ordem
    ServicoOrdens --> BolsaBase
    Investidor "1" --> "1" Conta
    Investidor "1" --> "*" Posicao
    Investidor "1" --> "*" Ordem
```

## 4. Banco de Dados (PostgreSQL)
Script: [`database/schema.sql`](../database/schema.sql) · Dados de exemplo: [`database/seed.sql`](../database/seed.sql)

```mermaid
erDiagram
    usuarios ||--o| contas : tem
    usuarios ||--o{ posicoes : tem
    usuarios ||--o{ ordens : envia
    usuarios ||--o{ notificacoes : recebe
    ativos ||--o{ posicoes : ""
    ativos ||--o{ ordens : ""
    ordens ||--o{ historico_ordens : "mudanças de status"

    usuarios {
        int id PK
        varchar nome
        varchar email UK
        varchar senha_hash
        varchar perfil
        boolean bloqueado
    }
    contas {
        int id PK
        int usuario_id FK
        numeric saldo
        numeric limite_por_ordem
    }
    ativos {
        varchar ticker PK
        varchar nome
        varchar tipo
        numeric preco_referencia
        numeric ultimo_preco
        boolean ativo
    }
    posicoes {
        int id PK
        int usuario_id FK
        varchar ticker FK
        int quantidade
        numeric preco_medio
    }
    ordens {
        int id PK
        varchar chave_idempotencia UK
        int usuario_id FK
        varchar ticker FK
        varchar tipo
        int quantidade
        numeric preco
        varchar status
    }
    historico_ordens {
        int id PK
        int ordem_id FK
        varchar status
        timestamp data_hora
    }
    auditoria {
        int id PK
        int usuario_id
        varchar acao
        varchar detalhes
        timestamp data_hora
    }
    notificacoes {
        int id PK
        int usuario_id FK
        varchar mensagem
    }
    parametros {
        varchar chave PK
        varchar valor
    }
```

- `chave_idempotencia` é `UNIQUE`: o banco não deixa gravar a mesma ordem duas vezes.
- A tabela `auditoria` tem uma trigger que impede `UPDATE` e `DELETE`.
- Dinheiro usa `NUMERIC(15,2)`.