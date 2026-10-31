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

/** Playlist do usuário. `locked` = Favoritos (não renomeia nem exclui). `photo` = caminho da capa. */
data class Playlist(
    val id: String,
    val name: String,
    val photo: String? = null,
    val trackKeys: List<String> = emptyList(),
    val locked: Boolean = false,
) {
    companion object {
        const val FAVORITES_ID = "favoritos"
        const val FAVORITES_NAME = "FAVORITOS"
    }
}

/** Personalização de uma faixa, só dentro do MPX (não altera o arquivo). */
data class TrackOverride(
    val title: String? = null,
    val cover: String? = null,
)

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

object EqPresets {
    val FLAT = List(10) { 6 }
    val ROCK = listOf(9, 8, 7, 5, 4, 5, 7, 8, 9, 9)
    val BASS = listOf(11, 10, 9, 7, 6, 6, 6, 6, 6, 6)
    val VOCAL = listOf(4, 5, 6, 7, 9, 10, 9, 7, 6, 5)

    val names = listOf("FLAT", "ROCK", "BASS+", "VOCAL", "CUSTOM")
    val freqLabels = listOf("32", "64", "125", "250", "500", "1K", "2K", "4K", "8K", "16K")
    val freqsHz = listOf(32f, 64f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f)

    fun levelsFor(name: String, custom: List<Int>): List<Int> = when (name) {
        "FLAT" -> FLAT
        "ROCK" -> ROCK
        "BASS+" -> BASS
        "VOCAL" -> VOCAL
        else -> custom
    }

    fun levelToDb(level: Int): Int = (level - 6) * 2
    fun preampToDb(preamp: Int): Float = (preamp - 12) * 0.5f
}
