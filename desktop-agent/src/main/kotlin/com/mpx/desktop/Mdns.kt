package com.mpx.desktop

import java.net.InetAddress
import javax.jmdns.JmDNS
import javax.jmdns.ServiceInfo

/**
 * Anuncia o agente como `_mpx._tcp.local` na interface LAN ([host]) e [port], name="MPX Desktop".
 * O TXT leva SÓ a versão (AGENT_VERSION) — NUNCA o token (qualquer um na LAN leria o anúncio). O
 * app (NsdManager) descobre o host; o token continua vindo do QR/deep-link ou digitado.
 * Devolve o [JmDNS] p/ o caller desregistrar/fechar no shutdown.
 */
fun registerMdns(host: String, port: Int): JmDNS {
    val jmdns = JmDNS.create(InetAddress.getByName(host), "MPX-Desktop-Agent")
    val props = hashMapOf("version" to AGENT_VERSION) // SEM token
    val info = ServiceInfo.create("_mpx._tcp.local.", "MPX Desktop", port, 0, 0, props)
    jmdns.registerService(info)
    return jmdns
}
