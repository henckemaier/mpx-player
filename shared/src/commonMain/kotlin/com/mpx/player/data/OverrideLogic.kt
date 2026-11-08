package com.mpx.player.data

import com.mpx.player.model.TrackOverride
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/** Lógica pura dos overrides de faixa (título/capa), multiplataforma e testável sem Android. */
object OverrideLogic {

    fun withTitle(map: Map<String, TrackOverride>, key: String, title: String?): Map<String, TrackOverride> {
        val clean = title?.trim()?.takeIf { it.isNotEmpty() }
        return put(map, key, (map[key] ?: TrackOverride()).copy(title = clean))
    }

    fun withCover(map: Map<String, TrackOverride>, key: String, cover: String?): Map<String, TrackOverride> =
        put(map, key, (map[key] ?: TrackOverride()).copy(cover = cover?.takeIf { it.isNotBlank() }))

    private fun put(map: Map<String, TrackOverride>, key: String, ov: TrackOverride): Map<String, TrackOverride> =
        if (ov.title == null && ov.cover == null) map - key else map + (key to ov)

    // ---- serialização (kotlinx.serialization; formato compatível com o legado org.json)

    fun toJson(map: Map<String, TrackOverride>): String = buildJsonObject {
        map.forEach { (k, v) ->
            putJsonObject(k) {
                if (v.title != null) put("title", v.title) else put("title", JsonNull)
                if (v.cover != null) put("cover", v.cover) else put("cover", JsonNull)
            }
        }
    }.toString()

    fun fromJson(text: String): Map<String, TrackOverride> {
        if (text.isBlank()) return emptyMap()
        val o = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return emptyMap()
        return buildMap {
            o.forEach { (k, el) ->
                runCatching {
                    val e = el.jsonObject
                    val ov = TrackOverride(
                        title = e["title"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.content?.takeIf { it.isNotBlank() },
                        cover = e["cover"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.content?.takeIf { it.isNotBlank() },
                    )
                    if (ov.title != null || ov.cover != null) put(k, ov)
                }
            }
        }
    }
}
