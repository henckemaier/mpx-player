package com.mpx.player.data

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.android.Android

internal actual fun newHttpClient(block: HttpClientConfig<*>.() -> Unit): HttpClient =
    HttpClient(Android) { block() }
