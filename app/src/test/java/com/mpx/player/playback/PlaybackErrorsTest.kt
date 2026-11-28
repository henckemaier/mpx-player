package com.mpx.player.playback

import androidx.media3.common.PlaybackException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Mensagem do erro do player (genérica: o app só toca faixas locais desde a 1.7.1). */
class PlaybackErrorsTest {

    @Test fun `mensagens genericas`() {
        assertEquals("ERRO: ARQUIVO NÃO ENCONTRADO", playbackErrorMessage(PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND))
        assertEquals("ERRO DE REDE AO TOCAR A FAIXA", playbackErrorMessage(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED))
        assertEquals("ERRO DE REDE AO TOCAR A FAIXA", playbackErrorMessage(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS))
        assertTrue(playbackErrorMessage(PlaybackException.ERROR_CODE_DECODER_INIT_FAILED).contains("FORMATO"))
        assertTrue(playbackErrorMessage(9999).contains("9999"))
    }

    @Test fun `nenhuma mensagem menciona o desktop`() {
        val codes = listOf(
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
            PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED,
            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            1000,
        )
        codes.forEach { code ->
            val m = playbackErrorMessage(code)
            listOf("AGENTE", "DESKTOP", "PC").forEach { w -> assertFalse("$code -> $m", m.contains(w)) }
        }
    }
}
