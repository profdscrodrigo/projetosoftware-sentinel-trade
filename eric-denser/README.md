# SentinelTrade
### Projeto da disciplina Projeto-De-Software
Integrantes: 
Eric Denser Alencar - RA 10737272
Lucas Borsoi - RA 10737271
Pedro Bartucci Branco - RA 10737920


## Contexto do problema 

A corretora fictícia Orion Capital opera uma plataforma digital de negociação de ações, ETFs e fundos imobiliários. Atualmente, suas operações dependem de sistemas pouco integrados, dificultando a rastreabilidade das ordens, o controle de risco e a auditoria.

A Orion Capital contratou sua equipe para especificar e modelar o SentinelTrade, uma plataforma que deverá:

    autenticar usuários com MFA;
    manter dados de investidores, contas, carteiras, ativos e limites financeiros;
    receber cotações em tempo quase real por meio de um provedor externo;
    permitir ordens de compra, venda, cancelamento e consulta;
    validar saldo, posição em carteira, limite de risco e situação do mercado antes de transmitir a ordem;
    integrar-se a uma Bolsa/Corretora simulada;
    acompanhar o ciclo de vida das ordens;
    registrar logs de auditoria imutáveis;
    notificar o investidor sobre execução, rejeição, cancelamento ou falha;
    operar com mecanismos de recuperação, indisponibilidade controlada e prevenção de duplicidade de ordens.

Não é necessário integrar com uma bolsa real ou utilizar dinheiro real. O projeto deverá operar com ativos, contas e cotações simuladas.

