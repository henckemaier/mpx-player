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
        assertEquals("Foo - Bar [abcdef12].mp3", SyncLogic.displayNameFor("abcdef123456", "Foo", "Bar", "audio/mpeg"))
        assertEquals("Bar [abc].audio", SyncLogic.displayNameFor("abc", null, "Bar", "x/y"))
        assertEquals("Bar [abc].wav", SyncLogic.displayNameFor("abc", "<unknown>", "Bar", "audio/wav"))
        assertEquals("Bar [abc].wav", SyncLogic.displayNameFor("abc", "  ", "Bar", "audio/wav"))
    }

    @Test fun nomeLegivelSanitizado() {
        assertEquals("a b c [id].mp3", SyncLogic.displayNameFor("id", null, "a/b\\c", "audio/mpeg"))
        assertEquals("x y [id].mp3", SyncLogic.displayNameFor("id", null, "x:*?\"<>|y", "audio/mpeg"))
        assertEquals("a b [id].mp3", SyncLogic.displayNameFor("id", null, "a\u0000\n\t  b", "audio/mpeg"))
        assertEquals("Ação - 日本語 ♪ [id].mp3", SyncLogic.displayNameFor("id", "Ação", "日本語 ♪", "audio/mpeg"))
    }

    @Test fun nomeLegivelPathTraversalEVazios() {
        val n = SyncLogic.displayNameFor("abcdef123", "..", "../../etc/passwd", "audio/mpeg")
        assertFalse(n.contains("/") || n.contains("\\"))
        assertEquals("etc passwd [abcdef12].mp3", n) // separadores viram espaço; pontos das pontas caem
        assertEquals("abc.mp3", SyncLogic.displayNameFor("abc", "x", "...", "audio/mpeg"))
        assertEquals("abc.mp3", SyncLogic.displayNameFor("abc", "x", "", "audio/mpeg"))
        assertEquals("abc.mp3", SyncLogic.displayNameFor("abc", "x", "///", "audio/mpeg"))
        assertEquals("abc.mp3", SyncLogic.displayNameFor("abc", "x", null, "audio/mpeg"))
    }

    @Test fun nomeLegivelLimitaTamanhoEmBytes() {
        val suffix = " [abcdef12].mp3"
        val n = SyncLogic.displayNameFor("abcdef123", "A".repeat(300), "T", "audio/mpeg")
        assertTrue(n.endsWith(suffix))
        assertTrue(n.toByteArray(Charsets.UTF_8).size <= 255)
        // não corta par substituto (emoji) ao meio
        val e = SyncLogic.displayNameFor("abcdef123", null, "🎵".repeat(100), "audio/mpeg")
        assertFalse(e.dropLast(suffix.length).last().isHighSurrogate())
        assertTrue(e.toByteArray(Charsets.UTF_8).size <= 255)
    }

    @Test fun tituloCjkLongoCabeEm255Bytes() {
        // 120 chars CJK = 360 bytes: antes passava do limite de 255 BYTES do nome no FS
        val cjk = "日本語の曲名".repeat(40)
        val n = SyncLogic.displayNameFor("abcdef123", "アーティスト", cjk, "audio/flac")
        assertTrue(n, n.toByteArray(Charsets.UTF_8).size <= 255)
        assertTrue(n.endsWith(" [abcdef12].flac"))
        assertTrue(n.startsWith("アーティスト - 日本語"))
    }

    @Test fun removeCaracteresDeFormatacaoUnicode() {
        // U+202E (RTL override) faria "evil[gpj].mp3" parecer outra extensão; U+200B/U+FEFF invisíveis;
        // U+E0041 (tag, plano suplementar = par substituto).
        val t = "abc‮gpj.exe​ x﻿y 󠁁z"
        val s = SyncLogic.sanitizeNamePart(t)
        assertFalse(s.any { Character.getType(it) == Character.FORMAT.toInt() })
        assertFalse(s.contains('\uDB40'))
        assertEquals("abc gpj.exe x y z", s)
    }
}
