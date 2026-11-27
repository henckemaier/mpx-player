package com.mpx.player.data

/** Dados de pareamento extraídos do QR/deep-link do agente desktop. `host` = "host:porta" validado. */
data class PairInfo(val host: String, val token: String)

/**
 * Parse PURO do payload `mpx://pair?v=1&h=<host:port>&t=<token>` (gerado pelo agente,
 * [com.mpx.desktop.buildPairPayload]) -> [PairInfo], ou null se inválido. FRAMEWORK-FREE
 * (String in/out; NÃO usa android.net.Uri — que não roda em JVM puro, lição da sub-2): parseia
 * a query na mão. Rejeita (null): scheme != mpx, host != pair, v != 1, h/t ausentes ou vazios,
 * parâmetro repetido, `h` que não seja um host:porta de LAN ([parseLanHostPort]) e token fora de
 * [isValidPairToken]. O deep-link é BROWSABLE (qualquer página/QR pode dispará-lo), então nada
 * aqui pode ser confiado sem essa validação — e o app ainda pede confirmação antes de parear.
 */
fun parsePairPayload(raw: String): PairInfo? {
    val s = raw.trim()
    val prefix = "mpx://pair?"
    if (!s.startsWith(prefix)) return null
    val query = s.substring(prefix.length)
    if (query.contains('#')) return null
    val params = HashMap<String, String>()
    for (part in query.split('&')) {
        val eq = part.indexOf('=')
        if (eq <= 0) continue
        val key = part.substring(0, eq)
        if (params.containsKey(key)) return null // h/t duplicados: ambíguo, rejeita
        params[key] = part.substring(eq + 1)
    }
    if (params["v"] != "1") return null
    val host = params["h"]?.let(::parseLanHostPort) ?: return null
    val token = params["t"]?.takeIf(::isValidPairToken) ?: return null
    return PairInfo(host, token)
}

/** Token do agente: hoje UUID hex sem hífens (32); aceita [A-Za-z0-9_-]{16,128}. */
fun isValidPairToken(token: String): Boolean =
    token.length in 16..128 && token.all { it.isLetterOrDigit() && it.code < 128 || it == '-' || it == '_' }

/**
 * Valida e normaliza um endereço "host:porta" do agente desktop, aceitando SÓ destinos de rede
 * local — o tráfego com o agente é HTTP em claro (ver res/xml/network_security_config.xml), então o
 * app se recusa a falar com qualquer outra coisa:
 *  - IPv4 privado (10/8, 172.16/12, 192.168/16) ou loopback (127/8; inclui 10.0.2.2 do emulador);
 *  - `localhost` ou nome mDNS `*.local` (rótulos [A-Za-z0-9-], 1..63);
 *  - porta obrigatória 1..65535.
 * Rejeita (null): userinfo (`user@host`), `/`, `?`, `#`, espaços, IPv6, IPs públicos, porta ausente,
 * zero, não numérica ou fora da faixa. Retorna "host:porta" (host em minúsculas).
 */
fun parseLanHostPort(raw: String): String? {
    val s = raw.trim()
    if (s.isEmpty() || s.length > 260) return null
    if (s.any { !(it.isLetterOrDigit() && it.code < 128) && it != '.' && it != '-' && it != ':' }) return null
    val colon = s.lastIndexOf(':')
    if (colon <= 0 || colon != s.indexOf(':')) return null // sem porta, ou IPv6 / ':' repetido
    val host = s.substring(0, colon).lowercase()
    val portStr = s.substring(colon + 1)
    if (portStr.isEmpty() || portStr.length > 5 || !portStr.all(Char::isDigit)) return null
    val port = portStr.toInt()
    if (port !in 1..65535) return null
    if (!isLanHost(host)) return null
    return "$host:$port"
}

/** True se [host] (sem porta) é IPv4 privado/loopback, `localhost` ou `*.local`. Ver [parseLanHostPort]. */
fun isLanHost(host: String): Boolean {
    val h = host.lowercase()
    if (h == "localhost") return true
    ipv4Octets(h)?.let { o ->
        return o[0] == 10 ||
            o[0] == 127 ||
            (o[0] == 172 && o[1] in 16..31) ||
            (o[0] == 192 && o[1] == 168)
    }
    if (h.endsWith(".local")) {
        val labels = h.removeSuffix(".local").split('.')
        return labels.all { l ->
            l.length in 1..63 && l.first() != '-' && l.last() != '-' && l.all { it in 'a'..'z' || it in '0'..'9' || it == '-' }
        }
    }
    return false
}

/** "a.b.c.d" com 4 octetos decimais 0..255 (sem zeros à esquerda ambíguos) -> octetos, senão null. */
private fun ipv4Octets(s: String): IntArray? {
    val parts = s.split('.')
    if (parts.size != 4) return null
    val out = IntArray(4)
    for ((i, p) in parts.withIndex()) {
        if (p.isEmpty() || p.length > 3 || !p.all(Char::isDigit)) return null
        if (p.length > 1 && p[0] == '0') return null // "010" (octal em alguns parsers): rejeita
        val v = p.toInt()
        if (v > 255) return null
        out[i] = v
    }
    return out
}
