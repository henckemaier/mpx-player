package com.mpx.desktop

import java.io.File
import java.security.MessageDigest

/** Extensoes de audio escaneadas (minusculas, sem ponto). */
val AUDIO_EXT = setOf("mp3", "flac", "m4a", "aac", "ogg", "opus", "wav")

/** Tags lidas de UM arquivo (independente da lib de leitura, p/ testar com fake). */
data class TagData(
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val trackNumber: Int,
    val year: Int?,
    val bitrate: Int?,
    val hasEmbeddedArt: Boolean,
)

/** Leitor de metadados de audio. Real = [JAudioTaggerReader]; testes injetam um fake. */
fun interface TagReader {
    /** Le as tags de [file]; devolve null se o arquivo falhar (o scan degrada, nao derruba). */
    fun read(file: File): TagData?
}

/** Uma faixa resolvida: o DTO servido + o arquivo real no disco (p/ /stream e /art). */
data class LibraryEntry(val dto: RemoteTrackDto, val file: File)

/** SHA-1 hex do caminho relativo (normalizado com '/') — id ESTAVEL entre scans. */
fun stableId(relPath: String): String {
    val md = MessageDigest.getInstance("SHA-1")
    val bytes = md.digest(relPath.toByteArray(Charsets.UTF_8))
    return bytes.joinToString("") { "%02x".format(it) }
}

/** Caminho de [file] relativo a [root], sempre com '/' (espelha o relativePath do app). */
fun relPathOf(root: File, file: File): String =
    root.toPath().relativize(file.toPath()).toString().replace(File.separatorChar, '/')

/** mime a partir da extensao (minuscula). */
fun mimeFor(ext: String): String = when (ext) {
    "mp3" -> "audio/mpeg"
    "flac" -> "audio/flac"
    "m4a" -> "audio/mp4"
    "aac" -> "audio/aac"
    "ogg" -> "audio/ogg"
    "opus" -> "audio/opus"
    "wav" -> "audio/wav"
    else -> "application/octet-stream"
}

/** format a partir do mime/ext — ESPELHA `Track.format` do :app (Models.kt). */
fun formatFor(mime: String, ext: String): String = when {
    mime.contains("mpeg") -> "MP3"
    mime.contains("flac") -> "FLAC"
    mime.contains("mp4") || mime.contains("aac") -> "AAC"
    mime.contains("opus") -> "OPUS"   // antes de ogg: "audio/opus" nao contem "ogg"
    mime.contains("ogg") -> "OGG"
    mime.contains("wav") -> "WAV"
    else -> ext.uppercase().ifBlank { "???" }
}

/** Existe uma capa de pasta (folder.jpg/cover.jpg, qualquer caixa) ao lado de [file]? */
fun folderArtFile(file: File): File? {
    val dir = file.parentFile ?: return null
    val names = setOf("folder.jpg", "folder.jpeg", "folder.png", "cover.jpg", "cover.jpeg", "cover.png")
    return dir.listFiles()?.firstOrNull { it.isFile && it.name.lowercase() in names }
}

/**
 * Escaneia [root] recursivamente por [AUDIO_EXT] e monta o modelo em memoria. Usa [reader]
 * p/ as tags (duracao/title/artist/album REAIS); se um arquivo falhar, DEGRADA p/
 * title=nome-sem-extensao (artist/album vazios, duracao 0) mas AINDA inclui a faixa.
 */
class LibraryScanner(private val root: File, private val reader: TagReader) {

    fun scan(): List<LibraryEntry> {
        if (!root.isDirectory) return emptyList()
        return root.walkTopDown()
            .filter { it.isFile && it.extension.lowercase() in AUDIO_EXT }
            .map { toEntry(it) }
            .sortedBy { it.dto.relPathHint.lowercase() }
            .toList()
    }

    private fun toEntry(file: File): LibraryEntry {
        val relPath = relPathOf(root, file)
        val ext = file.extension.lowercase()
        val mime = mimeFor(ext)
        val format = formatFor(mime, ext)
        val id = stableId(relPath)
        val baseName = file.nameWithoutExtension
        val folderArt = folderArtFile(file) != null
        val tag = runCatching { reader.read(file) }.getOrNull()
        val dto = if (tag != null) {
            RemoteTrackDto(
                id = id,
                title = tag.title.ifBlank { baseName },
                artist = tag.artist,
                album = tag.album,
                durationMs = tag.durationMs,
                trackNumber = tag.trackNumber,
                year = tag.year,
                mime = mime,
                format = format,
                bitrate = tag.bitrate,
                hasArt = tag.hasEmbeddedArt || folderArt,
                relPathHint = relPath,
            )
        } else {
            RemoteTrackDto(
                id = id, title = baseName, artist = "", album = "",
                durationMs = 0L, trackNumber = 0, year = null,
                mime = mime, format = format, bitrate = null,
                hasArt = folderArt, relPathHint = relPath,
            )
        }
        return LibraryEntry(dto, file)
    }
}
