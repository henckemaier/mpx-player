package com.mpx.player.model

// Track, Album e Artist foram movidos para :shared/commonMain (com.mpx.player.model), com
// Track.uri neutralizado para String (comum a Android/iOS).
// Playlist, TrackOverride, EqPresets e LyricLine também vivem em :shared/commonMain.

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

// EqSettings e EqPresets foram movidos para :shared/commonMain (com.mpx.player.model).
// UserSettings (acima) referencia EqSettings do :shared (mesmo pacote).
