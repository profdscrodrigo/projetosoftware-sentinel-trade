## contexto do sistema

    A Orion Capital é uma corretora fictícia que opera uma plataforma digital
    de negociação de ações, ETFs e fundos imobiliários. Hoje, suas operações 
    dependem de sistemas pouco integrados, o que dificulta a rastreabilidade 
    das ordens, o controle de risco e a auditoria.

    O SentinelTrade é a plataforma de trade financeiro de alta criticidade proposta 
    para resolver esse problema. Nela, investidores   autorizados acompanham cotações, 
    mantêm sua carteira de ativos, enviam ordens de compra e venda e consultam o histórico 
    de operações. Decisões erradas, atrasadas ou não auditáveis podem gerar impacto 
    financeiro, regulatório e reputacional, por isso o sistema deve ser distribuído, 
    seguro, escalável e tolerante a falhas.

    O acesso é protegido por autenticação com múltiplos fatores (MFA). 
    Antes de transmitir qualquer ordem, o sistema valida saldo, posição em carteira, limite 
    de risco e situação do mercado. Em seguida, a ordem é enviada a uma Bolsa/Corretora 
    simulada, e seu ciclo de vida é acompanhado até a execução, rejeição, cancelamento ou falha. 
    O investidor é notificado sobre cada desfecho.

    Todas as operações relevantes geram logs de auditoria imutáveis, consultados pelo administrador. 
    O sistema também conta com   mecanismos de recuperação, indisponibilidade controlada e prevenção 
    de duplicidade de ordens.

    O SentinelTrade interage com o Investidor, o Administrador, a Bolsa/Corretora simulada, um provedor 
    externo de cotações e um serviço de notificação. O projeto opera apenas com ativos, contas e cotações 
    simulados, sem integração com bolsa real nem uso de dinheiro real.
