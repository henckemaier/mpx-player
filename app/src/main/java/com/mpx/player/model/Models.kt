package com.mpx.player.model

// Track, Album e Artist foram movidos para :shared/commonMain (com.mpx.player.model), com
// Track.uri neutralizado para String (comum a Android/iOS).
// Playlist, TrackOverride e EqPresets também vivem em :shared/commonMain.

data class LyricLine(val timeMs: Long, val text: String)

data class UserSettings(
    val theme: Int = 0,
    /** Cor de destaque personalizada (ARGB). Usada quando theme == CUSTOM_THEME. */
    val customColor: Int? = null,
    val oled: Boolean = false,
    val font: Int = 0,
    val cover: Int = 0,
    val scanlines: Boolean = true,
    val glow: Boolean = true,
    val cursor: Boolean = true,
    val monoCovers: Boolean = true,
    val crossfade: Boolean = false,
    val lrcTreeUri: String? = null,
    val lastScan: Long = 0L,
    val playlistsImported: Int = 0,
    val eq: EqSettings = EqSettings(),
)

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

// EqPresets foi movido para :shared/commonMain (com.mpx.player.model).
