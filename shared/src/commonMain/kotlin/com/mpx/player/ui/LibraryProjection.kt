package com.mpx.player.ui

import com.mpx.player.data.LibraryState
import com.mpx.player.model.Playlist

/**
 * Projeção PURA do estado da biblioteca → [LibraryUi], extraída do lambda inline do antigo
 * `LibraryViewModel.ui` (`:app`) na fatia LIBRARY (P7). Lógica IDÊNTICA: filtro por substring
 * (case-insensitive) em title/artist/album/displayName; ordenação asc/desc; faixas por
 * `title.uppercase()`; map de [PlaylistUi]; [OpenedPlaylist] resolvido via `tracksForKeys`;
 * `favoriteKeys` da playlist [Playlist.FAVORITES_ID]. SEM mudança de comportamento — comum e
 * testável (LibraryProjectionTest), reusada pelo `LibraryViewModel` comum.
 */
fun projectLibrary(
    lib: LibraryState,
    playlists: List<Playlist>,
    filter: LibFilter,
    query: String,
    ascending: Boolean,
    openedId: String?,
): LibraryUi {
    val needle = query.trim().lowercase()
    fun ok(vararg s: String) = needle.isEmpty() || s.any { it.lowercase().contains(needle) }
    val albums = lib.albums.filter { ok(it.title, it.artist) }.let { if (ascending) it else it.reversed() }
    val artists = lib.artists.filter { ok(it.name) }.let { if (ascending) it else it.reversed() }
    val tracks = lib.tracks.filter { ok(it.title, it.artist, it.album, it.displayName) }
        .sortedBy { it.title.uppercase() }.let { if (ascending) it else it.reversed() }
    val playlistUis = playlists.map { PlaylistUi(it.id, it.name, it.photo, it.trackKeys.size, it.locked) }
    val openedPl = openedId?.let { id ->
        playlists.firstOrNull { it.id == id }?.let { p ->
            OpenedPlaylist(p.id, p.name, p.photo, p.locked, lib.tracksForKeys(p.trackKeys))
        }
    }
    val favKeys = playlists.firstOrNull { it.id == Playlist.FAVORITES_ID }?.trackKeys?.toSet() ?: emptySet()
    return LibraryUi(filter, query, ascending, albums, artists, tracks, lib.tracks.size, lib.scanned, playlistUis, openedPl, favKeys)
}
