package com.mpx.player.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Migração 1.7.0 -> 1.7.1: entradas REMOTAS (http://<ip>:porta/stream/<id>?token=...) que tenham
 * sobrado numa fila são PULADAS sem crash; só URIs locais tocam.
 */
class PlayableQueueTest {

    private val remote = "http://192.168.0.10:8099/stream/abc?token=x"

    private fun scheme(u: String): String? = u.substringBefore("://", "").ifEmpty { null }

    @Test fun `esquemas tocaveis`() {
        assertTrue(isPlayableScheme("content"))
        assertTrue(isPlayableScheme("CONTENT"))
        assertTrue(isPlayableScheme("file"))
        assertTrue(isPlayableScheme("android.resource"))
        listOf("http", "https", "mpx", "", null).forEach { assertFalse("$it", isPlayableScheme(it)) }
    }

    @Test fun `fila so local fica intacta`() {
        val q = listOf("content://a", "content://b", "file:///c")
        assertEquals(q to 1, playableQueue(q, 1, ::scheme))
    }

    @Test fun `remotas somem e o indice segue o mesmo item`() {
        val q = listOf(remote, "content://a", remote, "content://b")
        assertEquals(listOf("content://a", "content://b") to 1, playableQueue(q, 3, ::scheme))
        assertEquals(listOf("content://a", "content://b") to 0, playableQueue(q, 1, ::scheme))
    }

    @Test fun `inicio numa remota pula para a proxima local`() {
        val q = listOf("content://a", remote, "content://b")
        assertEquals(listOf("content://a", "content://b") to 1, playableQueue(q, 1, ::scheme))
    }

    @Test fun `inicio numa remota no fim cai na ultima local`() {
        val q = listOf("content://a", "content://b", remote)
        assertEquals(listOf("content://a", "content://b") to 1, playableQueue(q, 2, ::scheme))
    }

    @Test fun `fila so remota vira vazia sem crash`() {
        assertEquals(emptyList<String>() to -1, playableQueue(listOf(remote, remote), 0, ::scheme))
        assertEquals(emptyList<String>() to -1, playableQueue(emptyList<String>(), 0, ::scheme))
    }

    @Test fun `indice fora da faixa e tolerado`() {
        val q = listOf("content://a", "content://b")
        assertEquals(q to 1, playableQueue(q, 99, ::scheme))
        assertEquals(q to 0, playableQueue(q, -5, ::scheme))
    }
}
