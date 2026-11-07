#!/usr/bin/env python3
# A/B PIXEL-DIFF — GATE DURO da fidelidade visual iOS x Android (P7, fatia "estado real").
#
# Compara o render OFFSCREEN do NowPlayingPanel no iOS/Skia (ab/nowplaying-ios.png, gerado
# determinist. pelo NowPlayingPanelRenderTest via ImageComposeScene 393x420 @ Density(1f))
# com o BASELINE Android commitado (ab/nowplaying-android.png, capturado 1x pela
# AbBaselineActivity no mesmo tamanho/densidade). Falha (exit 1) se divergirem.
#
# DOIS CHECKS, ambos tem que passar:
#
#   (1) ESTRUTURAL (layout/posicao) — AE mascarado com tolerancia de vizinhanca.
#   (2) GLOW (o halo do text-shadow) — area iluminada do titulo (lit-area).
#
# POR QUE DOIS: o AE-contagem sozinho e CEGO ao glow. O halo e tenue (Shadow alpha .6);
# colapsar o blur 18f->4f muda MUITOS pixels por POUCO (cada delta < TOL) -> absorvido ->
# AE mal se mexe (619 -> 691, provado no CI run b63b762). Metricas CONTINUAS (SAD/MSE) na
# ROI tambem NAO separam (o shift sub-pixel de ~1px das letras BRILHANTES domina a soma:
# SAD known-good 77k vs regressao 97k = so 1.25x). O que colapsa num blur menor e a
# EXTENSAO do halo: contar pixels fracamente iluminados (green > TENUE) numa ROI do titulo
# mede o espalhamento do glow e e IMUNE ao shift de 1px das letras (deslocar glifo nao muda
# quantos pixels de halo existem). Separacao medida contra o par REAL do CI (blur18 x blur4):
#   lit-area(green>18) na ROI:  |AND-IOS| known-good = 70   |  regressao 18f->4f = 1480  (21x)
#
# ----------------------------------------------------------------------- CHECK (1) ESTRUTURAL
# AE (Absolute Error) mascarado = nº de pixels divergentes, com tolerancia de vizinhanca ±1px
# (um pixel só conta se diverge de TODOS os 9 vizinhos 3x3 do outro render -> absorve o shift
# sub-pixel da VT323 no Skia, ruido de AA legitimo) e TOL 32/canal. MASCARA (retangulos
# pretos nos DOIS antes de comparar) sobre a faixa DINAMICA (cursor "_", medidores L/R, barras
# do Spectrum). Pega quebras de LAYOUT/posicao, nao o glow.
#   known-good AE = 619  |  regressao de glow AE = 691  (ambos < teto; o glow NAO e pego aqui)
# TETO_AE = 1000.
#
# ------------------------------------------------------------------------- CHECK (2) GLOW
# Na ROI do titulo ("NEON_DRIVE", SEM o cursor), conta pixels com canal verde > GLOW_T (faixa
# do halo tenue). Compara a EXTENSAO do glow do iOS com a do baseline Android; o desvio
# absoluto tem que ficar <= GLOW_BOUND. Colapsar o halo derruba a contagem -> desvio estoura.
#   known-good desvio = 70  |  regressao 18f->4f desvio = 1480.  GLOW_BOUND = 500 (7x folga
#   sobre known-good, 3x abaixo da regressao). Fora do Spectrum o render iOS e DETERMINISTICO
#   entre runs -> as duas metricas sao estaveis, tetos nao-flaky.
#
# POR QUE Pillow e NAO ImageMagick: roda IDENTICO local (calibragem) e no CI (gate) -> teto
# real. Despacho abencoou "python3 + Pillow". 1 pip no runner via venv (PEP 668).
#
# Uso: python3 ab/ab_diff.py <android.png> <ios.png>
#   exit 0 = PASS (ambos os checks)   exit 1 = FAIL (algum check, ou PNG ausente/forma errada)

import sys
from functools import reduce
from PIL import Image, ImageChops, ImageDraw

W, H = 393, 420

