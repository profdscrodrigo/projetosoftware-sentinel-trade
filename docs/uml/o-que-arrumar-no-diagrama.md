Este passo a passo usa o draw.io (app.diagrams.net), que é o que você já usou para o diagrama original.

## Preparação

1. Abra o draw.io e escolha **Arquivo → Abrir** para carregar o diagrama atual. Se ele estiver salvo no Google Drive ou no computador, selecione a origem.
2. Ative a biblioteca UML: clique em **+ Mais formas** (ou *More Shapes*) no canto inferior esquerdo, marque **UML** e clique em **Aplicar**.
3. Confira que a biblioteca mostra as formas **Actor** (boneco) e **Use Case** (elipse).

## Passo 1: Ajustar os textos

1. Dê dois cliques em **Sentinel/Trade** e troque para **SentinelTrade**.
2. Dê dois cliques em **Receber cotações do provedor extremo** e troque para **Receber cotações do provedor externo**.
3. Dê dois cliques no título do diagrama, se quiser atualizá-lo.

## Passo 2: Adicionar os atores faltantes

1. Arraste a forma **Actor** para fora da área do sistema (fora do retângulo) e escreva **Administrador**.
2. Repita para **Analista de Risco**.
3. Adicione **Serviço de Notificação** e **Serviço de Auditoria**. Para atores de sistema, você pode usar a mesma forma de boneco ou uma caixa com o texto "<<sistema>>" acima do nome.
4. Organize os atores do lado direito, perto da Bolsa/Corretora e do Provedor de Cotações.

## Passo 3: Separar "Enviar ordem"

1. Dê dois cliques em **Enviar ordem** e troque o texto para **Enviar ordem de compra**.
2. Duplique a elipse: selecione-a, pressione **Ctrl+C** e depois **Ctrl+V**.
3. Mude o texto da cópia para **Enviar ordem de venda** e posicione-a logo abaixo.

## Passo 4: Adicionar os casos de uso faltantes

Use a forma **Use Case** (elipse) para cada item abaixo:

1. **Validar ordem**: posicione entre as duas ordens e as verificações.
2. **Gerenciar ativos**: perto do Administrador.
3. **Gerenciar limites de risco**: perto do Analista de Risco.
4. **Confirmar operação acima do limite**: ao lado da compra.

## Passo 5: Ajustar as verificações

As verificações já existentes (Validar saldo, Verificar posição em carteira, Verificar limite de risco, Verificar situação do mercado, Verificar duplicidade) devem ficar ligadas a **Validar ordem**, e não diretamente às ordens.

1. Apague as setas que ligam **Enviar ordem de compra** e **Enviar ordem de venda** às verificações.
2. Ligue **Validar ordem** a cada verificação.
3. Ligue compra e venda a **Validar ordem**.

## Passo 6: Criar as setas `<<include>>`

Para criar uma seta de inclusão:

1. Clique na borda de um caso de uso e arraste até o outro. Solte o mouse sobre a forma de destino.
2. Com a seta selecionada, abra o menu de estilo (ícone de balde de tinta) e em **Line** marque **Dashed** (pontilhada).
3. Em **Arrow**, escolha a ponta aberta (seta simples, não preenchida).
4. Para escrever o rótulo, dê dois cliques na seta e digite **<<include>>**. Use o texto exatamente assim, com os sinais de maior e menor.

Ligações que precisam de `<<include>>`:
- Enviar ordem de compra → Validar ordem
- Enviar ordem de venda → Validar ordem
- Validar ordem → Verificar saldo ou posição
- Validar ordem → Verificar limite de risco
- Validar ordem → Verificar situação do mercado
- Validar ordem → Verificar duplicidade
- Enviar ordem de compra → Registrar evento de auditoria
- Enviar ordem de venda → Registrar evento de auditoria
- Enviar ordem de compra → Notificar investidor
- Enviar ordem de venda → Notificar investidor

## Passo 7: Criar a seta `<<extend>>`

1. Crie uma seta tracejada da **Confirmar operação acima do limite** para **Enviar ordem de compra**. A ponta deve ficar no caso de uso base (compra).
2. Escreva o rótulo **<<extend>>**.

A diferença para `<<include>>` é o sentido: a seta de extend aponta do caso opcional para o caso base.

## Passo 8: Ligar atores aos casos de uso

Ligações sólidas, sem rótulo:
- Investidor → Autenticar, Consultar cotações, Consultar saldo, Consultar carteira, Enviar ordem de compra, Enviar ordem de venda, Cancelar ordem, Acompanhar ordem, Consultar histórico
- Administrador → Gerenciar ativos
- Analista de Risco → Gerenciar limites de risco
- Provedor de Cotações → Receber cotações do provedor
- Bolsa/Corretora Simulada → Transmitir ordem à Bolsa/Corretora e Solicitar cancelamento
- Serviço de Notificação → Notificar investidor
- Serviço de Auditoria → Registrar evento de auditoria

Para estas ligações, use seta sólida (padrão do draw.io, sem tracejado).

## Passo 9: Conferir

Confira o diagrama:
- Todo caso de uso tem pelo menos um ator ou outro caso de uso ligado a ele.
- Todas as setas de `<<include>>` e `<<extend>>` têm o rótulo correto e estão pontilhadas.
- Os textos estão escritos igual aos da especificação (por exemplo, "Enviar ordem de compra", não "Enviar compra").

## Passo 10: Exportar

1. Clique em **Arquivo → Exportar como → PNG**.
2. Em opções, marque **Transparent Background** se quiser fundo transparente, ou deixe o fundo branco.
3. Aumente a qualidade para **200%** ou mais.
4. Salve o arquivo como `diagrama-casos-de-uso.png`, dentro de `docs/uml/` no repositório.

## Alternativa mais rápida: PlantUML

Se a edição no draw.io ficar trabalhosa, use o código que deixei no arquivo `diagrama-casos-de-uso.puml`:

1. Abra [plantuml.com/plantuml](https://www.plantuml.com/plantuml).
2. Apague o conteúdo do editor e cole o conteúdo do arquivo.
3. Clique em **Submit** e depois em **PNG** para baixar a imagem.
4. Salve como `docs/uml/diagrama-casos-de-uso.png`.

Nesse caso, o PNG é gerado automaticamente com as setas `<<include>>` e `<<extend>>` já no lugar certo.
