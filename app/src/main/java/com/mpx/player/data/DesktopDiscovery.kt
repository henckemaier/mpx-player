package com.mpx.player.data

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** Agente desktop descoberto por mDNS. O token NÃO vem daqui (só host:porta). */
data class DiscoveredAgent(val name: String, val host: String, val port: Int) {
    val endpoint: String get() = "$host:$port"
}

/**
 * Descoberta mDNS do agente (_mpx._tcp) via [NsdManager] (android.net.nsd, BUILT-IN — NENHUMA
 * permissão nova; o NsdManager gerencia o multicast lock internamente). Expõe um Flow da lista
 * de descobertos; a descoberta liga quando a UI assina e desliga no [awaitClose] (ciclo da tela
 * Conectar). O token continua vindo do QR/deep-link ou digitado — mDNS só traz host:porta.
 */
class DesktopDiscovery(context: Context) {

    private val nsd = context.applicationContext.getSystemService(Context.NSD_SERVICE) as NsdManager

    fun discover(): Flow<List<DiscoveredAgent>> = callbackFlow {
        val found = LinkedHashMap<String, DiscoveredAgent>()
        fun push() { trySend(found.values.toList()) }

        val discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) { close() }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}

            override fun onServiceFound(service: NsdServiceInfo) {
                if (!service.serviceType.contains("_mpx._tcp")) return
                // Resolve p/ obter host/porta reais (um resolve por serviço achado).
                runCatching {
                    nsd.resolveService(service, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
                        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                            val addr = serviceInfo.host?.hostAddress ?: return
                            val name = serviceInfo.serviceName ?: "MPX Desktop"
                            found[name] = DiscoveredAgent(name, addr, serviceInfo.port)
                            push()
                        }
                    })
                }
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                if (found.remove(service.serviceName) != null) push()
            }
        }

        val started = runCatching {
            nsd.discoverServices("_mpx._tcp", NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        }.isSuccess
        if (!started) close()

        awaitClose { runCatching { nsd.stopServiceDiscovery(discoveryListener) } }
    }
}
