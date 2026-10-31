package com.mpx.player.ui

import android.app.RecoverableSecurityException
import android.content.IntentSender
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mpx.player.AppContainer
import com.mpx.player.MpxApp
import com.mpx.player.data.LibraryState
import com.mpx.player.data.PlaylistLogic
import com.mpx.player.model.Album
import com.mpx.player.model.Artist
import com.mpx.player.model.EqPresets
import com.mpx.player.model.EqSettings
import com.mpx.player.model.LyricLine
import com.mpx.player.model.Playlist
import com.mpx.player.model.Track
import com.mpx.player.model.UserSettings
import com.mpx.player.playback.PlayerState
import com.mpx.player.playback.RepeatMode
import com.mpx.player.playback.VolumeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
inline fun <reified VM : ViewModel> mpxViewModel(crossinline create: (AppContainer) -> VM): VM =
    viewModel(factory = viewModelFactory { initializer { create((this[APPLICATION_KEY] as MpxApp).container) } })

private fun <T> kotlinx.coroutines.flow.Flow<T>.state(vm: ViewModel, initial: T): StateFlow<T> =
    stateIn(vm.viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

// ============================================================ PLAYER (Now Playing + Fila)

data class PlayerUi(
    val current: Track? = null,
    val queue: List<Track> = emptyList(),
    val index: Int = -1,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val shuffle: Boolean = false,
    val repeat: RepeatMode = RepeatMode.OFF,
    val sourceLabel: String = "",
) {
    val totalMs: Long get() = queue.sumOf { it.durationMs }
    val remainingMs: Long
        get() = queue.drop(index.coerceAtLeast(0)).sumOf { it.durationMs } - positionMs.coerceAtMost(current?.durationMs ?: 0)
}

data class TechInfo(val format: String = "---", val bitrate: String = "---", val sampleRate: String = "---", val extra: String = "")

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModel(private val app: AppContainer) : ViewModel() {

    private val player = app.player

    val ui: StateFlow<PlayerUi> = combine(player.state, app.library.state) { p: PlayerState, lib: LibraryState ->
        val tracks = p.queue.mapNotNull { lib.track(it) }
        PlayerUi(
            current = p.currentId?.let { lib.track(it) },
            queue = tracks,
            index = p.index,
            isPlaying = p.isPlaying,
            positionMs = p.positionMs,
            durationMs = if (p.durationMs > 0) p.durationMs else (p.currentId?.let { lib.track(it)?.durationMs } ?: 0),
            shuffle = p.shuffle,
            repeat = p.repeat,
            sourceLabel = p.sourceLabel,
        )
    }.state(this, PlayerUi())

    private val currentTrack = ui.map { it.current }.distinctUntilChanged { a, b -> a?.id == b?.id }

    val lyrics: StateFlow<List<LyricLine>> = combine(
        currentTrack,
        app.settings.settings.map { it.lrcTreeUri }.distinctUntilChanged(),
        app.lyrics.version,
    ) { t, tree, _ -> t to tree }
        .flatMapLatest { (t, tree) -> if (t == null) flowOf(emptyList()) else flow { emit(app.lyrics.load(t, tree)) } }
        .state(this, emptyList())

    val tech: StateFlow<TechInfo> = currentTrack
        .flatMapLatest { t -> if (t == null) flowOf(TechInfo()) else flow { emit(readTech(t)) } }
        .state(this, TechInfo())

    val volume: StateFlow<VolumeState> = player.volume

    init {
        viewModelScope.launch {
            while (true) {
                player.pollVolume(); delay(1_000)
            }
        }
    }

    private suspend fun readTech(t: Track): TechInfo = withContext(Dispatchers.IO) {
        var rate = "---"
        var bitrate = t.bitrate?.let { "${it / 1000}K" } ?: "---"
        val retr = MediaMetadataRetriever()
        try {
            retr.setDataSource(app.context, t.uri)
            if (bitrate == "---") retr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull()?.let { bitrate = "${it / 1000}K" }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                retr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)?.toIntOrNull()?.let {
                    rate = if (it % 1000 == 0) "${it / 1000}kHz" else String.format(java.util.Locale.US, "%.1fkHz", it / 1000f)
                }
            }
        } catch (_: Exception) {
        } finally {
            runCatching { retr.release() }
        }
        TechInfo(
            format = t.format,
            bitrate = bitrate,
            sampleRate = rate,
            extra = t.year?.let { "ANO - $it" } ?: "TRK - %02d".format(t.trackNumber.coerceAtLeast(1)),
        )
    }

    fun toggle() = player.toggle()
    fun next() = player.next()
    fun previous() = player.previous()
    fun seek(f: Float) = player.seekToFraction(f)
    fun toggleShuffle() = player.toggleShuffle()
    fun cycleRepeat() = player.cycleRepeat()
    fun setVolume(f: Float) = player.setVolume(f)
    fun toggleMute() = player.toggleMute()
    fun playIndex(i: Int) = player.seekToIndex(i)
    fun shuffleQueue() = player.shuffleQueue()
    fun clearQueue() = player.clearQueue()
    fun play() = player.play()

    // ---- favoritos / playlists (botão de marcador no Now Playing)
    val favorite: StateFlow<Boolean> = combine(currentTrack, app.playlists.playlists) { t, pls ->
        t != null && PlaylistLogic.isFavorite(pls, t.key)
    }.state(this, false)

    val playlists: StateFlow<List<Playlist>> = app.playlists.playlists

    fun toggleFavorite() { ui.value.current?.let { app.playlists.toggleFavorite(it.key) } }
    fun addCurrentToPlaylist(id: String) { ui.value.current?.let { app.playlists.addTrack(id, it.key) } }
}

