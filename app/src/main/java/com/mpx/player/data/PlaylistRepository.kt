package com.mpx.player.data

import android.content.Context
import com.mpx.player.model.Playlist
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Lógica pura de playlists (sem Android), 100% testável. Todas as funções recebem e devolvem uma
 * lista imutável; a geração de id/tempo fica fora (passada como argumento), para os testes serem
 * determinísticos.
 */
object PlaylistLogic {

    /** Garante que exista exatamente um Favoritos (locked), sempre em primeiro. */
    fun ensureFavorites(list: List<Playlist>): List<Playlist> {
        val fav = list.firstOrNull { it.id == Playlist.FAVORITES_ID }
            ?: Playlist(Playlist.FAVORITES_ID, Playlist.FAVORITES_NAME, locked = true)
        val rest = list.filter { it.id != Playlist.FAVORITES_ID }
        return listOf(fav.copy(locked = true, name = Playlist.FAVORITES_NAME)) + rest
    }

    fun created(list: List<Playlist>, id: String, name: String, photo: String?): List<Playlist> {
        val clean = name.trim()
        if (clean.isEmpty()) return list
        return list + Playlist(id = id, name = clean, photo = photo)
    }

    fun renamed(list: List<Playlist>, id: String, name: String): List<Playlist> {
        val clean = name.trim()
        if (clean.isEmpty()) return list
        return list.map { if (it.id == id && !it.locked) it.copy(name = clean) else it }
    }

    fun deleted(list: List<Playlist>, id: String): List<Playlist> =
        list.filterNot { it.id == id && !it.locked }

    fun photoSet(list: List<Playlist>, id: String, photo: String?): List<Playlist> =
        list.map { if (it.id == id) it.copy(photo = photo) else it }

    fun withTrack(list: List<Playlist>, id: String, key: String): List<Playlist> =
        list.map { if (it.id == id && key !in it.trackKeys) it.copy(trackKeys = it.trackKeys + key) else it }

    fun withoutTrack(list: List<Playlist>, id: String, key: String): List<Playlist> =
        list.map { if (it.id == id) it.copy(trackKeys = it.trackKeys - key) else it }

    fun favoriteToggled(list: List<Playlist>, key: String): List<Playlist> =
        if (isFavorite(list, key)) withoutTrack(list, Playlist.FAVORITES_ID, key)
        else withTrack(list, Playlist.FAVORITES_ID, key)

    fun isFavorite(list: List<Playlist>, key: String): Boolean =
        list.firstOrNull { it.id == Playlist.FAVORITES_ID }?.trackKeys?.contains(key) == true

    /** Remove uma chave de TODAS as playlists (ex.: faixa excluída do aparelho). */
    fun purgeKey(list: List<Playlist>, key: String): List<Playlist> =
        list.map { it.copy(trackKeys = it.trackKeys - key) }

    // ---- serialização (org.json)

    fun toJson(list: List<Playlist>): String {
        val arr = JSONArray()
        list.forEach { p ->
            arr.put(
                JSONObject()
                    .put("id", p.id)
                    .put("name", p.name)
                    .put("photo", p.photo ?: JSONObject.NULL)
                    .put("locked", p.locked)
                    .put("trackKeys", JSONArray(p.trackKeys)),
            )
        }
        return arr.toString()
    }

    fun fromJson(text: String): List<Playlist> {
        if (text.isBlank()) return emptyList()
        // Resiliente por-entrada: uma entrada corrompida é pulada, não derruba a lista toda.
        val arr = runCatching { JSONArray(text) }.getOrNull() ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            runCatching {
                val o = arr.getJSONObject(i)
                val keysArr = o.optJSONArray("trackKeys") ?: JSONArray()
                Playlist(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    photo = if (o.isNull("photo")) null else o.optString("photo").takeIf { it.isNotBlank() },
                    trackKeys = (0 until keysArr.length()).mapNotNull { k -> runCatching { keysArr.getString(k) }.getOrNull() },
                    locked = o.optBoolean("locked", false),
                )
            }.getOrNull()
        }
    }
}

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
