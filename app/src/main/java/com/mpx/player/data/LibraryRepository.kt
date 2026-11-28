package com.mpx.player.data

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import com.mpx.player.model.Album
import com.mpx.player.model.Artist
import com.mpx.player.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class LibraryState(
    val tracks: List<Track> = emptyList(),
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val scanning: Boolean = false,
    val scanned: Boolean = false,
    val newSinceLastScan: Int = 0,
) {
    private val byId: Map<Long, Track> by lazy { tracks.associateBy { it.id } }
    fun track(id: Long): Track? = byId[id]

    private val byKey: Map<String, Track> by lazy { tracks.associateBy { it.key } }
    fun trackByKey(key: String): Track? = byKey[key]
    fun tracksForKeys(keys: List<String>): List<Track> = keys.mapNotNull { byKey[it] }
}

class LibraryRepository(
    private val context: Context,
    private val overrides: OverrideRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var rawTracks: List<Track> = emptyList()
    private var scanned = false
    private var newCount = 0
    @Volatile private var scanningFlag = false

    private val _state = MutableStateFlow(LibraryState())
    val state: StateFlow<LibraryState> = _state.asStateFlow()

    init {
        // Re-aplica os overrides (título/capa) sem re-consultar o MediaStore quando eles mudam.
        scope.launch { overrides.overrides.drop(1).collect { rebuild() } }
    }

    suspend fun scan() {
        if (scanningFlag) return
        scanningFlag = true
        _state.update { it.copy(scanning = true) }
        val previous = rawTracks.map { it.id }.toSet()
        rawTracks = withContext(Dispatchers.IO) { query() }
        scanned = true
        newCount = if (previous.isEmpty()) 0 else rawTracks.count { it.id !in previous }
        scanningFlag = false
        rebuild()
    }

    /** Re-escaneia no escopo do repositório (sobrevive à troca de tela/aba). */
    fun rescan() = scope.launch { scan() }

    @Synchronized
    private fun rebuild() {
        val ov = overrides.overrides.value
        val tracks = if (ov.isEmpty()) rawTracks else rawTracks.map { t ->
            val o = ov[t.key] ?: return@map t
            t.copy(title = o.title ?: t.title, coverOverride = o.cover ?: t.coverOverride)
        }
        val albums = tracks.groupBy { it.albumId }.map { (id, list) ->
            Album(
                id = id,
                title = list.first().album,
                artist = list.groupingBy { it.artist }.eachCount().maxBy { it.value }.key,
                tracks = list.sortedWith(compareBy({ it.trackNumber }, { it.displayName })),
            )
        }.sortedBy { it.title.uppercase() }
        val artists = tracks.groupBy { it.artist }.map { (name, list) ->
            Artist(name, list.sortedWith(compareBy({ it.album }, { it.trackNumber })))
        }.sortedBy { it.name.uppercase() }
        _state.value = LibraryState(tracks, albums, artists, scanning = scanningFlag, scanned = scanned, newSinceLastScan = newCount)
    }

    private fun query(): List<Track> {
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val projection = buildList {
            add(MediaStore.Audio.Media._ID)
            add(MediaStore.Audio.Media.TITLE)
            add(MediaStore.Audio.Media.ARTIST)
            add(MediaStore.Audio.Media.ALBUM)
            add(MediaStore.Audio.Media.ALBUM_ID)
            add(MediaStore.Audio.Media.DURATION)
            add(MediaStore.Audio.Media.DISPLAY_NAME)
            add(MediaStore.Audio.Media.RELATIVE_PATH)
            add(MediaStore.Audio.Media.MIME_TYPE)
            add(MediaStore.Audio.Media.TRACK)
            add(MediaStore.Audio.Media.YEAR)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) add(MediaStore.Audio.Media.BITRATE)
        }.toTypedArray()
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} > 0"
        val out = ArrayList<Track>()
        context.contentResolver.query(collection, projection, selection, null, "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC")
            ?.use { c ->
                val iId = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val iTitle = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val iArtist = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val iAlbum = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val iAlbumId = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val iDur = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val iName = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val iPath = c.getColumnIndexOrThrow(MediaStore.Audio.Media.RELATIVE_PATH)
                val iMime = c.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
                val iTrack = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
                val iYear = c.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
                val iBitrate = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) c.getColumnIndex(MediaStore.Audio.Media.BITRATE) else -1
                while (c.moveToNext()) {
                    val id = c.getLong(iId)
                    val artist = c.getString(iArtist).orUnknown()
                    out += Track(
                        id = id,
                        uri = ContentUris.withAppendedId(collection, id),
                        title = c.getString(iTitle) ?: c.getString(iName).orEmpty(),
                        artist = artist,
                        album = c.getString(iAlbum).orUnknown(),
                        albumId = c.getLong(iAlbumId),
                        durationMs = c.getLong(iDur),
                        displayName = c.getString(iName).orEmpty(),
                        relativePath = c.getString(iPath).orEmpty(),
                        mimeType = c.getString(iMime).orEmpty(),
                        bitrate = if (iBitrate >= 0 && !c.isNull(iBitrate)) c.getInt(iBitrate) else null,
                        trackNumber = c.getInt(iTrack) % 1000,
                        year = c.getInt(iYear).takeIf { it > 0 },
                    )
                }
            }
        return out
    }

    private fun String?.orUnknown() = if (this.isNullOrBlank() || this == "<unknown>") "DESCONHECIDO" else this
}
