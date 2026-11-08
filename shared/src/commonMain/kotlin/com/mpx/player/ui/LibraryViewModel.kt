package com.mpx.player.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mpx.player.data.LibraryState
import com.mpx.player.di.AppContainer
import com.mpx.player.model.Album
import com.mpx.player.model.Artist
import com.mpx.player.model.Track
import com.mpx.player.shared.playback.toPlaybackItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/**
 * ViewModel da biblioteca (BROWSE) — migrado para `commonMain` na fatia LIBRARY (P7). Molde do
 * [PlayerViewModel]/[EqViewModel]: `(app: AppContainer) : ViewModel()` + `viewModelScope` +
 * `.state(initial)` sobre `stateIn(WhileSubscribed)`. A projeção é a [projectLibrary] pura.
 *
 * `open` + `protected val app`: a subclasse Android (`AndroidLibraryViewModel`) herda o browse
 * comum e ADICIONA só a superfície Android-only (diálogos de edição/criação/exclusão que tocam
 * Uri/overrides/IntentSender). NADA de Uri/File/context/overrides aqui.
 */
open class LibraryViewModel(protected val app: AppContainer) : ViewModel() {

    private val filter = MutableStateFlow(LibFilter.ALBUNS)
    private val query = MutableStateFlow("")
    private val ascending = MutableStateFlow(true)
    private val openedId = MutableStateFlow<String?>(null)

    private data class Inputs(val lib: LibraryState, val f: LibFilter, val q: String, val asc: Boolean)

    // combine de 6 flows aninhado (como no :app): (state,filter,query,ascending) -> Inputs, depois
    // com playlists e openedId -> projectLibrary.
    val ui: StateFlow<LibraryUi> = combine(
        combine(app.library.state, filter, query, ascending) { lib, f, q, asc -> Inputs(lib, f, q, asc) },
        app.playlists.playlists,
        openedId,
    ) { inp, pls, opened ->
        projectLibrary(inp.lib, pls, inp.f, inp.q, inp.asc, opened)
    }.state(LibraryUi())

    fun setFilter(f: LibFilter) {
        filter.value = f
        if (f != LibFilter.PLAYLISTS) openedId.value = null
    }
    fun setQuery(q: String) { query.value = q }
    fun toggleSort() = ascending.update { !it }
    fun openPlaylist(id: String) { openedId.value = id }
    fun closePlaylist() { openedId.value = null }

    fun playAlbum(a: Album) = playNow(a.tracks, 0, a.title)
    fun playArtist(a: Artist) = playNow(a.tracks, 0, a.name)
    fun playTrack(list: List<Track>, i: Int) = playNow(list, i, "/all_tracks")
    fun playPlaylist(id: String) {
        val p = app.playlists.playlists.value.firstOrNull { it.id == id } ?: return
        val tracks = app.library.state.value.tracksForKeys(p.trackKeys)
        playNow(tracks, 0, "/" + p.name.lowercase())
    }

    /** Enfileira (mapeando Track -> PlaybackItem) e toca via audioPlayer. */
    private fun playNow(tracks: List<Track>, startIndex: Int, label: String) {
        if (tracks.isEmpty()) return
        app.audioPlayer.setQueue(tracks.map { it.toPlaybackItem() }, startIndex, label)
        app.audioPlayer.play()
    }

    fun addToPlaylist(playlistId: String, key: String) = app.playlists.addTrack(playlistId, key)
    fun removeFromPlaylist(playlistId: String, key: String) = app.playlists.removeTrack(playlistId, key)
    fun toggleFavorite(key: String) = app.playlists.toggleFavorite(key)

    /** Molde EqViewModel/PlayerViewModel: stateIn sobre o viewModelScope multiplataforma. */
    private fun <T> Flow<T>.state(initial: T): StateFlow<T> =
        stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)
}
