'use strict';
/* Front-end do SentinelTrade. Regras de negócio, validação e autorização vivem no servidor; aqui só há apresentação.
   Segurança: o token fica apenas em memória (some ao recarregar); NENHUM dado do servidor é inserido como HTML
   (somente textContent/atributos), e a CSP proíbe scripts inline. */

const estado = { token: null, papel: null, pendente: null, email: null, autenticadorSimulado: false, timer: null, aba: null };
const raiz = document.getElementById('app');

// ---------------------------------------------------------------- utilidades
function el(tag, attrs, ...filhos) {
  const e = document.createElement(tag);
  for (const [k, v] of Object.entries(attrs || {})) {
    if (k === 'class') e.className = v;
    else if (k.startsWith('on')) e.addEventListener(k.slice(2), v);
    else if (v !== false && v != null) e.setAttribute(k, v === true ? '' : v);
  }
  for (const f of filhos.flat()) if (f != null) e.append(f.nodeType ? f : document.createTextNode(String(f)));
  return e;
}
const dinheiro = (v) => v == null ? '—' : Number(v).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
const hora = (iso) => iso ? new Date(iso).toLocaleTimeString('pt-BR') : '—';
const novaChave = () => crypto.randomUUID();

function aviso(texto, tipo) {
  const m = document.getElementById('mensagem');
  m.textContent = texto; m.className = tipo || 'ok'; m.hidden = !texto;
  if (texto && tipo !== 'erro') setTimeout(() => { if (m.textContent === texto) m.hidden = true; }, 6000);
}

async function api(metodo, caminho, corpo) {
  const cab = {};
  if (estado.token) cab['Authorization'] = 'Bearer ' + estado.token;
  if (corpo !== undefined) cab['Content-Type'] = 'application/json';
  let resp;
  try { resp = await fetch('/api' + caminho, { method: metodo, headers: cab, body: corpo === undefined ? undefined : JSON.stringify(corpo) }); }
  catch (e) { throw { codigo: 'REDE', mensagem: 'Sem comunicação com o servidor.' }; }
  let dados = {};
  try { dados = await resp.json(); } catch (e) { /* corpo vazio */ }
  if (!resp.ok) {
    const err = (dados && dados.erro) || { codigo: 'ERRO', mensagem: 'Falha ' + resp.status };
    if (resp.status === 401 && estado.token) { sair(true); }
    throw err;
  }
  return dados;
}

async function acao(fn, sucesso) {
  try { const r = await fn(); if (sucesso) aviso(sucesso); return r; }
  catch (e) { aviso(e.mensagem || 'Falha inesperada', 'erro'); return null; }
}

function tabela(colunas, linhas) {
  return el('table', {}, el('thead', {}, el('tr', {}, colunas.map(c => el('th', { class: c.num ? 'num' : '' }, c.titulo)))),
    el('tbody', {}, linhas.length ? linhas.map(l => el('tr', {}, colunas.map(c => el('td', { class: c.num ? 'num' : '' }, c.valor(l))))) :
      [el('tr', {}, el('td', { colspan: colunas.length, class: 'pequeno' }, 'Nenhum registro.'))]));
}

// ---------------------------------------------------------------- login e MFA
function telaLogin() {
  parar(); estado.token = null; estado.pendente = null;
  document.getElementById('sessao').hidden = true;
  const email = el('input', { type: 'email', autocomplete: 'username', required: true, maxlength: 254 });
  const senha = el('input', { type: 'password', autocomplete: 'current-password', required: true, maxlength: 128 });
  raiz.replaceChildren(el('div', { class: 'cartao login' }, el('h2', {}, 'Entrar'),
    el('form', { onsubmit: async (ev) => {
      ev.preventDefault();
      const r = await acao(() => api('POST', '/auth/login', { email: email.value, senha: senha.value }));
      if (r) { estado.pendente = r.tokenPendente; estado.email = email.value.trim().toLowerCase(); senha.value = ''; telaMfa(); }
    } }, el('label', {}, 'E-mail', email), el('label', {}, 'Senha', senha), el('p', {}, el('button', { type: 'submit' }, 'Continuar')),
      el('p', { class: 'pequeno' }, 'Em seguida será pedido o código de verificação (MFA).'))));
}

