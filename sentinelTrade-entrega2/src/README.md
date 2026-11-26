# Código-fonte (src/)

Código da aplicação em **Java 17 + Spring Boot**. Cada pasta é uma camada (separação entre interface, regras, banco e integração externa):

```
src/main/
├── java/br/mackenzie/sentineltrade/
│   ├── SentinelTradeApplication.java   ← execute esta classe para abrir o site
│   ├── controller/      telas: recebem o que o usuário envia (InvestidorController, AdminController...)
│   │   └── form/        formulários com as regras de validação dos campos
│   ├── service/         regras de negócio (OrdemService, ContaService, MercadoService)
│   │   └── regras/      uma classe por regra de validação (saldo, posição, limites...)
│   ├── seguranca/       login com senha + MFA, perfis e proteções (SegurancaConfig)
│   ├── auditoria/       registro de auditoria
│   ├── notificacao/     avisos ao investidor
│   ├── integracao/      Bolsa simulada (interface BolsaGateway)
│   ├── repository/      acesso ao banco com SQL parametrizado
│   └── model/           classes do domínio (Usuario, Investidor, Ordem, Ativo...)
└── resources/
    ├── application.properties   configuração
    ├── templates/               telas HTML (Thymeleaf)
    └── static/css/estilo.css    visual do site
```

## Onde estão os conceitos de orientação a objetos

| Conceito | Exemplo no código |
| :--- | :--- |
| Encapsulamento | `Ordem`: atributos privados; o status só muda por `mudarStatus()`, que respeita as regras |
| Abstração / classe abstrata | `Usuario` é abstrata, com `getPerfil()` e `getPaginaInicial()` abstratos |
| Herança e polimorfismo | `Investidor`, `Administrador` e `AnalistaRisco` herdam de `Usuario` |
| Interfaces | `RegraValidacao` (6 regras implementam) e `BolsaGateway` (`BolsaSimulada` implementa) |
| Tratamento seguro de exceções | `RegraNegocioException` (mensagem amigável) e `TratadorDeErros` (nunca mostra detalhes internos) |
