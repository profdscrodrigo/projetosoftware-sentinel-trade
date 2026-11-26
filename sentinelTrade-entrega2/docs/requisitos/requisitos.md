### Requisitos funcionais e não funcionais

### Atores

| Ator | Responsabilidade |
| :--- | :--- |
| Investidor | Consulta cotações, carteira, saldo, histórico e envia ou cancela ordens. |
| Administrador | Gerencia usuários (investidores), contas, ativos, limites e parâmetros operacionais. |
| Analista de Risco | Consulta e ajusta limites de risco e acompanha ordens bloqueadas ou rejeitadas. |
| Corretora/Bolsa Simulada | Recebe ordens válidas e retorna informações sobre execução, rejeição ou falha. |
| Provedor de Cotações | Fornece valores simulados e atualizados dos ativos. |
| Serviço de Notificação | Informa o investidor sobre eventos relevantes. |
| Serviço de Auditoria | Mantém o registro de ações, decisões e alterações de estado. |

> Requisitos funcionais:

| ID | RF | Descrição | Critério de aceitação |
| :--- | :--- | :--- | :--- |
| RF-01 | Autenticação do usuário com MFA | Permitir o acesso de investidores autorizados por autenticação multifator. | Usuário válido acessa o sistema somente após validar o segundo fator (código MFA); usuário ou código inválido é bloqueado. |
| RF-02 | Gerenciar investidores | Cadastrar, consultar e atualizar os dados dos investidores. | O administrador cadastra, consulta e atualiza investidores; CPF duplicado ou dados inválidos são recusados. |
| RF-03 | Gerenciamento de contas | Cadastrar e consultar as contas vinculadas aos investidores. | Toda conta fica vinculada a um investidor ativo; não é possível criar conta para investidor inexistente ou inativo. |
| RF-04 | Gerenciamento de carteiras e ativos | Manter as carteiras dos investidores e os ativos presentes em cada uma. | A carteira mostra os ativos de cada conta; nenhum ajuste pode deixar quantidade negativa. |
| RF-05 | Gerenciar limites financeiros | Controlar os limites financeiros e de risco associados às contas. | Os limites (por ordem, diário e de alto risco) ficam salvos por conta e passam a valer nas próximas validações. |
| RF-06 | Cotações | Receber e disponibilizar cotações atualizadas dos ativos por meio de um provedor externo. | O sistema apresenta preço, horário da cotação e variação simulada; se o provedor cair, mostra a última cotação com aviso. |
| RF-07 | Gerenciar ordens | Permitir criar, consultar e cancelar ordens de compra e venda. | A ordem de compra só é aceita com saldo e limite disponíveis; a de venda, com quantidade disponível do ativo; ordens executadas não podem ser canceladas. |
| RF-08 | Validação de ordens | Verificar saldo, posição, limites de risco e condições do mercado antes do envio. | Dados inválidos, saldo insuficiente, posição insuficiente, limite excedido ou mercado fechado impedem o envio. |
| RF-09 | Integração com Bolsa/Corretora simulada | Enviar as ordens aprovadas para a bolsa/corretora e receber seus resultados. | Apenas ordens aprovadas são enviadas à Bolsa simulada, e o resultado (executada, rejeitada ou falha) é registrado na ordem. |
| RF-10 | Acompanhar ciclo de vida das ordens | Acompanhar o status das ordens desde a criação até a execução, rejeição, cancelamento ou falha. | O usuário visualiza o status atualizado de cada ordem e o histórico de mudanças com data e hora. |
| RF-11 | Registro de logs de auditoria | Registrar as operações realizadas em logs de auditoria imutáveis. | Cada criação, validação, rejeição, cancelamento ou execução gera um log que não pode ser alterado ou apagado. |
| RF-12 | Notificação investidores | Informar investidores sobre execução, rejeição, cancelamento ou falha de uma ordem. | Uma notificação é gerada em execução, rejeição, cancelamento ou falha da ordem. |
| RF-13 | Recuperar operações | Permitir a recuperação das operações após falhas ou indisponibilidade. | Ordens com status "Falha" podem ser recuperadas sem serem duplicadas, sincronizando o status real com a Bolsa. |
| RF-14 | Prevenção de duplicidade | Impedir que uma mesma ordem seja processada ou transmitida mais de uma vez. | Reenviar a mesma ordem (mesmo identificador) não cria uma segunda ordem nem uma segunda execução. |
| RF-15 | Consultar saldo, carteira e posição | Permitir que o investidor consulte seu saldo, sua carteira e a posição de cada ativo. | A carteira apresenta ativos, quantidades, preço médio e valor estimado; o saldo disponível é exibido. |
| RF-16 | Consultar histórico de operações | Permitir que o investidor consulte o histórico das suas ordens e operações por período e status. | O histórico lista as ordens do próprio investidor, com filtro por período e status. |
| RF-17 | Gerenciar ativos e parâmetros do sistema | Permitir que o administrador cadastre ativos negociáveis e altere parâmetros operacionais. | Alterações em ativos e parâmetros ficam registradas na auditoria e disponíveis para consulta. |
| RF-18 | Acompanhar ordens bloqueadas ou rejeitadas | Permitir que o analista de risco acompanhe ordens bloqueadas/rejeitadas e ajuste limites de risco. | O analista visualiza as ordens bloqueadas/rejeitadas com o motivo e pode ajustar os limites de risco da conta. |

