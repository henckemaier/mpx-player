package com.mpx.player.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.os.StatFs
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

    @Volatile
    private var cancelRequested = false

    fun syncFavorites(favoritasRemotas: List<Track>) {
        if (_state.value.running) return
        cancelRequested = false
        _state.value = _state.value.copy(running = true, result = null) // reserva já (evita duplo-clique)
        job = scope.launch { runSync(favoritasRemotas) }
    }

    /** Cancela o sync em andamento: o download atual para no próximo bloco e a faixa parcial é apagada. */
    fun cancel() {
        if (_state.value.running) cancelRequested = true
    }

    private sealed interface Outcome {
        data class Ok(val mediaId: Long) : Outcome
        data object Fail : Outcome
        data object NoSpace : Outcome
        data object Cancelled : Outcome
    }

    /**
     * Executa o sync. Nenhuma exceção pode escapar do scope (não há handler) nem deixar
     * `running = true` preso na UI: erro vira resultado "ERRO NO SYNC" e o estado é sempre liberado.
     */
    private suspend fun runSync(favoritasRemotas: List<Track>) {
        try {
            runSyncInner(favoritasRemotas)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.value = SyncState(result = "ERRO NO SYNC: ${e.javaClass.simpleName}")
        } finally {
            if (_state.value.running) _state.value = _state.value.copy(running = false)
        }
    }

    private suspend fun runSyncInner(favoritasRemotas: List<Track>) {
        val diff = SyncLogic.syncDiff(favoritasRemotas, index.index.value)
        // remoteId do servidor só vira nome de arquivo se for seguro; os inválidos contam como erro.
        val todo = diff.filter { SyncLogic.isSafeRemoteId(it.remoteId) }
        if (diff.isEmpty()) {
            _state.value = SyncState(result = "NADA A SINCRONIZAR")
            return
        }
        _state.value = SyncState(running = true, total = diff.size)
        var failed = diff.size - todo.size
        var noSpace = false
        var cancelled = false
        val written = LinkedHashMap<String, Long>() // remoteId -> _ID do MediaStore (do insert)
        for ((i, t) in todo.withIndex()) {
            if (cancelRequested) { cancelled = true; break }
            _state.value = _state.value.copy(done = i)
            val rid = t.remoteId ?: continue
            when (val r = downloadAndInsert(t, SyncLogic.displayNameFor(rid, t.artist, t.title, t.mimeType))) {
                is Outcome.Ok -> written[rid] = r.mediaId
                Outcome.Fail -> failed++
                Outcome.NoSpace -> noSpace = true
                Outcome.Cancelled -> cancelled = true
            }
            if (noSpace || cancelled) break
        }
        // PÓS-ESCRITA: re-scaneia e resolve a localKey REAL pelo _ID devolvido no insert (NÃO pelo
        // nome: o MediaStore pode renomear "id.mp3" -> "id (1).mp3" numa colisão).
        library.scan()
        val scan = library.state.value.tracks
        var resolved = 0
        written.forEach { (rid, mediaId) ->
            val track = scan.firstOrNull { it.origin == TrackOrigin.LOCAL && it.id == mediaId }
            if (track != null) {
                index.put(rid, SyncedEntry(track.key, mediaId))
                resolved++
            }
        }
        val notIndexed = written.size - resolved
        _state.value = SyncState(
            total = diff.size,
            done = if (cancelled || noSpace) resolved else diff.size,
            result = buildString {
                if (cancelled) append("CANCELADO · ")
                append("OFFLINE OK: $resolved/${diff.size}")
                if (failed > 0) append(" · $failed FALHARAM")
                if (noSpace) append(" · ESPAÇO INSUFICIENTE (LIBERE ARMAZENAMENTO)")
                if (notIndexed > 0) append(" · $notIndexed NÃO ACHADAS NO SCAN (RE-TENTE)")
            },
        )
    }

    private fun freeBytes(): Long =
        runCatching { StatFs(Environment.getExternalStorageDirectory().path).availableBytes }.getOrDefault(Long.MAX_VALUE)

    /**
     * Baixa o /stream (Uri da sub-2, com ?token=) e grava via MediaStore scoped (molde Downloader).
     * Confere espaço livre ANTES de inserir e Content-Length x bytes gravados DEPOIS; qualquer falha,
     * divergência ou cancelamento apaga a entrada pendente (nunca fica faixa parcial).
     * Sem Content-Length (agente não informou): o espaço é checado com margem fixa de 128 MB e a
     * comparação de tamanho é pulada — só falha de leitura/escrita detecta truncamento nesse caso.
     */
    private fun downloadAndInsert(t: Track, fileName: String): Outcome {
        val resolver = context.contentResolver
        val conn = runCatching {
            (URL(t.uri.toString()).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false // redirect levaria o ?token= para outro host
                connectTimeout = 10_000
                readTimeout = 30_000
            }
        }.getOrNull() ?: return Outcome.Fail
        try {
            val code = runCatching { conn.responseCode }.getOrDefault(-1)
            if (code != 200) return Outcome.Fail
            val expected = conn.contentLengthLong // -1 se ausente
            if (!SyncLogic.hasEnoughSpace(expected, freeBytes())) return Outcome.NoSpace

            val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Audio.Media.MIME_TYPE, t.mimeType)
                // O arquivo sincronizado (ex.: WAV) pode não ter tags: o MediaStore usa estes valores.
                put(MediaStore.Audio.Media.TITLE, t.title)
                t.artist.takeIf { it.isNotBlank() && !it.equals("<unknown>", ignoreCase = true) }
                    ?.let { put(MediaStore.Audio.Media.ARTIST, it) }
                t.album.takeIf { it.isNotBlank() && !it.equals("<unknown>", ignoreCase = true) }
                    ?.let { put(MediaStore.Audio.Media.ALBUM, it) }
                put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/MPX")
                put(MediaStore.Audio.Media.IS_MUSIC, 1)
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
            val uri = runCatching { resolver.insert(collection, values) }.getOrNull() ?: return Outcome.Fail
            var bytes = 0L
            var cancelled = false
            val ok = runCatching {
                val out = resolver.openOutputStream(uri) ?: return@runCatching false
                out.use { o ->
                    conn.inputStream.use { input ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            if (cancelRequested) { cancelled = true; break }
                            val n = input.read(buf)
                            if (n < 0) break
                            o.write(buf, 0, n)
                            bytes += n
                        }
                    }
                }
                !cancelled
            }.getOrDefault(false)
            if (cancelled || !ok || !SyncLogic.sizeMatches(expected, bytes)) {
                runCatching { resolver.delete(uri, null, null) }
                return if (cancelled) Outcome.Cancelled else Outcome.Fail
            }
            val published = runCatching {
                resolver.update(uri, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
            }.getOrDefault(0)
            if (published <= 0) {
                runCatching { resolver.delete(uri, null, null) }
                return Outcome.Fail
            }
            val mediaId = runCatching { ContentUris.parseId(uri) }.getOrNull()
            if (mediaId == null) {
                runCatching { resolver.delete(uri, null, null) }
                return Outcome.Fail
            }
            return Outcome.Ok(mediaId)
        } finally {
            conn.disconnect()
        }
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