function telaMfa() {
  const codigo = el('input', { inputmode: 'numeric', pattern: '[0-9]{6}', maxlength: 6, autocomplete: 'one-time-code', required: true });
  const simulado = estado.autenticadorSimulado ? el('p', {}, el('button', { type: 'button', class: 'sec', onclick: async () => {
    const r = await acao(() => api('GET', '/demo/autenticador?email=' + encodeURIComponent(estado.email)));
    if (r) codigo.value = r.codigo;
  } }, 'Autenticador simulado (demonstração)')) : null;
  raiz.replaceChildren(el('div', { class: 'cartao login' }, el('h2', {}, 'Verificação em duas etapas'),
    el('form', { onsubmit: async (ev) => {
      ev.preventDefault();
      const r = await acao(() => api('POST', '/auth/mfa', { tokenPendente: estado.pendente, codigo: codigo.value }));
      if (r) { estado.token = r.token; estado.papel = r.papel; estado.pendente = null; entrar(); }
    } }, el('label', {}, 'Código de 6 dígitos', codigo), simulado,
      el('p', {}, el('button', { type: 'submit' }, 'Verificar'), ' ', el('button', { type: 'button', class: 'sec', onclick: telaLogin }, 'Voltar')))));
  codigo.focus();
}

function entrar() {
  document.getElementById('sessao').hidden = false;
  document.getElementById('quem').textContent = estado.email + ' · ' + estado.papel;
  aviso('Autenticado com sucesso.');
  if (estado.papel === 'INVESTIDOR') telaInvestidor(); else if (estado.papel === 'ADMINISTRADOR') telaAdmin(); else telaRisco();
}

async function sair(expirou) {
  if (!expirou && estado.token) { try { await api('POST', '/auth/logout'); } catch (e) { /* já inválida */ } }
  estado.token = null; telaLogin();
  if (expirou) aviso('Sessão expirada. Entre novamente.', 'erro');
}
document.getElementById('sair').addEventListener('click', () => sair(false));

function parar() { if (estado.timer) { clearInterval(estado.timer); estado.timer = null; } }

