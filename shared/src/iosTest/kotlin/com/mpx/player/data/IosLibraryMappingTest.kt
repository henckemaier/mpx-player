package com.mpx.player.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Lógica PURA do mapa MPMediaItem→Track do [IosLibraryRepository] (sem MediaPlayer):
 * skip de item sem arquivo (DRM/nuvem), normalização de nulos igual ao Android,
 * conversão de duração e agrupamento final via [buildAlbumsAndArtists].
 * Roda no simulador via :shared:iosSimulatorArm64Test.
 */
class IosLibraryMappingTest {

    @Test
    fun assetUrlNulo_pula() {
        val t = trackFromMediaFields(
            persistentId = 1, assetUrl = null, title = "T", artist = "A", album = "Alb",
            albumPersistentId = 2, durationSec = 10.0, trackNumber = 1,
        )
        assertNull(t) // item de nuvem/DRM sem arquivo local não é tocável → pula
    }

    @Test
    fun mapeamentoFeliz_todosOsCampos() {
        val t = trackFromMediaFields(
            persistentId = 42,
            assetUrl = "file:///var/mobile/Media/signal_decay.mp3",
            title = "Signal Decay",
            artist = "Null Sector",
            album = "Void",
            albumPersistentId = 7,
            durationSec = 123.5,
            trackNumber = 4,
        )!!
        assertEquals(42L, t.id)
        assertEquals("file:///var/mobile/Media/signal_decay.mp3", t.uri)
        assertEquals("Signal Decay", t.title)
        assertEquals("Null Sector", t.artist)
        assertEquals("Void", t.album)
        assertEquals(7L, t.albumId)
        assertEquals(123500L, t.durationMs)      // segundos → ms
        assertEquals("signal_decay.mp3", t.displayName)
        assertEquals("", t.relativePath)
        assertEquals("audio/mpeg", t.mimeType)   // inferido da extensão
        assertNull(t.bitrate)
        assertEquals(4, t.trackNumber)
        assertNull(t.year)
        assertEquals("MP3", t.format)            // Track.format deriva do mimeType
    }

    @Test
    fun nulos_viramDefaults() {
        val t = trackFromMediaFields(
            persistentId = 1,
            assetUrl = "ipod-library://item/item.m4a?id=9",
            title = null,
            artist = null,
            album = null,
            albumPersistentId = 0,
            durationSec = 10.0,
            trackNumber = 0,
        )!!
        assertEquals("DESCONHECIDO", t.artist)
        assertEquals("DESCONHECIDO", t.album)
        assertEquals("item.m4a", t.displayName)  // lastPathComponent (query removida)
        assertEquals("item.m4a", t.title)        // título ausente cai no nome do arquivo
        assertEquals("audio/mp4", t.mimeType)
        assertEquals(10000L, t.durationMs)
    }

    @Test
    fun artistaEmBrancoOuUnknown_viraDesconhecido() {
        val vazio = trackFromMediaFields(1, "file:///a/x.mp3", "T", "", "", 0, 1.0, 1)!!
        assertEquals("DESCONHECIDO", vazio.artist)
        assertEquals("DESCONHECIDO", vazio.album)
        val unknown = trackFromMediaFields(1, "file:///a/x.mp3", "T", "<unknown>", "<unknown>", 0, 1.0, 1)!!
        assertEquals("DESCONHECIDO", unknown.artist)
        assertEquals("DESCONHECIDO", unknown.album)
    }

    @Test
    fun conjunto_agrupaViaBuildAlbumsAndArtists_pulandoDrm() {
        val tracks = listOfNotNull(
            trackFromMediaFields(1, "file:///a/x1.mp3", "T1", "Artist A", "Alb1", 100, 60.0, 1),
            trackFromMediaFields(2, "file:///a/x2.mp3", "T2", "Artist A", "Alb1", 100, 60.0, 2),
            trackFromMediaFields(3, null, "T3", "Artist B", "Alb2", 200, 60.0, 1), // DRM → pula
        )
        assertEquals(2, tracks.size) // o item sem assetUrl ficou de fora

        val (albums, artists) = buildAlbumsAndArtists(tracks)
        assertEquals(1, albums.size)
        assertEquals(100L, albums.first().id)
        assertEquals("Alb1", albums.first().title)
        assertEquals(2, albums.first().tracks.size)
        assertEquals(1, artists.size)
        assertEquals("Artist A", artists.first().name)
    }

    @Test
    fun helpers_lastPathComponent_eMime() {
        assertEquals("song.mp3", lastPathComponentOf("file:///x/song.mp3"))
        assertEquals("a.flac", lastPathComponentOf("scheme://host/a.flac?q=1")) // query removida
        assertEquals("bare", lastPathComponentOf("bare"))                        // sem barra
        assertNull(lastPathComponentOf("file:///x/"))                            // termina em barra → vazio

        assertEquals("audio/mpeg", mimeFromExtension("a.mp3"))
        assertEquals("audio/flac", mimeFromExtension("A.FLAC"))                   // case-insensitive
        assertEquals("audio/mp4", mimeFromExtension("a.m4a"))
        assertEquals("", mimeFromExtension("semextensao"))
        assertTrue(mimeFromExtension("x.wav") == "audio/wav")
    }
}
