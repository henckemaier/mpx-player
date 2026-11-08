package com.mpx.player.data

import com.mpx.player.model.Playlist
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * Lógica pura de playlists (sem Android, multiplataforma), 100% testável. Todas as funções recebem e
 * devolvem uma lista imutável; a geração de id/tempo fica fora (passada como argumento), para os
 * testes serem determinísticos.
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

    // ---- serialização (kotlinx.serialization; formato compatível com o legado org.json)

    fun toJson(list: List<Playlist>): String = buildJsonArray {
        list.forEach { p ->
            addJsonObject {
                put("id", p.id)
                put("name", p.name)
                if (p.photo != null) put("photo", p.photo) else put("photo", JsonNull)
                put("locked", p.locked)
                putJsonArray("trackKeys") { p.trackKeys.forEach { add(it) } }
            }
        }
    }.toString()

    fun fromJson(text: String): List<Playlist> {
        if (text.isBlank()) return emptyList()
        // Resiliente por-entrada: uma entrada corrompida é pulada, não derruba a lista toda.
        val arr = runCatching { Json.parseToJsonElement(text).jsonArray }.getOrNull() ?: return emptyList()
        return arr.mapNotNull { el ->
            runCatching {
                val o = el.jsonObject
                val keysArr = (o["trackKeys"] as? JsonArray) ?: JsonArray(emptyList())
                Playlist(
                    id = o["id"]!!.jsonPrimitive.content,
                    name = o["name"]!!.jsonPrimitive.content,
                    photo = o["photo"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.content?.takeIf { it.isNotBlank() },
                    trackKeys = keysArr.mapNotNull { k -> runCatching { k.jsonPrimitive.content }.getOrNull() },
                    locked = (o["locked"] as? JsonPrimitive)?.booleanOrNull ?: false,
                )
            }.getOrNull()
        }
    }
}
