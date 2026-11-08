package com.mpx.player.ui

import com.mpx.player.model.LyricLine
import com.mpx.player.model.Playlist
import com.mpx.player.model.Track
import com.mpx.player.shared.playback.RepeatMode
import com.mpx.player.shared.playback.VolumeState

// Dados DETERMINISTICOS da tela NOW PLAYING CHEIA p/ o gate visual A/B offscreen (P7 fatia
// nowplaying-cheia c2) — 7o par. Reusados pelo harness iOS (NowPlayingFullRenderTest) e pelo
// baseline Android (AbNowPlayingFullBaselineActivity) — MESMOS pixels em toda execucao, dos dois
// lados (Android x iOS/Skia). COMPARTILHADO = zero drift.
//
// INSIGHT que torna o par BARATO (decisao do Maestro): com isPlaying=FALSE a tela-cheia fica
// QUASE ESTATICA no 1o frame — so os 2 blinkingCursor (titulo + cmdlog) piscam. DETERMINISMO:
//  - isPlaying=FALSE -> o [com.mpx.player.ui.components.Spectrum] NAO roda o LaunchedEffect de tick
//    (barras em repouso: v=6 fixo, medidores L/R vazios) -> ESTATICO.
//  - console COLAPSADO: o Animatable `expand` nasce em 0f (sem animacao disparada) -> standingVolume
//    = false (sem VerticalVolume); aba = CMD.LOG (lyricMode rememberSaveable=false por default) ->
//    sem withFrameMillis de lyrics. Dialog do picker = FECHADO (showPicker=false por default).
//  - COVER: o Track NAO tem capa (coverOverride=null) e o MpxTheme usa UserSettings() (cover=0) ->
//    no [com.mpx.player.ui.components.Cover], coverStyle==0 CURTO-CIRCUITA p/ art=null SINCRONO (sem
//    loader async) -> GeneratedCover(disc=true), Canvas deterministico (variante 0). Mesmo mecanismo
//    de Queue/Library -> NAO precisa LocalCoverLoader explicito.
//  - basicMarquee do titulo/subtitulo em t=0 -> offset 0 no 1o frame (initialDelay 2-3s nao venceu).
// Os UNICOS bits animados = os 2 blinkingCursor; o `_` arranca em "_" (on=true) nos DOIS lados no
// frame 0, mas o do titulo e VT323 40sp glow=true (halo de AA pode divergir) -> mascarado no perfil,
// mesma convencao do par 'nowplaying' (painel). Ver ab/ab_diff.py perfil 'nowplaying-full'.

/** Faixa fake fixa da tela-cheia. dosPath derivado = C:\MUSIC\NIGHT_CALL\MIDNIGHT_PROTOCOL.FLAC. */
val sampleNpFullTrack = Track(
    id = 7,
    uri = "file:///music/midnight_protocol.flac",
    title = "MIDNIGHT PROTOCOL",
    artist = "NIGHT CALL",
    album = "SIGNAL DECAY",
    albumId = 7L,
    durationMs = 258_000,       // 4:18
    displayName = "MIDNIGHT_PROTOCOL.FLAC",
    relativePath = "MUSIC/NIGHT_CALL/",
    mimeType = "audio/flac",
    bitrate = 1041,
    trackNumber = 3,
    year = 1987,
    coverOverride = null,
)

/**
 * PlayerUi FIXO p/ o gate A/B da tela-cheia: faixa 3 de 9 (-> "TRK 03/09"), PAUSADA (isPlaying=false,
 * CRITICO: congela o Spectrum), ~1:36 de 4:18.
 */
val sampleNpFullUi = PlayerUi(
    current = sampleNpFullTrack,
    queue = List(9) { i ->
        if (i == 2) sampleNpFullTrack
        else sampleNpFullTrack.copy(
            id = i.toLong(),
            title = "TRACK ${i + 1}",
            displayName = "T${i + 1}.FLAC",
        )
    },
    index = 2,
    isPlaying = false,          // CRITICO: sem tick -> Spectrum em repouso (estatico)
    positionMs = 96_000,        // 1:36
    durationMs = 258_000,
    shuffle = false,
    repeat = RepeatMode.OFF,
    sourceLabel = "NIGHT_CALL",
)

/** TechInfo fixa: alimenta FMT/RATE/extra do NpCoverMeta e a linha DECODE da CMD.LOG. */
val sampleNpFullTech = TechInfo(format = "FLAC", bitrate = "1041K", sampleRate = "44.1KHZ", extra = "STEREO/16")

/** Volume fixo: 9/15 -> 12 segmentos acesos ("060"), sem mute. */
val sampleNpFullVolume = VolumeState(level = 9, max = 15, muted = false)

/** Letras fixas (NAO renderizadas na aba CMD.LOG, mas passam p/ a tela). */
val sampleNpFullLyrics = listOf(
    LyricLine(0, "SYSTEM BOOT"),
    LyricLine(12_000, "NEON RAIN ON CHROME"),
    LyricLine(26_000, "ENGINE HUMS IN THE DARK"),
    LyricLine(41_000, "MIDNIGHT PROTOCOL ENGAGED"),
    LyricLine(58_000, "DRIVE UNTIL THE SIGNAL DECAYS"),
)

/** Playlists fixas (alimentam o PlaylistPickerDialog, que fica FECHADO no par). */
val sampleNpFullPlaylists = listOf(
    Playlist(id = "favoritos", name = "FAVORITOS", locked = true),
    Playlist(id = "synthwave", name = "SYNTHWAVE NIGHTS"),
    Playlist(id = "outrun", name = "OUTRUN"),
)

/** Favorito fixo do par (false -> botao de marcador em estado nao-favorito). */
const val sampleNpFullFavorite = false

/** Acoes no-op: o harness/baseline so renderizam — nenhum callback dispara comportamento. */
val noopNpFullActions = NowPlayingActions(
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
