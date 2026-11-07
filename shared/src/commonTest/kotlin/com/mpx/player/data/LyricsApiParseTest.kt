package com.mpx.player.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Cobre o parser de `.lrc` (puro, sem rede) agora compartilhado em [LyricsApi]. */
class LyricsApiParseTest {

    @Test fun parse_basicTimestamp() {
        val r = LyricsApi.parse("[00:12.34]hello")
        assertEquals(1, r.size)
        assertEquals(12_340, r[0].timeMs)
        assertEquals("hello", r[0].text)
    }

    @Test fun parse_minutesAndSeconds() {
        val r = LyricsApi.parse("[01:02.50]world")
        assertEquals(1 * 60_000 + 2 * 1000 + 500, r[0].timeMs.toInt())
        assertEquals("world", r[0].text)
    }

    @Test fun parse_fractionDigitsVariants() {
        // sem fração, 1, 2 e 3 dígitos → ms = 0, x100, x10, exato
        assertEquals(0, LyricsApi.parse("[00:00]a")[0].timeMs)
        assertEquals(500, LyricsApi.parse("[00:00.5]a")[0].timeMs)
        assertEquals(50, LyricsApi.parse("[00:00.05]a")[0].timeMs)
        assertEquals(123, LyricsApi.parse("[00:00.123]a")[0].timeMs)
    }

    @Test fun parse_multiTimestampLineExpandsAndSorts() {
        val r = LyricsApi.parse("[00:03.00][00:01.00]repeat")
        assertEquals(2, r.size)
        assertEquals(listOf(1_000L, 3_000L), r.map { it.timeMs }) // ordenado por tempo
        assertTrue(r.all { it.text == "repeat" })
    }

    @Test fun parse_skipsLinesWithoutTimestampAndEmptyText() {
        val r = LyricsApi.parse(
            """
            metadata sem timestamp
            [00:02.00]segunda
            [00:05.00]
            [00:01.00]primeira
            """.trimIndent(),
        )
        assertEquals(listOf("primeira", "segunda"), r.map { it.text }) // vazio filtrado, ordenado
    }

    @Test fun parse_blankReturnsEmpty() {
        assertTrue(LyricsApi.parse("").isEmpty())
        assertTrue(LyricsApi.parse("linha solta\noutra").isEmpty())
    }
}
