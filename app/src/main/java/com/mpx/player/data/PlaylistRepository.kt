package com.mpx.player.data

import android.content.Context
import com.mpx.player.model.Playlist
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.UUID

// PlaylistLogic foi movido para :shared/commonMain (com.mpx.player.data) e é consumido abaixo.

/** Grava de forma atômica (temp + rename) para um kill no meio da escrita não truncar o arquivo. */
internal fun File.writeAtomic(text: String) {
    runCatching {
        val tmp = File(parentFile, "$name.tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(this)) {
            writeText(text)
            tmp.delete()
        }
    }
}

/** Persiste playlists em filesDir/playlists.json e expõe como StateFlow. */
class PlaylistRepository(private val context: Context) {

    private val file: File get() = File(context.filesDir, "playlists.json")

    private val _playlists = MutableStateFlow(load())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    private fun load(): List<Playlist> =
        PlaylistLogic.ensureFavorites(
            runCatching { if (file.exists()) PlaylistLogic.fromJson(file.readText()) else emptyList() }
                .getOrDefault(emptyList()),
        )

    private fun update(transform: (List<Playlist>) -> List<Playlist>) {
        val next = PlaylistLogic.ensureFavorites(transform(_playlists.value))
        _playlists.value = next
        file.writeAtomic(PlaylistLogic.toJson(next))
    }

    fun create(name: String, photo: String? = null) =
        update { PlaylistLogic.created(it, UUID.randomUUID().toString(), name, photo) }

    fun rename(id: String, name: String) = update { PlaylistLogic.renamed(it, id, name) }
    fun delete(id: String) = update { PlaylistLogic.deleted(it, id) }
    fun setPhoto(id: String, photo: String?) = update { PlaylistLogic.photoSet(it, id, photo) }
    fun addTrack(id: String, key: String) = update { PlaylistLogic.withTrack(it, id, key) }
    fun removeTrack(id: String, key: String) = update { PlaylistLogic.withoutTrack(it, id, key) }
    fun toggleFavorite(key: String) = update { PlaylistLogic.favoriteToggled(it, key) }
    fun purgeKey(key: String) = update { PlaylistLogic.purgeKey(it, key) }
}
