package com.mpx.desktop

import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * IPv4s SITE-LOCAL (LAN) da maquina — nao-loopback, de interfaces ativas. O agente liga SO
 * numa dessas (inegociavel [SD-C2]): nunca 0.0.0.0 (nao expoe interface WAN). Ordena
 * deterministicamente (por interface e endereco) p/ a escolha "1a util" ser estavel.
 */
fun siteLocalIpv4s(): List<String> =
    NetworkInterface.getNetworkInterfaces().asSequence()
        .filter { runCatching { it.isUp && !it.isLoopback && !it.isVirtual }.getOrDefault(false) }
        .sortedBy { it.index }
        .flatMap { it.inetAddresses.asSequence() }
        .filterIsInstance<Inet4Address>()
        .filter { it.isSiteLocalAddress && !it.isLoopbackAddress }
        .map { it.hostAddress }
        .distinct()
        .toList()
