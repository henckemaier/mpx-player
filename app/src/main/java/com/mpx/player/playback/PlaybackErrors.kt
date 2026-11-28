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

/** Eventos que podem mexer na mensagem de erro do player (ver [reducePlayerError]). */
enum class PlayerErrorEvent { ERROR, USER_ACTION, STARTED_PLAYING, TRANSITION, PAUSED }

/**
 * Regra DETERMINÍSTICA da mensagem de erro (reteste da Lupa: com o agente parado o erro chegava,
 * mas sumia da tela). O MediaController reentrega `onMediaItemTransition` (já mascarada no
 * setMediaItems) quando chega o estado real da sessão — às vezes DEPOIS do `onPlayerError` — e o
 * player pausa por causa do próprio erro. Então: o erro só some por AÇÃO do usuário
 * (play/toggle/next/prev/seek de faixa) ou quando o player REALMENTE volta a tocar; transição e pausa
 * não limpam. PURO.
 */
fun reducePlayerError(current: String?, event: PlayerErrorEvent, newError: String? = null): String? = when (event) {
    PlayerErrorEvent.ERROR -> newError ?: current
    PlayerErrorEvent.USER_ACTION, PlayerErrorEvent.STARTED_PLAYING -> null
    PlayerErrorEvent.TRANSITION, PlayerErrorEvent.PAUSED -> current
}