// ---------------------------------------------------------------- investidor
function telaInvestidor() {
  const secCarteira = el('div', { class: 'cartao' }), secCot = el('div', { class: 'cartao' }), secOrdens = el('div', { class: 'cartao' }),
        secNotif = el('div', { class: 'cartao' }), secForm = el('div', { class: 'cartao' }), secAlterar = el('div');
  raiz.replaceChildren(el('div', { class: 'grade' }, secForm, secCot), secCarteira, secAlterar, secOrdens, secNotif);
  let ativos = [];

  // --- formulário de ordem
  const lado = el('select', {}, el('option', { value: 'COMPRA' }, 'Compra'), el('option', { value: 'VENDA' }, 'Venda'));
  const tipo = el('select', {}, el('option', { value: 'LIMITE' }, 'A preço limite'), el('option', { value: 'MERCADO' }, 'A mercado'));
  const ticker = el('select', {});
  const qtd = el('input', { inputmode: 'numeric', required: true, maxlength: 7 });
  const preco = el('input', { inputmode: 'decimal', maxlength: 10, placeholder: 'ex.: 38,50' });
  const confirmacao = el('div');
  let pendenteConfirmar = null;
  tipo.addEventListener('change', () => { preco.disabled = tipo.value === 'MERCADO'; if (preco.disabled) preco.value = ''; });
  async function enviar(confirmada) {
    const corpo = pendenteConfirmar ? { ...pendenteConfirmar.corpo, confirmada: true } : {
      lado: lado.value, tipo: tipo.value, ticker: ticker.value, quantidade: qtd.value, chaveIdempotencia: novaChave(), confirmada: false };
    if (!pendenteConfirmar && tipo.value === 'LIMITE') corpo.preco = preco.value;
    try {
      const r = await api('POST', '/ordens', corpo);
      pendenteConfirmar = null; confirmacao.replaceChildren();
      aviso(r.duplicada ? 'Reenvio reconhecido: ordem já existente.' : 'Ordem ' + r.ordem.status + (r.ordem.motivoDescricao ? ' — ' + r.ordem.motivoDescricao : ''),
        r.ordem.status === 'REJEITADA' ? 'erro' : 'ok');
      atualizar();
    } catch (e) {
      if (e.codigo === 'CONFIRMACAO_NECESSARIA') {
        pendenteConfirmar = { corpo };                         // mesma chave de idempotência no reenvio confirmado
        confirmacao.replaceChildren(el('div', { class: 'caixa' }, el('p', {}, e.mensagem),
          el('button', { type: 'button', onclick: () => enviar(true) }, 'Confirmar operação'), ' ',
          el('button', { type: 'button', class: 'sec', onclick: () => { pendenteConfirmar = null; confirmacao.replaceChildren(); } }, 'Cancelar')));
      } else { aviso(e.mensagem || 'Falha', 'erro'); }
    }
  }
  secForm.append(el('h2', {}, 'Nova ordem'), el('form', { onsubmit: (ev) => { ev.preventDefault(); pendenteConfirmar = null; enviar(false); } },
    el('div', { class: 'linha' }, el('label', {}, 'Operação', lado), el('label', {}, 'Execução', tipo)),
    el('div', { class: 'linha' }, el('label', {}, 'Ativo', ticker), el('label', {}, 'Quantidade', qtd), el('label', {}, 'Preço limite (R$)', preco)),
    el('p', {}, el('button', { type: 'submit' }, 'Enviar ordem')),
    el('p', { class: 'pequeno' }, 'Cada envio usa uma chave de idempotência própria: reenvios não duplicam a ordem.')), confirmacao);

  // --- alteração
  function abrirAlteracao(o) {
    const q = el('input', { value: o.quantidade, inputmode: 'numeric', maxlength: 7 });
    const p = el('input', { value: o.tipo === 'MERCADO' ? '' : String(o.precoLimite), inputmode: 'decimal', maxlength: 10, disabled: o.tipo === 'MERCADO' });
    const caixa = el('div');
    let chave = novaChave(), confirmada = false;
    async function enviarAlteracao() {
      try {
        const corpo = { quantidade: q.value, chaveIdempotencia: chave, confirmada };
        if (o.tipo === 'LIMITE') corpo.preco = p.value;
        await api('POST', '/ordens/' + o.id + '/alterar', corpo);
        secAlterar.replaceChildren(); aviso('Ordem alterada (cancelada e substituída por uma nova).'); atualizar();
      } catch (e) {
        if (e.codigo === 'CONFIRMACAO_NECESSARIA') {
          confirmada = false;
          caixa.replaceChildren(el('div', { class: 'caixa' }, el('p', {}, e.mensagem),
            el('button', { type: 'button', onclick: () => { confirmada = true; enviarAlteracao(); } }, 'Confirmar alteração')));
        } else aviso(e.mensagem || 'Falha', 'erro');
      }
    }
    secAlterar.replaceChildren(el('div', { class: 'cartao' }, el('h2', {}, 'Alterar ordem ' + o.ticker + ' (' + o.lado + ')'),
      el('p', { class: 'pequeno' }, 'A alteração cancela a ordem atual e cria uma nova. Se algo impedir a nova ordem, a original permanece intacta.'),
      el('div', { class: 'linha' }, el('label', {}, 'Nova quantidade', q), el('label', {}, o.tipo === 'MERCADO' ? 'Preço (a mercado)' : 'Novo preço limite', p),
        el('button', { type: 'button', onclick: enviarAlteracao }, 'Alterar'), el('button', { type: 'button', class: 'sec', onclick: () => secAlterar.replaceChildren() }, 'Fechar')), caixa));
  }

  async function atualizar() {
    try {
      const [cot, cart, ord, not] = await Promise.all([api('GET', '/cotacoes'), api('GET', '/carteira'), api('GET', '/ordens'), api('GET', '/notificacoes')]);
      if (!ativos.length) {
        const lst = await api('GET', '/ativos'); ativos = lst.ativos;
        ticker.replaceChildren(...ativos.filter(a => a.status === 'ATIVO').map(a => el('option', { value: a.ticker }, a.ticker + ' — ' + a.nome)));
      }
      secCot.replaceChildren(el('h2', {}, 'Cotações'), tabela([
        { titulo: 'Ativo', valor: c => c.ticker }, { titulo: 'Preço', num: true, valor: c => dinheiro(c.preco) },
        { titulo: 'Var. %', num: true, valor: c => c.variacaoPercentual + '%' },
        { titulo: 'Hora', valor: c => hora(c.instante) + (c.desatualizada ? ' (desatualizada)' : '') }], cot.cotacoes));
      secCarteira.replaceChildren(el('h2', {}, 'Saldo e carteira'),
        el('p', {}, 'Disponível: ', el('strong', {}, dinheiro(cart.disponivel)), ' · Bloqueado (reservas): ', el('strong', {}, dinheiro(cart.bloqueado)),
          ' · Patrimônio estimado: ', el('strong', {}, dinheiro(cart.patrimonioEstimado))),
        tabela([{ titulo: 'Ativo', valor: i => i.ticker }, { titulo: 'Qtd.', num: true, valor: i => i.quantidade },
          { titulo: 'Bloqueada', num: true, valor: i => i.quantidadeBloqueada }, { titulo: 'Preço médio', num: true, valor: i => dinheiro(i.precoMedio) },
          { titulo: 'Valor estimado', num: true, valor: i => dinheiro(i.valorEstimado) }], cart.itens));
      secOrdens.replaceChildren(el('h2', {}, 'Histórico de ordens'), tabela([
        { titulo: 'Hora', valor: o => hora(o.criadaEm) }, { titulo: 'Operação', valor: o => o.lado + ' ' + o.tipo }, { titulo: 'Ativo', valor: o => o.ticker },
        { titulo: 'Qtd. (exec.)', num: true, valor: o => o.quantidade + ' (' + o.quantidadeExecutada + ')' },
        { titulo: 'Limite', num: true, valor: o => dinheiro(o.precoLimite) },
        { titulo: 'Status', valor: o => el('span', { class: 'etiqueta ' + o.status }, o.status + (o.motivoDescricao ? ' — ' + o.motivoDescricao : '')) },
        { titulo: '', valor: o => el('span', {}, o.alteravel ? el('button', { type: 'button', class: 'sec', onclick: () => abrirAlteracao(o) }, 'Alterar') : null, ' ',
            o.cancelavel ? el('button', { type: 'button', class: 'perigo', onclick: async () => {
              if (await acao(() => api('POST', '/ordens/' + o.id + '/cancelar'), 'Ordem cancelada.')) atualizar(); } }, 'Cancelar') : null) }], ord.ordens));
      secNotif.replaceChildren(el('h2', {}, 'Notificações'), tabela([{ titulo: 'Hora', valor: n => hora(n.criadaEm) }, { titulo: 'Tipo', valor: n => n.tipo },
        { titulo: 'Mensagem', valor: n => n.mensagem }], not.notificacoes.slice(0, 10)));
    } catch (e) { if (e.codigo !== 'REDE') aviso(e.mensagem, 'erro'); }
  }
  atualizar(); parar(); estado.timer = setInterval(atualizar, 3000);
}

