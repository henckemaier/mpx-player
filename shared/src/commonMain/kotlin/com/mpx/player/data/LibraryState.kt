package com.mpx.player.data

import com.mpx.player.model.Album
import com.mpx.player.model.Artist
import com.mpx.player.model.Track

/**
 * Estado observável da biblioteca (puro, comum a Android/iOS). A varredura em si
 * é por plataforma (MediaStore no Android); aqui ficam só os dados e os índices.
 */
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
