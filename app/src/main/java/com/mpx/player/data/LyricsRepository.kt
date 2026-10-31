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
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.coroutines.coroutineContext

/**
 * Procura um arquivo .lrc com o mesmo nome da faixa dentro da pasta escolhida pelo
 * usuário (Storage Access Framework). O índice da pasta é cacheado até a pasta mudar.
 */
class LyricsRepository(private val context: Context) {

    private var indexedTree: String? = null
    private var index: Map<String, Uri> = emptyMap()

    /** Incrementa quando as letras mudam, para o Now Playing recarregar a faixa atual na hora. */
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version

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
     * Busca a letra SINCRONIZADA tentando as fontes em ordem (LRCLIB, depois NetEase). Retorna o
     * conteúdo `.lrc` (com timestamps) da primeira fonte que encontrar, ou null. Operação de rede —
     * chame fora da thread principal. Para adicionar uma fonte nova, implemente uma função
     * `(title, artist, album, durationSec) -> String?` e inclua-a em [providers].
     */
    fun fetchSynced(title: String, artist: String?, album: String?, durationSec: Long): String? {
        if (title.isBlank()) return null
        for (provider in providers) {
            val lrc = runCatching { provider(title, artist, album, durationSec) }.getOrNull()
            if (!lrc.isNullOrBlank()) return lrc
        }
        return null
    }

    /** Fontes de letra sincronizada, tentadas nesta ordem. Acrescente novas fontes aqui. */
    private val providers: List<(String, String?, String?, Long) -> String?> =
        listOf(::fetchFromLrclib, ::fetchFromNetease)

    /** LRCLIB (lrclib.net): correspondência exata por metadados, depois busca por título/artista. */
    private fun fetchFromLrclib(title: String, artist: String?, album: String?, durationSec: Long): String? {
        val art = artist ?: ""
        getJson(
            "https://lrclib.net/api/get?" + query(
                "track_name" to title,
                "artist_name" to art,
                "album_name" to (album ?: ""),
                "duration" to durationSec.toString(),
            ),
        )?.let { obj -> syncedOf(obj)?.let { return it } }
        getJsonArray("https://lrclib.net/api/search?" + query("track_name" to title, "artist_name" to art))
            ?.let { arr ->
                for (i in 0 until arr.length()) {
                    syncedOf(arr.optJSONObject(i) ?: continue)?.let { return it }
                }
            }
        return null
    }

    /**
     * NetEase (music.163.com): busca a faixa, escolhe o resultado de duração mais próxima e lê a
     * letra sincronizada (`lrc.lyric`). API não-oficial — usada só como fallback, com headers de
     * navegador; qualquer falha é engolida pela cadeia em [fetchSynced].
     */
    private fun fetchFromNetease(title: String, artist: String?, album: String?, durationSec: Long): String? {
        val art = artist ?: ""
        val search = getJson(
            "https://music.163.com/api/search/get?" + query("s" to "$title $art".trim(), "type" to "1", "limit" to "8"),
            NETEASE_HEADERS,
        ) ?: return null
        val songs = search.optJSONObject("result")?.optJSONArray("songs") ?: return null
        var bestId = -1L
        var bestDelta = Long.MAX_VALUE
        for (i in 0 until songs.length()) {
            val s = songs.optJSONObject(i) ?: continue
            val id = s.optLong("id", -1L)
            if (id < 0) continue
            val durMs = s.optLong("duration", 0L)
            val delta = if (durationSec > 0 && durMs > 0) kotlin.math.abs(durMs - durationSec * 1000) else 0L
            if (bestId < 0 || delta < bestDelta) {
                bestId = id
                bestDelta = delta
            }
        }
        if (bestId < 0) return null
        if (durationSec > 0 && bestDelta > 8000) return null // nenhuma faixa com duração próxima (±8s)
        val lyr = getJson("https://music.163.com/api/song/lyric?id=$bestId&lv=1&kv=1&tv=1", NETEASE_HEADERS) ?: return null
        val lrc = lyr.optJSONObject("lrc")?.optString("lyric")?.takeIf { it.isNotBlank() } ?: return null
        return lrc.takeIf { TAG.containsMatchIn(it) } // só aceita se for sincronizada (tem timestamps)
    }

    /**
     * Para cada faixa SEM `.lrc` salvo em filesDir/lyrics, busca a letra no LRCLIB e grava.
     * Retorna quantas letras foram baixadas. Reporta progresso (concluídas, total, achadas) e
     * respeita o cancelamento do coroutine. As que já têm letra são puladas sem rede.
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

    private fun syncedOf(obj: JSONObject): String? =
        obj.optString("syncedLyrics").takeIf { it.isNotBlank() && it != "null" }

    private fun query(vararg params: Pair<String, String>): String =
        params.joinToString("&") { (k, v) -> "$k=" + URLEncoder.encode(v, "UTF-8") }

    private fun getJson(url: String, headers: Map<String, String> = emptyMap()): JSONObject? =
        httpGet(url, headers)?.let { runCatching { JSONObject(it) }.getOrNull() }

    private fun getJsonArray(url: String, headers: Map<String, String> = emptyMap()): JSONArray? =
        httpGet(url, headers)?.let { runCatching { JSONArray(it) }.getOrNull() }

    private fun httpGet(url: String, headers: Map<String, String> = emptyMap()): String? = runCatching {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8000
            readTimeout = 8000
            setRequestProperty("User-Agent", "MPX-Player (https://github.com/henckemaier/mpx-player)")
            setRequestProperty("Accept", "application/json")
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
        }
        try {
            if (conn.responseCode != 200) return null
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }.getOrNull()

    /** NetEase exige Referer/UA de navegador nas rotas /api legadas. */
    private val NETEASE_HEADERS = mapOf(
        "Referer" to "https://music.163.com",
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
    )

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
