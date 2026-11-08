#!/usr/bin/env python3
# A/B PIXEL-DIFF — GATE DURO da fidelidade visual iOS x Android (P7).
#
# Compara o render OFFSCREEN de uma tela no iOS/Skia (gerado determinist. por um *RenderTest via
# ImageComposeScene @ Density(1f)) com o BASELINE Android commitado (capturado 1x pela Activity
# espelho no mesmo tamanho/densidade). Falha (exit 1) se divergirem.
#
# OITO PERFIS (o perfil escolhe tamanho, ROIs, máscaras e QUAIS checks rodam):
#
#   nowplaying (393x420) — NowPlayingPanel. Checks: ESTRUTURAL + GLOW + COVER.
#   library    (393x600) — LibraryScreen (filtro PLAYLISTS, grade LazyVerticalGrid).
#                          Checks: ESTRUTURAL + GLOW. SEM cover (a grade de playlists usa
#                          PlaylistCover(photo=null) → GeneratedCover vetorial, não a capa 1-bit).
#   settings   (393x760) — SettingsScreen (CONFIG.SYS, seção CORES aberta via initialOpen=2).
#                          Checks: ESTRUTURAL (R-A: MÁSCARA-DE-TEXTO, gateia o CHROME) + GLOW
#                          (PREVIEW "SIGNAL//DECAY_"). SEM cover. Calibrado na fatia c3.
#   nav        (393x200) — MpxShell com conteúdo DUMMY (gateia o CHROME de navegação: barra MpxNav
#                          + cabeçalho MpxHeader, com abas PLAY/LIB/CFG ativas e QUEUE/EQ apagadas).
#                          Checks: ESTRUTURAL + GLOW (presença do texto display das abas, SEM halo —
#                          como o perfil library). SEM cover. Calibrado na fatia c3 (AE limpo=196).
#   eq         (393x720) — EqScreen (EQUALIZER, retrato: Preset/BandInfo/Bands 10x13/Preamp/Effects/
#                          Buttons). Dominada por FILLS de segmento (como o nav) + rotulos pequenos.
#                          Checks: ESTRUTURAL + GLOW (presença dos fills verdes das bandas, SEM halo).
#                          SEM cover. Calibrado na fatia c3 (AE limpo=1728, sem mascara).
#   queue      (393x720) — QueueScreen (fila, retrato: Header c/ disc-cover + InvertedBar + colunas +
#                          LazyColumn de 6 rows [row tocando = bg accent] + Totais + Botoes). Mistura
#                          GeneratedCover (disc) + fills da row-highlight + VARIAS rows de titulo
#                          (texto pequeno empilhado). Checks: ESTRUTURAL + GLOW (presenca). SEM cover
#                          1-bit. Tetos PLACEHOLDER (calibrados no c3; masks so-titulo SE dominar).
#   nowplaying-full (393x800) — NowPlayingScreen CHEIA (tela inteira: painel + VolumeRow + Console).
#                          isPlaying=FALSE congela o Spectrum -> quase estatica; so os 2 blinkingCursor
#                          (titulo + cmdlog) animam, e MEDIDO sao no-op (renderizam identico no frame 0).
#                          Checks: ESTRUTURAL + GLOW (HALO real do titulo, glow=true). SEM cover
#                          (GeneratedCover disc, cover=0 sincrono). Calibrado na fatia c3 (AE limpo=3730,
#                          masks=[] familia eq/queue; title_roi apertado excluindo cursor+subtitulo).
#   boot       (393x800) — BootScreen (splash/BIOS, FRAME TERMINAL via stepOverride=6/barOverride=20 ->
#                          LaunchedEffect nao roda, estado "ready" estatico). Checks: ESTRUTURAL (R-A:
#                          MASCARA o LOGO ascii box-drawing — nao-equivalencia de raster de glifo, classe
#                          do ♥ da Library) + GLOW (HALO do LOGO, glow=true). SEM cover. Par HIBRIDO
#                          (nivel Settings 12845); calibrado na fatia c3 (OPCAO B: clean pos-mascara=8879,
#                          SegmentBar+texto no estrutural honesto; cursor do "//PLAYER" no-op).
#
# As constantes/métrica do perfil `nowplaying` são IDÊNTICAS às da fatia anterior (byte-a-byte):
# reorganizadas num dict de perfil, sem recalibrar nada do par que já está verde.
#
# ----------------------------------------------------------------------- CHECK (1) ESTRUTURAL
# AE (Absolute Error) mascarado = nº de pixels divergentes, com tolerancia de vizinhanca ±1px (um
# pixel só conta se diverge de TODOS os 9 vizinhos 3x3 do outro render -> absorve o shift sub-pixel
# da VT323 no Skia / ruido de AA legitimo) e TOL/canal. MASCARA (retangulos pretos nos DOIS antes
# de comparar) sobre regioes cobertas por outro check (a capa no nowplaying) e sobre a faixa
# DINAMICA (cursor, medidores, barras animadas). Pega quebras de LAYOUT/posicao.
#
# ------------------------------------------------------------------------- CHECK (2) GLOW
# Na ROI de um texto, conta pixels com canal verde > GLOW_T. Compara a EXTENSAO do verde do iOS com
# a do baseline Android; o desvio absoluto tem que ficar <= GLOW_BOUND.
#   - nowplaying: a ROI cobre o titulo "NEON_DRIVE", que tem text-shadow (Shadow blur 18f, o UNICO
#     glow=true da UI). Colapsar o blur derruba a lit-area do HALO -> desvio estoura. (LICAO: AE e
#     SAD/MSE sao CEGOS ao halo tenue; a lit-area nao.)
#   - library: ATENCAO — a LibraryScreen NAO tem text-shadow (o unico glow=true da UI inteira e o
#     titulo do NowPlaying). Aqui a ROI cobre o NOME de uma playlist (Txt display=true VT323, SEM
#     halo), entao este check mede a PRESENCA/EXTENSAO do texto verde (pega o nome sumir/dimmar/
#     mover) — sensibilidade DISJUNTA do estrutural, mas o vetor de regressao da Library e DESLOCAR
#     A GRADE (nao colapsar blur, que aqui nao existe).
#
# ------------------------------------------------------------------------- CHECK (3) COVER
# SO no perfil nowplaying. O sample da capa e 96x96 PURO preto/branco -> no CoverProcessor o
# scale() curto-circuita (sem bilinear) e o dither Floyd–Steinberg nao difunde erro (luminancia
# 0/1, longe do limiar 0.5) -> o 1-bit sai BIT-IDENTICO no decode dos dois lados (skiko x
# BitmapFactory), imune ao micro-jitter. Logo o AE na ROI da capa e APERTADO.
#
# POR QUE Pillow e NAO ImageMagick: roda IDENTICO local (calibragem) e no CI (gate) -> teto real.
#
# Uso: python3 ab/ab_diff.py <android.png> <ios.png> [perfil]
#   perfil ∈ {nowplaying (default), library, settings, nav, eq, queue, nowplaying-full, boot}.  O check COVER so roda se o perfil define cover.
#   exit 0 = PASS (todos os checks do perfil)   exit 1 = FAIL (algum check, ou PNG ausente/forma errada)

