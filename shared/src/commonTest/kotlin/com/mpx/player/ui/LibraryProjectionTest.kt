package com.mpx.player.ui

import com.mpx.player.data.LibraryState
import com.mpx.player.model.Album
import com.mpx.player.model.Artist
import com.mpx.player.model.Playlist
import com.mpx.player.model.Track
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Trava a [projectLibrary] PURA (fatia LIBRARY, P7) sobre fixtures fixas → saída EXATA: filtro
 * substring, ordenação asc/desc, faixas por title.uppercase, map de PlaylistUi, OpenedPlaylist
 * via tracksForKeys e favoriteKeys da playlist FAVORITES. Roda em `:shared:testDebugUnitTest` E
 * `iosSimulatorArm64Test`. NÃO duplica o LibraryGroupingTest (aquele cobre o buildAlbumsAndArtists).
 */
class LibraryProjectionTest {

    private val t1 = trk(1, "BRAVO", "AX", "ALB1", 10, "b.mp3") // key = "b.mp3"
    private val t2 = trk(2, "alpha", "BX", "ALB2", 20, "a.mp3") // key = "a.mp3"

    private val albums = listOf(Album(10, "ALB1", "AX", listOf(t1)), Album(20, "ALB2", "BX", listOf(t2)))
    private val artists = listOf(Artist("AX", listOf(t1)), Artist("BX", listOf(t2)))
    private val lib = LibraryState(tracks = listOf(t1, t2), albums = albums, artists = artists, scanned = true)
    private val playlists = listOf(
        Playlist(Playlist.FAVORITES_ID, Playlist.FAVORITES_NAME, locked = true, trackKeys = listOf("b.mp3")),
        Playlist("p1", "MIX", trackKeys = listOf("a.mp3", "b.mp3")),
    )

    @Test
    fun default_ascending_sortsTracksAndMapsPlaylists() {
        val ui = projectLibrary(lib, playlists, LibFilter.ALBUNS, "", ascending = true, openedId = null)
        assertEquals(listOf(2L, 1L), ui.tracks.map { it.id })   // ALPHA < BRAVO
        assertEquals(listOf(10L, 20L), ui.albums.map { it.id }) // ordem original (asc)
        assertEquals(2, ui.totalFiles)
        assertEquals(true, ui.scanned)
        assertEquals(
            listOf(
                PlaylistUi(Playlist.FAVORITES_ID, Playlist.FAVORITES_NAME, null, 1, true),
                PlaylistUi("p1", "MIX", null, 2, false),
            ),
            ui.playlists,
        )
        assertEquals(setOf("b.mp3"), ui.favoriteKeys)
        assertNull(ui.opened)
    }

    @Test
    fun query_filtersSubstringAcrossFields() {
        val ui = projectLibrary(lib, playlists, LibFilter.FAIXAS, "alpha", ascending = true, openedId = null)
        assertEquals(listOf(2L), ui.tracks.map { it.id })
        assertEquals(emptyList(), ui.albums.map { it.id })
    }

    @Test
    fun descending_reversesAlbumsAndTracks() {
        val ui = projectLibrary(lib, playlists, LibFilter.ALBUNS, "", ascending = false, openedId = null)
        assertEquals(listOf(1L, 2L), ui.tracks.map { it.id })
        assertEquals(listOf(20L, 10L), ui.albums.map { it.id })
    }

    @Test
    fun opened_resolvesTracksForKeysInOrder() {
        val ui = projectLibrary(lib, playlists, LibFilter.PLAYLISTS, "", ascending = true, openedId = "p1")
        assertEquals("MIX", ui.opened?.name)
        assertEquals(listOf(2L, 1L), ui.opened?.tracks?.map { it.id }) // tracksForKeys(["a.mp3","b.mp3"])
    }

    private fun trk(id: Long, title: String, artist: String, album: String, albumId: Long, name: String) = Track(
        id = id, uri = "file://$name", title = title, artist = artist, album = album, albumId = albumId,
        durationMs = 1000, displayName = name, relativePath = "", mimeType = "audio/mpeg",
        bitrate = null, trackNumber = 1, year = null,
    )
}
