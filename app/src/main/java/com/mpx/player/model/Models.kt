package com.mpx.player.model

import android.net.Uri

data class Track(
    val id: Long,
    val uri: Uri,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val displayName: String,
    val relativePath: String,
    val mimeType: String,
    val bitrate: Int?,
    val trackNumber: Int,
    val year: Int?,
    /** Caminho de uma capa personalizada (override do app); null = usa a arte embutida. */
    val coverOverride: String? = null,
) {
    /** Identidade estável do arquivo (sobrevive a mudanças do _ID do MediaStore). */
    val key: String get() = (relativePath + displayName).lowercase()

    /** Caminho no estilo DOS, ex.: C:\MUSIC\NULL_SECTOR\04_SIGNAL_DECAY.MP3 */
    val dosPath: String
        get() {
            val dir = relativePath.trimEnd('/').replace('/', '\\').uppercase()
            return "C:\\$dir\\${displayName.uppercase()}".replace(' ', '_')
        }

    val format: String
        get() = when {
            mimeType.contains("mpeg") -> "MP3"
            mimeType.contains("flac") -> "FLAC"
            mimeType.contains("mp4") || mimeType.contains("aac") -> "AAC"
            mimeType.contains("ogg") -> "OGG"
            mimeType.contains("opus") -> "OPUS"
            mimeType.contains("wav") -> "WAV"
            else -> displayName.substringAfterLast('.', "???").uppercase()
        }
}

data class Album(
    val id: Long,
    val title: String,
    val artist: String,
    val tracks: List<Track>,
) {
    val durationMs: Long get() = tracks.sumOf { it.durationMs }
}

data class Artist(
    val name: String,
    val tracks: List<Track>,
) {
    val albumCount: Int get() = tracks.map { it.albumId }.distinct().size
}

// Playlist e TrackOverride foram movidos para :shared/commonMain (com.mpx.player.model),
// para compartilhar a lógica pura (PlaylistLogic/OverrideLogic) com o iOS.

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
