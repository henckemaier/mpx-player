package com.mpx.player.shared.playback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Lógica pura da fila do IosAudioPlayer (sem AVFoundation): ordem de reprodução,
 * avanço de índice (manual e no fim), e saneamento de tempo. Roda no simulador
 * via :shared:iosSimulatorArm64Test.
 */
class IosQueueLogicTest {

    @Test
    fun playOrder_identidadeQuandoSemShuffle() {
        assertEquals(listOf(0, 1, 2, 3), buildPlayOrder(4, 2, shuffle = false))
        assertEquals(emptyList(), buildPlayOrder(0, 0, shuffle = false))
    }

    @Test
    fun playOrder_shuffle_startNoTopo_eContemTudo() {
        val o = buildPlayOrder(5, 3, shuffle = true)
        assertEquals(3, o.first())            // faixa escolhida no topo
        assertEquals(5, o.size)
        assertEquals((0..4).toSet(), o.toSet()) // permutação completa, sem perdas
    }

    @Test
    fun manualNext_avanca_eTrataFim() {
        assertEquals(1, manualNextPos(0, 3, RepeatMode.OFF))
        assertNull(manualNextPos(2, 3, RepeatMode.OFF))          // fim sem repeat: no-op
        assertEquals(0, manualNextPos(2, 3, RepeatMode.ALL))     // wrap
        assertEquals(2, manualNextPos(1, 3, RepeatMode.ONE))     // ONE manual avança como OFF
        assertNull(manualNextPos(2, 3, RepeatMode.ONE))          // ONE manual no fim: para
    }

    @Test
    fun endAdvance_porModo() {
        assertEquals(1, endAdvancePos(0, 3, RepeatMode.OFF))
        assertNull(endAdvancePos(2, 3, RepeatMode.OFF))          // fim sem repeat: para
        assertEquals(0, endAdvancePos(2, 3, RepeatMode.ALL))     // fim → wrap pra 0
        assertEquals(2, endAdvancePos(1, 3, RepeatMode.ALL))     // meio → avança
        assertEquals(1, endAdvancePos(1, 3, RepeatMode.ONE))     // ONE: reinicia a mesma
        assertEquals(0, endAdvancePos(0, 1, RepeatMode.ALL))     // fila de 1 com ALL: volta a 0
    }

    @Test
    fun tempo_saneado() {
        assertEquals(0.0, safeSeconds(Double.NaN))
        assertEquals(0.0, safeSeconds(Double.POSITIVE_INFINITY))
        assertEquals(0.0, safeSeconds(-5.0))
        assertEquals(1.5, safeSeconds(1.5))
        assertEquals(1500L, secondsToMs(1.5))
        assertEquals(0L, secondsToMs(Double.NaN))
    }
}
