package com.mpx.player.ui

import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.mpx.player.AndroidAppContainer
import com.mpx.player.BuildConfig
import com.mpx.player.shared.playback.toPlaybackItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Superfície Android-only de CONFIG — herda o comum ([SettingsViewModel]) e adiciona o que NÃO é
 * portável: import de playlist e pasta de letras (SAF/`Uri`), download do YouTube Music, auto-update
 * do app, busca de letras faltantes (LRCLIB), contagem de `.lrc` e os rótulos formatados
 * ([SettingsUi.lastScanLabel]/[SettingsUi.appVersion]). Fatia SETTINGS (P7): a factory `mpxViewModel`
 * constrói esta classe; o `:app` consome `ui`/ações daqui, o host iOS consome só o comum.
 */
class AndroidSettingsViewModel(
    private val container: AndroidAppContainer,
) : SettingsViewModel(container) {

    private val lrcCount = MutableStateFlow(0)

    override val ui: StateFlow<SettingsUi> =
        combine(container.settings.settings, container.library.state, lrcCount, message) { s, lib, n, msg ->
            SettingsUi(
                settings = s,
                files = lib.tracks.size,
                scanning = lib.scanning,
                newFiles = lib.newSinceLastScan,
                lrcCount = n,
                message = msg,
                lastScanLabel = formatScan(s.lastScan),
                appVersion = "${BuildConfig.VERSION_NAME} (BUILD 2026.10)",
            )
        }.state(SettingsUi())

    init {
        viewModelScope.launch {
            container.settings.settings.map { it.lrcTreeUri }.distinctUntilChanged()
                .collect { lrcCount.value = container.lyrics.countLrc(it) }
        }
    }

    private fun run(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    /** Carimba `lastScan` (relógio do JVM) e invalida as letras, além do (re)scan comum. */
    override fun rescan() = run {
        container.library.scan()
        container.lyrics.invalidate()
        container.settings.setLastScan(System.currentTimeMillis())
    }

    fun setLrcFolder(uri: Uri) = run {
        container.lyrics.invalidate()
        container.settings.setLrcTree(uri.toString())
    }

    fun importPlaylist(uri: Uri) = run {
        val pl = container.playlistImporter.import(uri, container.library.state.value.tracks)
        if (pl.tracks.isEmpty()) {
            message.value = "ERRO: NENHUMA FAIXA ENCONTRADA EM ${pl.name.uppercase()}"
        } else {
            container.settings.incPlaylists()
            container.audioPlayer.setQueue(pl.tracks.map { it.toPlaybackItem() }, 0, pl.name)
            container.audioPlayer.play()
            message.value = "OK: ${pl.tracks.size} FAIXAS CARREGADAS" + if (pl.missing > 0) " · ${pl.missing} AUSENTES" else ""
        }
    }

    // ---- Downloader (YouTube Music → MP3 + letra + capa)
    val download = container.downloader.state

    fun startDownload(url: String) = run { container.downloader.download(url) }
    fun cancelDownload() = container.downloader.cancel()
    fun updateYtDlp() = run { container.downloader.updateEngine() }
    fun clearDownload() = container.downloader.reset()

    // ---- Sincronizar letras faltantes (LRCLIB), sem rebaixar o áudio
    private val _lyricsSync = MutableStateFlow(LyricsSyncUi())
    val lyricsSync: StateFlow<LyricsSyncUi> = _lyricsSync
    private var lyricsJob: Job? = null

    fun syncLyrics() {
        if (_lyricsSync.value.running) return
        lyricsJob = viewModelScope.launch {
            val tracks = container.library.state.value.tracks
            if (tracks.isEmpty()) {
                _lyricsSync.value = LyricsSyncUi(result = "BIBLIOTECA VAZIA · ESCANEIE PRIMEIRO")
                return@launch
            }
            _lyricsSync.value = LyricsSyncUi(running = true, total = tracks.size)
            try {
                val found = container.lyrics.syncMissing(tracks) { done, total, f ->
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
    val update = container.updater.state

    fun checkUpdate() = run { container.updater.check() }
    fun installUpdate() = run { container.updater.downloadAndInstall() }
    fun clearUpdate() = container.updater.reset()

    private fun formatScan(lastScan: Long): String =
        if (lastScan == 0L) "NUNCA" else SimpleDateFormat("dd/MM HH:mm", Locale.US).format(Date(lastScan))
}
