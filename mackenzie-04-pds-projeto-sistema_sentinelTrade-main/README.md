# Sistema SentinelTrade
Gustavo Leal Gomes - RA: 10738069

Manuela Atanes Garabedian - RA: 10735908

## Visão do Projeto



O SentinelTrade é um sistema de negociação financeira de alta criticidade desenvolvido para a Orion Capital, uma corretora de investimentos fictícia. O objetivo é permitir que investidores consultem cotações, enviem e acompanhem ordens de compra e venda de ativos com segurança, rastreabilidade e confiabilidade.



Todo o ambiente é simulado: os ativos, as contas, as cotações e a integração com a bolsa/corretora são fictícios. Nenhuma operação envolve bolsa real ou dinheiro real.



### Sobre a Orion Capital



A Orion Capital é uma corretora fictícia que precisa de uma plataforma para intermediar operações de seus investidores com o mercado. Por lidar com operações financeiras, a corretora exige que o sistema garanta autenticação forte, controle de limites, prevenção de erros e registro completo de todas as ações para fins de auditoria.



### Estrutura



```
projeto/
├── docs/
│   ├── casos-de-uso/
│   │   └── casos-de-uso.md
│   │
│   ├── diagrama/uml.png
│   │
│   └── requisitos/
│       └── requisitos.md
|
├── .env.example
├── .gitignore
└── README.md
```

| Documentos | Descrição |
|---|---|
| [Requisitos](./docs/requisitos/) | Requisitos funcionais: 14 e Requisitos não funcionais: 7 |
| [Diagramas](./docs/diagrama/) | Diagrama UML representando a estrutura, o comportamento e os componentes do sistema. |
| [Casos de Uso](./docs/casos-de-uso/) | Interações entre os usuários e o sistema. |



### Atores do sistema



| Ator | Papel |
|------|-------|
| **Investidor** | Consulta cotações, envia ordens e acompanha seu andamento |
| **Administrador** | Gerencia investidores, contas, carteiras, limites e recuperação de operações |
| **Bolsa/Corretora simulada** | Sistema externo que fornece cotações e executa as ordens |



### Tecnologias
- DRAW.IO

### Diagrama UML
<img width="1186" height="924" alt="SentinelTrade_casos_de_uso drawio" src="https://github.com/user-attachments/assets/f6cbb82c-4bb0-4323-afd7-496c883d0ca3" />