import sys
from functools import reduce
from PIL import Image, ImageChops, ImageDraw

# ===================================================================== PERFIS
# Cada perfil define o canvas (w,h), o ESTRUTURAL (tol/ceiling/masks), o GLOW (roi/t/bound) e,
# opcionalmente, o COVER (roi/tol/ceiling). cover=None => o perfil nao roda o check COVER.
PROFILES = {
    # NowPlayingPanel — IDENTICO a fatia anterior (par CI ja verde: AE=1044, glow=171, cover=0).
    "nowplaying": {
        "w": 393, "h": 420,
        "tol": 32,
        "ceiling": 1600,            # known-good real (CI 7f0f536) = 1044; ~1.5x de folga
        "masks": [
            (22, 60, 151, 189),     # CAPA (fidelidade cobrada no check COVER — regiao disjunta)
            (167, 230, 187, 242),   # CURSOR: só o "_" final do titulo
            (204, 388, 385, 405),   # medidores L/R
            (10, 405, 385, 420),    # barras do Spectrum (sliver clipado, animado)
        ],
        "title_roi": (6, 205, 166, 252),  # bbox de "NEON_DRIVE" + halo, SEM o cursor (x<167)
        "glow_t": 18,
        "glow_bound": 500,
        "cover": {
            "roi": (24, 62, 148, 186),    # interior da capa 128px (dither puro, sem a moldura)
            "tol": 40,
            "ceiling": 50,                # APERTADO: known-good real = 0 (capa BIT-PERFEITA)
        },
    },
    # LibraryScreen (filtro PLAYLISTS). CALIBRADO (fatia c3) com os numeros REAIS do par CI (run
    # 42378475448, f5c4826): estrutural AE_LIMPO=810 (pos-mascara do ♥) -> ceiling 810*~1.54; glow
    # dev=39 -> bound 150 (folga modesta). O AE bruto era 1052 (modesto/fiel, ~ NowPlaying 1044
    # num canvas MAIOR); a mascara do ♥ remove a UNICA nao-equivalencia conhecida (ver abaixo).
    "library": {
        "w": 393, "h": 600,
        "tol": 32,
        "ceiling": 1250,            # known-good LIMPO = 810 (pos-mascara ♥); ~1.54x de folga
        "masks": [
            (58, 20, 92, 46),       # CURSOR do SearchRow (bbox aprox. do cursor no inicio do campo)
            (208, 403, 233, 427),   # ♥ (U+2665) do nome da NEON (col1 linha1): emoji COLORIDO no
                                    # Android (fallback do sistema; a VT323 nao tem o glifo) x glifo
                                    # do skiko no iOS -> regiao NAO-comparavel, mascarada na mesma
                                    # categoria do cursor/medidores/spectrum. So o ♥ (x210-230,
                                    # y405-424): NAO toca o texto "NEON" (verde, comeca em x242) nem
                                    # a glow-ROI do RETRO (y226-252). Contribuia 242 px ao AE.
            # Sem mascara de overscroll: 4 itens (tile NOVA + 3 playlists) em 2 colunas = 2 linhas,
            # cabem sem rolagem -> sem glow de overscroll (confirmado no par real).
        ],
        "title_roi": (205, 226, 379, 252),  # nome da 1a playlist ("RETRO", col1 linha0); limpo (dev=39)
        "glow_t": 18,
        "glow_bound": 150,          # known-good dev=39; folga modesta (lit-area de PRESENCA do nome)
        "cover": None,              # Library NAO tem a capa 1-bit -> sem check COVER
    },
    # SettingsScreen (CONFIG.SYS, secao CORES aberta via initialOpen=2). 3o par A/B. CALIBRADO na
    # fatia c3 (decisao R-A do Maestro) com os pixels REAIS do par CI (baseline Android x PNG-iOS do
    # run 42400339184).
    #
    # POR QUE R-A (mascara-de-TEXTO no estrutural): o par cru deu AE=18034 (cru, ab_diff) -> FAIL no
    # placeholder. Diagnostico (eu + Lente): NAO e regressao de layout — e stem-darkening/AA do TEXTO
    # DE CORPO (JetBrains Mono pequeno) iOS/skiko x Android (peso de raster do glifo). Prova de que e
    # texto e nao layout: os textos right-aligned terminam no MESMO x (advances batem), zero tofu,
    # chrome fiel item-a-item. Numa tela DENSA de texto isso empilha. R-A = EXCLUIR o texto de corpo
    # do estrutural (mesma categoria do cursor/spectrum/♥, so mais area) e GATEAR O CHROME (molduras,
    # barras/fills accent, quadrados de swatch, segmentos, dividers, bg + suas bordas). O GLOW segue
    # cobrando o titulo "SIGNAL//DECAY_" (lit-area do halo). ~49% de area mascarada e ESPERADO numa
    # tela de texto; a PROVA DE REGRESSAO (grid-shift +12dp -> estrutural estoura) garante que as
    # mascaras NAO engoliram o chrome.
    #
    # MASCARAS-DE-TEXTO: cada retangulo cobre SO uma banda de texto de corpo/header, DISJUNTO de (a)
    # os 4 quadrados de swatch (y~213-235), (b) o quadrado verde da PERSONALIZADA (x<58), (c) as
    # bordas/fills que gateiam o chrome, (d) a ROI de GLOW. Verificado visualmente (chrome sobrevive).
    #
    # CLEAN structural AE (medido no ab_diff, pos-mascara) = 11052. TETO = 11052 * ~1.5 = 16578 (folga
    # MODESTA; NUNCA ~27000 = inflar p/ encobrir layout, VETADO). RAW (sem mascara) era 18034.
    "settings": {
        "w": 393, "h": 760,
        "tol": 32,
        "ceiling": 16578,           # known-good CLEAN = 11052 (pos-mascara de texto); ~1.5x de folga
        "masks": [
            # Retangulos (x0,y0,x1,y1) sobre BANDAS DE TEXTO DE CORPO (excluidas do estrutural; o
            # chrome entre/ao redor sobrevive). Conteudo x∈[10,383].
            (10, 9, 383, 54),       # top header: "C:\> EDIT CONFIG.SYS" + "SELECIONE...[1-6]" + CURSOR
            (20, 80, 379, 96),      # [1] APARENCIA header (texto; bordas y~72/106 sobrevivem)
            (20, 120, 379, 159),    # [2] CORES accent-bar (texto onAccent; bordas top/bottom do fill sobrevivem)
            (14, 183, 383, 197),    # label "FOSFORO (COR DE DESTAQUE)"
            (14, 238, 383, 252),    # labels dos swatches (PRESERVA os quadrados coloridos em y~213-235)
            (58, 261, 383, 296),    # "PERSONALIZADA" + "#3DFF8F" (PRESERVA o swatch verde da esquerda, x<58)
            (20, 355, 379, 369),    # FUNDO seg "PRETO #040704" (bordas do segmento sobrevivem)
            (20, 381, 379, 395),    # FUNDO seg "OLED #000000"
            (14, 419, 383, 441),    # label "PREVIEW"
            (20, 539, 379, 583),    # [3] IMPORTAR header texto
            (20, 594, 379, 635),    # [5] AUDIO header texto
            (20, 648, 379, 690),    # [6] SOBRE header texto
            (10, 714, 383, 727),    # rodape "(C) 2026 MPX//OS / F10 = SAVE & EXIT"
        ],
        # GLOW: ROI do PREVIEW "SIGNAL//DECAY_" (Txt display=true, glow=true) — o UNICO glow real da
        # secao CORES (text-shadow blur 18f, mesmo mecanismo do titulo do NowPlaying). O topo da ROI
        # fica ABAIXO da barra accent "> NOW PLAYING" (y446-463, CHROME que fica no estrutural); a ROI
        # cobre titulo+halo+"NULL SECTOR". Mede a lit-area do halo (AE/SAD sao CEGOS ao halo tenue).
        "title_roi": (8, 466, 386, 515),
        "glow_t": 18,
        "glow_bound": 1650,         # known-good dev=1093 (ROI nova); folga modesta ~1.5x
        "cover": None,              # CONFIG nao tem a capa 1-bit -> sem check COVER
    },
    # MpxShell com conteudo DUMMY ("CONTENT"): gateia o CHROME de navegacao (barra MpxNav horizontal
    # + cabecalho MpxHeader), 4o par A/B (P7 fatia nav). Canvas PEQUENO (393x200): cabecalho (~31px)
    # + barra (44dp) + placeholder (80dp) + espacamentos/padding. A barra tem POUCO texto (so os
    # labels VT323 display das abas, que batem como os headers de secao dos outros perfis).
    #
    # Checks: ESTRUTURAL (presenca/posicao da moldura da barra, dividers entre abas, accent-fill da
    # aba PLAY selecionada, texto apagado das abas QUEUE/EQ desabilitadas, moldura do cabecalho) +
    # GLOW (a barra NAO tem glow=true -> a ROI mede a PRESENCA/EXTENSAO do verde display das abas,
    # como no perfil library; NAO ha halo a colapsar). SEM cover.
    #
    # CALIBRADO (fatia c3) com os numeros REAIS do par CI (run 42410882821, 9490b2c): o par e LIMPINHO
    # — chrome = fills solidos + labels VT323 display que batem item-a-item; SEM o problema de texto-
    # denso do Settings -> ZERO mascara necessaria (masks=[]). ESTRUTURAL AE_LIMPO=196 -> ceiling
    # 196*~1.53=300; GLOW dev=124 -> bound 124*~1.53=190 (mesma convencao modesta da Library, 810->
    # 1250). A PROVA DE REGRESSAO (branch -TEMP: +12dp no padding do shell -> a barra+5 labels movem ->
    # estrutural na casa dos milhares, estoura 300 com conforto) garante que os tetos nao sao frouxos.
    # NUNCA inflar p/ encobrir layout.
    "nav": {
        "w": 393, "h": 200,
        "tol": 32,
        "ceiling": 300,             # known-good LIMPO = 196 (chrome-only, sem mascara); ~1.53x de folga
        "masks": [],                # par limpo: labels display batem -> nenhuma mascara necessaria
        "title_roi": (10, 55, 383, 95),  # banda dos labels da barra MpxNav (presenca do verde display)
        "glow_t": 18,
        "glow_bound": 190,          # known-good dev=124 (presenca das labels); ~1.53x de folga
        "cover": None,              # chrome de nav nao tem capa 1-bit -> sem check COVER
    },
    # EqScreen (EQUALIZER, retrato). 5o par A/B (P7 fatia eq). Canvas ALTO (393x720): Header(InvertedBar
    # +[ON]) + PresetRow(5 pilulas, CUSTOM filled) + BandInfo + Bands(10 bandas x 13 segmentos, curva
    # "sorriso") + Preamp(SegmentBar +/-) + Effects(3 ToggleRow) + Buttons(RESET/SAVE). ESTATICA
    # (sem cursor/marquee/spectrum/animacao) -> 1o frame = estado final.
    #
    # Dominada por FILLS de segmento (barras das bandas + preamp) que batem bem (como o nav), mas tem
    # rotulos PEQUENOS (freq labels VT323 9sp, preamp dB/fmt1 em JB mono, labels dos ToggleRow). O
    # check GLOW mede a PRESENCA/EXTENSAO do verde dos fills das bandas (a EQ NAO tem glow=true -> sem
    # halo a colapsar; sensibilidade de presenca estilo nav/library).
    #
    # CALIBRADO (fatia c3) com os numeros REAIS do par CI (run 42416997422, 6ac4ccd): o par e LIMPO —
    # ESTRUTURAL AE_CLEAN=1728, GLOW dev=2. O glow=2 prova que os FILLS das bandas batem QUASE PERFEITO
    # (chrome fiel, como o nav); o 1728 em 393x720 (282960 px, ~0.6%) e so o PISO de AA dos poucos
    # rotulos PEQUENOS (freq labels 9sp VT323, preamp dB/fmt1, labels dos ToggleRow) — da ordem da
    # Library (810, que tb rodou SEM mascara de texto), NAO o texto-denso/stem-darkening do Settings
    # (11052, que exigiu R-A). Decisao (Maestro/Bigorna): NAO mascarar — nada a esconder, e mascarar
    # rotulos espalhados seria fiddly sem ganho (o gate ja morde). ESTRUTURAL ceiling = 1728*~1.5=2600.
    # GLOW bound = 150 (PISO absoluto; 2*1.5=3 flakaria): dev known-good=2, mas banda sumir/mover desvia
    # a lit-area em MILHARES -> 150 pega qualquer regressao com folga gigante e nao flaka no AA. A PROVA
    # DE REGRESSAO (branch -TEMP: shift na EQ -> estrutural na casa dos milhares, estoura 2600) garante
    # que os tetos nao sao frouxos. NUNCA inflar p/ encobrir layout.
    "eq": {
        "w": 393, "h": 720,
        "tol": 32,
        "ceiling": 2600,            # known-good CLEAN = 1728 (piso de AA de rotulo pequeno); ~1.5x de folga
        "masks": [],                # par limpo (glow dev=2, fills perfeitos); piso de AA aceito SEM mascara (como Library)
        "title_roi": (14, 128, 379, 320),  # faixa das BANDAS (fills de segmento verdes) — presenca
        "glow_t": 18,
        "glow_bound": 150,          # PISO absoluto (known-good dev=2); banda sumir/mover desvia milhares -> pega com folga
        "cover": None,              # EQ nao tem a capa 1-bit -> sem check COVER
    },
    # QueueScreen (fila, retrato). 6o par A/B (P7 fatia queue). Canvas ALTO (393x720): QueueHeader
    # (GeneratedCover disc 92dp + album display 26sp + meta + playlist) + InvertedBar("> DIR /QUEUE",
    # glow=false) + ColumnHeaders(NR/FILENAME/TIME) + QueueList (LazyColumn de 6 rows: NR + marca
    # ▶/✓ + titulo.uppercase + TIME; a row TOCANDO [index=1] com bg accent) + Totais + Botoes.
    #
    # COVER: fixture sem art + cover=0 -> GeneratedCover(disc) SINCRONO/deterministico (sem a capa
    # 1-bit do NowPlaying) -> SEM check COVER. index=1 (<=2) -> sem animateScrollToItem (lista do topo).
    #
    # Mistura FILLS (disc-cover, row-highlight accent da row tocando, molduras) + VARIAS rows de TITULO
    # (texto pequeno 12sp empilhado = mais texto que EQ/nav). O check GLOW mede PRESENCA do verde do
    # album display 26sp do header (display=true, grande; chrome fiel).
    #
    # CALIBRADO (fatia c3) com os numeros REAIS do par CI (run 42422060024, 29a0e08): ESTRUTURAL
    # AE_CLEAN=2721, GLOW dev=33. O glow=33 prova que o album display 26sp do header + o chrome batem;
    # o 2721 em 393x720 (~1.0%) e o PISO de AA do texto PEQUENO EMPILHADO (6 titulos 12sp + NR/TIME +
    # col headers + totais) somado ao disc GeneratedCover. Da FAMILIA EQ(1728)/Library(810) — pisos
    # aceitos SEM mascara, NAO o texto-denso/stem-darkening do Settings (11052, que exigiu R-A).
    # Decisao (Maestro/Bigorna): NAO mascarar — nada a esconder; mascarar a coluna de titulos seria
    # fiddly sem ganho (o gate ja morde). ESTRUTURAL ceiling = 2721*~1.5 = 4100. GLOW bound = 150
    # (PISO, mesma folga de Library dev=39->150 e EQ dev=2->150; known-good=33; album title sumir/mover
    # desvia centenas+ -> 150 pega e nao flaka). A PROVA DE REGRESSAO (branch -TEMP: +12dp no padding ->
    # estrutural nas DEZENAS DE MILHARES) garante que os tetos nao sao frouxos. NUNCA inflar.
    "queue": {
        "w": 393, "h": 720,
        "tol": 32,
        "ceiling": 4100,            # known-good CLEAN = 2721 (piso de AA de texto empilhado + disc); ~1.5x
        "masks": [],                # par limpo (glow dev=33, display/chrome fieis); piso de AA aceito SEM mascara (como EQ/Library)
        "title_roi": (120, 16, 383, 52),  # album display 26sp do header (presenca do verde display)
        "glow_t": 18,
        "glow_bound": 150,          # PISO absoluto (known-good dev=33); album title sumir/mover desvia centenas+ -> pega com folga
        "cover": None,              # QUEUE usa GeneratedCover (disc, cover=0 sincrono), nao a capa 1-bit -> sem check COVER
    },
    # NowPlayingScreen CHEIA (tela inteira, retrato). 7o par A/B (P7 fatia nowplaying-cheia). Canvas
    # ALTO (393x800): InvertedBar + NpCoverMeta(disc 128dp) + NpTitle(VT323 40sp glow=true) + NpProgress
    # + NpTransport(6 botoes) + regiao do Console (Spectrum+Volume ao fundo + CMD.LOG colapsado por cima).
    #
    # INSIGHT (decisao do Maestro): com isPlaying=FALSE a tela fica QUASE ESTATICA no 1o frame — o
    # Spectrum NAO roda tick (barras em repouso), o console nasce COLAPSADO (expand=0f) na aba CMD.LOG,
    # o dialog fica FECHADO. Os UNICOS bits animados = os 2 blinkingCursor (titulo + cmdlog). COVER:
    # Track sem art + cover=0 -> GeneratedCover(disc) SINCRONO (como Queue) -> SEM check COVER (nao e a
    # capa 1-bit do par 'nowplaying' painel).
    #
    # DISTINTO do par 'nowplaying' (painel, 393x420): aqui e a TELA INTEIRA (+ VolumeRow + Console). O
    # painel segue INTOCADO byte-a-byte (AE=1044). O GLOW aqui cobra o HALO real do titulo (NpTitle tem
    # glow=true, Shadow blur 18f) — mesma sensibilidade de colapso-de-blur do 'nowplaying'/'settings',
    # NAO so presenca (nav/eq/queue).
    #
    # CALIBRADO (fatia c3) com os numeros REAIS do par CI (run 42437302986, cd10639): ESTRUTURAL
    # AE_CLEAN=3730 (masks=[]), GLOW dev=42 no ROI largo / dev=9 no ROI apertado. DECISAO DE MASCARA
    # RESOLVIDA MEDINDO (nao por convencao): o despacho previa "masks = 2 cursores (titulo + cmdlog)",
    # mas a medicao do par real PROVA que os cursores NAO sao fonte de divergencia:
    #   masks=[]                 -> AE 3730
    #   masks=[cursor do titulo] -> AE 3730  (efeito ZERO: o "_" do titulo arranca "_" nos DOIS lados
    #                                         no frame 0 e renderiza identico; mascarar seria no-op)
    #   masks=[2 cursores]       -> AE 3660  (o cmdlog so remove ~70px de TEXTO accent REAL adjacente,
    #                                         NAO o cursor -> mascarar ESCONDERIA chrome de verdade)
    # Logo masks=[] e a escolha HONESTA: o piso 3730 em 393x800 (~1.2%) e AA de texto PEQUENO ESPALHADO
    # (InvertedBar, cover-meta, titulo, digitos do progress, linhas da CMD.LOG) — FAMILIA EQ(1728)/
    # Queue(2721)/Library(810), pisos aceitos SEM mascara, NAO o texto-denso/stem-darkening do Settings
    # (11052, que exigiu R-A). O proprio recorte do Maestro previa esse galho ("+ familia eq/queue se
    # houver AA empilhado"); a medicao desempatou p/ ele. NAO inflamos, NAO escondemos. ESTRUTURAL
    # ceiling = 3730*~1.5 = 5600.
    #
    # GLOW: title_roi APERTADO de (10,205,383,262) p/ (10,212,272,250) = "MIDNIGHT_PROTOCOL" + halo,
    # EXCLUINDO o cursor "_" (a barra vai ate x~296, fora em x>272) e o subtitulo dosPath (12sp dim, em
    # y>250) — MESMA logica do par 'nowplaying' painel (title_roi exclui o cursor, x<167). O NpTitle tem
    # glow=true (Shadow blur 18f) -> este check cobra o HALO real (colapso-de-blur), nao so presenca.
    # known-good dev=9 (ROI apertado). BOUND = 150 (PISO absoluto, mesma folga de EQ dev=2->150 e Queue
    # dev=33->150; 9*1.5=14 flakaria no AA; titulo sumir/mover desvia MILHARES -> 150 pega com folga).
    #
    # A PROVA DE REGRESSAO (branch -TEMP: +12dp no padding do NowPlayingFullRenderTest -> a tela inteira
    # desloca -> estrutural nas DEZENAS DE MILHARES, ~5-8x o teto 5600, enquanto os 6 anteriores PASSAM
    # e Android verde) garante que o teto nao e frouxo. NUNCA inflar p/ encobrir layout. Os 7 baselines
    # PNG seguem INTOCADOS byte-a-byte (ESP. nowplaying painel 1044/171/0); o c3 toca SO este dict.
    "nowplaying-full": {
        "w": 393, "h": 800,
        "tol": 32,
        "ceiling": 5600,            # known-good CLEAN = 3730 (piso de AA espalhado, masks=[]); ~1.5x de folga
        "masks": [],                # MEDIDO: os 2 cursores sao no-op (titulo efeito 0; cmdlog so esconderia texto real) -> piso de AA aceito SEM mascara (familia EQ/Queue)
        "title_roi": (10, 212, 272, 250),  # "MIDNIGHT_PROTOCOL" + HALO, excluindo o cursor (x>272) e o subtitulo (y>250)
        "glow_t": 18,
        "glow_bound": 150,          # PISO absoluto (known-good dev=9); titulo sumir/mover desvia milhares -> pega com folga
        "cover": None,              # GeneratedCover disc (cover=0 sincrono) -> sem check COVER (nao e a capa 1-bit)
    },
    # BootScreen (splash/BIOS, FRAME TERMINAL). 8o par A/B (P7 fatia boot, o ULTIMO 4/4). Canvas ALTO
    # (393x800): BootHeader (MPX BIOS + VER 1.0) + BootBranding (Panel @ECHO OFF... + LOGO ascii 6 linhas
    # glow=true 13sp + "//PLAYER"+cursor display 24sp) + BootLog (Panel weight(1f): 6 linhas "> CHECK.."
    # [OK]/[WAIT]/[NOPE] incl. "> INDEX 12.345 FILES" via fmtThousands + USER_PROFILE LOADED + SegmentBar
    # (20,20) cheia + "100%") + BootButton "[ PRESS ENTER ]" filled + BootFooter.
    #
    # FRAME TERMINAL (decisao do Maestro): stepOverride=6 + barOverride=20 -> os LaunchedEffect NAO rodam
    # (guard do c1) -> estado "ready" final, ESTATICO. O UNICO bit animado = o blinkingCursor do
    # "//PLAYER" (display 24sp), que arranca "_" nos dois lados no frame 0 -> provavelmente no-op (como
    # os cursores do npfull). COVER: Boot nao tem capa -> cover=None.
    #
    # CALIBRADO (fatia c3, decisao OPCAO B do Maestro) com os numeros REAIS do par CI (run 42444594401,
    # f222cf5): este par NAO e limpo (familia eq/queue) como o plano previa — saiu HIBRIDO (nivel
    # Settings). clean AE masks=[] = 12845. DIAGNOSTICO (MEDIDO, local=CI; Bigorna revalidou identico):
    #   - SEM shift global (exact-AE minimiza em dy=0 -> LAYOUT FIEL; nao ha cascata de deslocamento).
    #   - LOGO ascii box-drawing (█╗═, glow=true): ~3966px, ~10x mais denso que um titulo VT323 normal
    #     (o npfull era ~centenas) -> NAO-EQUIVALENCIA de raster/fallback de glifo box-drawing skiko x
    #     Android, MESMA CLASSE do ♥ (U+2665) mascarado na Library. E o ALVO ISOLAVEL que eq/queue nao
    #     tinham -> MASCARADO (um retangulo sobre o bloco de 6 linhas).
    #   - SegmentBar CHEIA (20/20, y~461-474): ~3331px de rounding sub-pixel dos 20 segmentos = CHROME
    #     EQUIVALENTE -> FICA no estrutural (mascara-la seria esconder chrome real; opcao C descartada).
    #   - resto (header labels + branding dim + "//PLAYER" display + 6 linhas da CMD.LOG + footer):
    #     ~5548px = piso de AA de texto denso (classe Settings) -> FICA no estrutural (piso honesto).
    #   - cursor do "//PLAYER": no-op (arranca "_" identico nos 2 lados no frame 0, nao domina) -> NAO
    #     entra nas masks.
    # OPCAO B (vs A masks=[]/teto 19300 frouxo, descartada; vs C mask+SegBar que esconderia chrome): a
    # mascara cirurgica do LOGO (a nao-equivalencia conhecida, precedente ♥ da Library / R-A do Settings)
    # + calibragem HONESTA no resto. clean pos-mascara = 8879 -> ESTRUTURAL ceiling = 8879*~1.5 = 13300.
    # A fidelidade do LOGO NAO fica sem gate: o GLOW cobra o halo (abaixo).
    #
    # GLOW: title_roi (10,152,383,242) = bloco do LOGO ascii + halo, EXCLUINDO a linha "//PLAYER"+cursor
    # (display 24sp, em y>242). O LOGO e glow=true (Shadow blur 18f). known-good dev=842 (o box-drawing
    # infla a lit-area LEGITIMAMENTE — e a mesma nao-equivalencia que o estrutural mascara; aqui ela NAO
    # e mascarada, so tolerada pelo bound). BOUND = 842*~1.5 = 1300 (NAO o piso 150 dos pares limpos — o
    # LOGO tem AA real; mas o LOGO sumir/mover desvia a lit-area em MILHARES -> 1300 morde com folga).
    #
    # A PROVA DE REGRESSAO (branch -TEMP: +12dp no padding do BootScreenRenderTest -> a tela inteira
    # desloca -> estrutural bem acima de 13300 + glow acima de 1300, enquanto os 7 anteriores PASSAM e
    # Android verde) garante que o teto nao e frouxo e e disjunto. NUNCA inflar p/ encobrir layout. Os 8
    # baselines PNG seguem INTOCADOS byte-a-byte (os 7 antigos + boot-android.png); o c3 toca SO este dict.
    "boot": {
        "w": 393, "h": 800,
        "tol": 32,
        "ceiling": 13300,           # known-good CLEAN pos-mascara do LOGO = 8879; ~1.5x de folga (hibrido nivel Settings)
        "masks": [
            (8, 148, 385, 246),     # LOGO ascii box-drawing (█╗═): nao-equivalencia de raster de glifo
                                    # skiko x Android (~3966px, ~10x um titulo VT323; classe do ♥ da
                                    # Library). A fidelidade do LOGO segue gateada pelo GLOW (halo).
                                    # SegmentBar (chrome equivalente) e texto denso FICAM no estrutural.
        ],
        "title_roi": (10, 152, 383, 242),  # LOGO ascii + HALO, excluindo "//PLAYER"+cursor (y>242)
        "glow_t": 18,
        "glow_bound": 1300,         # known-good dev=842 (box-drawing infla a lit-area legit); ~1.5x; LOGO sumir/mover desvia milhares
        "cover": None,              # Boot nao tem capa -> sem check COVER
    },
}


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


