#!/usr/bin/env python3
# A/B PIXEL-DIFF — GATE DURO da fidelidade visual iOS x Android (P7, fatia "estado real").
#
# Compara o render OFFSCREEN do NowPlayingPanel no iOS/Skia (ab/nowplaying-ios.png, gerado
# determinist. pelo NowPlayingPanelRenderTest via ImageComposeScene 393x420 @ Density(1f))
# com o BASELINE Android commitado (ab/nowplaying-android.png, capturado 1x pela
# AbBaselineActivity no mesmo tamanho/densidade). Falha (exit 1) se divergirem demais.
#
# POR QUE Pillow e NAO ImageMagick: o script tem que rodar IDENTICO local (calibragem) e no
# CI (gate) p/ o teto calibrado ser o teto real. magick nao esta no meu box de dev, entao a
# semantica do -fuzz nao era testavel localmente; Pillow (+ 1 pip) da paridade exata. O
# despacho abencoou "python3 + Pillow" como caminho valido.
#
# ----------------------------------------------------------------------------- METRICA
# AE (Absolute Error) = nº de pixels que divergem, com DUAS defesas contra falso-FAIL:
#
# 1) TOLERANCIA DE VIZINHANCA (±1 px): um pixel só conta como diferente se diverge de TODOS
#    os 9 vizinhos (3x3) do outro render. Isso ABSORVE o shift sub-pixel de ~1px do texto
#    (a VT323 renderiza um fio mais larga no Skia iOS) — ruido de anti-aliasing legitimo,
#    NAO regressao — SEM absorver o colapso do GLOW (o halo do text-shadow encolhe >>1px
#    quando o blur cai, entao continua sendo cobrado — que é o PONTO do A/B).
#
# 2) TOL por canal (32/255): gradientes suaves do glow equivalentes nao disparam; uma queda
#    de brilho do halo > TOL (ex. blur 18f->4f) dispara.
#
# MASCARA (retangulos pretos nos DOIS antes de comparar) sobre a faixa DINAMICA — conteudo
# que varia por frame/tick e NAO é fidelidade de layout:
#   - CURSOR: só o "_" final do titulo (pisca a cada 530ms). NAO cobre as letras
#     NEON_DRIVE nem o halo delas — o glow do titulo CONTINUA no gate.
#   - L/R: medidores "L ▮▮▯ R ▮▯" (dependem do tick do Spectrum).
#   - SPECTRUM: as barras do Canvas (variam por run — bbox empirico (20,254,373,282)).
#
# CALIBRAGEM (par known-good real: baseline Android commitado x PNG-iOS do run verde 2ccfd0b):
#   known-good masked AE = 619   | regressao de prova (glow colapsado) masked AE ~ 1560 (x2.5)
#   Fora das mascaras o render iOS é DETERMINISTICO entre runs (só o Spectrum varia, e está
#   mascarado) => o AE known-good é estavel e o teto é fixo, nao flaky.
#
# TETO = 1000: passa known-good (619) com folga ~1.6x e reprova o colapso do glow.
#
# Uso: python3 ab/ab_diff.py <android.png> <ios.png>
#   exit 0 = PASS (<= teto)   exit 1 = FAIL (> teto, ou PNG ausente/forma divergente)

import sys
from functools import reduce
from PIL import Image, ImageChops, ImageDraw

W, H = 393, 420
TOL = 32          # limiar por canal (0-255)
CEILING = 1000    # teto de pixels divergentes (AE)

# (x0, y0, x1, y1) no canvas 393x420 — faixa dinamica mascarada nos DOIS renders.
MASKS = [
    (167, 48, 198, 92),    # CURSOR: só o "_" final (E termina ~x166; glow de NEON_DRIVE fica)
    (200, 228, 384, 250),  # L/R meters (à direita do ":: SPECTRUM")
    (16, 250, 380, 286),   # SPECTRUM bars (dentro da borda do frame)
]


def _chanmax(diff):
    r, g, b = diff.split()
    return ImageChops.lighter(ImageChops.lighter(r, g), b)


def _apply_masks(im):
    im = im.copy()
    d = ImageDraw.Draw(im)
    for m in MASKS:
        d.rectangle(m, fill=(0, 0, 0))
    return im


def ae_neighborhood(a, b, tol=TOL):
    """Pixels de A sem par (dentro de TOL) em nenhum dos 9 vizinhos 3x3 de B."""
    masks = []
    for dy in (-1, 0, 1):
        for dx in (-1, 0, 1):
            cm = _chanmax(ImageChops.difference(a, ImageChops.offset(b, dx, dy)))
            masks.append(cm.point(lambda v: 255 if v > tol else 0))
    both = reduce(ImageChops.darker, masks)  # AND: diverge em TODOS os shifts
    return sum(both.get_flattened_data()) // 255


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

    am, bm = _apply_masks(android), _apply_masks(ios)
    ae = ae_neighborhood(am, bm)

    print(f"A/B pixel-diff: AE={ae}  TETO={CEILING}  TOL={TOL}/canal  vizinhanca=±1px")
    print(f"  mascaras (faixa dinamica): cursor={MASKS[0]} L/R={MASKS[1]} spectrum={MASKS[2]}")
    if ae > CEILING:
        print(f"::error::A/B FAIL — {ae} > {CEILING} pixels divergentes fora da faixa dinamica. "
              f"Fidelidade iOS x Android quebrou (provavel regressao do glow/text-shadow). "
              f"Baixe o artifact ab-nowplaying e compare os PNGs.")
        sys.exit(1)
    print(f"A/B PASS — {ae} <= {CEILING}. Fidelidade iOS x Android mantida (glow coberto).")
    sys.exit(0)


if __name__ == "__main__":
    main()
