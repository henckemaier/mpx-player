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

    /** Nome do arquivo sincronizado: "<remoteId>.<ext>". PURO. */
    fun fileNameFor(remoteId: String, mime: String): String = "$remoteId.${extForMime(mime)}"

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
