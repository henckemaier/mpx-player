package com.mpx.player.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.mpx.player.model.LyricLine
import com.mpx.player.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.coroutineContext

/**
 * Letras no lado Android: arquivos `.lrc` locais (baixados pelo app em filesDir/lyrics) e a pasta
 * escolhida pelo usuário via Storage Access Framework (índice cacheado até a pasta mudar). A busca
 * ONLINE (LRCLIB → NetEase) e o parse de `.lrc` ficam em [LyricsApi] (:shared, multiplataforma).
 */
class AndroidLyricsRepository(private val context: Context) : LyricsRepository {

    private val api = LyricsApi()

    private var indexedTree: String? = null
    private var index: Map<String, Uri> = emptyMap()

    /** Incrementa quando as letras mudam, para o Now Playing recarregar a faixa atual na hora. */
    private val _version = MutableStateFlow(0)
    override val version: StateFlow<Int> = _version

    suspend fun countLrc(treeUri: String?): Int = withContext(Dispatchers.IO) {
        if (treeUri == null) 0 else ensureIndex(treeUri).size
    }

    override suspend fun load(track: Track, treeUri: String?): List<LyricLine> = withContext(Dispatchers.IO) {
        // 1) letras baixadas pelo app (pasta interna)
        localLyric(track)?.let { return@withContext it }
        // 2) pasta .lrc escolhida pelo usuário (SAF)
        if (treeUri == null) return@withContext emptyList()
        val idx = ensureIndex(treeUri)
        val base = track.displayName.substringBeforeLast('.').lowercase()
        val uri = idx[base] ?: idx[track.title.lowercase()] ?: return@withContext emptyList()
        runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { LyricsApi.parse(it.readText()) }
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
        return runCatching { LyricsApi.parse(file.readText()) }.getOrNull()?.takeIf { it.isNotEmpty() }
    }

    fun invalidate() {
        indexedTree = null
        _version.value++
    }

    /** Grava (ou apaga, se vazio) a letra editada da faixa em filesDir/lyrics/<base>.lrc. */
    suspend fun save(track: Track, text: String) = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "lyrics").apply { mkdirs() }
        val file = File(dir, track.displayName.substringBeforeLast('.').lowercase() + ".lrc")
        runCatching { if (text.isBlank()) file.delete() else file.writeText(text) }
        Unit
    }

    /**
     * Busca a letra SINCRONIZADA online (LRCLIB → NetEase) via [LyricsApi]. Operação de rede
     * (suspend) — chame fora da thread principal.
     */
    suspend fun fetchSynced(title: String, artist: String?, album: String?, durationSec: Long): String? =
        api.fetchSynced(title, artist, album, durationSec)

    /**
     * Para cada faixa SEM `.lrc` salvo em filesDir/lyrics, busca a letra e grava. Retorna quantas
     * foram baixadas. Reporta progresso (concluídas, total, achadas) e respeita o cancelamento do
     * coroutine. As que já têm letra são puladas sem rede.
     */
    suspend fun syncMissing(
        tracks: List<Track>,
        onProgress: (done: Int, total: Int, found: Int) -> Unit,
    ): Int = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "lyrics").apply { mkdirs() }
        val total = tracks.size
        var found = 0
        tracks.forEachIndexed { i, t ->
            coroutineContext.ensureActive()
            onProgress(i, total, found)
            val base = t.displayName.substringBeforeLast('.').lowercase()
            val file = File(dir, "$base.lrc")
            if (file.isFile && file.length() > 0) return@forEachIndexed // já tem letra — pula
            val lrc = runCatching { fetchSynced(t.title, t.artist, t.album, t.durationMs / 1000) }.getOrNull()
            if (!lrc.isNullOrBlank()) runCatching { file.writeText(lrc) }.onSuccess { found++ }
            delay(150) // educado com o LRCLIB entre requisições
        }
        onProgress(total, total, found)
        _version.value++ // faz o Now Playing recarregar a faixa atual
        found
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
}
