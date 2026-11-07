package com.mpx.player.model

// Movido de :app (model/Models.kt) para :shared/commonMain na fatia fina de UI (P7): o
// MpxTheme — agora em commonMain — recebe UserSettings, então o modelo precisa ser comum.
// Mesmo pacote/FQN (com.mpx.player.model.UserSettings) do original, logo os call-sites do
// :app resolvem sem edição. Só referencia EqSettings, que já vive aqui no :shared.
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
