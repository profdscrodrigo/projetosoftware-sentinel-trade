# Especificação de Casos de Uso

Este documento detalha os fluxos de eventos para os casos de uso do sistema, definindo as interações entre os atores e o sistema, além dos comportamentos esperados em cenários de sucesso e falha.

## 1. Enviar Ordem de Compra/Venda

Ator Primário: Investidor
Atores Secundários: Bolsa Simulada
Precondições: O Investidor deve estar autenticado no sistema via MFA. O mercado simulado deve estar aberto.

## Fluxo Principal:
1. O Investidor seleciona o ativo e opta por enviar uma ordem.
2. O Investidor informa se é compra/venda, a quantidade e o preço.
3. O sistema faz a validação da operação (verifica o saldo e os limites).
4. O sistema registra a ordem como "Pendente" e a envia para a Bolsa Simulada.
5. O sistema atualiza o status na tela do investidor e o caso de uso termina.

## Fluxos Alternativos:
1. Falha na validação: Se o saldo for insuficiente ou o limite for ultrapassado, o sistema rejeita a ordem e notifica o investidor.
2. Falha na comunicação com a bolsa: Se a Bolsa Simulada não responder (time-out), a ordem vai para uma fila específica para nova tentativa assíncrona, com status "Aguardando Envio".
3. Execução/Alteração de Status: O sistema faz uso do "notificar investidor"" para avisar sobre a execução ou falha da ordem.

## 2. Autenticar com MFA
1. Ator Primário: Investidor
2. Pré condições: Usuário cadastrado no sistema.

## Fluxo Principal:
1. O Investidor insere o login e a senha.
2. O sistema valida as credenciais.
3. O sistema solicita o código do token MFA.
4. O Investidor insere o código MFA.
5. O sistema valida o código e liberta o acesso do Investidor ao sistema.

## Fluxos Alternativos:
1. Senha Incorreta: O sistema exibe mensagem de erro e solicita novas credenciais.
2. Código MFA Inválido: O sistema alerta sobre o código errado ou expirado e permite tentar novamente.
3. Tentativas Excedidas: Após 3 tentativas incorretas, o acesso é temporariamente bloqueado por motivos de segurança.
