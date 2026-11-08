package com.mpx.player.shared.playback

import androidx.media3.common.Player
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Mapeamentos puros do AndroidAudioPlayer (sem device: só constantes int do Media3). */
class AndroidAudioPlayerMappingTest {

    @Test
    fun repeatMode_idaEVolta() {
        for (mode in RepeatMode.entries) {
            assertEquals(mode, repeatFromMedia3(mode.toMedia3()))
        }
        assertEquals(Player.REPEAT_MODE_OFF, RepeatMode.OFF.toMedia3())
        assertEquals(Player.REPEAT_MODE_ALL, RepeatMode.ALL.toMedia3())
        assertEquals(Player.REPEAT_MODE_ONE, RepeatMode.ONE.toMedia3())
        // valor desconhecido cai em OFF
        assertEquals(RepeatMode.OFF, repeatFromMedia3(999))
    }

    @Test
    fun volumeLevel_escalaEClampa() {
        assertEquals(0, volumeLevelFor(0f, 15))
        assertEquals(15, volumeLevelFor(1f, 15))
        assertEquals(7, volumeLevelFor(0.5f, 15)) // 7.5 -> trunca
        assertEquals(0, volumeLevelFor(-1f, 15)) // clamp inferior
        assertEquals(15, volumeLevelFor(2f, 15)) // clamp superior
    }

    @Test
    fun shuffledTail_permutaCaudaSemTopo() {
        // shuffleQueue() mantém a posição 0 (faixa atual) fixa e embaralha 1..count-1.
        assertEquals(emptyList(), shuffledTailPositions(0)) // fila vazia
        assertEquals(emptyList(), shuffledTailPositions(1)) // só a faixa atual: nada a embaralhar
        val tail = shuffledTailPositions(6)
        assertEquals((1..5).toSet(), tail.toSet())          // exatamente as posições 1..5
        assertEquals(5, tail.size)                          // sem perdas nem repetição
        assertTrue(tail.none { it == 0 })                   // nunca mexe no topo (faixa atual)
    }

    @Test
    fun clearUpcoming_removeDepoisEntaoAntes_mantendoAtual() {
        // fila de 0/1 item: nada a remover (no-op)
        assertEquals(emptyList(), clearUpcomingRemovals(0, 0))
        assertEquals(emptyList(), clearUpcomingRemovals(1, 0))
        // atual no meio (count=5, cur=2): remove a cauda (3..5) ANTES do começo (0..2)
        assertEquals(listOf(3 to 5, 0 to 2), clearUpcomingRemovals(5, 2))
        // atual no topo (cur=0): só a cauda
        assertEquals(listOf(1 to 4), clearUpcomingRemovals(4, 0))
        // atual no fim (cur=last): só o começo
        assertEquals(listOf(0 to 3), clearUpcomingRemovals(4, 3))
        // index fora de faixa é saneado
        assertEquals(listOf(0 to 3), clearUpcomingRemovals(4, 9))
    }

    @Test
    fun playbackOrder_lineareShuffleeVazioeCiclo() {
        val UNSET = -1
        // linear (shuffle off): 0->1->2->3->fim
        assertEquals(
            listOf(0, 1, 2, 3),
            playbackOrder(firstIndex = 0, unset = UNSET) { if (it < 3) it + 1 else UNSET },
        )
        // shuffle: ordem de reprodução derivada do mapa next (2,0,3,1)
        val nextMap = mapOf(2 to 0, 0 to 3, 3 to 1, 1 to UNSET)
        assertEquals(
            listOf(2, 0, 3, 1),
            playbackOrder(firstIndex = 2, unset = UNSET) { nextMap.getValue(it) },
        )
        // fila vazia: firstIndex == unset
        assertEquals(emptyList(), playbackOrder(firstIndex = UNSET, unset = UNSET) { UNSET })
        // guarda anti-loop: next que cicla (0->1->0) para após visitar cada um 1x
        val cyclic = mapOf(0 to 1, 1 to 0)
        assertEquals(listOf(0, 1), playbackOrder(firstIndex = 0, unset = UNSET) { cyclic.getValue(it) })
    }
}