# --- check (1) estrutural
TOL = 32
CEILING = 1000
MASKS = [
    (167, 48, 198, 92),    # CURSOR: só o "_" final (E termina ~x166; glow de NEON_DRIVE fica)
    (200, 228, 384, 250),  # L/R meters (à direita do ":: SPECTRUM")
    (16, 250, 380, 286),   # SPECTRUM bars (dentro da borda do frame)
]

# --- check (2) glow
TITLE_ROI = (6, 44, 167, 96)   # bbox de "NEON_DRIVE" + halo, SEM o cursor (x<167)
GLOW_T = 18                    # limiar do halo tenue no canal verde
GLOW_BOUND = 500               # desvio maximo da extensao do glow (iOS vs Android)


def _chanmax(diff):
    r, g, b = diff.split()
    return ImageChops.lighter(ImageChops.lighter(r, g), b)


def _apply_masks(im):
    im = im.copy()
    d = ImageDraw.Draw(im)
    for m in MASKS:
        d.rectangle(m, fill=(0, 0, 0))
    return im


def structural_ae(a, b):
    """Pixels de A sem par (dentro de TOL) em nenhum dos 9 vizinhos 3x3 de B, pós-mascara."""
    am, bm = _apply_masks(a), _apply_masks(b)
    masks = []
    for dy in (-1, 0, 1):
        for dx in (-1, 0, 1):
            cm = _chanmax(ImageChops.difference(am, ImageChops.offset(bm, dx, dy)))
            masks.append(cm.point(lambda v: 255 if v > TOL else 0))
    both = reduce(ImageChops.darker, masks)  # AND: diverge em TODOS os shifts
    return sum(both.get_flattened_data()) // 255


def glow_extent(im):
    """Area iluminada do halo (green > GLOW_T) na ROI do titulo."""
    g = im.split()[1].crop(TITLE_ROI)
    return sum(1 for v in g.get_flattened_data() if v > GLOW_T)


def _load(path, who):
    try:
        im = Image.open(path).convert("RGB")
    except Exception as e:
        print(f"::error::A/B FAIL — nao abri o PNG {who} '{path}': {e}")
        sys.exit(1)
    if im.size != (W, H):
        print(f"::error::A/B FAIL — {who} tem {im.size}, esperado {(W, H)} "
              f"(iOS ImageComposeScene e Android ComposeView tem que casar tamanho/densidade).")
        sys.exit(1)
    return im


def main():
    if len(sys.argv) != 3:
        print("uso: python3 ab/ab_diff.py <android.png> <ios.png>")
        sys.exit(2)
    android = _load(sys.argv[1], "Android (baseline)")
    ios = _load(sys.argv[2], "iOS (render offscreen)")

    ae = structural_ae(android, ios)
    glow_dev = abs(glow_extent(ios) - glow_extent(android))

    ok_struct = ae <= CEILING
    ok_glow = glow_dev <= GLOW_BOUND
    print(f"A/B check (1) ESTRUTURAL: AE={ae}  TETO={CEILING}  TOL={TOL}/canal  vizinhanca=±1px "
          f"-> {'PASS' if ok_struct else 'FAIL'}")
    print(f"      mascaras: cursor={MASKS[0]} L/R={MASKS[1]} spectrum={MASKS[2]}")
    print(f"A/B check (2) GLOW: desvio_lit-area(green>{GLOW_T}, ROI={TITLE_ROI})={glow_dev}  "
          f"BOUND={GLOW_BOUND}  -> {'PASS' if ok_glow else 'FAIL'}")

    if ok_struct and ok_glow:
        print("A/B PASS — layout fiel E glow do text-shadow preservado (iOS x Android).")
        sys.exit(0)
    if not ok_struct:
        print(f"::error::A/B FAIL (estrutural) — {ae} > {CEILING} pixels divergentes fora da "
              f"faixa dinamica. Layout/posicao quebrou.")
    if not ok_glow:
        print(f"::error::A/B FAIL (glow) — extensao do halo do titulo desviou {glow_dev} > "
              f"{GLOW_BOUND} do baseline. Provavel regressao do text-shadow (blur). "
              f"Baixe o artifact ab-nowplaying e compare o halo de NEON_DRIVE.")
    sys.exit(1)


if __name__ == "__main__":
    main()
