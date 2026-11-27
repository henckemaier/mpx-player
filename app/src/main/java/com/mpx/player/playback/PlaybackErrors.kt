package com.mpx.player.playback

import androidx.media3.common.PlaybackException

/**
 * Texto curto (estilo CMD.LOG) para um erro do player. PURO (códigos int do Media3) p/ teste JVM.
 * [remote] = a faixa atual é do agente desktop (id sintético negativo, ver syntheticId): erro de
 * rede/HTTP nela quase sempre é agente parado, IP errado ou Wi-Fi diferente.
 */
fun playbackErrorMessage(errorCode: Int, remote: Boolean): String {
    val network = errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
        errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ||
        errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ||
        errorCode == PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED ||
        errorCode == PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE
    return when {
        remote && errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ->
            "ERRO: AGENTE RECUSOU A FAIXA (TOKEN/ARQUIVO) · RECONECTE EM CFG > DESKTOP"
        remote && network -> "ERRO: AGENTE DESKTOP OFFLINE? VERIFIQUE O PC E A REDE"
        network -> "ERRO DE REDE AO TOCAR A FAIXA"
        errorCode == PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> "ERRO: ARQUIVO NÃO ENCONTRADO"
        errorCode in 4000..4999 -> "ERRO: FORMATO/DECODER NÃO SUPORTADO"
        else -> "ERRO DE REPRODUÇÃO ($errorCode)"
    }
}