def _apply_masks(im, masks):
    im = im.copy()
    d = ImageDraw.Draw(im)
    for m in masks:
        d.rectangle(m, fill=(0, 0, 0))
    return im


def structural_ae(a, b, p):
    return _neigh_ae(_apply_masks(a, p["masks"]), _apply_masks(b, p["masks"]), p["tol"])


def cover_ae(a, b, p):
    roi = p["cover"]["roi"]
    return _neigh_ae(a.crop(roi), b.crop(roi), p["cover"]["tol"])


def glow_extent(im, p):
    """Area iluminada (green > glow_t) na ROI do texto."""
    g = im.split()[1].crop(p["title_roi"])
    t = p["glow_t"]
    return sum(1 for v in g.get_flattened_data() if v > t)


def _load(path, who, p):
    try:
        im = Image.open(path).convert("RGB")
    except Exception as e:
        print(f"::error::A/B FAIL — nao abri o PNG {who} '{path}': {e}")
        sys.exit(1)
    want = (p["w"], p["h"])
    if im.size != want:
        print(f"::error::A/B FAIL — {who} tem {im.size}, esperado {want} "
              f"(iOS ImageComposeScene e Android ComposeView tem que casar tamanho/densidade).")
        sys.exit(1)
    return im


def main():
    if len(sys.argv) not in (3, 4):
        print("uso: python3 ab/ab_diff.py <android.png> <ios.png> [perfil]")
        sys.exit(2)
    name = (sys.argv[3] if len(sys.argv) == 4 else "nowplaying").lower()
    p = PROFILES.get(name)
    if p is None:
        print(f"::error::perfil desconhecido '{name}'. Use um de: {', '.join(PROFILES)}")
        sys.exit(2)

    android = _load(sys.argv[1], "Android (baseline)", p)
    ios = _load(sys.argv[2], "iOS (render offscreen)", p)

    print(f"A/B perfil: {name} ({p['w']}x{p['h']})")

    ae = structural_ae(android, ios, p)
    glow_dev = abs(glow_extent(ios, p) - glow_extent(android, p))
    ok_struct = ae <= p["ceiling"]
    ok_glow = glow_dev <= p["glow_bound"]
    print(f"A/B check (1) ESTRUTURAL: AE={ae}  TETO={p['ceiling']}  TOL={p['tol']}/canal  "
          f"vizinhanca=±1px -> {'PASS' if ok_struct else 'FAIL'}")
    print(f"      mascaras: {p['masks']}")
    print(f"A/B check (2) GLOW: desvio_lit-area(green>{p['glow_t']}, ROI={p['title_roi']})={glow_dev}  "
          f"BOUND={p['glow_bound']}  -> {'PASS' if ok_glow else 'FAIL'}")

    ok_cover = True
    cov = None
    if p["cover"] is not None:
        cov = cover_ae(android, ios, p)
        ok_cover = cov <= p["cover"]["ceiling"]
        print(f"A/B check (3) COVER: AE(ROI={p['cover']['roi']}, TOL={p['cover']['tol']}/canal, "
              f"viz=±1px)={cov}  TETO={p['cover']['ceiling']}  -> {'PASS' if ok_cover else 'FAIL'}")

    if ok_struct and ok_glow and ok_cover:
        print(f"A/B PASS ({name}) — layout fiel e texto/glow preservado"
              + (" E capa 1-bit fiel" if p["cover"] is not None else "") + " (iOS x Android).")
        sys.exit(0)
    if not ok_struct:
        print(f"::error::A/B FAIL ({name}, estrutural) — {ae} > {p['ceiling']} pixels divergentes "
              f"fora das mascaras. Layout/posicao quebrou.")
    if not ok_glow:
        print(f"::error::A/B FAIL ({name}, glow) — extensao do verde na ROI desviou {glow_dev} > "
              f"{p['glow_bound']} do baseline. Baixe o artifact e compare.")
    if not ok_cover:
        print(f"::error::A/B FAIL ({name}, cover) — AE da capa {cov} > {p['cover']['ceiling']} na "
              f"ROI {p['cover']['roi']}. A capa dithered divergiu (sample/decode/dither mudou).")
    sys.exit(1)


if __name__ == "__main__":
    main()
