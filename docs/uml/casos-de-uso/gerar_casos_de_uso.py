#!/usr/bin/env python3
"""Gera o diagrama de casos de uso (Graphviz/neato com posições fixas) e VERIFICA que nenhuma linha atravessa
uma elipse alheia. Uso, dentro desta pasta:  python3 gerar_casos_de_uso.py
Requer Graphviz (neato) e Pillow. Saídas: casos-de-uso.dot, a_*.png (atores) e ../../../diagrams/casos-de-uso.png"""
import glob, math, subprocess, sys
from PIL import Image, ImageDraw, ImageFont

H = 1400
# ------------------------------------------------------------------ atores (figura + nome desenhados na própria imagem)
def ator(arq, linhas):
    fp = glob.glob("/usr/share/fonts/**/DejaVuSans.ttf", recursive=True)[0]
    S = 3; W, Ht = 260 * S, 330 * S
    im = Image.new("RGBA", (W, Ht), (255, 255, 255, 0)); d = ImageDraw.Draw(im); c = "#222222"; w = 4 * S; cx = W // 2
    d.ellipse([cx-24*S, 10*S, cx+24*S, 58*S], outline=c, width=w); d.line([cx, 58*S, cx, 140*S], fill=c, width=w)
    d.line([cx-48*S, 90*S, cx+48*S, 90*S], fill=c, width=w); d.line([cx, 140*S, cx-38*S, 210*S], fill=c, width=w); d.line([cx, 140*S, cx+38*S, 210*S], fill=c, width=w)
    f = ImageFont.truetype(fp, 22*S); y = 228*S
    for l in linhas:
        tw = d.textlength(l, font=f); d.text((cx-tw/2, y), l, fill=c, font=f); y += 28*S
    im.resize((260*2, 330*2), Image.LANCZOS).save(arq)

ATORES = {  # id: (arquivo, linhas, x, y)
    "a_inv": ("a_inv.png", ["Investidor"], 60, 430), "a_adm": ("a_adm.png", ["Administrador"], 60, 1060), "a_ris": ("a_ris.png", ["Analista", "de Risco"], 60, 1300),
    "a_prov": ("a_prov.png", ["Provedor de", "Cotações"], 1450, 170), "a_corr": ("a_corr.png", ["Corretora/Bolsa", "Simulada"], 1450, 735),
    "a_notf": ("a_notf.png", ["Serviço de", "Notificação"], 1450, 1130), "a_aud": ("a_aud.png", ["Serviço de", "Auditoria"], 1450, 945)}
for k, (arq, linhas, x, y) in ATORES.items(): ator(arq, linhas)

# ------------------------------------------------------------------ casos de uso: id: (rótulo, x, y, estilo, largura_in, altura_in)
N = (2.15, 0.62)
UCS = {
 "cot": ("Consultar cotação", 300, 60, "", N), "saldo": ("Consultar saldo", 300, 130, "", N), "cart": ("Consultar carteira", 300, 200, "", N),
 "notc": ("Consultar notificações", 300, 270, "", N), "st": ("Consultar status\\nda ordem", 300, 340, "", N), "hist": ("Consultar histórico\\nde operações", 300, 410, "", N),
 "buy": ("Enviar ordem de compra\\n(a limite ou a mercado)", 300, 520, "", (2.4, 0.7)), "sell": ("Enviar ordem de venda\\n(a limite ou a mercado)", 300, 610, "", (2.4, 0.7)),
 "alt": ("Alterar ordem\\n(cancelar e substituir)", 300, 695, "", (2.4, 0.7)), "can": ("Cancelar ordem", 300, 780, "", N),
 "auth": ("Autenticar-se com MFA", 300, 880, "", N), "ginv": ("Gerenciar investidores", 300, 955, "", N), "ati": ("Gerenciar ativos\\ne parâmetros", 300, 1030, "", N),
 "lmt": ("Gerenciar limites\\nde risco", 300, 1105, "", N), "ctr": ("Consultar e verificar\\ntrilha de auditoria", 300, 1180, "", N), "rej": ("Consultar ordens\\nrejeitadas", 300, 1255, "", N),
 "conf": ("Confirmar operação\\nde alto valor", 700, 470, "", N), "val": ("Validar ordem", 700, 545, "", N), "tx": ("Transmitir ordem\\nà corretora", 700, 625, "", N),
 "exe": ("Executar ou rejeitar ordem\\n(relatório da corretora)", 700, 940, "", (2.9, 0.8)),
 "sp": ("Verificar saldo\\nou posição", 1100, 455, "", N), "lim": ("Verificar limites\\nde risco", 1100, 530, "", N), "mkt": ("Verificar mercado\\ne cotação", 1100, 605, "", N),
 "aud": ("Registrar auditoria", 1100, 900, "sup", N), "not": ("Enviar notificação", 1100, 985, "sup", N)}
ASSOC = [("a_inv", u) for u in ["cot", "saldo", "cart", "notc", "st", "hist", "buy", "sell", "alt", "can", "auth"]] + \
        [("a_adm", u) for u in ["auth", "ginv", "ati", "lmt", "ctr"]] + [("a_ris", u) for u in ["auth", "lmt", "rej"]] + \
        [("a_prov", "cot"), ("a_prov", "mkt"), ("a_corr", "tx"), ("a_corr", "exe"), ("a_corr", "can"), ("a_corr", "alt"), ("a_notf", "not"), ("a_aud", "aud")]
INCLUDE = [("buy", "val"), ("sell", "val"), ("buy", "tx"), ("sell", "tx"), ("buy", "aud"), ("sell", "aud"), ("alt", "val"), ("alt", "aud"),
           ("val", "sp"), ("val", "lim"), ("val", "mkt"), ("can", "aud"), ("can", "not"), ("exe", "not"), ("exe", "aud")]
