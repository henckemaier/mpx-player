package com.mpx.player.data

import android.content.Context
import com.mpx.player.model.Track
import com.mpx.player.model.TrackOrigin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Uma faixa remota baixada p/ o aparelho: a [localKey] REAL que ela tem no scan + o _ID do MediaStore. */
data class SyncedEntry(val localKey: String, val mediaId: Long)

/**
 * Lógica PURA do sync offline (molde [PlaylistLogic], framework-free: String/Track/Map in/out, SEM
 * android.net.Uri — lição da sub-2). Diff do que falta, marcação OFFLINE (cross-origin via o mapa),
 * extensão por mime, e serialização do índice. Testável no JVM (app/src/test).
 */
object SyncLogic {

    /** remoteIds das favoritas remotas que AINDA não estão no índice (= o que falta baixar). PURO. */
    fun missingRemoteIds(favRemoteIds: List<String>, index: Map<String, SyncedEntry>): List<String> =
        favRemoteIds.filter { it !in index }

    /**
     * remoteIds sincronizados COM a cópia local presente no scan = OFFLINE-disponíveis. Uma entrada
     * STALE (localKey não está mais no scan) NÃO entra -> volta a ser [STREAM]/re-baixável. PURO.
     */
    fun offlineRemoteIds(index: Map<String, SyncedEntry>, localKeysPresent: Set<String>): Set<String> =
        index.filterValues { it.localKey in localKeysPresent }.keys

    /** key local sincronizada -> remoteId de origem (p/ marcar [OFFLINE] a cópia local). PURO. */
    fun syncedKeyToRemoteId(index: Map<String, SyncedEntry>, localKeysPresent: Set<String>): Map<String, String> =
        index.entries.filter { it.value.localKey in localKeysPresent }.associate { it.value.localKey to it.key }

    /** Extensão de arquivo a partir do mime (p/ o DISPLAY_NAME do arquivo sincronizado). PURO. */
    fun extForMime(mime: String): String = when {
        mime.contains("mpeg") -> "mp3"
        mime.contains("flac") -> "flac"
        mime.contains("mp4") -> "m4a"
        mime.contains("aac") -> "aac"
        mime.contains("opus") -> "opus"
        mime.contains("ogg") -> "ogg"
        mime.contains("wav") -> "wav"
        else -> "audio"
    }

    private val SAFE_REMOTE_ID = Regex("[A-Za-z0-9_-]{1,128}")

    /**
     * remoteId vindo do servidor só vale como nome de arquivo/chave se for [A-Za-z0-9_-]{1,128}
     * (ASCII estrito: barra, ponto, '..', unicode, vazio ou longo demais são rejeitados). PURO.
     */
    fun isSafeRemoteId(id: String?): Boolean = id != null && SAFE_REMOTE_ID.matches(id)

    /** Margem de espaço livre exigida ALÉM do tamanho do arquivo (e mínimo quando o tamanho é desconhecido). */
    const val SPACE_MARGIN_BYTES: Long = 32L * 1024 * 1024
    const val UNKNOWN_SIZE_MIN_FREE_BYTES: Long = 128L * 1024 * 1024

    /** Há espaço p/ gravar? [contentLength] <= 0 = desconhecido -> exige o mínimo fixo. PURO. */
    fun hasEnoughSpace(contentLength: Long, usableBytes: Long): Boolean =
        if (contentLength > 0) usableBytes >= contentLength + SPACE_MARGIN_BYTES
        else usableBytes >= UNKNOWN_SIZE_MIN_FREE_BYTES

    /** Integridade: sem Content-Length (<0) aceita; com ele, os bytes gravados têm de bater exatamente. PURO. */
    fun sizeMatches(expected: Long, written: Long): Boolean = expected < 0 || expected == written

    private val FORBIDDEN_NAME_CHARS = "/\\:*?\"<>|".toSet()
    private val WHITESPACE = Regex("\\s+")
    /**
     * Limite da base do nome em BYTES UTF-8 (o FS limita o nome a 255 bytes, não a chars: um título
     * CJK de 120 chars já passa de 255). 200 + " [id8].ext" fica folgado abaixo de 255.
     */
    private const val MAX_BASE_BYTES = 200

    /**
     * Remove / \ : * ? " < > |, controles (Cc) e caracteres de FORMATAÇÃO Unicode (Cf: U+202E RTL
     * override, U+200B zero-width, U+FEFF, tags U+E00xx...), colapsa espaços e tira pontos/espaços das
     * pontas. Opera por code point (pares substitutos inteiros). PURO.
     */
    fun sanitizeNamePart(raw: String?): String {
        if (raw == null) return ""
        val sb = StringBuilder(raw.length)
        raw.codePoints().forEach { cp ->
            val bad = (cp < 0x10000 && cp.toChar() in FORBIDDEN_NAME_CHARS) ||
                Character.isISOControl(cp) ||
                Character.getType(cp) == Character.FORMAT.toInt()
            if (bad) sb.append(' ') else sb.appendCodePoint(cp)
        }
        return sb.toString().replace(WHITESPACE, " ").trim(' ', '.')
    }