// ---------------------------------------------------------------- administrador
function formulario(titulo, campos, enviar) {
  const inputs = campos.map(c => c.opcoes ? el('select', {}, c.opcoes.map(o => el('option', { value: o }, o))) :
    el('input', { type: c.tipo || 'text', maxlength: c.max || 100, required: true, autocomplete: 'off' }));
  return el('div', { class: 'cartao' }, el('h2', {}, titulo), el('form', { onsubmit: async (ev) => {
    ev.preventDefault();
    const corpo = {}; campos.forEach((c, i) => corpo[c.nome] = inputs[i].value);
    if (await enviar(corpo)) inputs.forEach(i => { if (i.tagName === 'INPUT') i.value = ''; });
  } }, el('div', { class: 'linha' }, campos.map((c, i) => el('label', {}, c.rotulo, inputs[i])), el('button', { type: 'submit' }, 'Executar'))));
}

function telaAdmin() {
  const lista = el('div'), audit = el('div');
  async function carregar() {
    const [at, pa] = await Promise.all([api('GET', '/ativos'), api('GET', '/admin/parametros')]);
    lista.replaceChildren(el('div', { class: 'cartao' }, el('h2', {}, 'Ativos'), tabela([{ titulo: 'Ativo', valor: a => a.ticker }, { titulo: 'Nome', valor: a => a.nome },
      { titulo: 'Tipo', valor: a => a.tipo }, { titulo: 'Status', valor: a => a.status },
      { titulo: '', valor: a => el('button', { type: 'button', class: 'sec', onclick: async () => {
        if (await acao(() => api('POST', '/admin/ativos/' + a.ticker + '/status', { status: a.status === 'ATIVO' ? 'SUSPENSO' : 'ATIVO' }), 'Status alterado.')) carregar(); } },
        a.status === 'ATIVO' ? 'Suspender' : 'Reativar') }], at.ativos)),
      el('div', { class: 'cartao' }, el('h2', {}, 'Parâmetros'), tabela([{ titulo: 'Chave', valor: p => p[0] }, { titulo: 'Valor', valor: p => p[1] }], Object.entries(pa.parametros))));
  }
  raiz.replaceChildren(el('div', { class: 'grade' },
    formulario('Cadastrar investidor', [{ nome: 'nome', rotulo: 'Nome' }, { nome: 'email', rotulo: 'E-mail', tipo: 'email', max: 254 }, { nome: 'senha', rotulo: 'Senha (12+ caracteres)', tipo: 'password', max: 128 }],
      async (c) => !!(await acao(() => api('POST', '/admin/investidores', c), 'Investidor cadastrado.'))),
    formulario('Depósito simulado', [{ nome: 'investidorId', rotulo: 'ID do investidor', max: 36 }, { nome: 'valor', rotulo: 'Valor (R$)', max: 10 }],
      async (c) => !!(await acao(() => api('POST', '/admin/depositos', c), 'Depósito registrado.'))),
    formulario('Cadastrar ativo', [{ nome: 'ticker', rotulo: 'Código', max: 6 }, { nome: 'nome', rotulo: 'Nome' }, { nome: 'tipo', rotulo: 'Tipo', opcoes: ['ACAO', 'ETF', 'FII'] }],
      async (c) => { const r = await acao(() => api('POST', '/admin/ativos', c), 'Ativo cadastrado.'); if (r) carregar(); return !!r; }),
    formulario('Alterar parâmetro', [{ nome: 'chave', rotulo: 'Chave', max: 60 }, { nome: 'valor', rotulo: 'Valor', max: 40 }],
      async (c) => { const r = await acao(() => api('POST', '/admin/parametros', c), 'Parâmetro alterado.'); if (r) carregar(); return !!r; })),
    lista, el('div', { class: 'cartao' }, el('h2', {}, 'Auditoria'), el('p', {},
      el('button', { type: 'button', onclick: async () => {
        const r = await acao(() => api('GET', '/admin/auditoria/verificacao'));
        if (r) aviso(r.mensagem, r.integra ? 'ok' : 'erro'); } }, 'Verificar integridade da trilha'), ' ',
      el('button', { type: 'button', class: 'sec', onclick: async () => {
        const r = await acao(() => api('GET', '/admin/auditoria?limite=30'));
        if (r) audit.replaceChildren(tabela([{ titulo: '#', num: true, valor: e => e.seq }, { titulo: 'Hora', valor: e => hora(e.instante) }, { titulo: 'Evento', valor: e => e.tipo },
          { titulo: 'Detalhes', valor: e => e.detalhes }], r.eventos)); } }, 'Ver primeiros eventos')), audit));
  carregar().catch(e => aviso(e.mensagem, 'erro'));
}

