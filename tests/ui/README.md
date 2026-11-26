# Teste de interface em navegador (opcional)

`teste-interface.js` dirige a interface web com um Chromium headless (Playwright) e vigia erros de console e violações da CSP.
Roteiro: login com MFA → compra → confirmação de alto valor → alteração → cancelamento → ordem a mercado → administrador → auditoria.

```bash
npm install playwright && npx playwright install chromium
export SENHA="Aa1!$(cat /proc/sys/kernel/random/uuid)"                  # senha só para este teste
SENTINEL_MARKET_ALWAYS_OPEN=true WEB_PORT=8099 DEMO_PASSWORD_INVESTIDOR_A="$SENHA" DEMO_PASSWORD_ADMIN="$SENHA" \
  java -cp out:src/main/resources br.orion.sentineltrade.Main --web &
echo "$SENHA" > /tmp/senha_ana.txt && node tests/ui/teste-interface.js
```
Saída registrada em [`docs/evidencias/teste-interface-web.txt`](../../docs/evidencias/teste-interface-web.txt).
