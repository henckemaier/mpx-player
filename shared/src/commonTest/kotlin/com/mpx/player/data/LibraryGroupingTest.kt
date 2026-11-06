package com.mpx.player.data

import com.mpx.player.model.Track
import kotlin.test.Test
import kotlin.test.assertEquals

class LibraryGroupingTest {

    /** Track mínimo: só os campos que o agrupamento usa importam; o resto é default. */
    private fun track(
        id: Long,
        title: String = "T$id",
        artist: String,
        album: String,
        albumId: Long,
        trackNumber: Int = 0,
        displayName: String = "$id.mp3",
    ) = Track(
        id = id,
        uri = "uri://$id",
        title = title,
        artist = artist,
        album = album,
        albumId = albumId,
        durationMs = 1000,
        displayName = displayName,
        relativePath = "music/",
        mimeType = "audio/mpeg",
        bitrate = null,
        trackNumber = trackNumber,
        year = null,
    )

    @Test fun empty_producesEmptyAlbumsAndArtists() {
        val (albums, artists) = buildAlbumsAndArtists(emptyList())
        assertEquals(emptyList(), albums)
        assertEquals(emptyList(), artists)
    }

    @Test fun albums_groupedByAlbumId_sortedByTitleUppercase() {
        val tracks = listOf(
            track(1, artist = "A", album = "Zebra", albumId = 10),
            track(2, artist = "A", album = "Zebra", albumId = 10),
            track(3, artist = "B", album = "apple", albumId = 20),
        )
        val (albums, _) = buildAlbumsAndArtists(tracks)

        assertEquals(2, albums.size)
        // Ordenação por título em UPPERCASE: "apple" (A...) antes de "Zebra" (Z...).
        assertEquals(listOf("apple", "Zebra"), albums.map { it.title })
        val zebra = albums.first { it.id == 10L }
        assertEquals(2, zebra.tracks.size)
        assertEquals("Zebra", zebra.title)
    }

    @Test fun album_title_fromFirstTrack_artist_mostFrequent() {
        // albumId 10: 2 faixas do artista "Main" + 1 de "Guest" -> artista do álbum = "Main".
        val tracks = listOf(
            track(1, artist = "Main", album = "Split", albumId = 10),
            track(2, artist = "Guest", album = "Split", albumId = 10),
            track(3, artist = "Main", album = "Split", albumId = 10),
        )
        val (albums, _) = buildAlbumsAndArtists(tracks)

        assertEquals(1, albums.size)
        assertEquals("Split", albums[0].title)
        assertEquals("Main", albums[0].artist)
    }

    @Test fun album_tracks_sortedByTrackNumberThenDisplayName() {
        val tracks = listOf(
            track(1, artist = "A", album = "Rec", albumId = 10, trackNumber = 3, displayName = "c.mp3"),
            track(2, artist = "A", album = "Rec", albumId = 10, trackNumber = 1, displayName = "b.mp3"),
            // Mesmo trackNumber 1: desempata por displayName ("a" antes de "b").
            track(3, artist = "A", album = "Rec", albumId = 10, trackNumber = 1, displayName = "a.mp3"),
        )
        val (albums, _) = buildAlbumsAndArtists(tracks)

        val ordered = albums.single().tracks.map { it.id }
        assertEquals(listOf(3L, 2L, 1L), ordered)
    }

    @Test fun artists_groupedByName_sortedByNameUppercase() {
        val tracks = listOf(
            track(1, artist = "zoe", album = "X", albumId = 10),
            track(2, artist = "Ana", album = "Y", albumId = 20),
            track(3, artist = "zoe", album = "Z", albumId = 30),
        )
        val (_, artists) = buildAlbumsAndArtists(tracks)

        assertEquals(2, artists.size)
        // "Ana" (A...) antes de "zoe" (Z...) com uppercase.
        assertEquals(listOf("Ana", "zoe"), artists.map { it.name })
        assertEquals(2, artists.first { it.name == "zoe" }.tracks.size)
    }

    @Test fun artist_tracks_sortedByAlbumThenTrackNumber() {
        val tracks = listOf(
            track(1, artist = "A", album = "Beta", albumId = 20, trackNumber = 1),
            track(2, artist = "A", album = "Alpha", albumId = 10, trackNumber = 2),
            track(3, artist = "A", album = "Alpha", albumId = 10, trackNumber = 1),
        )
        val (_, artists) = buildAlbumsAndArtists(tracks)

        val ordered = artists.single().tracks.map { it.id }
        // Álbum "Alpha" antes de "Beta"; dentro de Alpha, trackNumber 1 antes de 2.
        assertEquals(listOf(3L, 2L, 1L), ordered)
    }
}
