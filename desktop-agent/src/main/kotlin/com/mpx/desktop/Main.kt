package com.mpx.desktop

import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import java.io.File
import java.util.UUID
import java.util.logging.Level
import java.util.logging.Logger

const val AGENT_VERSION = "0.1.0"

/**
 * Entry point do agente desktop-sync (MVP, sub-1).
 *
 * Uso: `MPXDesktopAgent [pastaDeMusicas] [--port N] [--host IP]`
 *  - pastaDeMusicas: 1o arg posicional; default = a pasta Music do usuario (~/Music).
 *  - --port: porta (default 8080).
 *  - --host: forca o IP de bind (override); senao usa o 1o IPv4 site-local (LAN) detectado.
 *
 * NUNCA liga em 0.0.0.0 ([SD-C2]). Gera um token aleatorio no arranque e o imprime no
 * console junto das URLs da LAN. Scan da pasta no arranque (re-scan sob demanda = sub futura).
 */
fun main(args: Array<String>) {
    // jaudiotagger loga demais em java.util.logging -> silencia.
    Logger.getLogger("org.jaudiotagger").level = Level.OFF

    val opts = parseArgs(args)
    val musicDir = opts.folder?.let(::File) ?: defaultMusicDir()
    val port = opts.port ?: 8080

    println("== MPX Desktop Agent v$AGENT_VERSION ==")
    println("Pasta de musicas: ${musicDir.absolutePath}")
    if (!musicDir.isDirectory) {
        System.err.println("ERRO: pasta nao existe ou nao e diretorio: ${musicDir.absolutePath}")
        return
    }

    print("Escaneando... ")
    val entries = LibraryScanner(musicDir, JAudioTaggerReader()).scan()
    val library = Library(entries, ::jAudioTaggerArt)
    println("${library.count} faixa(s).")

    // Bind LAN-only: IP do override, senao o 1o site-local.
    val lanIps = siteLocalIpv4s()
    val host = opts.host ?: lanIps.firstOrNull()
    if (host == null) {
        System.err.println(
            "ERRO: nenhuma interface IPv4 site-local (LAN) encontrada. " +
                "Conecte-se a uma rede local ou passe --host <ip>. (Nao ligamos em 0.0.0.0.)"
        )
        return
    }
    if (lanIps.size > 1 && opts.host == null) {
        println("Varias interfaces LAN: $lanIps -> usando a 1a ($host). Override: --host <ip>.")
    }

    val token = UUID.randomUUID().toString().replace("-", "")

    println()
    println("TOKEN (cole no celular): $token")
    println("URL da LAN:  http://$host:$port")
    println("  /ping     http://$host:$port/ping?token=$token")
    println("  /library  http://$host:$port/library?token=$token")
    println()

    // QR de pareamento: leia com a câmera/leitor do sistema do celular -> abre o MPX pareado.
    val payload = buildPairPayload(host, port, token)
    runCatching {
        println("QR DE PAREAMENTO (leia com a câmera/leitor do sistema do celular):")
        println(qrToAnsi(pairQrMatrix(payload)))
    }.onFailure { System.err.println("QR indisponível: ${it.message}") }
    println("Payload: $payload")
    println("Bind: $host:$port (LAN-only; nunca 0.0.0.0). Ctrl+C p/ parar.")
    println()

    val engine = embeddedServer(CIO, host = host, port = port) {
        mpxAgentModule(library, token, AGENT_VERSION)
    }
    engine.start(wait = false)

    // Descoberta automática: anuncia _mpx._tcp na LAN (sem o token). Degrada se indisponível.
    val mdns = runCatching { registerMdns(host, port) }.getOrNull()
    if (mdns != null) {
        println("mDNS: \"MPX Desktop\" (_mpx._tcp) anunciado na porta $port — SEM token no anúncio.")
    } else {
        System.err.println("mDNS indisponível (descoberta automática off; use o IP/QR).")
    }

    val stop = java.util.concurrent.CountDownLatch(1)
    Runtime.getRuntime().addShutdownHook(
        Thread {
            runCatching { mdns?.unregisterAllServices(); mdns?.close() }
            runCatching { engine.stop(500, 1500) }
            stop.countDown()
        },
    )
    stop.await() // mantém o processo vivo até o Ctrl+C (shutdown hook)
}

private class Options(val folder: String?, val port: Int?, val host: String?)

private fun parseArgs(args: Array<String>): Options {
    var folder: String? = null
    var port: Int? = null
    var host: String? = null
    var i = 0
    while (i < args.size) {
        when (val a = args[i]) {
            "--port" -> { port = args.getOrNull(++i)?.toIntOrNull() }
            "--host" -> { host = args.getOrNull(++i) }
            else -> if (!a.startsWith("--") && folder == null) folder = a
        }
        i++
    }
    return Options(folder, port, host)
}

private fun defaultMusicDir(): File {
    val home = System.getProperty("user.home") ?: "."
    return File(home, "Music")
}