// ============================================================ BIBLIOTECA

enum class LibFilter { PLAYLISTS, ALBUNS, ARTISTAS, FAIXAS }

data class PlaylistUi(val id: String, val name: String, val photo: String?, val count: Int, val locked: Boolean)

data class OpenedPlaylist(
    val id: String,
    val name: String,
    val photo: String?,
    val locked: Boolean,
    val tracks: List<Track>,
)

data class LibraryUi(
    val filter: LibFilter = LibFilter.ALBUNS,
    val query: String = "",
    val ascending: Boolean = true,
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val tracks: List<Track> = emptyList(),
    val totalFiles: Int = 0,
    val scanned: Boolean = false,
    val playlists: List<PlaylistUi> = emptyList(),
    val opened: OpenedPlaylist? = null,
    val favoriteKeys: Set<String> = emptySet(),
)

class LibraryViewModel(private val app: AppContainer) : ViewModel() {
    private val filter = MutableStateFlow(LibFilter.ALBUNS)
    private val query = MutableStateFlow("")
    private val ascending = MutableStateFlow(true)
    private val openedId = MutableStateFlow<String?>(null)

    private data class Inputs(val lib: LibraryState, val f: LibFilter, val q: String, val asc: Boolean)

    val ui: StateFlow<LibraryUi> = combine(
        combine(app.library.state, filter, query, ascending) { lib, f, q, asc -> Inputs(lib, f, q, asc) },
        app.playlists.playlists,
        openedId,
    ) { inp, pls, opened ->
        val (lib, f, q, asc) = inp
        val needle = q.trim().lowercase()
        fun ok(vararg s: String) = needle.isEmpty() || s.any { it.lowercase().contains(needle) }
        val albums = lib.albums.filter { ok(it.title, it.artist) }.let { if (asc) it else it.reversed() }
        val artists = lib.artists.filter { ok(it.name) }.let { if (asc) it else it.reversed() }
        val tracks = lib.tracks.filter { ok(it.title, it.artist, it.album, it.displayName) }
            .sortedBy { it.title.uppercase() }.let { if (asc) it else it.reversed() }
        val playlistUis = pls.map { PlaylistUi(it.id, it.name, it.photo, it.trackKeys.size, it.locked) }
        val openedPl = opened?.let { id ->
            pls.firstOrNull { it.id == id }?.let { p ->
                OpenedPlaylist(p.id, p.name, p.photo, p.locked, lib.tracksForKeys(p.trackKeys))
            }
        }
        val favKeys = pls.firstOrNull { it.id == Playlist.FAVORITES_ID }?.trackKeys?.toSet() ?: emptySet()
        LibraryUi(f, q, asc, albums, artists, tracks, lib.tracks.size, lib.scanned, playlistUis, openedPl, favKeys)
    }.state(this, LibraryUi())

    fun setFilter(f: LibFilter) {
        filter.value = f
        if (f != LibFilter.PLAYLISTS) openedId.value = null
    }
    fun setQuery(q: String) { query.value = q }
    fun toggleSort() = ascending.update { !it }

    fun playAlbum(a: Album) = app.player.play(a.tracks, 0, a.title)
    fun playArtist(a: Artist) = app.player.play(a.tracks, 0, a.name)
    fun playTrack(list: List<Track>, i: Int) = app.player.play(list, i, "/all_tracks")

