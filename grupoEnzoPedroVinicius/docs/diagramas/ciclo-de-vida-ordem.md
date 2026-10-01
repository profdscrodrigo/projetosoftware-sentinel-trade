# Ciclo de vida da ordem (diagrama de estados)

```mermaid
stateDiagram-v2
    [*] --> Criada : investidor confirma (CSU04)
    Criada --> Rejeitada : validação reprovada (RN01 a RN07)
    Criada --> Validada : validação aprovada
    Validada --> Enviada : transmitida à Bolsa
    Enviada --> ParcialmenteExecutada : execução parcial
    Enviada --> Executada : execução total
    Enviada --> Rejeitada : Bolsa rejeita
    Enviada --> Falha : time-out ou erro de integração
    Falha --> Enviada : reprocessamento (CSU18)
    Falha --> IntervencaoManual : limite de tentativas
    ParcialmenteExecutada --> Executada : execução do restante
    Validada --> Cancelada : cancelamento (CSU06)
    Enviada --> Cancelada : cancelamento confirmado
    ParcialmenteExecutada --> Cancelada : cancela o restante
    Executada --> [*]
    Rejeitada --> [*]
    Cancelada --> [*]
    IntervencaoManual --> [*]
```

Estados em que a ordem pode ser **alterada ou cancelada** (RN09): Validada, Enviada e Parcialmente Executada.
Estados finais: Executada, Rejeitada, Cancelada e Intervenção Manual.
