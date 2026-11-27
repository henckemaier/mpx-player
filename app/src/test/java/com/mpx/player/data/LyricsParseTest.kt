package com.mpx.player.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsParseTest {

    @Test fun linhaSimples() {
        val r = LyricsRepository.parse("[01:02.50]Olá mundo")
        assertEquals(1, r.size)
        assertEquals(62_500L, r[0].timeMs)
        assertEquals("Olá mundo", r[0].text)
    }

    @Test fun fracaoComUmDoisETresDigitos() {
        val r = LyricsRepository.parse("[00:01.5]a\n[00:02.25]b\n[00:03.123]c")
        assertEquals(listOf(1500L, 2250L, 3123L), r.map { it.timeMs })
    }

    @Test fun semFracaoEComDoisPontos() {
        val r = LyricsRepository.parse("[00:05]a\n[00:06:50]b")
        assertEquals(listOf(5000L, 6500L), r.map { it.timeMs })
    }

    @Test fun multiplosTimestampsNaMesmaLinhaFicamOrdenados() {
        val r = LyricsRepository.parse("[00:30.00][00:10.00]refrão\n[00:20.00]verso")
        assertEquals(listOf(10_000L, 20_000L, 30_000L), r.map { it.timeMs })
        assertEquals(listOf("refrão", "verso", "refrão"), r.map { it.text })
    }

    @Test fun ignoraMetadadosLinhasSemTagEVazias() {
        val r = LyricsRepository.parse("[ar:Artista]\n[ti:Titulo]\nsem tag\n\n[00:01.00]   \n[00:02.00]ok")
        assertEquals(1, r.size)
        assertEquals("ok", r[0].text)
    }

    @Test fun aceitaCrLfEMinutosAcimaDe59() {
        val r = LyricsRepository.parse("[75:00.00]longa\r\n[00:01.00]curta\r\n")
        assertEquals(listOf(1000L, 4_500_000L), r.map { it.timeMs })
        assertEquals("curta", r[0].text)
    }

    @Test fun textoVazioRetornaVazio() {
        assertTrue(LyricsRepository.parse("").isEmpty())
    }
}
