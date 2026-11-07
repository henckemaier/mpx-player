package com.mpx.player.ui.nav

import com.mpx.player.ui.nav.Destination.CONFIG
import com.mpx.player.ui.nav.Destination.EQ
import com.mpx.player.ui.nav.Destination.LIBRARY
import com.mpx.player.ui.nav.Destination.PLAY
import com.mpx.player.ui.nav.Destination.QUEUE
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Trava a lógica PURA do back-stack [NavState] (commonTest → `:shared:testDebugUnitTest` E
 * `iosSimulatorArm64Test`; re-confirma a convenção reordena-vs-empilha no runtime iOS).
 */
class NavStateTest {

    @Test
    fun goTo_pushesAndBecomesCurrent() {
        val s = NavState().goTo(LIBRARY)
        assertEquals(LIBRARY, s.current)
        assertEquals(listOf(PLAY, LIBRARY), s.stack)
        assertTrue(s.canGoBack)
    }

    @Test
    fun goTo_current_isNoOp() {
        val s = NavState(listOf(PLAY, LIBRARY))
        assertSame(s, s.goTo(LIBRARY)) // mesmo destino → mesma instância, sem empilhar
    }

    @Test
    fun goTo_alreadyVisited_reordersWithoutDuplicating() {
        // [PLAY, LIB, QUEUE] e volto p/ LIB → LIB sai do meio e vai pro topo, sem duplicar.
        val s = NavState(listOf(PLAY, LIBRARY, QUEUE)).goTo(LIBRARY)
        assertEquals(listOf(PLAY, QUEUE, LIBRARY), s.stack)
        assertEquals(LIBRARY, s.current)
    }

    @Test
    fun goTo_neverExceedsFive() {
        // Visita os 5 e revisita PLAY: dedup mantém a pilha em no máx. 5.
        val s = NavState()
            .goTo(LIBRARY).goTo(QUEUE).goTo(EQ).goTo(CONFIG)
            .goTo(PLAY)
        assertEquals(5, s.stack.size)
        assertEquals(listOf(LIBRARY, QUEUE, EQ, CONFIG, PLAY), s.stack)
        assertEquals(s.stack.size, s.stack.toSet().size) // zero duplicata
    }

    @Test
    fun pop_goesBackPreservingOrder() {
        val s = NavState(listOf(PLAY, QUEUE, LIBRARY)).pop()
        assertEquals(listOf(PLAY, QUEUE), s.stack)
        assertEquals(QUEUE, s.current)
    }

    @Test
    fun pop_onSingle_isNoOp() {
        val s = NavState()
        assertSame(s, s.pop())
    }

    @Test
    fun popToRoot_reducesToPlay() {
        // Política A1: back volta direto pra PLAY, mesmo com PLAY no meio da pilha.
        val s = NavState(listOf(LIBRARY, PLAY, EQ)).popToRoot()
        assertEquals(listOf(PLAY), s.stack)
        assertFalse(s.canGoBack)
    }

    @Test
    fun popToRoot_onlyPlay_isNoOp() {
        val s = NavState()
        assertSame(s, s.popToRoot())
    }

    @Test
    fun canGoBack_reflectsStackSize() {
        assertFalse(NavState().canGoBack)
        assertTrue(NavState(listOf(PLAY, LIBRARY)).canGoBack)
    }
}
