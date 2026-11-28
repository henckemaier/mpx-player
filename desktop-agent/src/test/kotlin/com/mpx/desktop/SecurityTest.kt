package com.mpx.desktop

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Hardening F8: token constante, rate-limit 429, 500 generico, symlink/traversal, wildcard. */
class SecurityTest {

    private val token = "secret-token-xyz"
    private lateinit var tmp: File

    @BeforeTest fun setup() { tmp = Files.createTempDirectory("mpx-sec").toFile() }
    @AfterTest fun tearDown() { tmp.deleteRecursively() }

    private fun dto(id: String, rel: String, hasArt: Boolean = false) = RemoteTrackDto(
        id = id, title = "x", artist = "", album = "", durationMs = 0L, trackNumber = 0, year = null,
        mime = "audio/mpeg", format = "MP3", bitrate = null, hasArt = hasArt, relPathHint = rel,
    )

    // ---- 1) token ----
    @Test fun tokenMatches_casos() {
        assertTrue(tokenMatches("abc", "abc"))
        assertFalse(tokenMatches("abd", "abc"))
        assertFalse(tokenMatches("ab", "abc"))
        assertFalse(tokenMatches("abcd", "abc"))
        assertFalse(tokenMatches("", "abc"))
        assertFalse(tokenMatches(null, "abc"))
        assertTrue(tokenMatches("tökén", "tökén"))
    }

    // ---- 2) rate-limit ----
    @Test fun limiter_bloqueiaNa10aFalha_eLiberaApos60s() {
        var now = 1_000_000L
        val l = AuthRateLimiter(clock = { now })
        repeat(9) { l.recordFailure("1.1.1.1") }
        assertEquals(0, l.retryAfterSeconds("1.1.1.1"))
        l.recordFailure("1.1.1.1")
        assertEquals(60, l.retryAfterSeconds("1.1.1.1"))
        assertEquals(0, l.retryAfterSeconds("2.2.2.2"), "outro IP nao e afetado")
        now += 30_000
        assertEquals(30, l.retryAfterSeconds("1.1.1.1"))
        now += 30_001
        assertEquals(0, l.retryAfterSeconds("1.1.1.1"))
    }

    @Test fun limiter_falhasForaDaJanelaNaoContam() {
        var now = 0L
        val l = AuthRateLimiter(clock = { now })
        repeat(9) { l.recordFailure("ip") }
        now += 60_000
        repeat(9) { l.recordFailure("ip") }
        assertEquals(0, l.retryAfterSeconds("ip"))
    }

    @Test fun limiter_limpaEntradasExpiradas() {
        var now = 0L
        val l = AuthRateLimiter(clock = { now })
        repeat(300) { l.recordFailure("ip$it") }
        now += 120_000
        l.recordFailure("novo")
        assertTrue(l.trackedCount() < 10, "entradas expiradas devem ser podadas: ${l.trackedCount()}")
    }

    @Test fun limiter_threadSafe() {
        val l = AuthRateLimiter()
        val ts = List(8) { Thread { repeat(500) { i -> l.recordFailure("ip${i % 5}") } } }
        ts.forEach { it.start() }
        ts.forEach { it.join() }
        assertTrue(l.retryAfterSeconds("ip0") > 0)
    }

    @Test fun servidor_429Apos10TokensErrados_inclusiveComTokenCerto() = testApplication {
        application { mpxAgentModule(Library(emptyList()), token, "t", AuthRateLimiter()) }
        repeat(9) {
            assertEquals(HttpStatusCode.Unauthorized, client.get("/ping?token=errado$it").status)
        }
        assertEquals(HttpStatusCode.Unauthorized, client.get("/ping?token=errado").status) // 10a falha
        val r = client.get("/ping") { header(HttpHeaders.Authorization, "Bearer $token") }
        assertEquals(HttpStatusCode.TooManyRequests, r.status)
        assertNotNull(r.headers[HttpHeaders.RetryAfter])
    }

    @Test fun servidor_9FalhasNaoBloqueiam_eTokenCertoPassa() = testApplication {
        application { mpxAgentModule(Library(emptyList()), token, "t", AuthRateLimiter()) }
        repeat(9) { client.get("/ping?token=x$it") }
        assertEquals(HttpStatusCode.OK, client.get("/ping?token=$token").status)
    }

    @Test fun falhasEmStreamEArte_naoBloqueiamLibrary_eTemLimiteProprio() = testApplication {
        // Agente reiniciado: o app ainda tem o token velho em prefetch/capas -> varias falhas de midia.
        application { mpxAgentModule(Library(emptyList()), token, "t", AuthRateLimiter(), AuthRateLimiter(maxFailures = 30)) }
        repeat(15) {
            assertEquals(HttpStatusCode.Unauthorized, client.get("/stream/x$it?token=velho").status)
            assertEquals(HttpStatusCode.Unauthorized, client.get("/art/x$it?token=velho").status)
        }
        // 30 falhas de midia: /library (limite de API) segue liberado p/ o repareamento
        assertEquals(HttpStatusCode.OK, client.get("/library") { header(HttpHeaders.Authorization, "Bearer $token") }.status)
        // ...mas a propria midia atingiu o limite dela
        assertEquals(HttpStatusCode.TooManyRequests, client.get("/stream/x?token=$token").status)
    }

