package com.mpx.player.data

import com.mpx.player.model.TrackOverride
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OverrideLogicTest {

    @Test fun withTitle_setsTrimmed() {
        val m = OverrideLogic.withTitle(emptyMap(), "k", "  Novo Nome ")
        assertEquals("Novo Nome", m["k"]?.title)
    }

    @Test fun withTitle_blankClearsAndRemovesWhenNoCover() {
        val m = OverrideLogic.withTitle(mapOf("k" to TrackOverride(title = "X")), "k", "  ")
        assertFalse(m.containsKey("k"))
    }

    @Test fun withTitle_nullKeepsCover() {
        val m = OverrideLogic.withTitle(mapOf("k" to TrackOverride(title = "X", cover = "/c.jpg")), "k", null)
        assertNull(m["k"]?.title)
        assertEquals("/c.jpg", m["k"]?.cover)
    }

    @Test fun withCover_setAndClear() {
        var m = OverrideLogic.withCover(emptyMap(), "k", "/c.jpg")
        assertEquals("/c.jpg", m["k"]?.cover)
        m = OverrideLogic.withCover(m, "k", null)
        assertFalse(m.containsKey("k")) // sem title nem cover -> entrada some
    }

    @Test fun bothSetThenClearBothRemovesEntry() {
        var m = OverrideLogic.withTitle(emptyMap(), "k", "T")
        m = OverrideLogic.withCover(m, "k", "/c.jpg")
        assertEquals(TrackOverride("T", "/c.jpg"), m["k"])
        m = OverrideLogic.withTitle(m, "k", null)
        assertEquals(TrackOverride(title = null, cover = "/c.jpg"), m["k"])
        m = OverrideLogic.withCover(m, "k", null)
        assertTrue(m.isEmpty())
    }

    @Test fun jsonRoundTrip() {
        var m = OverrideLogic.withTitle(emptyMap(), "a/one.mp3", "Um")
        m = OverrideLogic.withCover(m, "a/one.mp3", "/cover.jpg")
        m = OverrideLogic.withTitle(m, "b/two.mp3", "Dois")
        val back = OverrideLogic.fromJson(OverrideLogic.toJson(m))
        assertEquals(m, back)
    }

    @Test fun fromJson_blankOrGarbageIsEmpty() {
        assertTrue(OverrideLogic.fromJson("").isEmpty())
        assertTrue(OverrideLogic.fromJson("}{nope").isEmpty())
    }

    @Test fun fromJson_skipsBadEntryKeepsGood() {
        val json = """{"a":{"title":"Um"},"bad":42,"b":{"cover":"/c.jpg"}}"""
        val r = OverrideLogic.fromJson(json)
        assertEquals("Um", r["a"]?.title)
        assertEquals("/c.jpg", r["b"]?.cover)
        assertFalse(r.containsKey("bad"))
    }

    @Test fun titleLiterallyNullIsPreserved() {
        val m = OverrideLogic.withTitle(emptyMap(), "k", "null")
        val back = OverrideLogic.fromJson(OverrideLogic.toJson(m))
        assertEquals("null", back["k"]?.title)
    }
}
