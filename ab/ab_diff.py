#!/usr/bin/env python3
# A/B PIXEL-DIFF — GATE DURO da fidelidade visual iOS x Android (P7, fatias "estado real" + "cover").
#
# Compara o render OFFSCREEN do NowPlayingPanel no iOS/Skia (ab/nowplaying-ios.png, gerado
# determinist. pelo NowPlayingPanelRenderTest via ImageComposeScene 393x420 @ Density(1f)) com o
# BASELINE Android commitado (ab/nowplaying-android.png, capturado 1x pela AbBaselineActivity no
# mesmo tamanho/densidade). Falha (exit 1) se divergirem.
#
# TRES CHECKS, todos tem que passar:
#
#   (1) ESTRUTURAL (layout/posicao) — AE mascarado com tolerancia de vizinhanca.
#   (2) GLOW (o halo do text-shadow do titulo) — area iluminada da ROI do titulo (lit-area).
#   (3) COVER (fidelidade da capa 1-bit) — AE APERTADO na ROI da capa dithered.
#
# ----------------------------------------------------------------------- CHECK (1) ESTRUTURAL
# AE (Absolute Error) mascarado = nº de pixels divergentes, com tolerancia de vizinhanca ±1px (um
# pixel só conta se diverge de TODOS os 9 vizinhos 3x3 do outro render -> absorve o shift sub-pixel
# da VT323 no Skia / ruido de AA legitimo) e TOL 32/canal. MASCARA (retangulos pretos nos DOIS
# antes de comparar) sobre: a CAPA (fidelidade dela é cobrada no check (3), regiao DISJUNTA) e a
# faixa DINAMICA (cursor "_", medidores L/R, barras do Spectrum clipadas no rodape). Pega quebras
# de LAYOUT/posicao do resto do painel (InvertedBar, ART/ALB/FMT, titulo, progresso, transporte).
#
# ------------------------------------------------------------------------- CHECK (2) GLOW
# Na ROI do titulo ("NEON_DRIVE", SEM o cursor), conta pixels com canal verde > GLOW_T (faixa do
# halo tenue). Compara a EXTENSAO do glow do iOS com a do baseline Android; o desvio absoluto tem
# que ficar <= GLOW_BOUND. Colapsar o blur do text-shadow derruba a contagem -> desvio estoura.
# (LICAO da fatia anterior: AE-contagem e SAD/MSE sao CEGOS ao glow tenue; a lit-area do halo nao.)
#
# ------------------------------------------------------------------------- CHECK (3) COVER
# O sample da capa é 96x96 PURO preto/branco -> no CoverProcessor o scale() curto-circuita (sem
# bilinear) e o dither Floyd–Steinberg nao difunde erro (luminancia 0/1, longe do limiar 0.5) -> o
# 1-bit sai BIT-IDENTICO no decode dos dois lados (skiko x BitmapFactory), imune ao micro-jitter
# (medido: ±8/canal -> 0 de 9216 bits mudam). A UNICA variacao possivel é o upscale nearest 96->128
# no desenho (convencao de amostragem skiko x Canvas Android), 1px nas BORDAS das figuras -> quase
# tudo absorvido pela vizinhanca ±1px. Logo o AE na ROI da capa é APERTADO. Degradar a capa (trocar
# o sample / mexer no dither) muda MILHARES de pixels do interior -> estoura o teto.
#
# POR QUE Pillow e NAO ImageMagick: roda IDENTICO local (calibragem) e no CI (gate) -> teto real.
#
# Uso: python3 ab/ab_diff.py <android.png> <ios.png>
#   exit 0 = PASS (os 3 checks)   exit 1 = FAIL (algum check, ou PNG ausente/forma errada)

import sys
from functools import reduce
from PIL import Image, ImageChops, ImageDraw

W, H = 393, 420

# --- check (1) estrutural (coords recalibradas p/ o layout COM a capa; density 1f -> dp==px)
TOL = 32
CEILING = 1600          # known-good real (CI 7f0f536) = 1044; ~1.5x de folga (layout com a capa)
MASKS = [
    (22, 60, 151, 189),    # CAPA (fidelidade cobrada no check (3) COVER — regiao disjunta)
    (167, 230, 187, 242),  # CURSOR: só o "_" final do titulo (E de DRIVE termina ~x166)
    (204, 388, 385, 405),  # medidores L/R (à direita do ":: SPECTRUM")
    (10, 405, 385, 420),   # barras do Spectrum (sliver clipado no rodape, animado)
]