> Requisitos Não-funcionais:

| ID | RNF | Descrição | Aplicação prática |
| :--- | :--- | :--- | :--- |
| RNF-01 | Segurança | Garantir a proteção dos dados e o acesso apenas a usuários autorizados. | Senhas guardadas com hash, MFA no login, controle de acesso por perfil, validação de entradas e acesso por HTTPS. |
| RNF-02 | Rastreabilidade | Permitir identificar o usuário, a ação realizada, a ordem envolvida e o momento da operação. | Toda ação registra usuário, ação, ordem/entidade e data/hora (tabela auditoria). |
| RNF-03 | Integridade | Manter os dados de contas, carteiras e operações consistentes e protegidos contra alterações indevidas. | Uma ordem precisa ter investidor, ativo, tipo, quantidade e preço válidos; saldo e quantidade nunca ficam negativos. |
| RNF-04 | Disponibilidade | Manter o sistema disponível mesmo diante de falhas em componentes externos. | Falha na Bolsa ou no provedor de cotações não corrompe ordens nem posições (ordem vira "Falha" e vai para recuperação). |
| RNF-05 | Desempenho | Processar operações rapidamente e disponibilizar cotações em tempo quase real. | Consultas de carteira e cotações respondem em tempo adequado ao ambiente simulado (índices no banco). |
| RNF-06 | Confiabilidade | Garantir que as ordens sejam processadas corretamente e de forma consistente. | Impede duplicidade de ordens em reenvios ou falhas de comunicação (chave de idempotência). |
| RNF-07 | Auditabilidade | Manter registros confiáveis e imutáveis para consulta e verificações das operações. | Eventos críticos geram registros rastreáveis e protegidos contra alteração (gatilhos bloqueiam UPDATE/DELETE). |
| RNF-08 | Manutenibilidade | Manter código, documentação, banco de dados e diagramas organizados e versionados no Git. | Estrutura de pastas padronizada, commits claros e documentação/diagramas versionados. |
| RNF-09 | Privacidade | Garantir que os dados do investidor sejam acessíveis somente a ele e a usuários autorizados. | O investidor só consulta as próprias informações; nenhum dado pessoal real é usado (somente dados fictícios). |

> Regras de negócio: [regras-de-negocio.md](./regras-de-negocio.md) · Rastreabilidade: [matriz-rastreabilidade.md](./matriz-rastreabilidade.md)
