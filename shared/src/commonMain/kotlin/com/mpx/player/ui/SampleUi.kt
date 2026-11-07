package com.mpx.player.ui

import com.mpx.player.model.Track
import com.mpx.player.shared.playback.RepeatMode

// Dados DETERMINÍSTICOS p/ a fatia fina de UI (P7): reusados pelo host iOS (MainViewController)
// e pelo harness de render A/B offscreen (iosSimulatorArm64Test). O determinismo é o que torna
// o A/B reproduzível — mesmos pixels em toda execução, dos dois lados (Android × iOS/Skia).

/** Faixa fake fixa. dosPath derivado = C:\MUSIC\NEON.MP3 (relativePath/displayName em UPPER). */
val sampleTrack = Track(
    id = 4,
    uri = "file:///music/neon.mp3",
    title = "NEON DRIVE",
    artist = "OUTRUN",
    album = "MIDNIGHT PROTOCOL",
    albumId = 1,
    durationMs = 213_000,
    displayName = "NEON.MP3",
    relativePath = "MUSIC/",
    mimeType = "audio/mpeg",
    bitrate = 320,
    trackNumber = 4,
    year = 1986,
)

/** PlayerUi fixo: tocando, faixa 4 de 12 (→ "TRK 04/12"), ~1:12 de 3:33. */
val sampleUi = PlayerUi(
    current = sampleTrack,
    queue = List(12) { i ->
        if (i == 3) sampleTrack
        else sampleTrack.copy(id = i.toLong(), title = "TRACK ${i + 1}", displayName = "T${i + 1}.MP3")
    },
    index = 3,
    isPlaying = true,
    positionMs = 72_000,
    durationMs = 213_000,
    shuffle = false,
    repeat = RepeatMode.OFF,
    sourceLabel = "LIBRARY",
)

/** TechInfo fixa p/ o A/B: alimenta FMT/RATE/extra do NpCoverMeta de forma determinística. */
val sampleTech = TechInfo(format = "MP3", bitrate = "320K", sampleRate = "44.1KHZ", extra = "STEREO")

/** Ações no-op: o painel de amostra não dispara comportamento (host/harness só renderizam). */
val noopActions = NowPlayingActions(
    toggle = {},
    next = {},
    previous = {},
    seek = {},
    shuffle = {},
    repeat = {},
    setVolume = {},
    mute = {},
    openLibrary = {},
    toggleFavorite = {},
    addToPlaylist = {},
)
