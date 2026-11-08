package com.mpx.player.data

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig

/**
 * Cria um [HttpClient] Ktor com a engine da plataforma (Android: `HttpURLConnection`; iOS: Darwin /
 * `NSURLSession`). A configuração comum (timeouts, headers) é aplicada via [block], mantida em
 * `commonMain` para as duas plataformas compartilharem o mesmo comportamento de rede.
 */
internal expect fun newHttpClient(block: HttpClientConfig<*>.() -> Unit): HttpClient
