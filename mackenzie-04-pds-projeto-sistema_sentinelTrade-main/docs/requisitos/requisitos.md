### Requisitos funcionais e não funcionais



> Requisitos funcionais:

| ID | RF | Descrição |
| :--- | :--- | :--- |
| RF-01 | Autenticação do usuário com MFA | Permitir o acesso de investidores autorizados por autenticação multifator. |
| RF-02 | Gerenciar investidores | Cadastrar, consultar e atualizar os dados dos investidores. |
| RF-03 | Gerenciamento de contas | Cadastrar e consultar as contas vinculadas aos investidores. |
| RF-04 | Gerenciamento de carteiras e ativos | Manter as carteiras dos investidores e os ativos presentes em cada um. |
| RF-05 | Gerenciar limites financeiros | Controlar os limites financeiros e de risco associados as contas. |
| RF-06 | Cotações | Receber e disponibilizar cotações atualizadas dos ativos por meio de um provedor externo. |
| RF-07 | Gerenciar ordens | Permitir criar, consultar e cancelar ordens de compra e venda. |
| RF-08 | Validação de ordens | Verificar saldo, posição, limites de risco e condições do mercado antes do envio. |
| RF-09 | Integração com Bolsa/Corretora simulada | Enviar as ordens aprovadas para a bolsa/corretora e receber seus resultados. |
| RF-10 | Acompanhar ciclo de vida das ordens | Acompanhar o status das ordens desde a criação até a execução, rejeição, cancelamento ou falha. |
| RF-11 | Registro de logs de auditoria | Registrar as operações realizadas em logs de auditoria imutáveis. |
| RF-12 | Notificação investidores | Informar investidores sobre execução, rejeição, cancelamento ou falha de uma ordem. |
| RF-13 | Recuperar operações | Permitir a recuperação das operações após falhas ou indisponibilidade. |
| RF-14 | Prevenção de duplicidade | Impedir que uma mesma ordem seja processada ou transmitida mais de uma vez. |

> Requisitos Não-funcionais:

| ID | RNF | Descrição |
| ----------- | ----------- | ----------- |
| RNF-01 | Segurança        | Garantir a proteção dos dados e o acesso apenas a usuários autorizados.    |
| RNF-02 | Rastreabilidade  | Permitir identificar o usuário, a ação realizada, a ordem envolvida e o momento da operação.    |
| RNF-03 | Integridade      | Manter os dados de contas, carteiras e operações consistentes protegidos contra alterações indevidas.      |
| RNF-04 | Disponibilidade  | Manter o sistema disponível mesmo diante de falhas em componentes externos.    |
| RNF-05 | Desempenho       | Processar operações rapidamente e disponibilizar cotações em tempo quase real.      |
| RNF-06 | Confiabilidade   | Garantir que as ordens sejam processadas corretamente e de forma consistente.    |
| RNF-07 | Auditabilidade   | Manter registros confiáveis e imutáveis para consulta e verificações das operações.     |