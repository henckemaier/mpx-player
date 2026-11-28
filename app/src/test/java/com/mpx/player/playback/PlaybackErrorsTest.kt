package com.mpx.player.playback

import androidx.media3.common.PlaybackException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** (B) do reteste: faixa remota com agente parado precisa de mensagem visível. */
class PlaybackErrorsTest {

    @Test fun `agente parado em faixa remota`() {
        listOf(
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED,
        ).forEach { assertTrue(playbackErrorMessage(it, remote = true).contains("AGENTE DESKTOP OFFLINE")) }
    }

    @Test fun `http recusado pelo agente`() {
        assertTrue(playbackErrorMessage(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS, true).contains("RECUSOU"))
    }

    @Test fun `faixa local`() {
        assertEquals("ERRO: ARQUIVO NÃO ENCONTRADO", playbackErrorMessage(PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND, false))
        assertEquals("ERRO DE REDE AO TOCAR A FAIXA", playbackErrorMessage(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED, false))
        assertTrue(playbackErrorMessage(PlaybackException.ERROR_CODE_DECODER_INIT_FAILED, false).contains("FORMATO"))
        assertTrue(playbackErrorMessage(9999, false).contains("9999"))
    }
}
