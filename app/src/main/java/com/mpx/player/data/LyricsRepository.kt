package com.mpx.player.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.mpx.player.model.LyricLine
import com.mpx.player.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Procura um arquivo .lrc com o mesmo nome da faixa dentro da pasta escolhida pelo
 * usuário (Storage Access Framework). O índice da pasta é cacheado até a pasta mudar.
 */
class LyricsRepository(private val context: Context) {

    private var indexedTree: String? = null
    private var index: Map<String, Uri> = emptyMap()

    suspend fun countLrc(treeUri: String?): Int = withContext(Dispatchers.IO) {
        if (treeUri == null) 0 else ensureIndex(treeUri).size
    }

    suspend fun load(track: Track, treeUri: String?): List<LyricLine> = withContext(Dispatchers.IO) {
        // 1) letras baixadas pelo app (pasta interna)
        localLyric(track)?.let { return@withContext it }
        // 2) pasta .lrc escolhida pelo usuário (SAF)
        if (treeUri == null) return@withContext emptyList()
        val idx = ensureIndex(treeUri)
        val base = track.displayName.substringBeforeLast('.').lowercase()
        val uri = idx[base] ?: idx[track.title.lowercase()] ?: return@withContext emptyList()
        runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { parse(it.readText()) }
        }.getOrNull().orEmpty()
    }

    /** Letras salvas pelo downloader em filesDir/lyrics como `<nome do arquivo sem extensão>.lrc`. */
    private fun localLyric(track: Track): List<LyricLine>? {
        val dir = File(context.filesDir, "lyrics")
        if (!dir.isDirectory) return null
        val base = track.displayName.substringBeforeLast('.').lowercase()
        val file = File(dir, "$base.lrc").takeIf { it.isFile }
            ?: File(dir, track.title.lowercase() + ".lrc").takeIf { it.isFile }
            ?: return null
        return runCatching { parse(file.readText()) }.getOrNull()?.takeIf { it.isNotEmpty() }
    }

    fun invalidate() {
        indexedTree = null
    }

    private fun ensureIndex(treeUri: String): Map<String, Uri> {
        if (indexedTree == treeUri) return index
        val root = runCatching { DocumentFile.fromTreeUri(context, Uri.parse(treeUri)) }.getOrNull()
        val map = HashMap<String, Uri>()
        fun walk(dir: DocumentFile, depth: Int) {
            for (f in dir.listFiles()) {
                val name = f.name ?: continue
                if (f.isDirectory && depth < 3) walk(f, depth + 1)
                else if (name.endsWith(".lrc", ignoreCase = true)) map[name.dropLast(4).lowercase()] = f.uri
            }
        }
        if (root != null) runCatching { walk(root, 0) }
        index = map
        indexedTree = treeUri
        return map
    }

    companion object {
        private val TAG = Regex("""\[(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?]""")

        fun parse(text: String): List<LyricLine> {
            val out = ArrayList<LyricLine>()
            text.lineSequence().forEach { raw ->
                val stamps = TAG.findAll(raw).toList()
                if (stamps.isEmpty()) return@forEach
                val lyric = raw.substring(stamps.last().range.last + 1).trim()
                for (m in stamps) {
                    val (mm, ss, frac) = m.destructured
                    val fracMs = when (frac.length) {
                        0 -> 0
                        1 -> frac.toInt() * 100
                        2 -> frac.toInt() * 10
                        else -> frac.take(3).toInt()
                    }
                    out += LyricLine(mm.toLong() * 60_000 + ss.toLong() * 1000 + fracMs, lyric)
                }
            }
            return out.filter { it.text.isNotEmpty() }.sortedBy { it.timeMs }
        }
    }
}
