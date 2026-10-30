package com.mpx.player.data

import android.content.ContentValues
import android.content.Context
import android.media.MediaMetadataRetriever
import android.provider.MediaStore
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

enum class DownloadStatus { IDLE, INIT, DOWNLOADING, PUBLISHING, DONE, ERROR, CANCELED }

data class DownloadState(
    val status: DownloadStatus = DownloadStatus.IDLE,
    /** 0..1 do item atual. */
    val progress: Float = 0f,
    /** Ex.: "BAIXANDO 3/12". */
    val overall: String = "",
    /** Última linha de log do yt-dlp. */
    val line: String = "",
    val tracks: Int = 0,
    val lyrics: Int = 0,
    val skipped: Int = 0,
    val error: String? = null,
) {
    val running: Boolean get() = status == DownloadStatus.INIT || status == DownloadStatus.DOWNLOADING || status == DownloadStatus.PUBLISHING
}

/**
 * Baixa áudio (MP3) de links do YouTube Music via yt-dlp (youtubedl-android), embute capa e
 * metadados, busca letra sincronizada no LRCLIB e publica tudo no MediaStore (Music/MPX) para
 * aparecer na biblioteca. A capa sai filtrada no estilo do app pelo [CoverArtLoader] no render.
 */
class Downloader(
    private val context: Context,
    private val library: LibraryRepository,
    private val lyrics: LyricsRepository,
    private val settings: SettingsRepository,
) {
    private val _state = MutableStateFlow(DownloadState())
    val state: StateFlow<DownloadState> = _state.asStateFlow()

    private val initMutex = Mutex()
    @Volatile private var initialized = false
    @Volatile private var canceled = false
    @Volatile private var currentProcessId: String? = null

    private val workDir: File get() = File(context.getExternalFilesDir(null) ?: context.filesDir, "ytdlp")
    private val lyricsDir: File get() = File(context.filesDir, "lyrics")

    private val itemRegex = Regex("""item\s+(\d+)\s+of\s+(\d+)""", RegexOption.IGNORE_CASE)

    private suspend fun ensureInit() {
        if (initialized) return
        initMutex.withLock {
            if (initialized) return
            _state.update { it.copy(status = DownloadStatus.INIT, overall = "INICIALIZANDO ENGINE...") }
            YoutubeDL.getInstance().init(context)
            FFmpeg.getInstance().init(context)
            initialized = true
        }
    }

    suspend fun download(url: String) = withContext(Dispatchers.IO) {
        val link = url.trim()
        if (link.isEmpty() || _state.value.running) return@withContext
        canceled = false
        _state.value = DownloadState(status = DownloadStatus.INIT, overall = "INICIALIZANDO ENGINE...")
        try {
            ensureInit()

            workDir.deleteRecursively()
            workDir.mkdirs()

            val processId = "mpx_dl_${System.currentTimeMillis()}"
            currentProcessId = processId

            _state.update { it.copy(status = DownloadStatus.DOWNLOADING, overall = "PREPARANDO...") }

            val request = YoutubeDLRequest(link).apply {
                addOption("--extract-audio")
                addOption("--audio-format", "mp3")
                addOption("--audio-quality", "0")
                addOption("--embed-thumbnail")
                addOption("--convert-thumbnails", "jpg")
                // Capa pequena (baixa qualidade), já que o app re-filtra no estilo retrô.
                addOption("--postprocessor-args", "ThumbnailsConvertor:-vf scale=320:-2")
                addOption("--embed-metadata")
                addOption("--no-mtime")
                addOption("--restrict-filenames")
                addOption("-i")
                // Registra o que já foi baixado (por ID do vídeo) p/ não rebaixar na próxima vez.
                addOption("--download-archive", File(context.filesDir, "ytdlp_archive.txt").absolutePath)
                addOption("-o", File(workDir, "%(playlist_index)s-%(title).150B.%(ext)s").absolutePath)
            }

            var archivedSkips = 0
            YoutubeDL.getInstance().execute(request, processId) { progress, _, line ->
                val l = line.trim()
                if (l.contains("already been recorded in the archive")) archivedSkips++
                val m = itemRegex.find(l)
                _state.update { s ->
                    s.copy(
                        status = DownloadStatus.DOWNLOADING,
                        progress = (progress / 100f).coerceIn(0f, 1f),
                        overall = if (m != null) "BAIXANDO ${m.groupValues[1]}/${m.groupValues[2]}" else s.overall,
                        line = if (l.isNotEmpty()) l else s.line,
                    )
                }
            }

            if (canceled) {
                _state.update { it.copy(status = DownloadStatus.CANCELED, overall = "CANCELADO", line = "") }
                return@withContext
            }

            // Publica os MP3 resultantes no MediaStore + busca letra.
            _state.update { it.copy(status = DownloadStatus.PUBLISHING, overall = "SALVANDO...", progress = 1f) }
            lyricsDir.mkdirs()
            val files = workDir.listFiles { f -> f.isFile && f.extension.equals("mp3", ignoreCase = true) }
                ?.sortedBy { it.name }
                ?: emptyList()

            var saved = 0
            var withLyrics = 0
            var skipped = 0
            for (file in files) {
                if (existsInLibrary(file.name)) {
                    skipped++
                    file.delete()
                    continue
                }
                val meta = readTags(file)
                if (meta.title.isNotEmpty() && fetchAndStoreLyrics(file, meta)) withLyrics++
                if (publishToMediaStore(file)) saved++
            }
            val existed = skipped + archivedSkips

            workDir.deleteRecursively()

            library.scan()
            lyrics.invalidate()
            settings.setLastScan(System.currentTimeMillis())

            _state.update {
                it.copy(
                    status = DownloadStatus.DONE,
                    overall = "CONCLUÍDO",
                    progress = 1f,
                    tracks = saved,
                    lyrics = withLyrics,
                    skipped = existed,
                    line = "",
                )
            }
        } catch (e: YoutubeDLException) {
            finishError(e)
        } catch (e: InterruptedException) {
            _state.update { it.copy(status = DownloadStatus.CANCELED, overall = "CANCELADO", line = "") }
        } catch (e: Exception) {
            finishError(e)
        } finally {
            currentProcessId = null
        }
    }

    private fun finishError(e: Throwable) {
        if (canceled) {
            _state.update { it.copy(status = DownloadStatus.CANCELED, overall = "CANCELADO", line = "") }
            return
        }
        val msg = (e.message ?: e.javaClass.simpleName).lineSequence().firstOrNull { it.isNotBlank() }?.take(140).orEmpty()
        _state.update { it.copy(status = DownloadStatus.ERROR, overall = "ERRO", error = msg, line = "") }
    }

    fun cancel() {
        canceled = true
        val id = currentProcessId ?: return
        runCatching { YoutubeDL.getInstance().destroyProcessById(id) }
    }

    fun reset() {
        if (_state.value.running) return
        _state.value = DownloadState()
    }

    suspend fun updateEngine(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            ensureInit()
            _state.update { it.copy(status = DownloadStatus.INIT, overall = "ATUALIZANDO YT-DLP...") }
            YoutubeDL.getInstance().updateYoutubeDL(context)
            _state.update { it.copy(status = DownloadStatus.DONE, overall = "YT-DLP ATUALIZADO") }
            true
        }.getOrElse { e ->
            finishError(e)
            false
        }
    }

    // ---------------------------------------------------------------- tags / letra / publicação

    private data class Tags(val title: String, val artist: String?, val album: String?, val durationSec: Int)

    private fun readTags(file: File): Tags {
        val retr = MediaMetadataRetriever()
        return try {
            retr.setDataSource(file.absolutePath)
            val title = retr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?.takeIf { it.isNotBlank() } ?: file.nameWithoutExtension
            val artist = retr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.takeIf { it.isNotBlank() }
            val album = retr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)?.takeIf { it.isNotBlank() }
            val durSec = (retr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L) / 1000
            Tags(title, artist, album, durSec.toInt())
        } catch (_: Exception) {
            Tags(file.nameWithoutExtension, null, null, 0)
        } finally {
            runCatching { retr.release() }
        }
    }

    /** Salva `<base>.lrc` (base = nome do arquivo sem extensão, lowercase) se o LRCLIB tiver letra sincronizada. */
    private fun fetchAndStoreLyrics(file: File, meta: Tags): Boolean {
        val synced = runCatching { fetchSyncedLyrics(meta) }.getOrNull()?.takeIf { it.isNotBlank() } ?: return false
        return runCatching {
            File(lyricsDir, file.nameWithoutExtension.lowercase() + ".lrc").writeText(synced)
            true
        }.getOrDefault(false)
    }

    private fun fetchSyncedLyrics(meta: Tags): String? {
        val artist = meta.artist ?: ""
        // 1) correspondência exata (tolerância de ±2s no LRCLIB)
        getJson(
            "https://lrclib.net/api/get?" + query(
                "track_name" to meta.title,
                "artist_name" to artist,
                "album_name" to (meta.album ?: ""),
                "duration" to meta.durationSec.toString(),
            ),
        )?.let { obj -> synced(obj)?.let { return it } }
        // 2) busca por título/artista
        getJsonArray("https://lrclib.net/api/search?" + query("track_name" to meta.title, "artist_name" to artist))
            ?.let { arr ->
                for (i in 0 until arr.length()) {
                    synced(arr.optJSONObject(i) ?: continue)?.let { return it }
                }
            }
        return null
    }

    private fun synced(obj: JSONObject): String? =
        obj.optString("syncedLyrics").takeIf { it.isNotBlank() && it != "null" }

    private fun query(vararg params: Pair<String, String>): String =
        params.joinToString("&") { (k, v) -> "$k=" + URLEncoder.encode(v, "UTF-8") }

    private fun getJson(url: String): JSONObject? = httpGet(url)?.let { runCatching { JSONObject(it) }.getOrNull() }
    private fun getJsonArray(url: String): JSONArray? = httpGet(url)?.let { runCatching { JSONArray(it) }.getOrNull() }

    private fun httpGet(url: String): String? = runCatching {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8000
            readTimeout = 8000
            setRequestProperty("User-Agent", "MPX-Player/1.03 (https://github.com/mpx-player)")
            setRequestProperty("Accept", "application/json")
        }
        try {
            if (conn.responseCode != 200) return null
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }.getOrNull()

    /** True se já existe uma faixa com esse nome em Music/MPX (evita duplicar no re-download). */
    private fun existsInLibrary(displayName: String): Boolean {
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val selection = "${MediaStore.Audio.Media.RELATIVE_PATH}=? AND ${MediaStore.Audio.Media.DISPLAY_NAME}=?"
        val args = arrayOf("Music/MPX/", displayName)
        return runCatching {
            context.contentResolver
                .query(collection, arrayOf(MediaStore.Audio.Media._ID), selection, args, null)
                ?.use { it.count > 0 } ?: false
        }.getOrDefault(false)
    }

    private fun publishToMediaStore(file: File): Boolean {
        val resolver = context.contentResolver
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg")
            put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/MPX")
            put(MediaStore.Audio.Media.IS_MUSIC, 1)
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val uri = runCatching { resolver.insert(collection, values) }.getOrNull() ?: return false
        val ok = runCatching {
            resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
            true
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
}
