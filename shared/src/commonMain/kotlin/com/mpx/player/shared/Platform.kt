package com.mpx.player.shared

/**
 * Prova de fumaça do toolchain KMP: resolvida por [actual] em cada plataforma.
 * Útil também para telas de diagnóstico (ex.: SOBRE) distinguirem Android/iOS.
 */
expect object Platform {
    /** Ex.: "Android 35" ou "iOS 17.4". */
    val name: String
}
