package com.mpx.player.model

/**
 * Configuração do equalizador. Modelo puro (comum a Android/iOS); a aplicação dos
 * efeitos em si é por plataforma. [UserSettings] (no :app) referencia este tipo.
 */
data class EqSettings(
    val enabled: Boolean = true,
    val preset: String = "FLAT",
    /** 10 níveis de 0..12, 6 = 0 dB, passo de 2 dB. */
    val levels: List<Int> = EqPresets.FLAT,
    val custom: List<Int> = EqPresets.FLAT,
    /** 0..24, 12 = 0 dB, passo de 0,5 dB. */
    val preamp: Int = 12,
    val bassBoost: Boolean = false,
    val surround: Boolean = false,
    val loudness: Boolean = false,
)
