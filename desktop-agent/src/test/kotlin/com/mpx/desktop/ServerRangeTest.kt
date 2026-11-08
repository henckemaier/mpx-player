package com.mpx.desktop

import io.ktor.client.request.get
import io.ktor.client.request.head
import io.ktor.client.request.header
import io.ktor.client.statement.readBytes
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * (c) Range: via ktor-server-test-host, requisicao COM header Range -> 206 Partial Content +
 * os bytes certos (o plugin PartialContent do Ktor faz o trabalho; nao implementamos na mao).
 */
class ServerRangeTest {

    private val token = "t0ken"
    private lateinit var file: File
    private lateinit var library: Library
    private val id = stableId("audio.mp3")

    @BeforeTest
    fun setup() {
        file = Files.createTempFile("mpx-stream", ".mp3").toFile()
        // 100 bytes deterministicos: byte[i] = i
        file.writeBytes(ByteArray(100) { it.toByte() })
        val dto = RemoteTrackDto(
            id = id, title = "x", artist = "", album = "", durationMs = 0L,
            trackNumber = 0, year = null, mime = "audio/mpeg", format = "MP3",
            bitrate = null, hasArt = false, relPathHint = "audio.mp3",
        )
        library = Library(listOf(LibraryEntry(dto, file)))
    }

    @AfterTest
    fun tearDown() {
        file.delete()
    }

    @Test
    fun getCompleto_200_todosOsBytes_eAcceptRanges() = testApplication {
        application { mpxAgentModule(library, token, "test") }
        val r = client.get("/stream/$id") { header(HttpHeaders.Authorization, "Bearer $token") }
        assertEquals(HttpStatusCode.OK, r.status)
        assertEquals(100, r.readBytes().size)
        assertEquals("bytes", r.headers[HttpHeaders.AcceptRanges], "PartialContent anuncia Accept-Ranges")
    }

    @Test
    fun getComRange_206_bytesCertos() = testApplication {
        application { mpxAgentModule(library, token, "test") }
        val r = client.get("/stream/$id") {
            header(HttpHeaders.Authorization, "Bearer $token")
            header(HttpHeaders.Range, "bytes=0-9")
        }
        assertEquals(HttpStatusCode.PartialContent, r.status) // 206
        val body = r.readBytes()
        assertEquals(10, body.size, "bytes 0..9 = 10 bytes")
        // Content desses bytes = 0,1,...,9 (file[i]=i)
        for (i in 0 until 10) assertEquals(i.toByte(), body[i])
        val cr = assertNotNull(r.headers[HttpHeaders.ContentRange], "Content-Range presente no 206")
        assertTrue(cr.startsWith("bytes 0-9/100"), "Content-Range = 'bytes 0-9/100', veio: $cr")
    }

    @Test
    fun getRangeOffset_206_suffix() = testApplication {
        application { mpxAgentModule(library, token, "test") }
        val r = client.get("/stream/$id") {
            header(HttpHeaders.Authorization, "Bearer $token")
            header(HttpHeaders.Range, "bytes=90-99")
        }
        assertEquals(HttpStatusCode.PartialContent, r.status)
        val body = r.readBytes()
        assertEquals(10, body.size)
        assertEquals(90.toByte(), body[0])
        assertEquals(99.toByte(), body[9])
    }

    @Test
    fun head_200_eAcceptRanges_semCorpo() = testApplication {
        // AutoHeadResponse: HEAD /stream/{id} roda o GET e descarta o corpo -> 200 + Accept-Ranges
        // (players que fazem HEAD antes do GET+Range). Sem o plugin, HEAD caria em 404.
        application { mpxAgentModule(library, token, "test") }
        val r = client.head("/stream/$id") { header(HttpHeaders.Authorization, "Bearer $token") }
        assertEquals(HttpStatusCode.OK, r.status)
        assertEquals("bytes", r.headers[HttpHeaders.AcceptRanges])
        assertEquals(0, r.readBytes().size, "HEAD nao tem corpo")
    }

    @Test
    fun idDesconhecido_404() = testApplication {
        application { mpxAgentModule(library, token, "test") }
        val r = client.get("/stream/naoexiste") { header(HttpHeaders.Authorization, "Bearer $token") }
        assertEquals(HttpStatusCode.NotFound, r.status)
    }
}