    /** Prefixo com no máximo [maxBytes] bytes em UTF-8, sem partir code point. PURO. */
    internal fun String.takeUtf8Bytes(maxBytes: Int): String {
        var bytes = 0
        var i = 0
        while (i < length) {
            val cp = codePointAt(i)
            val n = when {
                cp < 0x80 -> 1
                cp < 0x800 -> 2
                cp < 0x10000 -> 3
                else -> 4
            }
            if (bytes + n > maxBytes) break
            bytes += n
            i += Character.charCount(cp)
        }
        return substring(0, i).trimEnd(' ', '.')
    }

    private fun knownTag(s: String?): String? =
        s?.trim()?.takeIf { it.isNotEmpty() && !it.equals("<unknown>", ignoreCase = true) }

    /**
     * DISPLAY_NAME legível do arquivo sincronizado: "Artista - Título [id8].ext" (sem artista:
     * "Título [id8].ext"; id8 = 8 primeiros chars do remoteId, evita colisão). Sanitizado; se nada
     * sobrar do artista/título, cai no remoteId ("<remoteId>.<ext>"). A dedup NÃO depende do nome. PURO.
     */
    fun displayNameFor(remoteId: String, artist: String?, title: String?, mime: String): String {
        val ext = extForMime(mime)
        val t = sanitizeNamePart(knownTag(title))
        if (t.isEmpty()) return "$remoteId.$ext"
        val a = sanitizeNamePart(knownTag(artist))
        val base = (if (a.isEmpty()) t else "$a - $t").takeUtf8Bytes(MAX_BASE_BYTES)
        if (base.isEmpty()) return "$remoteId.$ext"
        return "$base [${remoteId.take(8)}].$ext"
    }

    // ---- wrappers sobre Track (finos; validados no device, pois Track carrega Uri) ----

    /** Favoritas REMOTAS que faltam baixar (remoteId fora do índice). */
    fun syncDiff(favoritasRemotas: List<Track>, index: Map<String, SyncedEntry>): List<Track> =
        favoritasRemotas.filter { it.origin == TrackOrigin.REMOTE && it.remoteId != null && it.remoteId !in index }

    /**
     * Aplica o índice à lista mesclada (sub-2): some com o stream REMOTO cuja cópia local já existe
     * (offline) e MARCA a cópia local com o remoteId de origem ([OFFLINE]). Índice vazio ou sem cópia
     * presente -> devolve `merged` intacto (caminho local/sub-2 byte-idêntico).
     */
    fun applySyncIndex(merged: List<Track>, index: Map<String, SyncedEntry>): List<Track> {
        if (index.isEmpty()) return merged
        val localKeys = merged.asSequence().filter { it.origin == TrackOrigin.LOCAL }.map { it.key }.toHashSet()
        val offline = offlineRemoteIds(index, localKeys)
        if (offline.isEmpty()) return merged
        val keyToRid = syncedKeyToRemoteId(index, localKeys)
        return merged.mapNotNull { t ->
            when {
                t.origin == TrackOrigin.REMOTE && t.remoteId in offline -> null // cópia local presente -> some o stream
                t.origin == TrackOrigin.LOCAL && t.key in keyToRid -> t.copy(remoteId = keyToRid[t.key]) // [OFFLINE]
                else -> t
            }
        }
    }

    // ---- serialização do índice (org.json; molde PlaylistLogic, resiliente por-entrada) ----

    fun toJson(index: Map<String, SyncedEntry>): String {
        val arr = JSONArray()
        index.forEach { (rid, e) ->
            arr.put(JSONObject().put("r", rid).put("k", e.localKey).put("m", e.mediaId))
        }
        return arr.toString()
    }

    fun fromJson(text: String): Map<String, SyncedEntry> {
        if (text.isBlank()) return emptyMap()
        val arr = runCatching { JSONArray(text) }.getOrNull() ?: return emptyMap()
        val out = LinkedHashMap<String, SyncedEntry>()
        for (i in 0 until arr.length()) {
            runCatching {
                val o = arr.getJSONObject(i)
                val rid = o.getString("r")
                out[rid] = SyncedEntry(o.getString("k"), o.getLong("m"))
            }
        }
        return out
    }
}

/** Persiste o índice de sincronizadas em filesDir/sync_index.json e expõe como StateFlow (molde PlaylistRepository). */
class SyncIndex(context: Context) {

    private val file: File = File(context.filesDir, "sync_index.json")

    private val _index = MutableStateFlow(load())
    val index: StateFlow<Map<String, SyncedEntry>> = _index.asStateFlow()

    private fun load(): Map<String, SyncedEntry> =
        runCatching { if (file.exists()) SyncLogic.fromJson(file.readText()) else emptyMap() }.getOrDefault(emptyMap())

    @Synchronized
    private fun save(next: Map<String, SyncedEntry>) {
        _index.value = next
        file.writeAtomic(SyncLogic.toJson(next))
    }

    fun put(remoteId: String, entry: SyncedEntry) = save(_index.value + (remoteId to entry))
    fun remove(remoteId: String) = save(_index.value - remoteId)
}
