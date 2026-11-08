package com.mpx.player.ui

import com.mpx.player.model.Track
import com.mpx.player.shared.playback.RepeatMode
import com.mpx.player.ui.screens.LibraryActions

// Dados DETERMINÍSTICOS da BIBLIOTECA p/ o gate visual A/B offscreen (P7 fatia library c2).
// Reusados pelo harness iOS (LibraryScreenRenderTest) e pelo baseline Android
// (AbLibraryBaselineActivity) — MESMOS pixels em toda execução, dos dois lados (Android × iOS/Skia).
// Determinismo é o ponto: strings CURTAS (nenhum call-site da LibraryScreen aplica basicMarquee,
// mas nomes curtos evitam wrap/ellipsis e mantêm o layout fixo), SEM capa assíncrona (todas as
// playlists têm photo=null → PlaylistCover cai na GeneratedCover, Canvas PURO resolvido no 1º
// frame) e SEM tempo animado (positionMs fixo). Zero import android.
//
// SOBRE A CAPA DAS PLAYLISTS (watch-item do despacho): PlaylistCover(photo=null, name) NÃO é
// assíncrona no 1º frame — o `produceState` inicia em `null` e, com photo==null, PERMANECE `null`
// (não há I/O), então o ramo desenhado é SEMPRE a [GeneratedCover]: Canvas vetorial puro, variante
// escolhida por `abs(name.hashCode()) % 6`. `String.hashCode()` é idêntico em Kotlin/JVM e
// Kotlin/Native (algoritmo do Java), logo a variante é a MESMA nos dois lados. ÚNICO ramo não
// determinístico seria a variante 5 (desenha texto binário + iniciais com fonte NÃO-mono → jitter
// de AA skiko×Android); por isso os nomes abaixo foram escolhidos p/ cair em variantes vetoriais
// (0 disco, 2 ondas, 4 pixels), NUNCA na 5. Verificado: RETRO→0, ARCADE→2, NEON→4.

/** Faixa curta fixa p/ o mini-player (barra accent no rodapé da LibraryScreen). */
private val sampleLibTrack = Track(
    id = 7,
    uri = "file:///music/night.mp3",
    title = "NIGHT",
    artist = "OUTRUN",
    album = "MIDNIGHT PROTOCOL",
    albumId = 1,
    durationMs = 201_000,
    displayName = "NIGHT.MP3",
    relativePath = "MUSIC/",
    mimeType = "audio/mpeg",
    bitrate = 320,
    trackNumber = 1,
    year = 1986,
)

/**
 * PlayerUi fixo p/ a LibraryScreen: `current != null` + `isPlaying` + `positionMs` fixo → a barra
 * mini-player (fora do `when`, só depende de `player.current`) aparece determinística ("▶ NIGHT —
 * OUTRUN … 3:21"). Título/artista CURTOS p/ não estourar a largura.
 */
val sampleLibraryPlayer = PlayerUi(
    current = sampleLibTrack,
    queue = listOf(sampleLibTrack),
    index = 0,
    isPlaying = true,
    positionMs = 72_000,
    durationMs = 201_000,
    shuffle = false,
    repeat = RepeatMode.OFF,
    sourceLabel = "LIBRARY",
)

/**
 * LibraryUi FIXO no filtro PLAYLISTS (grade = LazyVerticalGrid — o MAIOR risco do watch-item). 3
 * playlists com photo=null (GeneratedCover determinística), nomes curtos, counts fixos, 1 locked
 * (exercita o "♥"). `scanned=true` e `opened=null` → cai direto no [PlaylistGrid].
 */
val sampleLibraryUi = LibraryUi(
    filter = LibFilter.PLAYLISTS,
    query = "",
    ascending = true,
    totalFiles = 42,
    scanned = true,
    opened = null,
    playlists = listOf(
        PlaylistUi(id = "pl-retro", name = "RETRO", photo = null, count = 12, locked = false),
        PlaylistUi(id = "pl-arcade", name = "ARCADE", photo = null, count = 8, locked = false),
        PlaylistUi(id = "pl-neon", name = "NEON", photo = null, count = 23, locked = true),
    ),
)

/** Faixa fixa determinística p/ o smoke do LazyColumn (filtro FAIXAS). */
private fun libTrack(i: Int, fav: Boolean = false) = Track(
    id = i.toLong(),
    uri = "file:///music/t$i.mp3",
    title = "TRACK $i",
    artist = "ARTIST ${(i % 3) + 1}",
    album = "ALBUM ${(i % 2) + 1}",
    albumId = ((i % 2) + 1).toLong(),
    durationMs = 180_000L + i * 7_000L,
    displayName = "T$i.MP3",
    relativePath = "MUSIC/",
    mimeType = "audio/mpeg",
    bitrate = 320,
    trackNumber = i,
    year = 1990 + i,
)

/**
 * LibraryUi no filtro FAIXAS (LazyColumn) p/ o SMOKE: prova que o LazyColumn COMPÕE offscreen no 1º
 * frame (asserção de bytes não-vazios, SEM baseline commitado nesta fatia). 6 faixas fixas, 1
 * tocando (id==7 → casa `sampleLibraryPlayer.current`), 1 favorita.
 */
val sampleLibraryTracksUi: LibraryUi = run {
    val tracks = listOf(libTrack(1), libTrack(2), libTrack(3), libTrack(4), libTrack(5), sampleLibTrack)
    LibraryUi(
        filter = LibFilter.FAIXAS,
        query = "",
        ascending = true,
        totalFiles = 42,
        scanned = true,
        tracks = tracks,
        favoriteKeys = setOf(libTrack(2).key),
    )
}

/** Ações no-op: o harness/baseline só renderizam — nenhum callback dispara comportamento. */
val noopLibActions = LibraryActions(
    onFilter = {},
    onQuery = {},
    onToggleSort = {},
    onPlayAlbum = {},
    onPlayArtist = {},
    onPlayTrack = { _, _ -> },
    onOpenPlaylist = {},
    onClosePlaylist = {},
    onPlayPlaylist = {},
    onRemoveFromPlaylist = { _, _ -> },
    onOpenPlayer = {},
    onTrackLong = {},
    onNewPlaylist = {},
    onEditPlaylist = {},
)
