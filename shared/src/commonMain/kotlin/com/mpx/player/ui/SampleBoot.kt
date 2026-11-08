package com.mpx.player.ui

// Dados DETERMINISTICOS da tela BOOT (splash/BIOS) p/ o gate visual A/B offscreen (P7 fatia boot c2)
// — 8o par (o ULTIMO, 4/4). Reusados pelo harness iOS (BootScreenRenderTest) e pelo baseline Android
// (AbBootBaselineActivity) — MESMOS pixels em toda execucao, dos dois lados (Android x iOS/Skia).
// COMPARTILHADO = zero drift.
//
// FRAME TERMINAL (decisao do Maestro): stepOverride=6 + barOverride=20 -> os dois LaunchedEffect da
// BootScreen NAO rodam (guard `if (override == null)`), a tela usa os valores FIXOS -> o frame e o
// estado FINAL/mais rico (ramo "ready"): 6 linhas de log "[ OK ]"/"[WAIT]"/"[NOPE]" + USER_PROFILE
// LOADED + SegmentBar(20,20) cheia + "100%" + botao "[ PRESS ENTER ]" filled; o botao GRANT nao
// aparece (hasPermission=true). Determinismo igual ao isPlaying=FALSE do npfull: nada anima no 1o
// frame alem do blinkingCursor do "//PLAYER" (display 24sp), que arranca "_" (on=true) nos DOIS lados
// -> provavelmente no-op no pixel-diff (a decisao de mascara sai do c3, MEDINDO).
//
// fileCount=12345 -> fmtThousands -> "12.345" (GATEIA o helper puro fmtThousands do CrtKit no diff).
// eqPreset="FLAT" (a tela faz .lowercase() -> "flat"). version="1.0" (iOS passa fixo, nao BuildConfig).

/** hasPermission FIXO: true -> ramo "ready" (sem botao GRANT). */
const val sampleBootHasPermission = true

/** scanned FIXO: true -> INDEX [ OK ] + USER_PROFILE LOADED (nao "LOADING..."). */
const val sampleBootScanned = true

/** fileCount FIXO exercendo o separador de milhar: 12345 -> "12.345". */
const val sampleBootFileCount = 12345

/** Preset do EQ p/ a linha "> EQ PRESET flat" (a tela faz .lowercase()). */
const val sampleBootEqPreset = "FLAT"

/** Versao FIXA (iOS passa "1.0"; determinismo — nao depende de BuildConfig). */
const val sampleBootVersion = "1.0"

/** step FIXO = 6 -> ready (todas as 6 linhas de log). Trava o LaunchedEffect do step. */
const val sampleBootStepOverride = 6

/** bar FIXO = 20 -> barra cheia / 100%. Trava o LaunchedEffect da barra. */
const val sampleBootBarOverride = 20
