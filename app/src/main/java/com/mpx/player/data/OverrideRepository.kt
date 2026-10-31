package com.mpx.player.data

import android.content.Context
import android.net.Uri
import com.mpx.player.model.TrackOverride
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.File

/** Lógica pura dos overrides de faixa (título/capa), testável sem Android. */
object OverrideLogic {

    fun withTitle(map: Map<String, TrackOverride>, key: String, title: String?): Map<String, TrackOverride> {
        val clean = title?.trim()?.takeIf { it.isNotEmpty() }
        return put(map, key, (map[key] ?: TrackOverride()).copy(title = clean))
    }

    fun withCover(map: Map<String, TrackOverride>, key: String, cover: String?): Map<String, TrackOverride> =
        put(map, key, (map[key] ?: TrackOverride()).copy(cover = cover?.takeIf { it.isNotBlank() }))

    private fun put(map: Map<String, TrackOverride>, key: String, ov: TrackOverride): Map<String, TrackOverride> =
        if (ov.title == null && ov.cover == null) map - key else map + (key to ov)

    fun toJson(map: Map<String, TrackOverride>): String {
        val o = JSONObject()
        map.forEach { (k, v) ->
            o.put(k, JSONObject().put("title", v.title ?: JSONObject.NULL).put("cover", v.cover ?: JSONObject.NULL))
        }
        return o.toString()
    }

    fun fromJson(text: String): Map<String, TrackOverride> {
        if (text.isBlank()) return emptyMap()
        val o = runCatching { JSONObject(text) }.getOrNull() ?: return emptyMap()
        return buildMap {
            o.keys().forEach { k ->
                runCatching {
                    val e = o.getJSONObject(k)
                    val ov = TrackOverride(
                        title = if (e.isNull("title")) null else e.optString("title").takeIf { it.isNotBlank() },
                        cover = if (e.isNull("cover")) null else e.optString("cover").takeIf { it.isNotBlank() },
                    )
                    if (ov.title != null || ov.cover != null) put(k, ov)
                }
            }
        }
    }
}

/** Persiste os overrides de faixa (filesDir/overrides.json) e copia as capas personalizadas. */
class OverrideRepository(private val context: Context) {

    private val file: File get() = File(context.filesDir, "overrides.json")
    private val images = ImageStore(context)

    private val _overrides = MutableStateFlow(load())
    val overrides: StateFlow<Map<String, TrackOverride>> = _overrides.asStateFlow()

    private fun load(): Map<String, TrackOverride> =
        runCatching { if (file.exists()) OverrideLogic.fromJson(file.readText()) else emptyMap() }
            .getOrDefault(emptyMap())

    private fun update(transform: (Map<String, TrackOverride>) -> Map<String, TrackOverride>) {
        val next = transform(_overrides.value)
        _overrides.value = next
        file.writeAtomic(OverrideLogic.toJson(next))
    }

    fun setTitle(key: String, title: String?) = update { OverrideLogic.withTitle(it, key, title) }

    /** Copia a imagem escolhida e grava como capa personalizada da faixa. */
    suspend fun setCover(key: String, src: Uri) {
        val path = images.save(src, "covers") ?: return
        update { OverrideLogic.withCover(it, key, path) }
    }

    fun clearCover(key: String) = update { OverrideLogic.withCover(it, key, null) }

    /** Copia uma imagem para servir de foto de playlist; devolve o caminho salvo. */
    suspend fun savePlaylistPhoto(src: Uri): String? = images.save(src, "playlist_covers")
}
