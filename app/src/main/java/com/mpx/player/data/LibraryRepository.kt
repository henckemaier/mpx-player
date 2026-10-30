package com.mpx.player.data

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import com.mpx.player.model.Album
import com.mpx.player.model.Artist
import com.mpx.player.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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
}

class LibraryRepository(private val context: Context) {

    private val _state = MutableStateFlow(LibraryState())
    val state: StateFlow<LibraryState> = _state.asStateFlow()

    suspend fun scan() {
        if (_state.value.scanning) return
        _state.update { it.copy(scanning = true) }
        val previous = _state.value.tracks.map { it.id }.toSet()
        val tracks = withContext(Dispatchers.IO) { query() }
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
        val newCount = if (previous.isEmpty()) 0 else tracks.count { it.id !in previous }
        _state.value = LibraryState(tracks, albums, artists, scanning = false, scanned = true, newSinceLastScan = newCount)
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
