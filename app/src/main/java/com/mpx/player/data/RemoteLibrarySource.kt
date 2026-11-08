package com.mpx.player.data

import android.net.Uri
import com.mpx.player.model.Track
import com.mpx.player.model.TrackOrigin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

enum class RemoteStatus { DISCONNECTED, CONNECTING, CONNECTED, ERROR }

/** Estado da conexão com o agente desktop (observado pela UI "CONECTAR AO DESKTOP"). */
data class RemoteState(
    val status: RemoteStatus = RemoteStatus.DISCONNECTED,
    val host: String? = null,
    val trackCount: Int = 0,
    val error: String? = null,
)

/**
 * id SINTÉTICO (puro, framework-free) de uma faixa remota a partir do seu id estável (SHA-1
 * hex do relPath, vindo do agente sub-1). Garante:
 *  - NEGATIVO (< 0): nunca colide com o _ID positivo do MediaStore (faixas locais);
 *  - != -1L: evita o sentinel de "mediaId inválido" do [PlayerController.refresh];
 *  - ESTÁVEL: o mesmo remoteId mapeia sempre pro mesmo Long (round-trip no refresh).
 * Usa os 15 primeiros hex (60 bits) do SHA-1; se não for hex, cai no hashCode da String.
 */
fun syntheticId(remoteId: String): Long {
    val base = remoteId.take(15).toLongOrNull(16) ?: (remoteId.hashCode().toLong() and 0xFFFF_FFFFL)
    val neg = -(base and Long.MAX_VALUE)
    return if (neg == 0L || neg == -1L) -2L else neg
}

/**
 * Projeção FRAMEWORK-FREE (sem android.net.Uri) de uma faixa remota — o núcleo testável do
 * mapeamento DTO->Track. Os 12 campos FIXOS do contrato da sub-1 viram escalares + o split do
 * relPathHint. [key] reconcatena EXATAMENTE o relPathHint (ver [mapRemoteTrack]).
 */
data class RemoteFields(
    val remoteId: String,
    val synthetic: Long,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val trackNumber: Int,
    val year: Int?,
    val mime: String,
    val bitrate: Int?,
    val hasArt: Boolean,
    val displayName: String,
    val relativePath: String,
) {
    /** == relPathHint.lowercase() por construção = Track.key. */
    val key: String get() = (relativePath + displayName).lowercase()
}

/**
 * Parse PURO do JSON do agente nos campos escalares (sem Uri) — testável no JVM. displayName/
 * relativePath são o split do relPathHint no ÚLTIMO '/', de modo que
 * (relativePath + displayName).lowercase() == relPathHint.lowercase() (= Track.key). Assim o
 * dedup do [mergeLibrary] casa com a cópia local-sincronizada de mesmo caminho (sync = sub-4).
 */
fun remoteFields(obj: JSONObject): RemoteFields {
    val remoteId = obj.getString("id")
    val rel = obj.optString("relPathHint")
    val slash = rel.lastIndexOf('/')
    val name = if (slash >= 0) rel.substring(slash + 1) else rel
    val dir = if (slash >= 0) rel.substring(0, slash + 1) else ""
    return RemoteFields(
        remoteId = remoteId,
        synthetic = syntheticId(remoteId),
        title = obj.optString("title").ifBlank { name },
        artist = obj.optString("artist"),
        album = obj.optString("album"),
        durationMs = obj.optLong("durationMs", 0L),
        trackNumber = obj.optInt("trackNumber", 0),
        year = if (obj.isNull("year")) null else obj.optInt("year").takeIf { it > 0 },
        mime = obj.optString("mime"),
        bitrate = if (obj.isNull("bitrate")) null else obj.optInt("bitrate").takeIf { it > 0 },
        hasArt = obj.optBoolean("hasArt", false),
        displayName = name,
        relativePath = dir,
    )
}

/**
 * Mapeia um RemoteTrackDto (JSON do agente) numa [Track] REMOTE. Wrapper fino sobre
 * [remoteFields] (o núcleo puro/testável) + a construção das Uri. As URLs de stream e capa
 * levam o token na query (?token=) porque o ExoPlayer e o [CoverArtLoader] fazem GET simples,
 * sem header de auth (o PlaybackService NÃO muda). albumId = id sintético (a arte vem por
 * artUri; cada remota = seu próprio "álbum" no id negativo, sem colidir com os álbuns locais).
 */
