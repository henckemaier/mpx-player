package com.mpx.desktop

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** (a) scan-de-pasta -> modelo: id estavel (hash do relPath), format do mime, hasArt, degrade. */
class LibraryScannerTest {

    private lateinit var root: File

    @BeforeTest
    fun setup() {
        root = Files.createTempDirectory("mpx-scan").toFile()
    }

    @AfterTest
    fun tearDown() {
        root.deleteRecursively()
    }

    private fun touch(rel: String, bytes: ByteArray = byteArrayOf(1, 2, 3)) {
        val f = File(root, rel)
        f.parentFile.mkdirs()
        f.writeBytes(bytes)
    }

    // Fake: tags "boas" p/ arquivos conhecidos pelo nome; null (falha) p/ o arquivo "broken".
    private val fakeReader = TagReader { file ->
        if (file.nameWithoutExtension.contains("broken")) null
        else TagData(
            title = "Title of ${file.nameWithoutExtension}",
            artist = "The Artist",
            album = "The Album",
            durationMs = 123_000L,
            trackNumber = 4,
            year = 1998,
            bitrate = 320,
            hasEmbeddedArt = file.extension.lowercase() == "flac", // flac "tem" arte embutida
        )
    }

    @Test
    fun scan_geraDtosCorretos_idEstavel_formatDoMime_hasArt() {
        touch("Artist/Album/01 Song.mp3")
        touch("loose.flac")
        touch("sub/noise.wav")

        val scanner = LibraryScanner(root, fakeReader)
        val entries = scanner.scan()
        assertEquals(3, entries.size, "deve achar os 3 arquivos de audio")

        val byRel = entries.associateBy { it.dto.relPathHint }

        val mp3 = assertNotNull(byRel["Artist/Album/01 Song.mp3"], "relPathHint com '/'")
        assertEquals(stableId("Artist/Album/01 Song.mp3"), mp3.dto.id) // id = SHA-1 do relPath
        assertEquals("MP3", mp3.dto.format)
        assertEquals("audio/mpeg", mp3.dto.mime)
        assertEquals("Title of 01 Song", mp3.dto.title)
        assertEquals("The Artist", mp3.dto.artist)
        assertEquals(123_000L, mp3.dto.durationMs)
        assertEquals(4, mp3.dto.trackNumber)
        assertEquals(1998, mp3.dto.year)
        assertEquals(320, mp3.dto.bitrate)
        assertTrue(!mp3.dto.hasArt, "mp3 sem arte embutida nem folder.jpg -> hasArt=false")

        val flac = assertNotNull(byRel["loose.flac"])
        assertEquals("FLAC", flac.dto.format)
        assertTrue(flac.dto.hasArt, "flac com arte embutida (fake) -> hasArt=true")

        assertEquals("WAV", assertNotNull(byRel["sub/noise.wav"]).dto.format)
    }

    @Test
    fun scan_idEstavel_entreScans() {
        touch("a/x.mp3")
        touch("b/y.ogg")
        val s = LibraryScanner(root, fakeReader)
        val ids1 = s.scan().map { it.dto.id }
        val ids2 = s.scan().map { it.dto.id }
        assertEquals(ids1, ids2, "o id nao muda entre scans (hash do relPath)")
        assertEquals(ids1.toSet().size, ids1.size, "ids distintos por faixa")
    }

    @Test
    fun scan_tagFalha_degradaMasInclui() {
        touch("broken_file.mp3")
        val entries = LibraryScanner(root, fakeReader).scan()
        assertEquals(1, entries.size, "arquivo com tag ilegivel AINDA e incluido")
        val dto = entries.first().dto
        assertEquals("broken_file", dto.title, "degrade -> title = nome sem extensao")
        assertEquals("", dto.artist)
        assertEquals(0L, dto.durationMs)
        assertEquals("MP3", dto.format)
    }

    @Test
    fun scan_folderArt_marcaHasArt_mesmoSemArteEmbutida() {
        touch("AlbumX/track.mp3")                       // mp3 fake -> sem arte embutida
        File(root, "AlbumX/cover.jpg").writeBytes(byteArrayOf(9, 9, 9))
        val dto = LibraryScanner(root, fakeReader).scan().first().dto
        assertTrue(dto.hasArt, "cover.jpg na pasta -> hasArt=true")
    }

    @Test
    fun scan_ignoraNaoAudio() {
        touch("song.mp3")
        touch("notes.txt")
        touch("image.png")
        assertEquals(1, LibraryScanner(root, fakeReader).scan().size)
    }
}
