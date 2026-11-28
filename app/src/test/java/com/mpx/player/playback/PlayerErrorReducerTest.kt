package com.mpx.player.playback

import com.mpx.player.playback.PlayerErrorEvent.ERROR
import com.mpx.player.playback.PlayerErrorEvent.PAUSED
import com.mpx.player.playback.PlayerErrorEvent.STARTED_PLAYING
import com.mpx.player.playback.PlayerErrorEvent.TRANSITION
import com.mpx.player.playback.PlayerErrorEvent.USER_ACTION
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** (B) do reteste final: a mensagem de erro do player tem de ficar visível de forma determinística. */
class PlayerErrorReducerTest {

    private val msg = "ERRO: AGENTE DESKTOP OFFLINE? VERIFIQUE O PC E A REDE"

    private fun run(vararg events: Pair<PlayerErrorEvent, String?>): String? =
        events.fold(null as String?) { cur, (e, m) -> reducePlayerError(cur, e, m) }

    @Test fun `transicao reentregue DEPOIS do erro nao apaga a mensagem`() {
        // toque novo: USER_ACTION -> transicao mascarada -> erro -> transicao real (reentregue) -> pausa
        assertEquals(msg, run(USER_ACTION to null, TRANSITION to null, ERROR to msg, TRANSITION to null, PAUSED to null))
    }

    @Test fun `pausa causada pelo proprio erro nao apaga`() {
        assertEquals(msg, run(ERROR to msg, PAUSED to null, PAUSED to null))
    }

    @Test fun `acao do usuario ou voltar a tocar limpam`() {
        assertNull(run(ERROR to msg, USER_ACTION to null))
        assertNull(run(ERROR to msg, STARTED_PLAYING to null))
    }

    @Test fun `novo erro substitui o anterior`() {
        assertEquals("B", run(ERROR to "A", ERROR to "B"))
    }
}