fun mapRemoteTrack(obj: JSONObject, host: String, token: String): Track {
    val f = remoteFields(obj)
    return Track(
        id = f.synthetic,
        uri = Uri.parse("http://$host/stream/${f.remoteId}?token=$token"),
        title = f.title,
        artist = f.artist,
        album = f.album,
        albumId = f.synthetic,
        durationMs = f.durationMs,
        displayName = f.displayName,
        relativePath = f.relativePath,
        mimeType = f.mime,
        bitrate = f.bitrate,
        trackNumber = f.trackNumber,
        year = f.year,
        origin = TrackOrigin.REMOTE,
        remoteId = f.remoteId,
        artUri = if (f.hasArt) Uri.parse("http://$host/art/${f.remoteId}?token=$token") else null,
    )
}

/**
 * Dedup PURO por key (local ganha), genérico p/ ser testável no JVM sem android.net.Uri:
 * se um remoto tem a MESMA key de um LOCAL, descarta o remoto e mantém o local. remote vazio
 * -> devolve `local` intacto. [mergeLibrary] é o wrapper fino sobre [Track.key].
 */
internal fun <T> dedupKeepLocal(local: List<T>, remote: List<T>, key: (T) -> String): List<T> {
    if (remote.isEmpty()) return local
    val localKeys = local.mapTo(HashSet(), key)
    return local + remote.filter { key(it) !in localKeys }
}

/**
 * União local+remoto, dedup por [Track.key] (offline ganha). Resultado = locais +
 * remotas-sem-par. remote vazio -> `local` byte-idêntico (caminho local intacto desconectado).
 */
fun mergeLibrary(local: List<Track>, remote: List<Track>): List<Track> =
    dedupKeepLocal(local, remote) { it.key }

/**
 * Fonte remota: busca a biblioteca do agente desktop via HTTP (reusa o padrão HttpURLConnection
 * do [LyricsRepository]) e expõe as faixas REMOTE + o estado da conexão. Degrada em silêncio
 * (offline/timeout/401/parse-fail -> lista vazia + estado ERRO); nunca crasha o app. Persiste
 * host+token no sucesso (reconecta sozinho no próximo arranque).
 */
class RemoteLibrarySource(private val settings: SettingsRepository) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(RemoteState())
    val state: StateFlow<RemoteState> = _state.asStateFlow()

    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    val tracks: StateFlow<List<Track>> = _tracks.asStateFlow()

    init {
        // Reconecta ao agente pareado no arranque (se houver host/token salvos).
        scope.launch {
            val s = settings.settings.first()
            val h = s.desktopHost
            val t = s.desktopToken
            if (!h.isNullOrBlank() && !t.isNullOrBlank()) doConnect(h, t)
        }
    }

    fun connect(host: String, token: String) {
        scope.launch { doConnect(host.trim(), token.trim()) }
    }

    fun disconnect() {
        scope.launch { settings.setDesktop(null, null) }
        _tracks.value = emptyList()
        _state.value = RemoteState()
    }

    private suspend fun doConnect(host: String, token: String) {
        if (host.isBlank() || token.isBlank()) {
            disconnect(); return
        }
        _state.value = RemoteState(RemoteStatus.CONNECTING, host)
        val fetched = withContext(Dispatchers.IO) { runCatching { fetchLibrary(host, token) }.getOrNull() }
        if (fetched == null) {
            _tracks.value = emptyList()
            _state.value = RemoteState(RemoteStatus.ERROR, host, 0, "SEM RESPOSTA · VERIFIQUE IP/TOKEN")
        } else {
            _tracks.value = fetched
            _state.value = RemoteState(RemoteStatus.CONNECTED, host, fetched.size)
            settings.setDesktop(host, token) // só persiste no sucesso
        }
    }

    private fun fetchLibrary(host: String, token: String): List<Track>? {
        val body = httpGet("http://$host/library", token) ?: return null
        val arr = runCatching { JSONArray(body) }.getOrNull() ?: return null
        val out = ArrayList<Track>(arr.length())
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            runCatching { mapRemoteTrack(obj, host, token) }.getOrNull()?.let { out += it }
        }
        return out
    }

    private fun httpGet(url: String, token: String): String? = runCatching {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 6000
            readTimeout = 6000
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Accept", "application/json")
        }
        try {
            if (conn.responseCode != 200) return null
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }.getOrNull()
}
