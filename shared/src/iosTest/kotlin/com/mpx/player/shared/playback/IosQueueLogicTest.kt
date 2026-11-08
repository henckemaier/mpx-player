package com.mpx.player.shared.playback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
    fun shuffleQueue_reorder_faixaAtualNoTopo_ePermutacaoCompleta() {
        // shuffleQueue() reconstrói order via buildPlayOrder(size, atual, shuffle=true)
        // SEMPRE embaralhando: a faixa atual (índice original 2) vai pro topo (→0) e a
        // ordem continua sendo uma permutação completa, sem perdas nem repetição.
        val atual = 2
        val o = buildPlayOrder(6, atual, shuffle = true)
        assertEquals(atual, o.first())            // faixa atual preservada no topo
        assertEquals(6, o.size)
        assertEquals((0..5).toSet(), o.toSet())    // permutação completa
        // E o novo pos da faixa atual na order reordenada é 0 (como shuffleQueue faz).
        assertEquals(0, o.indexOf(atual))
    }

    @Test
    fun clearQueue_filaZerada_eInerte() {
        // Após clearQueue(): items/order vazios e pos=-1. A lógica pura da fila trata
        // esse estado como inerte (sem ordem e sem próximo), em qualquer modo.
        assertEquals(emptyList(), buildPlayOrder(0, 0, shuffle = true))
        assertEquals(emptyList(), buildPlayOrder(0, 0, shuffle = false))
        assertNull(manualNextPos(-1, 0, RepeatMode.OFF))
        assertNull(manualNextPos(-1, 0, RepeatMode.ALL))
        assertNull(endAdvancePos(-1, 0, RepeatMode.OFF))
        assertNull(endAdvancePos(-1, 0, RepeatMode.ALL))
        assertNull(endAdvancePos(-1, 0, RepeatMode.ONE))
    }

    @Test
    fun queueIds_emOrdemDeReproducao_indexPosApontaAtual() {
        // debt1: após shuffleQueue a order fica embaralhada com a faixa atual no topo.
        // queueIds deve sair NA ORDEM DE REPRODUÇÃO e index=pos apontar a faixa atual.
        val items = pbItems("a", "b", "c", "d", "e")
        val atual = 2 // "c" (índice original)
        val order = buildPlayOrder(items.size, atual, shuffle = true) // "c" no topo
        val ids = queueIdsInPlayOrder(items, order)

        assertEquals(order.map { items[it].id }, ids)         // ids na ordem de reprodução
        assertEquals(items.map { it.id }.toSet(), ids.toSet()) // sem perdas/repetição
        val pos = 0 // shuffleQueue deixa a atual no topo da order
        assertEquals("c", ids[pos])                            // index=pos aponta a atual
    }

    @Test
    fun clearUpcoming_colapsaParaAtual_index0() {
        // debt1 + clearUpcoming: o modelo colapsa para só a faixa atual
        // (items=[atual], order=[0], pos=0) → queueIds=[id da atual], index=0.
        val atual = pbItem("cur")
        val order = listOf(0)
        val ids = queueIdsInPlayOrder(listOf(atual), order)
        assertEquals(listOf("cur"), ids)
        val pos = 0
        assertEquals("cur", ids[pos]) // index=0 segue apontando a atual
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

    @Test
    fun buildShuffleAll_permutacaoCompleta_semTopoFixo() {
        // FLAG de shuffle [SD=b]: PERMUTAÇÃO COMPLETA de 0..size-1, NENHUMA faixa presa no topo
        // (distinto do buildPlayOrder(..., true), que põe [start] no topo).
        val o = buildShuffleAll(6)
        assertEquals(6, o.size)
        assertEquals((0..5).toList(), o.sorted())   // permutação: todos presentes, sem repetição
        assertEquals(emptyList(), buildShuffleAll(0))
        assertEquals(listOf(0), buildShuffleAll(1))
    }

    @Test
    fun setShuffleFlag_permutacaoCompleta_atualPreservada_ondeCair() {
        // Convenção pós-setShuffle(true) [SD=b, canônica = Android/ExoPlayer]: order=buildShuffleAll(N);
        // a faixa atual é PRESERVADA no índice ONDE CAIR (pos=indexOf), podendo ser > 0 — diferente do
        // shuffleQueue(), que a põe no topo. Verifica as INVARIANTES de forma (o sorteio é aleatório),
        // repetindo várias vezes.
        val items = pbItems("a", "b", "c", "d", "e", "f")
        val atual = 3 // "d" (índice original)
        repeat(50) {
            val order = buildShuffleAll(items.size)
            val pos = order.indexOf(atual)
            assertTrue(pos >= 0, "a atual sempre está na permutação (buildShuffleAll é 0..N-1)")
            val ids = queueIdsInPlayOrder(items, order)
            assertEquals(items.size, ids.size)                          // sem perdas
            assertEquals(items.map { it.id }.sorted(), ids.sorted())    // permutação de TODOS os ids
            assertEquals("d", ids[pos])                                 // queueIds[index] == id da atual
        }
    }

    // --- Helpers de teste ---

    private fun pbItem(id: String): PlaybackItem =
        PlaybackItem(id = id, uri = "file:///$id", title = id, artist = "art", album = "alb")

    private fun pbItems(vararg ids: String): List<PlaybackItem> = ids.map { pbItem(it) }
}
