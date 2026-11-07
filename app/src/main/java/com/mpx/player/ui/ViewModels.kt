package com.mpx.player.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mpx.player.AndroidAppContainer
import com.mpx.player.MpxApp
import com.mpx.player.model.UserSettings
import com.mpx.player.shared.playback.toPlaybackItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Composable
inline fun <reified VM : ViewModel> mpxViewModel(crossinline create: (AndroidAppContainer) -> VM): VM =
    viewModel(factory = viewModelFactory { initializer { create((this[APPLICATION_KEY] as MpxApp).container) } })

private fun <T> kotlinx.coroutines.flow.Flow<T>.state(vm: ViewModel, initial: T): StateFlow<T> =
    stateIn(vm.viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

// ============================================================ PLAYER (Now Playing + Fila)

// PlayerUi foi movido para :shared/commonMain (com.mpx.player.ui.PlayerUi) na fatia fina de UI
// (P7). Na fatia "estado real" (P7), PlayerViewModel + TechInfo + a interface TechInfoReader
// também foram para :shared/commonMain (com.mpx.player.ui) — mesmo pacote/FQN, resolvem no :app
// sem import. O único trecho Android-only (ler metadados técnicos via MediaMetadataRetriever)
// virou AndroidTechInfoReader (com.mpx.player.data), injetado no VM pela factory (MainActivity).

// ============================================================ BIBLIOTECA

// LibFilter/PlaylistUi/OpenedPlaylist/LibraryUi + LibraryViewModel foram para :shared/commonMain
// na fatia LIBRARY (P7): tipos em com.mpx.player.ui (LibraryUi.kt), projeção pura em
// projectLibrary (LibraryProjection.kt) e o VM de BROWSE em com.mpx.player.ui.LibraryViewModel
// (mesmo FQN). A superfície Android-only (edição de playlist/faixa + exclusão de arquivo via
// Uri/overrides/IntentSender) virou a subclasse AndroidLibraryViewModel (com.mpx.player.ui),
// construída pela factory mpxViewModel.

// ============================================================ EQ

// EqViewModel foi movido para :shared/commonMain (com.mpx.player.ui.EqViewModel) — passo 6, fatia 1.

// ============================================================ CONFIG

data class SettingsUi(
    val settings: UserSettings = UserSettings(),
    val files: Int = 0,
    val scanning: Boolean = false,
    val newFiles: Int = 0,
    val lrcCount: Int = 0,
    val message: String? = null,
)

/** Progresso da busca de letras faltantes no LRCLIB. */
data class LyricsSyncUi(
    val running: Boolean = false,
    val done: Int = 0,
    val total: Int = 0,
    val found: Int = 0,
    val result: String? = null,
)

class SettingsViewModel(private val app: AndroidAppContainer) : ViewModel() {
    private val lrcCount = MutableStateFlow(0)
    private val message = MutableStateFlow<String?>(null)

    val ui: StateFlow<SettingsUi> = combine(app.settings.settings, app.library.state, lrcCount, message) { s, lib, n, msg ->
        SettingsUi(s, lib.tracks.size, lib.scanning, lib.newSinceLastScan, n, msg)
    }.state(this, SettingsUi())

    init {
        viewModelScope.launch {
            app.settings.settings.map { it.lrcTreeUri }.distinctUntilChanged().collect { lrcCount.value = app.lyrics.countLrc(it) }
        }
    }

    private fun run(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    fun setTheme(i: Int) = run { app.settings.setTheme(i) }
    fun setCustomColor(argb: Int) = run { app.settings.setCustomColor(argb) }
    fun setOled(v: Boolean) = run { app.settings.setOled(v) }
    fun setFont(i: Int) = run { app.settings.setFont(i) }
    fun setCover(i: Int) = run { app.settings.setCover(i) }
    fun toggleScanlines(cur: UserSettings) = run { app.settings.setScanlines(!cur.scanlines) }
    fun toggleGlow(cur: UserSettings) = run { app.settings.setGlow(!cur.glow) }
    fun toggleCursor(cur: UserSettings) = run { app.settings.setCursor(!cur.cursor) }
    fun toggleMono(cur: UserSettings) = run { app.settings.setMonoCovers(!cur.monoCovers) }
    fun toggleCrossfade(cur: UserSettings) = run { app.settings.setCrossfade(!cur.crossfade) }

    fun scan() = run {
        app.library.scan()
        app.lyrics.invalidate()
        app.settings.setLastScan(System.currentTimeMillis())
    }

    fun setLrcFolder(uri: Uri) = run {
        app.lyrics.invalidate()
        app.settings.setLrcTree(uri.toString())
    }

    fun importPlaylist(uri: Uri) = run {
        val pl = app.playlistImporter.import(uri, app.library.state.value.tracks)
        if (pl.tracks.isEmpty()) {
            message.value = "ERRO: NENHUMA FAIXA ENCONTRADA EM ${pl.name.uppercase()}"
        } else {
            app.settings.incPlaylists()
            app.audioPlayer.setQueue(pl.tracks.map { it.toPlaybackItem() }, 0, pl.name)
            app.audioPlayer.play()
            message.value = "OK: ${pl.tracks.size} FAIXAS CARREGADAS" + if (pl.missing > 0) " · ${pl.missing} AUSENTES" else ""
        }
    }

    fun clearMessage() { message.value = null }

    // ---- Downloader (YouTube Music → MP3 + letra + capa)
    val download = app.downloader.state

    fun startDownload(url: String) = run { app.downloader.download(url) }
    fun cancelDownload() = app.downloader.cancel()
    fun updateYtDlp() = run { app.downloader.updateEngine() }
    fun clearDownload() = app.downloader.reset()

    // ---- Sincronizar letras faltantes (LRCLIB), sem rebaixar o áudio
    private val _lyricsSync = MutableStateFlow(LyricsSyncUi())
    val lyricsSync: StateFlow<LyricsSyncUi> = _lyricsSync
    private var lyricsJob: kotlinx.coroutines.Job? = null

    fun syncLyrics() {
        if (_lyricsSync.value.running) return
        lyricsJob = viewModelScope.launch {
            val tracks = app.library.state.value.tracks
            if (tracks.isEmpty()) {
                _lyricsSync.value = LyricsSyncUi(result = "BIBLIOTECA VAZIA · ESCANEIE PRIMEIRO")
                return@launch
            }
            _lyricsSync.value = LyricsSyncUi(running = true, total = tracks.size)
            try {
                val found = app.lyrics.syncMissing(tracks) { done, total, f ->
                    _lyricsSync.value = _lyricsSync.value.copy(done = done, total = total, found = f)
                }
                _lyricsSync.value = LyricsSyncUi(total = tracks.size, found = found, result = "OK: $found LETRAS BAIXADAS")
            } catch (e: kotlinx.coroutines.CancellationException) {
                _lyricsSync.value = _lyricsSync.value.copy(running = false, result = "CANCELADO · ${_lyricsSync.value.found} BAIXADAS")
                throw e
            }
        }
    }

    fun cancelLyricsSync() { lyricsJob?.cancel() }
    fun clearLyricsSync() { _lyricsSync.value = LyricsSyncUi() }

    // ---- Auto-update do app (GitHub Releases)
    val update = app.updater.state

    fun checkUpdate() = run { app.updater.check() }
    fun installUpdate() = run { app.updater.downloadAndInstall() }
    fun clearUpdate() = app.updater.reset()
}