# --- check (2) glow
TITLE_ROI = (6, 205, 166, 252)  # bbox de "NEON_DRIVE" + halo, SEM o cursor (x<167)
GLOW_T = 18                     # limiar do halo tenue no canal verde
GLOW_BOUND = 500                # desvio maximo da extensao do glow (iOS vs Android)

# --- check (3) cover
COVER_ROI = (24, 62, 148, 186)  # interior da capa 128px (dither puro, sem a moldura accent)
COVER_TOL = 40                  # 2 cores alto contraste; tolerancia por canal
COVER_CEILING = 50              # APERTADO: known-good real (CI 7f0f536) = 0 (capa BIT-PERFEITA
                                # iOS x Android); regressao da capa estoura p/ ~15k. Folga minima.


def _chanmax(diff):
    r, g, b = diff.split()
    return ImageChops.lighter(ImageChops.lighter(r, g), b)


def _neigh_ae(a, b, tol):
    """Pixels de A sem par (dentro de tol) em nenhum dos 9 vizinhos 3x3 de B — absorve shift ±1px."""
    masks = []
    for dy in (-1, 0, 1):
        for dx in (-1, 0, 1):
            cm = _chanmax(ImageChops.difference(a, ImageChops.offset(b, dx, dy)))
            masks.append(cm.point(lambda v: 255 if v > tol else 0))
    both = reduce(ImageChops.darker, masks)  # AND: diverge em TODOS os shifts
    return sum(both.get_flattened_data()) // 255


def _apply_masks(im):
    im = im.copy()
    d = ImageDraw.Draw(im)
    for m in MASKS:
        d.rectangle(m, fill=(0, 0, 0))
    return im


def structural_ae(a, b):
    return _neigh_ae(_apply_masks(a), _apply_masks(b), TOL)


def cover_ae(a, b):
    return _neigh_ae(a.crop(COVER_ROI), b.crop(COVER_ROI), COVER_TOL)


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
    cov = cover_ae(android, ios)

    ok_struct = ae <= CEILING
    ok_glow = glow_dev <= GLOW_BOUND
    ok_cover = cov <= COVER_CEILING
    print(f"A/B check (1) ESTRUTURAL: AE={ae}  TETO={CEILING}  TOL={TOL}/canal  vizinhanca=±1px "
          f"-> {'PASS' if ok_struct else 'FAIL'}")
    print(f"      mascaras: capa={MASKS[0]} cursor={MASKS[1]} L/R={MASKS[2]} spectrum={MASKS[3]}")
    print(f"A/B check (2) GLOW: desvio_lit-area(green>{GLOW_T}, ROI={TITLE_ROI})={glow_dev}  "
          f"BOUND={GLOW_BOUND}  -> {'PASS' if ok_glow else 'FAIL'}")
    print(f"A/B check (3) COVER: AE(ROI={COVER_ROI}, TOL={COVER_TOL}/canal, viz=±1px)={cov}  "
          f"TETO={COVER_CEILING}  -> {'PASS' if ok_cover else 'FAIL'}")

    if ok_struct and ok_glow and ok_cover:
        print("A/B PASS — layout fiel, glow do text-shadow preservado E capa 1-bit fiel (iOS x Android).")
        sys.exit(0)
    if not ok_struct:
        print(f"::error::A/B FAIL (estrutural) — {ae} > {CEILING} pixels divergentes fora da "
              f"capa/faixa dinamica. Layout/posicao quebrou.")
    if not ok_glow:
        print(f"::error::A/B FAIL (glow) — extensao do halo do titulo desviou {glow_dev} > "
              f"{GLOW_BOUND} do baseline. Provavel regressao do text-shadow (blur). "
              f"Baixe o artifact ab-nowplaying e compare o halo de NEON_DRIVE.")
    if not ok_cover:
        print(f"::error::A/B FAIL (cover) — AE da capa {cov} > {COVER_CEILING} na ROI {COVER_ROI}. "
              f"A capa dithered divergiu (sample/decode/dither mudou). Baixe o artifact ab-nowplaying.")
    sys.exit(1)


if __name__ == "__main__":
    main()
