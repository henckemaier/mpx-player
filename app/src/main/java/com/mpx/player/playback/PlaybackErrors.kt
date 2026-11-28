package com.mpx.player.playback

import androidx.media3.common.PlaybackException

/**
 * Texto curto (estilo CMD.LOG) para um erro do player. PURO (códigos int do Media3) p/ teste JVM.
 * O app só toca arquivos LOCAIS (MediaStore); erro de rede aqui é inesperado, mas segue tratado.
 */
fun playbackErrorMessage(errorCode: Int): String {
    val network = errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
        errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ||
        errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ||
        errorCode == PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED ||
        errorCode == PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE
    return when {
        network -> "ERRO DE REDE AO TOCAR A FAIXA"
        errorCode == PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> "ERRO: ARQUIVO NÃO ENCONTRADO"
        errorCode in 4000..4999 -> "ERRO: FORMATO/DECODER NÃO SUPORTADO"
        else -> "ERRO DE REPRODUÇÃO ($errorCode)"
    }
}

/**
 * O app só reproduz URIs locais (content://, file://, android.resource://). Qualquer outro esquema
 * (ex.: http://<ip>:porta/stream/... deixado pelo desktop-sync da 1.7.0, removido na 1.7.1) é
 * pulado ao montar a fila, sem crash. PURO (recebe o scheme, não Uri) p/ teste JVM.
 */
fun isPlayableScheme(scheme: String?): Boolean =
    scheme != null && scheme.lowercase() in LOCAL_SCHEMES

private val LOCAL_SCHEMES = setOf("content", "file", "android.resource")

/**
 * Filtra a fila mantendo só itens tocáveis ([isPlayableScheme]) e reajusta o índice inicial para o
 * MESMO item (ou o próximo tocável depois dele; se não houver, o último tocável). Fila sem nada
 * tocável -> lista vazia e índice -1. PURO/genérico p/ teste JVM.
 */
fun <T> playableQueue(items: List<T>, startIndex: Int, scheme: (T) -> String?): Pair<List<T>, Int> {
    val start = startIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))
    val kept = ArrayList<T>(items.size)
    var newStart = -1
    items.forEachIndexed { i, t ->
        if (isPlayableScheme(scheme(t))) {
            if (newStart < 0 && i >= start) newStart = kept.size
            kept += t
        }
    }
    if (kept.isEmpty()) return emptyList<T>() to -1
    return kept to (if (newStart < 0) kept.lastIndex else newStart)
}

/** Eventos que podem mexer na mensagem de erro do player (ver [reducePlayerError]). */
enum class PlayerErrorEvent { ERROR, USER_ACTION, STARTED_PLAYING, TRANSITION, PAUSED }

/**
 * Regra DETERMINÍSTICA da mensagem de erro (reteste da Lupa: o erro chegava, mas sumia da tela). O MediaController reentrega `onMediaItemTransition` (já mascarada no
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