// ---------------------------------------------------------------- analista de risco
function telaRisco() {
  const inv = el('div'), rej = el('div');
  const id = el('input', { maxlength: 36, placeholder: 'ID do investidor' });
  const max = el('input', { maxlength: 10 }), dia = el('input', { maxlength: 10 }), conf = el('input', { maxlength: 10 });
  async function carregar() {
    const r = await acao(() => api('GET', '/risco/investidores'));
    if (r) inv.replaceChildren(tabela([{ titulo: 'Nome', valor: u => u.nome }, { titulo: 'E-mail', valor: u => u.email }, { titulo: 'ID', valor: u => u.id },
      { titulo: '', valor: u => el('button', { type: 'button', class: 'sec', onclick: async () => {
        id.value = u.id; const l = await acao(() => api('GET', '/risco/limites/' + u.id));
        if (l) { max.value = l.valorMaximoPorOrdem; dia.value = l.limiteDiario; conf.value = l.valorConfirmacao; } } }, 'Ver limites') }], r.investidores));
  }
  raiz.replaceChildren(el('div', { class: 'cartao' }, el('h2', {}, 'Investidores'), inv),
    el('div', { class: 'cartao' }, el('h2', {}, 'Limites de risco'), el('form', { onsubmit: async (ev) => {
      ev.preventDefault();
      await acao(() => api('POST', '/risco/limites/' + id.value, { valorMaximoPorOrdem: max.value, limiteDiario: dia.value, valorConfirmacao: conf.value }), 'Limites atualizados.');
    } }, el('div', { class: 'linha' }, el('label', {}, 'Investidor', id), el('label', {}, 'Máximo por ordem', max), el('label', {}, 'Limite diário', dia),
      el('label', {}, 'Confirmação acima de', conf), el('button', { type: 'submit' }, 'Salvar')))),
    el('div', { class: 'cartao' }, el('h2', {}, 'Ordens rejeitadas'), el('p', {}, el('button', { type: 'button', class: 'sec', onclick: async () => {
      const r = await acao(() => api('GET', '/risco/ordens-rejeitadas'));
      if (r) rej.replaceChildren(tabela([{ titulo: 'Hora', valor: o => hora(o.criadaEm) }, { titulo: 'Operação', valor: o => o.lado + ' ' + o.ticker },
        { titulo: 'Qtd.', num: true, valor: o => o.quantidade }, { titulo: 'Motivo', valor: o => o.motivoDescricao }], r.ordens)); } }, 'Atualizar')), rej));
  carregar();
}

// ---------------------------------------------------------------- partida
(async function iniciar() {
  try { const c = await api('GET', '/config'); estado.autenticadorSimulado = !!c.autenticadorSimulado; } catch (e) { /* segue sem o recurso */ }
  telaLogin();
})();
