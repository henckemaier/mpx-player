package com.mpx.player.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncLogicTest {

    @Test fun remoteIdSeguroAceitaValidos() {
        listOf("abc", "A1_b-2", "a".repeat(128), "0", "-", "_").forEach { assertTrue(it, SyncLogic.isSafeRemoteId(it)) }
    }

    @Test fun remoteIdMaliciosoRejeitado() {
        val bad = listOf(
            "", null, "../x", "..", ".", "a/b", "a\b", "a".repeat(129), "a b", "a.b", "a\u0000b",
            "a\nb", "é", "ação", "日本", "a%2fb", "id.mp3", "a/../b", "\u202eabc", "ａｂｃ",
        )
        bad.forEach { assertFalse("$it", SyncLogic.isSafeRemoteId(it)) }
    }

    @Test fun espacoComTamanhoConhecidoExigeMargem() {
        val size = 10L * 1024 * 1024
        assertTrue(SyncLogic.hasEnoughSpace(size, size + SyncLogic.SPACE_MARGIN_BYTES))
        assertFalse(SyncLogic.hasEnoughSpace(size, size + SyncLogic.SPACE_MARGIN_BYTES - 1))
    }

    @Test fun espacoComTamanhoDesconhecidoExigeMinimoFixo() {
        assertTrue(SyncLogic.hasEnoughSpace(-1, SyncLogic.UNKNOWN_SIZE_MIN_FREE_BYTES))
        assertFalse(SyncLogic.hasEnoughSpace(-1, SyncLogic.UNKNOWN_SIZE_MIN_FREE_BYTES - 1))
        assertFalse(SyncLogic.hasEnoughSpace(0, 0))
    }

    @Test fun integridadePorContentLength() {
        assertTrue(SyncLogic.sizeMatches(-1, 123))
        assertTrue(SyncLogic.sizeMatches(100, 100))
        assertFalse(SyncLogic.sizeMatches(100, 99))
        assertFalse(SyncLogic.sizeMatches(100, 101))
    }

    @Test fun missingEOffline() {
        val idx = mapOf("a" to SyncedEntry("k1", 1L), "b" to SyncedEntry("k2", 2L))
        assertEquals(listOf("c"), SyncLogic.missingRemoteIds(listOf("a", "b", "c"), idx))
        assertEquals(setOf("a"), SyncLogic.offlineRemoteIds(idx, setOf("k1")))
        assertEquals(mapOf("k1" to "a"), SyncLogic.syncedKeyToRemoteId(idx, setOf("k1")))
    }

    @Test fun jsonRoundTripEResiliencia() {
        val idx = mapOf("a" to SyncedEntry("k1", 1L), "b" to SyncedEntry("k2", 2L))
        assertEquals(idx, SyncLogic.fromJson(SyncLogic.toJson(idx)))
        assertEquals(emptyMap<String, SyncedEntry>(), SyncLogic.fromJson("lixo{"))
        assertEquals(1, SyncLogic.fromJson("""[{"r":"a","k":"k","m":1},{"x":1}]""").size)
    }

    @Test fun nomeDoArquivo() {
        assertEquals("abc.mp3", SyncLogic.fileNameFor("abc", "audio/mpeg"))
        assertEquals("abc.audio", SyncLogic.fileNameFor("abc", "x/y"))
    }
}
