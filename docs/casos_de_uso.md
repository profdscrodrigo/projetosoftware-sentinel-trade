## 3. Validar Saldo e Risco
1. Ator Primário: Sistema (incluído por "Enviar Ordem")
2. Pré-condições: Ordem recebida com ativo, quantidade e preço.

### Fluxo Principal:
1. O sistema consulta o saldo e a posição em carteira do investidor.
2. O sistema verifica o limite de risco e a situação do mercado.
3. O sistema confirma a validação e devolve o controle ao caso de uso que o chamou.

### Fluxos Alternativos:
1. Saldo ou posição insuficiente: a validação falha e a ordem é rejeitada.
2. Limite de risco ultrapassado: a validação falha e a ordem é rejeitada.
3. Mercado fechado: a ordem é rejeitada com mensagem informativa.

## 4. Registrar Log de Auditoria Imutável
1. Ator Primário: Sistema (incluído por "Enviar Ordem" e "Consultar Logs")
2. Pré-condições: Evento relevante ocorrido (ordem criada, enviada, executada, rejeitada ou cancelada).

### Fluxo Principal:
1. O sistema monta o registro (usuário, ação, data/hora, ID da ordem).
2. O sistema grava o registro de forma append-only, com hash do registro anterior.
3. O sistema confirma a gravação.

### Fluxos Alternativos:
1. Falha na gravação: a operação é abortada e o erro é alertado ao administrador.

## 5. Consultar Logs de Auditoria
1. Ator Primário: Administrador
2. Pré-condições: Administrador autenticado via MFA.

### Fluxo Principal:
1. O Administrador acessa a tela de auditoria e informa os filtros (período, usuário, ordem).
2. O sistema busca os registros e os exibe, somente leitura.
3. O sistema registra a própria consulta no log de auditoria.

###  Fluxos Alternativos:
1. Nenhum registro encontrado: o sistema informa que não há resultados.
2. Acesso não autorizado: o sistema nega o acesso e registra a tentativa.
