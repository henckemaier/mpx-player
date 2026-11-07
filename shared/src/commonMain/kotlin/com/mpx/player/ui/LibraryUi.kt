package com.mpx.player.ui

import com.mpx.player.model.Album
import com.mpx.player.model.Artist
import com.mpx.player.model.Track

// Tipos de UI da biblioteca — migrados de app/.../ui/ViewModels.kt para commonMain na fatia
// LIBRARY (P7). MESMO FQN (com.mpx.player.ui) → os call-sites do :app resolvem sem edição de
// import. Só referenciam tipos COMUNS (Album/Artist/Track), então o estado de UI da biblioteca
// é compartilhável por Android e iOS.

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
