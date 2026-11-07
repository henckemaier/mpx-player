package com.mpx.player.ui

import com.mpx.player.model.UserSettings
import com.mpx.player.ui.screens.SettingsActions

// Dados DETERMINÍSTICOS da tela CONFIG p/ o gate visual A/B offscreen (P7 fatia settings c2).
// Reusados pelo harness iOS (SettingsScreenRenderTest) e pelo baseline Android
// (AbSettingsBaselineActivity) — MESMOS pixels em toda execução, dos dois lados (Android × iOS/Skia).
//
// DETERMINISMO (watch-items (e)/(g) do despacho):
//  - settings = UserSettings() (DEFAULT): theme=0 (fósforo NÃO-custom → o ColorEditor dinâmico de
//    canais R/G/B NÃO renderiza, evitando a SegmentBar/sliders) e glow=true (o PREVIEW "SIGNAL//
//    DECAY_" é o ÚNICO Txt(glow=true) da seção CORES → o halo do text-shadow RENDERIZA e vira o
//    check GLOW real). scanlines/cursor=true (o cursor piscante do header é o único ponto animado —
//    no 1º frame vale "_", mascarado no perfil).
//  - counts/labels FIXOS e pré-formatados (ZERO java/BuildConfig no comum): sem relógio, sem I/O.
//  - SEM Spectrum, SEM capa assíncrona: a seção CORES (initialOpen=2) só tem Canvas/texto puros.
//  - O par A/B renderiza o MESMO conteúdo dos dois lados: slots Android (BAIXAR/IMPORTAR/UPDATE/
//    SAÍDA) = null (sumem no iOS de qualquer forma), onReboot/onCheckUpdate = null (linha some).

/**
 * SettingsUi FIXO p/ o gate A/B. `settings = UserSettings()` (ver nota acima: theme=0 + glow=true).
 * `files`/`newFiles`/`lrcCount` fixos e `lastScanLabel`/`appVersion` pré-formatados determinísticos
 * (o comum não formata data/versão — aqui chegam prontos, como o host Android injetaria). `message=
 * null` → sem a faixa accent de mensagem (que deslocaria o layout).
 */
val sampleSettingsUi = SettingsUi(
    settings = UserSettings(),
    files = 128,
    scanning = false,
    newFiles = 4,
    lrcCount = 37,
    message = null,
    lastScanLabel = "2026-01-01 00:00",
    appVersion = "1.0.0",
)

/** Ações no-op: o harness/baseline só renderizam — nenhum callback dispara comportamento. */
val noopSettingsActions = SettingsActions(
    onSetTheme = {},
    onSetCustomColor = {},
    onSetOled = {},
    onSetFont = {},
    onSetCover = {},
    onToggleScanlines = {},
    onToggleGlow = {},
    onToggleCursor = {},
    onToggleMono = {},
    onToggleCrossfade = {},
    onClearMessage = {},
    onScan = {},
)
