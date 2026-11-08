package com.mpx.player.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import com.mpx.player.model.Track
import com.mpx.player.model.TrackOrigin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL

/**
 * Sync OFFLINE das favoritas (EFEITO; SEPARADO do Downloader — só reusa o MESMO padrão MediaStore
 * scoped, SEM WRITE_EXTERNAL). Baixa /stream completo e grava em Music/MPX; depois RE-SCANEIA e lê
 * a localKey REAL do scan (nunca forja — robusto contra normalização do RELATIVE_PATH). Un-sync
 * deleta o arquivo próprio (sem prompt) + tira do índice. Não toca o caminho LOCAL existente nem o
 * Downloader; só ADICIONA arquivos que o scan já pega.
 */
class SyncManager(
    private val context: Context,
    private val library: LibraryRepository,
    private val index: SyncIndex,
) {
    data class SyncState(
        val running: Boolean = false,
        val done: Int = 0,
        val total: Int = 0,
        val result: String? = null,
    )

    private val _state = MutableStateFlow(SyncState())
    val state: StateFlow<SyncState> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    fun syncFavorites(favoritasRemotas: List<Track>) {
        if (_state.value.running) return
        job = scope.launch { runSync(favoritasRemotas) }
    }

    private suspend fun runSync(favoritasRemotas: List<Track>) {
        val todo = SyncLogic.syncDiff(favoritasRemotas, index.index.value)
        if (todo.isEmpty()) {
            _state.value = SyncState(result = "NADA A SINCRONIZAR")
            return
        }
        _state.value = SyncState(running = true, total = todo.size)
        var ok = 0
        val written = LinkedHashMap<String, String>() // remoteId -> fileName escrito
        todo.forEachIndexed { i, t ->
            _state.value = _state.value.copy(done = i)
            val rid = t.remoteId ?: return@forEachIndexed
            val name = SyncLogic.fileNameFor(rid, t.mimeType)
            if (downloadAndInsert(t, name)) {
                ok++
                written[rid] = name
            }
        }
        // PÓS-ESCRITA: re-scaneia e resolve a localKey REAL de cada sincronizada (lê do scan,
        // NÃO forja — o MediaStore pode normalizar o RELATIVE_PATH). Acha pelo DISPLAY_NAME único.
        library.scan()
        val scan = library.state.value.tracks
        var resolved = 0
        written.forEach { (rid, name) ->
            val track = scan.firstOrNull { it.origin == TrackOrigin.LOCAL && it.displayName.equals(name, ignoreCase = true) }
            if (track != null) {
                index.put(rid, SyncedEntry(track.key, track.id))
                resolved++
            }
        }
        val notIndexed = ok - resolved
        _state.value = SyncState(
            total = todo.size,
            done = todo.size,
            result = buildString {
                append("OFFLINE OK: $resolved/${todo.size}")
                if (ok < todo.size) append(" · ${todo.size - ok} FALHARAM")
                if (notIndexed > 0) append(" · $notIndexed NÃO ACHADAS NO SCAN (RE-TENTE)")
            },
        )
    }

    /** Baixa o /stream (Uri da sub-2, com ?token=) e grava via MediaStore scoped (molde Downloader). */
    private fun downloadAndInsert(t: Track, fileName: String): Boolean {
        val resolver = context.contentResolver
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Audio.Media.MIME_TYPE, t.mimeType)
            put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/MPX")
            put(MediaStore.Audio.Media.IS_MUSIC, 1)
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val uri = runCatching { resolver.insert(collection, values) }.getOrNull() ?: return false
        val ok = runCatching {
            val conn = (URL(t.uri.toString()).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 30_000
            }
            try {
                if (conn.responseCode != 200) return@runCatching false
                resolver.openOutputStream(uri)?.use { out -> conn.inputStream.use { it.copyTo(out) } }
                true
            } finally {
                conn.disconnect()
            }
        }.getOrDefault(false)
        if (!ok) {
            runCatching { resolver.delete(uri, null, null) }
            return false
        }
        runCatching {
            resolver.update(uri, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
        }
        return true
    }

    /** Remove UMA sincronizada: deleta o arquivo próprio + tira do índice + re-scaneia. */
    fun unsync(remoteId: String) {
        scope.launch {
            val e = index.index.value[remoteId] ?: return@launch
            deleteMedia(e.mediaId)
            index.remove(remoteId)
            library.scan()
        }
    }

    /** Remove TODAS as offline (un-sync geral). */
    fun removeAllOffline() {
        scope.launch {
            index.index.value.forEach { (rid, e) ->
                deleteMedia(e.mediaId)
                index.remove(rid)
            }
            library.scan()
            _state.value = SyncState(result = "OFFLINE REMOVIDO")
        }
    }

    private fun deleteMedia(mediaId: Long) {
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        runCatching { context.contentResolver.delete(ContentUris.withAppendedId(collection, mediaId), null, null) }
    }

    fun clearResult() {
        _state.value = _state.value.copy(result = null)
    }
}
