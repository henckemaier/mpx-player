package com.mpx.desktop

import java.io.File
import java.io.IOException
import java.security.MessageDigest

/** Compara o token em TEMPO CONSTANTE (evita timing attack). [provided] nulo -> false. */
fun tokenMatches(provided: String?, expected: String): Boolean =
    provided != null &&
        MessageDigest.isEqual(provided.toByteArray(Charsets.UTF_8), expected.toByteArray(Charsets.UTF_8))

/**
 * [file] (ja resolvido por symlink/junction/"..") esta DENTRO de [root]? Compara caminhos
 * canonicos com separador final (evita o falso positivo "/musica-evil" vs "/musica").
 * Qualquer erro de I/O na canonicalizacao -> false (fecha por padrao).
 */
fun isInsideRoot(root: File, file: File): Boolean = try {
    val r = root.canonicalFile.toPath()
    val f = file.canonicalFile.toPath()
    f.startsWith(r)
} catch (_: IOException) {
    false
}

/**
 * Rate-limit de falhas de token por IP de origem: apos [maxFailures] falhas dentro de
 * [windowMs], o IP fica bloqueado por [blockMs] (o chamador responde 429). Em memoria,
 * thread-safe (um lock; operacoes sao O(1) + limpeza ocasional). [clock] injetavel p/ teste.
 */
class AuthRateLimiter(
    private val maxFailures: Int = 10,
    private val windowMs: Long = 60_000,
    private val blockMs: Long = 60_000,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private class State(val failures: ArrayDeque<Long> = ArrayDeque(), var blockedUntil: Long = 0L)

    private val lock = Any()
    private val states = HashMap<String, State>()

    /** Segundos restantes de bloqueio (>0) se [ip] esta bloqueado; 0 caso contrario. */
    fun retryAfterSeconds(ip: String): Long = synchronized(lock) {
        val s = states[ip] ?: return 0
        val left = s.blockedUntil - clock()
        if (left > 0) (left + 999) / 1000 else 0
    }

    /** Registra uma falha de token de [ip]; bloqueia ao atingir o limite na janela. */
    fun recordFailure(ip: String) = synchronized(lock) {
        val now = clock()
        if (states.size > 256) prune(now)
        val s = states.getOrPut(ip) { State() }
        while (s.failures.isNotEmpty() && now - s.failures.first() >= windowMs) s.failures.removeFirst()
        s.failures.addLast(now)
        if (s.failures.size >= maxFailures) {
            s.blockedUntil = now + blockMs
            s.failures.clear()
        }
    }

    /** Numero de IPs rastreados (p/ teste da limpeza). */
    fun trackedCount(): Int = synchronized(lock) { states.size }

    private fun prune(now: Long) {
        states.entries.removeIf { (_, s) ->
            s.blockedUntil <= now && s.failures.none { now - it < windowMs }
        }
    }
}