EXTEND = [("conf", "buy"), ("conf", "sell"), ("conf", "alt")]
NOTA = (720, 1110)

# ------------------------------------------------------------------ verificação geométrica
def centro(n):
    if n in UCS: return UCS[n][1], UCS[n][2]
    if n in ATORES: return ATORES[n][2], ATORES[n][3]
    return NOTA
def semi(n):  # semi-eixos em pontos
    if n in UCS: w, h = UCS[n][4]; return w * 36, h * 36
    return 1.9 * 36, 2.41 * 36
def dentro(p, n, margem=0.0):
    cx, cy = centro(n); a, b = semi(n)
    if n in ATORES: return abs(p[0]-cx) <= a+margem and abs(p[1]-cy) <= b+margem
    return ((p[0]-cx)/(a+margem))**2 + ((p[1]-cy)/(b+margem))**2 <= 1
def ponta(n, lado):
    """Ponto de ancoragem (porta do Graphviz) no contorno: e/w/n/s para casos de uso; centro para atores."""
    cx, cy = centro(n)
    if n in ATORES: return (cx, cy)
    a, b = semi(n)
    return {"e": (cx + a, cy), "w": (cx - a, cy), "n": (cx, cy - b), "s": (cx, cy + b)}[lado]

def portas(u, v):
    """Portas de saída de u e de entrada em v, conforme a posição relativa (esquerda/direita)."""
    xu, yu = centro(u); xv, yv = centro(v)
    if u in ATORES: return None, ("w" if xv > xu else "e")
    if v in ATORES: return ("e" if xv > xu else "w"), None
    if abs(xu - xv) < 1: return ("s" if yv > yu else "n"), ("n" if yv > yu else "s")
    return ("e" if xv > xu else "w"), ("w" if xv > xu else "e")

def cruza(e):
    (u, v) = e; pu, pv = portas(u, v)
    (x1, y1) = ponta(u, pu) if pu else centro(u)
    (x2, y2) = ponta(v, pv) if pv else centro(v)
    ruins = []
    for n in UCS:
        if n in (u, v): continue
        for i in range(0, 401):
            t = i / 400; p = (x1 + (x2-x1)*t, y1 + (y2-y1)*t)
            if dentro(p, n, 2.0): ruins.append(n); break
    return ruins
problemas = []
for e in ASSOC + INCLUDE + EXTEND:
    r = cruza(e)
    if r: problemas.append((e, r))

# ------------------------------------------------------------------ DOT
def P(x, y): return f'pos="{x},{H-y}!"'
L = ['graph UC { graph [layout=neato, splines=line, overlap=true, fontname="Helvetica", pad=0.3, labelloc=t, fontsize=26, label="Diagrama de Casos de Uso — SentinelTrade\\n "];',
     ' node [fontname="Helvetica", fontsize=11]; edge [fontname="Helvetica", fontsize=9, color="#444444"];',
     f' borda [shape=box, style="rounded", penwidth=1.8, color="#444444", label="SentinelTrade", labelloc=t, fontsize=20, fixedsize=true, width=15.2, height={(H-60)/72:.2f}, {P(700, (H-60)/2 + 30)}];']
for k, (r, x, y, e, (w, h)) in UCS.items():
    cor = 'fillcolor="#FFF7E6", color="#B07A00"' if e == "sup" else 'fillcolor="#F4F8FF", color="#2B4C7E"'
    L.append(f' {k} [shape=ellipse, style=filled, {cor}, label="{r}", fixedsize=true, width={w}, height={h}, {P(x, y)}];')
for k, (arq, _, x, y) in ATORES.items():
    L.append(f' {k} [shape=none, label="", image="{arq}", imagescale=true, fixedsize=true, width=1.9, height=2.41, {P(x, y)}];')
def porta_attr(a, b):
    pu, pv = portas(a, b)
    return (f'tailport={pu}, ' if pu else '') + (f'headport={pv}, ' if pv else '')
for a, u in ASSOC:
    L.append(f' {a} -- {u} [{porta_attr(a, u)}];'.replace(", ]", "]"))
for a, b in INCLUDE: L.append(f' {a} -- {b} [{porta_attr(a, b)}dir=forward, arrowhead=open, style=dashed, color="#7A1F1F", fontcolor="#7A1F1F", label=" «include» "];')
for a, b in EXTEND: L.append(f' {a} -- {b} [{porta_attr(a, b)}dir=forward, arrowhead=open, style=dashed, color="#1F5A1F", fontcolor="#1F5A1F", label=" «extend» "];')
L.append(f' nota [shape=note, style=filled, fillcolor="#FFFFE0", fontsize=10, label="Gerenciar investidores, ativos e parâmetros\\ne limites de risco também incluem\\n«include» Registrar auditoria", {P(*NOTA)}];')
L.append(' nota -- aud [style=dotted, color="#888888"];')
L.append('}')
open("casos-de-uso.dot", "w", encoding="utf-8").write("\n".join(L))
subprocess.run(["neato", "-n2", "-Tpng", "-Gdpi=120", "casos-de-uso.dot", "-o", "../../../diagrams/casos-de-uso.png"], check=True)
if problemas:
    print("LINHAS QUE ATRAVESSAM ELIPSES ALHEIAS:")
    for e, r in problemas: print("  ", e, "atravessa", r)
    sys.exit(1)
print(f"ok: {len(ASSOC)+len(INCLUDE)+len(EXTEND)} linhas verificadas, nenhuma atravessa elipse alheia")