    // ---- 3) 500 generico ----
    @Test fun erro500_naoVazaMensagemNemStack() = testApplication {
        val f = File(tmp, "a.mp3").apply { writeBytes(byteArrayOf(1)) }
        val lib = Library(listOf(LibraryEntry(dto("id1", "a.mp3", true), f))) {
            throw IllegalStateException("SEGREDO C:/Users/x/y")
        }
        application { mpxAgentModule(lib, token, "t") }
        val r = client.get("/art/id1") { header(HttpHeaders.Authorization, "Bearer $token") }
        assertEquals(HttpStatusCode.InternalServerError, r.status)
        val body = r.bodyAsText()
        assertTrue("internal error" in body)
        assertFalse("SEGREDO" in body || "IllegalState" in body || "Users" in body || "at com." in body, body)
    }

    // ---- 4) isInsideRoot / symlink / traversal ----
    @Test fun isInsideRoot_puro() {
        val root = File(tmp, "musica").apply { mkdirs() }
        File(tmp, "musica-evil").mkdirs()
        File(root, "sub").mkdirs()
        assertTrue(isInsideRoot(root, File(root, "sub/a.mp3")))
        assertTrue(isInsideRoot(root, File(root, "sub/../b.mp3")))
        assertFalse(isInsideRoot(root, File(root, "../musica-evil/a.mp3")), "prefixo de nome nao basta")
        assertFalse(isInsideRoot(root, File(root, "../outro.mp3")))
        assertFalse(isInsideRoot(root, File(tmp, "x.mp3")))
    }

    private fun trySymlink(link: File, target: File): Boolean = try {
        Files.createSymbolicLink(link.toPath(), target.toPath())
        true
    } catch (_: Exception) {
        false // Windows sem privilegio / FS sem suporte
    }

    private val noTags = TagReader { null }

    @Test fun scan_naoSegueSymlinkParaForaDoRoot() {
        val root = File(tmp, "musica").apply { mkdirs() }
        val outside = File(tmp, "fora").apply { mkdirs() }
        File(outside, "secreto.mp3").writeBytes(byteArrayOf(9))
        File(root, "dentro.mp3").writeBytes(byteArrayOf(1))
        val dirLink = trySymlink(File(root, "linkdir"), outside)
        val fileLink = trySymlink(File(root, "linkfile.mp3"), File(outside, "secreto.mp3"))
        if (!dirLink && !fileLink) return // sem permissao p/ symlink: coberto por isInsideRoot_puro
        val names = LibraryScanner(root, noTags).scan().map { it.file.name }
        assertEquals(listOf("dentro.mp3"), names)
    }

    @Test fun capaDeFolderSymlinkParaForaNaoEhUsada() {
        val dir = File(tmp, "musica/alb").apply { mkdirs() }
        val outside = File(tmp, "fora.jpg").apply { writeBytes(byteArrayOf(7)) }
        val audio = File(dir, "a.mp3").apply { writeBytes(byteArrayOf(1)) }
        if (!trySymlink(File(dir, "cover.jpg"), outside)) return
        assertNull(folderArtFile(audio))
    }

    @Test fun stream_idNaoIndexadoOuTraversal_404() = testApplication {
        val f = File(tmp, "a.mp3").apply { writeBytes(byteArrayOf(1, 2)) }
        File(tmp, "b.mp3").writeBytes(byteArrayOf(3))
        application { mpxAgentModule(Library(listOf(LibraryEntry(dto("id1", "a.mp3"), f))), token, "t") }
        suspend fun status(path: String) =
            client.get(path) { header(HttpHeaders.Authorization, "Bearer $token") }.status
        assertEquals(HttpStatusCode.OK, status("/stream/id1"))
        assertEquals(HttpStatusCode.NotFound, status("/stream/b.mp3"))
        assertEquals(HttpStatusCode.NotFound, status("/stream/..%2Fb.mp3"))
        assertEquals(HttpStatusCode.NotFound, status("/stream/%2E%2E%2F%2E%2E%2Fetc%2Fpasswd"))
        assertEquals(HttpStatusCode.NotFound, status("/art/..%2Fb.mp3"))
    }

    // ---- 5) wildcard ----
    @Test fun isWildcardHost_casos() {
        assertTrue(isWildcardHost("0.0.0.0"))
        assertTrue(isWildcardHost("::"))
        assertTrue(isWildcardHost("[::]"))
        assertTrue(isWildcardHost("0:0:0:0:0:0:0:0"))
        assertFalse(isWildcardHost("192.168.0.10"))
        assertFalse(isWildcardHost("127.0.0.1"))
    }
}
