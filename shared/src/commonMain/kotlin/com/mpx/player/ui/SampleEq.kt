package com.mpx.player.ui

import com.mpx.player.model.EqSettings
import com.mpx.player.ui.screens.EqActions

// Dados DETERMINISTICOS da tela EQUALIZER p/ o gate visual A/B offscreen (P7 fatia eq c2).
// Reusados pelo harness iOS (EqScreenRenderTest) e pelo baseline Android (AbEqBaselineActivity) —
// MESMOS pixels em toda execucao, dos dois lados (Android x iOS/Skia). COMPARTILHADO = zero drift.
//
// DETERMINISMO (watch-item (c) do despacho): a EqScreen e ESTATICA (sem cursor/marquee/spectrum/
// animacao) -> o 1o frame (t=0) JA e o estado final. O drag das bandas e so input (pointerInput),
// nao afeta o render. Logo nada a assentar; o PNG e reproduzivel entre runs.
//
// O fixture e escolhido p/ EXERCITAR o render inteiro:
//  - enabled=true -> as barras usam c.accent/c.bright (nao c.line do desabilitado).
//  - preset="CUSTOM" -> a 5a pilula da PresetRow fica 'filled' (1 preset destacado); coerente com
//    levels custom (um VM real derivaria levels do preset nomeado; aqui e fixo e proprio).
//  - levels = curva "sorriso" NAO-flat (graves/agudos altos, medios baixos) -> as 10 bandas
//    renderizam ALTURAS DISTINTAS (nao uma parede uniforme), exercitando o mapa de segmentos.
//  - preamp=16 (0..24, centro=12) -> fora do centro -> a SegmentBar mostra fill parcial distinto
//    (preampToDb(16)=+2.0dB -> rotulo "+2.0dB" via fmt1).
//  - bassBoost=true, surround=false, loudness=true -> cobre ToggleRow ON e OFF no mesmo frame.
//  - sampleEqSelected=4 (banda do MEIO) -> a banda 4 ("500" Hz) renderiza destacada (c.bright) e o
//    BandInfo mostra "> BAND 500Hz -4dB" (levels[4]=4 -> levelToDb=-4).

/** EqSettings FIXO p/ o gate A/B (curva "sorriso", preset CUSTOM, 1 toggle ON + 1 OFF, preamp off-center). */
val sampleEqSettings = EqSettings(
    enabled = true,
    preset = "CUSTOM",
    levels = listOf(11, 10, 8, 6, 4, 4, 6, 8, 10, 11),
    custom = listOf(11, 10, 8, 6, 4, 4, 6, 8, 10, 11),
    preamp = 16,
    bassBoost = true,
    surround = false,
    loudness = true,
)

/** Banda destacada do gate A/B: uma do MEIO (c.bright + BandInfo da banda 500Hz). */
const val sampleEqSelected = 4

/** Acoes no-op: o harness/baseline so renderizam — nenhum callback dispara comportamento. */
val noopEqActions = EqActions(
    toggleOn = {},
    preset = {},
    setBand = { _, _ -> },
    preamp = {},
    bass = {},
    surround = {},
    loudness = {},
    reset = {},
    save = {},
)
