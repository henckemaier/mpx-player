package com.mpx.desktop

import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import java.io.File

/** Leitor real de tags/duracao via jaudiotagger (net.jthink). So usado no Main (nao nos testes). */
class JAudioTaggerReader : TagReader {
    override fun read(file: File): TagData? = runCatching {
        val af = AudioFileIO.read(file)
        val h = af.audioHeader
        val durationMs = (h.preciseTrackLength * 1000.0).toLong()
        val bitrate = runCatching { h.bitRateAsNumber.toInt() }.getOrDefault(0)
        val tag = af.tag
        TagData(
            title = tag?.getFirst(FieldKey.TITLE).orEmpty(),
            artist = tag?.getFirst(FieldKey.ARTIST).orEmpty(),
            album = tag?.getFirst(FieldKey.ALBUM).orEmpty(),
            durationMs = if (durationMs > 0) durationMs else 0L,
            trackNumber = tag?.getFirst(FieldKey.TRACK)?.filter { it.isDigit() }?.toIntOrNull() ?: 0,
            year = tag?.getFirst(FieldKey.YEAR)?.take(4)?.toIntOrNull(),
            bitrate = if (bitrate > 0) bitrate else null,
            hasEmbeddedArt = runCatching { tag?.firstArtwork != null }.getOrDefault(false),
        )
    }.getOrNull()
}

/**
 * Resolve a capa de uma faixa: arte EMBUTIDA (jaudiotagger) primeiro; senao folder/cover.(jpg|png)
 * na pasta do arquivo. Usado pela Library do Main; devolve null se nao houver capa.
 */
fun jAudioTaggerArt(entry: LibraryEntry): ArtData? {
    runCatching {
        val af = AudioFileIO.read(entry.file)
        af.tag?.firstArtwork?.let { art ->
            val bytes = art.binaryData
            if (bytes != null && bytes.isNotEmpty()) {
                return ArtData(bytes, art.mimeType?.takeIf { it.isNotBlank() } ?: "image/jpeg")
            }
        }
    }
    val cover = folderArtFile(entry.file) ?: return null
    val ct = if (cover.extension.lowercase() == "png") "image/png" else "image/jpeg"
    return ArtData(cover.readBytes(), ct)
}
