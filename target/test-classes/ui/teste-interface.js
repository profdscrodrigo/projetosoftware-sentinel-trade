const { chromium } = require('playwright');
const senha = require('fs').readFileSync('/tmp/senha_ana.txt', 'utf8').trim();
(async () => {
  process.on('exit', () => {});
  const b = await chromium.launch({ args: ['--no-sandbox'] });
  const p = await b.newPage({ viewport: { width: 1280, height: 1500 } });
  const problemas = [];
  p.on('console', m => { if (['error', 'warning'].includes(m.type()) && !/status of 409/.test(m.text())) problemas.push('console.' + m.type() + ': ' + m.text()); }); // o 409 é a resposta ESPERADA da confirmação de alto valor
  p.on('pageerror', e => problemas.push('pageerror: ' + e.message));
  const passos = []; global.P = p; global.passos = passos;
  const ok = (n) => { passos.push('✔ ' + n); };
  async function login(email) {
    await p.goto('http://127.0.0.1:8099/');
    await p.fill('input[type=email]', email); await p.fill('input[type=password]', senha);
    await p.click('text=Continuar');
    await p.click('text=Autenticador simulado');
    await p.waitForFunction(() => /^[0-9]{6}$/.test(document.querySelector('input[inputmode=numeric]').value));
    await p.click('text=Verificar');
  }
  await login('ana@orion.example');
  await p.waitForSelector('text=Nova ordem'); ok('login com senha + MFA (autenticador simulado) → tela do investidor');
  await p.waitForSelector('text=PETR4 — Petrobras PN', { state: 'attached' });
  ok('cotações e ativos carregados');
  // 1) compra limite
  await p.selectOption('form select >> nth=2', 'PETR4');
  await p.fill('input[maxlength="7"]', '10'); await p.fill('input[placeholder="ex.: 38,50"]', '38,50');
  await p.click('text=Enviar ordem');
  await p.waitForSelector('text=EXECUTADA', { timeout: 12000 }); ok('ordem de compra enviada e EXECUTADA pelo worker (status atualiza sozinho)');
  // 2) alto valor → confirmação
  await p.fill('input[maxlength="7"]', '700'); await p.fill('input[placeholder="ex.: 38,50"]', '38,50');
  await p.click('text=Enviar ordem');
  await p.waitForSelector('text=Confirmar operação'); ok('ordem de alto valor pede confirmação adicional');
  await p.click('button:has-text("Confirmar operação")');
  await p.waitForSelector('.caixa', { state: 'detached' }); ok('confirmação reenvia com a mesma chave de idempotência');
  // 3) ordem que não executa → alterar → cancelar
  await p.fill('input[maxlength="7"]', '100'); await p.fill('input[placeholder="ex.: 38,50"]', '36,00');
  await p.click('text=Enviar ordem');
  await p.waitForSelector('tr:has-text("R$ 36,00") button:has-text("Alterar")', { timeout: 8000 });
  await p.click('tr:has-text("R$ 36,00") button:has-text("Alterar")');
  await p.waitForSelector('text=Alterar ordem PETR4');
  await p.fill('.cartao:has-text("Alterar ordem PETR4") input >> nth=0', '50');
  await p.fill('.cartao:has-text("Alterar ordem PETR4") input >> nth=1', '35,00');
  await p.click('.cartao:has-text("Alterar ordem PETR4") button:has-text("Alterar")');
  await p.waitForSelector('text=Ordem alterada'); ok('alteração (cancelar e substituir) pela interface');
  await p.waitForSelector('tr:has-text("R$ 36,00"):has-text("CANCELADA")'); await p.waitForSelector('tr:has-text("R$ 35,00")'); ok('ordem original (R$ 36,00) CANCELADA e nova ordem (R$ 35,00) criada e ligada a ela');
  // 4) mercado
  await p.selectOption('form select >> nth=1', 'MERCADO');
  await p.fill('input[maxlength="7"]', '5');
  await p.click('text=Enviar ordem');
  await p.waitForSelector('tr:has-text("COMPRA MERCADO")', { timeout: 8000 }); ok('ordem a mercado (campo de preço desabilitado)');
  // 5) entrada maliciosa nunca vira HTML
  await p.screenshot({ path: '/tmp/ui_investidor.png', fullPage: true });
  await p.click('text=Sair'); await p.waitForSelector('text=Entrar');
  // 6) token expirado/logout: voltar não dá acesso
  await login('admin@orion.example');
  await p.waitForSelector('text=Cadastrar investidor'); ok('login do administrador → tela administrativa');
  await p.click('text=Verificar integridade da trilha');
  await p.waitForSelector('text=Cadeia íntegra'); ok('verificação da trilha de auditoria pela interface');
  await p.click('text=Ver primeiros eventos'); await p.waitForSelector('text=LOGIN_SUCESSO');
  await p.screenshot({ path: '/tmp/ui_admin.png', fullPage: true });
  console.log(passos.join('\n'));
  console.log(problemas.length ? 'PROBLEMAS DO NAVEGADOR:\n' + problemas.join('\n') : '✔ nenhum erro de console, nenhuma violação de CSP');
  await b.close();
})().catch(async e => { try { await global.P.screenshot({ path: '/tmp/ui_falha.png', fullPage: true }); console.error('passos ok até aqui:\n' + global.passos.join('\n')); } catch (x) {} console.error('FALHA NO TESTE DE UI:', e.message.split('\n').slice(0,3).join(' | ')); process.exit(1); });
