package com.mpx.player.data

/**
 * Mascara segredos antes de irem para o log local ([CrashLog]): parâmetros `token=`/`t=` em URLs,
 * cabeçalho `Bearer <x>` e os valores literais passados em [secrets] (ex.: o token do agente).
 * PURO (String in/out) para teste em JVM.
 */
fun maskSecrets(text: String, vararg secrets: String): String {
    var s = text
    for (sec in secrets) if (sec.length >= 4) s = s.replace(sec, "***")
    s = s.replace(Regex("""(?i)([?&](?:token|t)=)[^&\s#"'<>]+"""), "$1***")
    s = s.replace(Regex("""(?i)(Bearer\s+)[^\s"'<>]+"""), "$1***")
    // URL-encoded (ex.: dentro de outro parâmetro/redirect): %3Ftoken%3D... / %26t%3D...
    s = s.replace(Regex("""(?i)((?:%3F|%26)(?:token|t)%3D)[^&\s"'<>%]+"""), "$1***")
    return s
}

/** Exceção "limpa" para o log: mesma pilha, mensagem com segredos mascarados e a classe original no texto. */
class SanitizedException(message: String, cause: Throwable?) : Exception(message, cause)

/**
 * Cópia de [t] (e da cadeia de causas, até 8 níveis) com as mensagens passadas por [maskSecrets].
 * Mantém o stack trace original de cada nível; a classe original vai no início da mensagem.
 */
fun sanitizeThrowable(t: Throwable, vararg secrets: String): Throwable = sanitize(t, secrets, 0)

private fun sanitize(t: Throwable, secrets: Array<out String>, depth: Int): Throwable {
    val cause = t.cause?.takeIf { it !== t && depth < 8 }?.let { sanitize(it, secrets, depth + 1) }
    val msg = t.message?.let { maskSecrets(it, *secrets) }
    val text = if (msg.isNullOrEmpty()) t.javaClass.name else "${t.javaClass.name}: $msg"
    return SanitizedException(text, cause).also { it.stackTrace = t.stackTrace }
}
