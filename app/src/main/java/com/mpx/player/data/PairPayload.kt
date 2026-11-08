package com.mpx.player.data

/** Dados de pareamento extraídos do QR/deep-link do agente desktop. */
data class PairInfo(val host: String, val token: String)

/**
 * Parse PURO do payload `mpx://pair?v=1&h=<host:port>&t=<token>` (gerado pelo agente,
 * [com.mpx.desktop.buildPairPayload]) -> [PairInfo], ou null se inválido. FRAMEWORK-FREE
 * (String in/out; NÃO usa android.net.Uri — que não roda em JVM puro, lição da sub-2): parseia
 * a query na mão. Rejeita (null): scheme != mpx, host != pair, v != 1, h/t ausentes ou vazios.
 */
fun parsePairPayload(raw: String): PairInfo? {
    val s = raw.trim()
    val prefix = "mpx://pair?"
    if (!s.startsWith(prefix)) return null
    val query = s.substring(prefix.length)
    val params = HashMap<String, String>()
    for (part in query.split('&')) {
        val eq = part.indexOf('=')
        if (eq <= 0) continue
        params[part.substring(0, eq)] = part.substring(eq + 1)
    }
    if (params["v"] != "1") return null
    val host = params["h"]?.takeIf { it.isNotBlank() } ?: return null
    val token = params["t"]?.takeIf { it.isNotBlank() } ?: return null
    return PairInfo(host, token)
}
