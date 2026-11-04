package com.mpx.player.data

import com.mpx.player.model.LyricLine
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlin.math.abs

/**
 * Busca de letra SINCRONIZADA online, multiplataforma (Ktor + kotlinx.serialization). Tenta as
 * fontes em ordem (LRCLIB → NetEase) e devolve o conteúdo `.lrc` (com timestamps) da primeira que
 * encontrar, ou null. Para adicionar uma fonte, implemente uma
 * `suspend (title, artist, album, durationSec) -> String?` e inclua-a em [providers].
 */
class LyricsApi {

    // Mantido privado de propósito: o Ktor fica interno ao :shared e não vaza para o classpath de
    // compilação do :app (lá ele só é necessário em runtime, via a engine Android).
    private val client: HttpClient = defaultClient()

    suspend fun fetchSynced(title: String, artist: String?, album: String?, durationSec: Long): String? {
        if (title.isBlank()) return null
        for (provider in providers) {
            val lrc = runCatching { provider(title, artist, album, durationSec) }.getOrNull()
            if (!lrc.isNullOrBlank()) return lrc
        }
        return null
    }

    /** Fontes de letra sincronizada, tentadas nesta ordem. Acrescente novas fontes aqui. */
    private val providers: List<suspend (String, String?, String?, Long) -> String?> =
        listOf(::fetchFromLrclib, ::fetchFromNetease)

    /** LRCLIB (lrclib.net): correspondência exata por metadados, depois busca por título/artista. */
    private suspend fun fetchFromLrclib(title: String, artist: String?, album: String?, durationSec: Long): String? {
        val art = artist ?: ""
        getJson(
            "https://lrclib.net/api/get",
            listOf(
                "track_name" to title,
                "artist_name" to art,
                "album_name" to (album ?: ""),
                "duration" to durationSec.toString(),
            ),
        )?.let { obj -> syncedOf(obj)?.let { return it } }
        getJsonArray(
            "https://lrclib.net/api/search",
            listOf("track_name" to title, "artist_name" to art),
        )?.let { arr ->
            for (el in arr) {
                (el as? JsonObject)?.let { obj -> syncedOf(obj)?.let { return it } }
            }
        }
        return null
    }

    /**
     * NetEase (music.163.com): busca a faixa, escolhe a de duração mais próxima e lê a letra
     * sincronizada (`lrc.lyric`). API não-oficial — fallback com headers de navegador; qualquer
     * falha é engolida pela cadeia em [fetchSynced].
     */
    private suspend fun fetchFromNetease(title: String, artist: String?, album: String?, durationSec: Long): String? {
        val art = artist ?: ""
        val search = getJson(
            "https://music.163.com/api/search/get",
            listOf("s" to "$title $art".trim(), "type" to "1", "limit" to "8"),
            NETEASE_HEADERS,
        ) ?: return null
        val songs = (search["result"] as? JsonObject)?.get("songs") as? JsonArray ?: return null
        var bestId = -1L
        var bestDelta = Long.MAX_VALUE
        for (el in songs) {
            val s = el as? JsonObject ?: continue
            val id = s["id"]?.jsonPrimitive?.longOrNull ?: continue
            if (id < 0) continue
            val durMs = s["duration"]?.jsonPrimitive?.longOrNull ?: 0L
            val delta = if (durationSec > 0 && durMs > 0) abs(durMs - durationSec * 1000) else 0L
            if (bestId < 0 || delta < bestDelta) {
                bestId = id
                bestDelta = delta
            }
        }
        if (bestId < 0) return null
        if (durationSec > 0 && bestDelta > 8000) return null // nenhuma faixa com duração próxima (±8s)
        val lyr = getJson(
            "https://music.163.com/api/song/lyric",
            listOf("id" to bestId.toString(), "lv" to "1", "kv" to "1", "tv" to "1"),
            NETEASE_HEADERS,
        ) ?: return null
        val lrc = (lyr["lrc"] as? JsonObject)?.get("lyric")?.jsonPrimitive?.contentOrNull
            ?.takeIf { it.isNotBlank() } ?: return null
        return lrc.takeIf { TAG.containsMatchIn(it) } // só aceita se for sincronizada (tem timestamps)
    }

    private fun syncedOf(obj: JsonObject): String? =
        obj["syncedLyrics"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() && it != "null" }

    private suspend fun getJson(
        url: String,
        params: List<Pair<String, String>>,
        headers: Map<String, String> = emptyMap(),
    ): JsonObject? =
        httpGet(url, params, headers)?.let { runCatching { Json.parseToJsonElement(it).jsonObject }.getOrNull() }

    private suspend fun getJsonArray(
        url: String,
        params: List<Pair<String, String>>,
        headers: Map<String, String> = emptyMap(),
    ): JsonArray? =
        httpGet(url, params, headers)?.let { runCatching { Json.parseToJsonElement(it).jsonArray }.getOrNull() }

    private suspend fun httpGet(
        endpoint: String,
        params: List<Pair<String, String>>,
        extraHeaders: Map<String, String>,
    ): String? = runCatching {
        val resp = client.get(endpoint) {
            url { params.forEach { (k, v) -> parameters.append(k, v) } }
            headers {
                append(HttpHeaders.Accept, "application/json")
                append(HttpHeaders.UserAgent, extraHeaders[HttpHeaders.UserAgent] ?: USER_AGENT)
                extraHeaders.forEach { (k, v) ->
                    if (!k.equals(HttpHeaders.UserAgent, ignoreCase = true)) append(k, v)
                }
            }
        }
        if (resp.status.value == 200) resp.bodyAsText() else null
    }.getOrNull()

    companion object {
        private const val USER_AGENT = "MPX-Player (https://github.com/henckemaier/mpx-player)"

        /** NetEase exige Referer/UA de navegador nas rotas /api legadas. */
        private val NETEASE_HEADERS = mapOf(
            "Referer" to "https://music.163.com",
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        )

        private val TAG = Regex("""\[(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?]""")

        fun defaultClient(): HttpClient = newHttpClient {
            expectSuccess = false
            install(HttpTimeout) {
                requestTimeoutMillis = 8_000
                connectTimeoutMillis = 8_000
                socketTimeoutMillis = 8_000
            }
        }

        /** Converte texto `.lrc` (com timestamps) em linhas ordenadas por tempo. */
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