    // ---- playlists
    fun createPlaylist(name: String, photo: Uri?) = viewModelScope.launch {
        app.playlists.create(name, photo?.let { app.overrides.savePlaylistPhoto(it) })
    }
    fun renamePlaylist(id: String, name: String) = app.playlists.rename(id, name)
    fun deletePlaylist(id: String) {
        app.playlists.delete(id)
        if (openedId.value == id) openedId.value = null
    }
    fun setPlaylistPhoto(id: String, photo: Uri) = viewModelScope.launch {
        app.playlists.setPhoto(id, app.overrides.savePlaylistPhoto(photo))
    }
    fun openPlaylist(id: String) { openedId.value = id }
    fun closePlaylist() { openedId.value = null }
    fun playPlaylist(id: String) {
        val p = app.playlists.playlists.value.firstOrNull { it.id == id } ?: return
        val tracks = app.library.state.value.tracksForKeys(p.trackKeys)
        if (tracks.isNotEmpty()) app.player.play(tracks, 0, "/" + p.name.lowercase())
    }
    fun addToPlaylist(playlistId: String, key: String) = app.playlists.addTrack(playlistId, key)
    fun removeFromPlaylist(playlistId: String, key: String) = app.playlists.removeTrack(playlistId, key)
    fun toggleFavorite(key: String) = app.playlists.toggleFavorite(key)

    // ---- editar faixa (overrides só do MPX)
    fun editTitle(key: String, title: String?) = app.overrides.setTitle(key, title)
    fun editCover(key: String, photo: Uri) = viewModelScope.launch { app.overrides.setCover(key, photo) }
    fun clearCover(key: String) = app.overrides.clearCover(key)
    fun editLyrics(track: Track, text: String) = viewModelScope.launch {
        app.lyrics.save(track, text)
        app.lyrics.invalidate()
    }

    // ---- excluir faixa (apaga o arquivo; UI lança o IntentSender)
    private val _deleteRequests = MutableSharedFlow<IntentSender>(extraBufferCapacity = 1)
    val deleteRequests: SharedFlow<IntentSender> = _deleteRequests.asSharedFlow()
    private var pendingDeleteKey: String? = null

    fun requestDeleteTrack(track: Track) {
        pendingDeleteKey = track.key
        viewModelScope.launch(Dispatchers.IO) {
            val resolver = app.context.contentResolver
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                runCatching { MediaStore.createDeleteRequest(resolver, listOf(track.uri)).intentSender }
                    .onSuccess { _deleteRequests.emit(it) }
                    .onFailure { cancelDelete() }
            } else {
                try {
                    resolver.delete(track.uri, null, null)
                    onDeleteConfirmed()
                } catch (e: RecoverableSecurityException) {
                    _deleteRequests.emit(e.userAction.actionIntent.intentSender)
                } catch (_: Exception) {
                    cancelDelete()
                }
            }
        }
    }

    fun onDeleteConfirmed() {
        val key = pendingDeleteKey ?: return
        pendingDeleteKey = null
        app.playlists.purgeKey(key)
        app.library.rescan()
    }

    fun cancelDelete() { pendingDeleteKey = null }
}

// ============================================================ EQ

class EqViewModel(private val app: AppContainer) : ViewModel() {
    val eq: StateFlow<EqSettings> = app.settings.settings.map { it.eq }.state(this, EqSettings())
    val selectedBand = MutableStateFlow(2)

    private fun edit(t: (EqSettings) -> EqSettings) {
        viewModelScope.launch { app.settings.updateEq(t) }
    }

    fun toggleOn() = edit { it.copy(enabled = !it.enabled) }
    fun pickPreset(name: String) = edit { it.copy(preset = name, levels = EqPresets.levelsFor(name, it.custom)) }
    fun setBand(band: Int, level: Int) {
        selectedBand.value = band
        edit { e -> e.copy(preset = "CUSTOM", levels = e.levels.toMutableList().also { it[band] = level.coerceIn(0, 12) }) }
    }
    fun preamp(delta: Int) = edit { it.copy(preamp = (it.preamp + delta).coerceIn(0, 24)) }
    fun toggleBass() = edit { it.copy(bassBoost = !it.bassBoost) }
    fun toggleSurround() = edit { it.copy(surround = !it.surround) }
    fun toggleLoudness() = edit { it.copy(loudness = !it.loudness) }
    fun reset() = edit { it.copy(preset = "FLAT", levels = EqPresets.FLAT, preamp = 12) }
    fun saveCustom() = edit { it.copy(custom = it.levels, preset = "CUSTOM") }
}

// ============================================================ CONFIG

data class SettingsUi(
    val settings: UserSettings = UserSettings(),
    val files: Int = 0,
    val scanning: Boolean = false,
    val newFiles: Int = 0,
    val lrcCount: Int = 0,
    val message: String? = null,
)

class SettingsViewModel(private val app: AppContainer) : ViewModel() {
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
            app.player.play(pl.tracks, 0, pl.name)
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

    // ---- Auto-update do app (GitHub Releases)
    val update = app.updater.state

    fun checkUpdate() = run { app.updater.check() }
    fun installUpdate() = run { app.updater.downloadAndInstall() }
    fun clearUpdate() = app.updater.reset()
}
