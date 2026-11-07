package com.mpx.player.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mpx.player.data.LibraryState
import com.mpx.player.data.PlaylistLogic
import com.mpx.player.di.AppContainer
import com.mpx.player.model.LyricLine
import com.mpx.player.model.Playlist
import com.mpx.player.model.Track
import com.mpx.player.shared.playback.PlaybackState
import com.mpx.player.shared.playback.RepeatMode
import com.mpx.player.shared.playback.VolumeState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Infos técnicas da faixa (formato/bitrate/sample-rate) mostradas no Now Playing. Puro/comum. */
data class TechInfo(val format: String = "---", val bitrate: String = "---", val sampleRate: String = "---", val extra: String = "")

/**
 * Lê metadados técnicos de uma faixa — único ponto Android-only do antigo PlayerViewModel,
 * agora atrás de interface para o VM virar comum. Android: MediaMetadataRetriever
 * (`AndroidTechInfoReader`); iOS: AVURLAsset (follow-up), por ora no-op com só o `format`.
 */
interface TechInfoReader {
    suspend fun read(track: Track): TechInfo
}

// ============================================================ PLAYER (Now Playing + Fila)

/**
 * ViewModel do Now Playing + fila — migrado para o `commonMain` (P7 fatia "estado real").
 * Molde do [EqViewModel]: `(app: AppContainer) : ViewModel()` + `viewModelScope` +
 * `.state(initial)` sobre `stateIn(WhileSubscribed)`. O único trecho Android-only (ler
 * metadados técnicos) saiu para [TechInfoReader] injetado (`techReader`).
 *
 * API pública inalterada (ui/tech/lyrics/volume/favorite/playlists + ações) → NowPlayingScreen
 * segue igual (mesmo FQN com.mpx.player.ui.PlayerViewModel), sem regressão de tela.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModel(
    private val app: AppContainer,
    private val techReader: TechInfoReader,
) : ViewModel() {

    private val player = app.audioPlayer

    val ui: StateFlow<PlayerUi> = combine(player.state, app.library.state) { p: PlaybackState, lib: LibraryState ->
        // queueIds vem em ordem de reprodução (contrato); resolvemos via key (id = Track.key).
        val tracks = p.queueIds.mapNotNull { lib.trackByKey(it) }
        PlayerUi(
            current = p.currentId?.let { lib.trackByKey(it) },
            queue = tracks,
            index = p.index,
            isPlaying = p.isPlaying,
            positionMs = p.positionMs,
            durationMs = if (p.durationMs > 0) p.durationMs else (p.currentId?.let { lib.trackByKey(it)?.durationMs } ?: 0),
            shuffle = p.shuffle,
            repeat = p.repeat,
            sourceLabel = p.sourceLabel,
        )
    }.state(PlayerUi())

    private val currentTrack = ui.map { it.current }.distinctUntilChanged { a, b -> a?.id == b?.id }

    val lyrics: StateFlow<List<LyricLine>> = combine(
        currentTrack,
        app.settings.lrcTreeUri.distinctUntilChanged(),
        app.lyrics.version,
    ) { t, tree, _ -> t to tree }
        .flatMapLatest { (t, tree) -> if (t == null) flowOf(emptyList()) else flow { emit(app.lyrics.load(t, tree)) } }
        .state(emptyList())

    val tech: StateFlow<TechInfo> = currentTrack
        .flatMapLatest { t -> if (t == null) flowOf(TechInfo()) else flow { emit(techReader.read(t)) } }
        .state(TechInfo())

    val volume: StateFlow<VolumeState> = player.volume
    // Sem loop de pollVolume aqui: o AndroidAudioPlayer já pollueia o volume do sistema internamente.

    fun toggle() = player.toggle()
    fun next() = player.next()
    fun previous() = player.previous()
    fun seek(f: Float) = player.seekToFraction(f)
    fun toggleShuffle() = player.setShuffle(!player.state.value.shuffle)
    // Mesma ordem de ciclo do antigo PlayerController.cycleRepeat: OFF -> ALL -> ONE -> OFF.
    fun cycleRepeat() = player.setRepeat(
        when (player.state.value.repeat) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            else -> RepeatMode.OFF
        },
    )
    fun setVolume(f: Float) = player.setVolume(f)
    fun toggleMute() = player.toggleMute()
    fun playIndex(i: Int) = player.seekToIndex(i)
    fun shuffleQueue() = player.shuffleQueue()
    // Botão "limpar fila": mantém a faixa atual tocando (= comportamento de hoje).
    fun clearQueue() = player.clearUpcoming()
    fun play() = player.play()

    // ---- favoritos / playlists (botão de marcador no Now Playing)
    val favorite: StateFlow<Boolean> = combine(currentTrack, app.playlists.playlists) { t, pls ->
        t != null && PlaylistLogic.isFavorite(pls, t.key)
    }.state(false)

    val playlists: StateFlow<List<Playlist>> = app.playlists.playlists

    fun toggleFavorite() { ui.value.current?.let { app.playlists.toggleFavorite(it.key) } }
    fun addCurrentToPlaylist(id: String) { ui.value.current?.let { app.playlists.addTrack(id, it.key) } }

    /** Mesmo padrão do `.state()` do :app, mas sobre o viewModelScope multiplataforma (molde EqViewModel). */
    private fun <T> Flow<T>.state(initial: T): StateFlow<T> =
        stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)
}
