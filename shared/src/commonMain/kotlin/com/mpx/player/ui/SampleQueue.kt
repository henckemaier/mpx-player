package com.mpx.player.ui

import com.mpx.player.model.Track
import com.mpx.player.ui.screens.QueueActions

// Dados DETERMINISTICOS da tela QUEUE p/ o gate visual A/B offscreen (P7 fatia queue c2).
// Reusados pelo harness iOS (QueueScreenRenderTest) e pelo baseline Android (AbQueueBaselineActivity)
// — MESMOS pixels em toda execucao, dos dois lados (Android x iOS/Skia). COMPARTILHADO = zero drift.
//
// DETERMINISMO (watch-items (a)/(b) do despacho):
//  - COVER: os Tracks NAO tem capa (coverOverride=null) e o MpxTheme usa UserSettings() (cover=0).
//    No [com.mpx.player.ui.components.Cover], coverStyle==0 CURTO-CIRCUITA p/ art=null SINCRONO (sem
//    tocar o loader, sem produceState async) -> cai na GeneratedCover(disc=true), que e um Canvas
//    deterministico (variante 0 fixa p/ disc). Mesmo mecanismo da Library (PlaylistCover(null) ->
//    GeneratedCover). Logo NAO precisa LocalCoverLoader explicito: o default ja e NullAlbumArtSource
//    e o coverStyle=0 nem chega a chama-lo. Render de 1 frame estavel.
//  - index=1 (<=2) -> o `if (ui.index > 2) animateScrollToItem` do QueueScreen NAO dispara -> a
//    LazyColumn fica no TOPO (offset 0, t=0), deterministica entre runs.
//  - current=queue[1] (TOCANDO) -> row 1 destacada (bg accent, "▶ ", onAccent); row 0 "✓ "/dim
//    (ja tocada); rows 2..5 "" /accent (pendentes) -> cobre os 3 estados de row no mesmo frame.
//  - 6 Tracks FIXAS: displayName variados + durationMs distintos + album/artist/format fixos.

private fun qTrack(
    id: Long,
    displayName: String,
    durationMs: Long,
    mimeType: String,
    trackNumber: Int,
) = Track(
    id = id,
    uri = "mpx://queue/$id",
    title = displayName.substringBeforeLast('.'),
    artist = "MPX SYSTEM",
    album = "NULL SECTOR",
    albumId = 42L,
    durationMs = durationMs,
    displayName = displayName,
    relativePath = "MUSIC/NULL_SECTOR/",
    mimeType = mimeType,
    bitrate = 320,
    trackNumber = trackNumber,
    year = 2026,
    coverOverride = null,
)

private val sampleQueueTracks = listOf(
    qTrack(1, "NIGHTDRIVE.MP3", 225_000, "audio/mpeg", 1),
    qTrack(2, "OUTRUN_CITY.FLAC", 198_000, "audio/flac", 2),
    qTrack(3, "NEON_HIGHWAY.MP3", 243_000, "audio/mpeg", 3),
    qTrack(4, "CHROME_HEART.FLAC", 312_000, "audio/flac", 4),
    qTrack(5, "LASER_GRID.MP3", 176_000, "audio/mpeg", 5),
    qTrack(6, "SUNSET_BLVD.AAC", 207_000, "audio/mp4", 6),
)

/** PlayerUi FIXO p/ o gate A/B da QUEUE: 6 faixas, a de indice 1 TOCANDO, lista do topo. */
val sampleQueueUi = PlayerUi(
    current = sampleQueueTracks[1],
    queue = sampleQueueTracks,
    index = 1,
    isPlaying = true,
    positionMs = 45_000,
    durationMs = sampleQueueTracks[1].durationMs,
    sourceLabel = "SYNTHWAVE_NIGHTS",
)

/** Acoes no-op: o harness/baseline so renderizam — nenhum callback dispara comportamento. */
val noopQueueActions = QueueActions(
    pick = {},
    shuffle = {},
    clear = {},
    play = {},
)
