package com.mpx.player.model

/**
 * Faixa de áudio. `uri` é uma referência **neutra** ao arquivo/stream (String), para ser comum a
 * Android (content URI) e iOS (file URL / id da biblioteca) — cada plataforma interpreta a String.
 */
data class Track(
    val id: Long,
    val uri: String,
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
