package com.mpx.player.data

import com.mpx.player.model.EqSettings
import com.mpx.player.model.UserSettings
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * (De)serialização PURA de [EqSettings] em JSON (kotlinx.serialization, estilo manual igual ao
 * `PlaylistLogic` — sem `@Serializable`/plugin, zero impacto no `:app`). Resiliente: entrada
 * vazia/corrompida → `EqSettings()` padrão; `levels`/`custom` só são aceitos com 10 níveis.
 *
 * Promovido de `iosMain` para `commonMain` na fatia SETTINGS (P7) para servir também o
 * [UserSettingsJson] (bloco `eq`) e virar alvo de teste comum. `internal` ao `:shared` — visível
 * do `iosMain` (mesmo módulo) e do `commonTest`, invisível ao `:app`.
 */
internal object EqSettingsJson {

    fun toJson(eq: EqSettings): String = buildJsonObject { putEq(eq) }.toString()

    /** Escreve os campos do [EqSettings] no builder corrente (reusado pelo [UserSettingsJson]). */
    fun JsonObjectBuilder.putEq(eq: EqSettings) {
        put("enabled", eq.enabled)
        put("preset", eq.preset)
        putJsonArray("levels") { eq.levels.forEach { add(it) } }
        putJsonArray("custom") { eq.custom.forEach { add(it) } }
        put("preamp", eq.preamp)
        put("bassBoost", eq.bassBoost)
        put("surround", eq.surround)
        put("loudness", eq.loudness)
    }

    fun fromJson(text: String): EqSettings {
        if (text.isBlank()) return EqSettings()
        val o = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return EqSettings()
        return fromObject(o)
    }

    /** Lê um [EqSettings] de um objeto JSON já parseado (reusado pelo [UserSettingsJson]). */
    fun fromObject(o: JsonObject): EqSettings {
        val d = EqSettings()
        fun levels(name: String, def: List<Int>): List<Int> =
            (o[name] as? JsonArray)
                ?.mapNotNull { (it as? JsonPrimitive)?.intOrNull }
                ?.takeIf { it.size == 10 } ?: def
        return EqSettings(
            enabled = (o["enabled"] as? JsonPrimitive)?.booleanOrNull ?: d.enabled,
            preset = (o["preset"] as? JsonPrimitive)?.contentOrNull ?: d.preset,
            levels = levels("levels", d.levels),
            custom = levels("custom", d.custom),
            preamp = (o["preamp"] as? JsonPrimitive)?.intOrNull ?: d.preamp,
            bassBoost = (o["bassBoost"] as? JsonPrimitive)?.booleanOrNull ?: d.bassBoost,
            surround = (o["surround"] as? JsonPrimitive)?.booleanOrNull ?: d.surround,
            loudness = (o["loudness"] as? JsonPrimitive)?.booleanOrNull ?: d.loudness,
        )
    }
}

/**
 * (De)serialização PURA do [UserSettings] inteiro em JSON — molde do [EqSettingsJson]. É a fonte de
 * verdade da persistência iOS (`IosSettingsRepository` grava/lê um único objeto em NSUserDefaults).
 * Resiliente: entrada vazia/corrompida/campos faltando → `UserSettings()` padrão por campo. O bloco
 * `eq` delega ao [EqSettingsJson]. Sem `@Serializable`/plugin (zero impacto no `:app`).
 */
internal object UserSettingsJson {

    fun toJson(s: UserSettings): String = buildJsonObject {
        put("theme", s.theme)
        put("customColor", s.customColor) // Int? -> JSON null quando ausente
        put("oled", s.oled)
        put("font", s.font)
        put("cover", s.cover)
        put("scanlines", s.scanlines)
        put("glow", s.glow)
        put("cursor", s.cursor)
        put("monoCovers", s.monoCovers)
        put("crossfade", s.crossfade)
        put("lrcTreeUri", s.lrcTreeUri) // String? -> JSON null quando ausente
        put("lastScan", s.lastScan)
        put("playlistsImported", s.playlistsImported)
        putJsonObject("eq") { with(EqSettingsJson) { putEq(s.eq) } }
    }.toString()

    fun fromJson(text: String): UserSettings {
        if (text.isBlank()) return UserSettings()
        val o = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return UserSettings()
        val d = UserSettings()
        fun bool(name: String, def: Boolean) = (o[name] as? JsonPrimitive)?.booleanOrNull ?: def
        fun int(name: String, def: Int) = (o[name] as? JsonPrimitive)?.intOrNull ?: def
        return UserSettings(
            theme = int("theme", d.theme),
            customColor = (o["customColor"] as? JsonPrimitive)?.intOrNull,
            oled = bool("oled", d.oled),
            font = int("font", d.font),
            cover = int("cover", d.cover),
            scanlines = bool("scanlines", d.scanlines),
            glow = bool("glow", d.glow),
            cursor = bool("cursor", d.cursor),
            monoCovers = bool("monoCovers", d.monoCovers),
            crossfade = bool("crossfade", d.crossfade),
            lrcTreeUri = (o["lrcTreeUri"] as? JsonPrimitive)?.contentOrNull,
            lastScan = (o["lastScan"] as? JsonPrimitive)?.longOrNull ?: d.lastScan,
            playlistsImported = int("playlistsImported", d.playlistsImported),
            eq = (o["eq"] as? JsonObject)?.let { EqSettingsJson.fromObject(it) } ?: d.eq,
        )
    }
}
