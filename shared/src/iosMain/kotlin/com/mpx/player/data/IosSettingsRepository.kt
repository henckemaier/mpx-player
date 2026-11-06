package com.mpx.player.data

import com.mpx.player.model.EqSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import platform.Foundation.NSUserDefaults

/**
 * Settings no iOS: persiste o [EqSettings] e a árvore de letras (`lrcTreeUri`) em
 * `NSUserDefaults`, atrás do contrato comum [SettingsRepository] — espelho do
 * `AndroidSettingsRepository` (DataStore). Só a fatia comum (eq + lrcTreeUri + updateEq);
 * o resto do `UserSettings` é Android-only e não entra aqui.
 *
 * O EqSettings é serializado em JSON (via [EqSettingsJson], função pura testável). O
 * `lrcTreeUri` existe só por paridade de contrato: no iOS não há SAF para configurá-lo,
 * então fica no que estiver salvo (normalmente `null`) — a leitura local de letras é
 * Android-only (ver `IosLyricsRepository`).
 */
class IosSettingsRepository : SettingsRepository {

    private val defaults = NSUserDefaults.standardUserDefaults

    private val _eq = MutableStateFlow(loadEq())
    override val eq: Flow<EqSettings> = _eq.asStateFlow()

    private val _lrcTreeUri = MutableStateFlow(defaults.stringForKey(KEY_LRC))
    override val lrcTreeUri: Flow<String?> = _lrcTreeUri.asStateFlow()

    override suspend fun updateEq(transform: (EqSettings) -> EqSettings) {
        val next = transform(_eq.value)
        _eq.value = next
        defaults.setObject(EqSettingsJson.toJson(next), KEY_EQ)
    }

    private fun loadEq(): EqSettings =
        EqSettingsJson.fromJson(defaults.stringForKey(KEY_EQ).orEmpty())

    companion object {
        const val KEY_EQ = "mpx_eq_settings"
        const val KEY_LRC = "mpx_lrc_tree"
    }
}

/**
 * (De)serialização PURA de [EqSettings] em JSON (kotlinx.serialization, estilo manual
 * igual ao [PlaylistLogic] — sem `@Serializable`/plugin, zero impacto no `:app`).
 * Resiliente: entrada vazia/corrompida → `EqSettings()` padrão; `levels`/`custom` só são
 * aceitos com 10 níveis (senão caem no default).
 */
internal object EqSettingsJson {

    fun toJson(eq: EqSettings): String = buildJsonObject {
        put("enabled", eq.enabled)
        put("preset", eq.preset)
        putJsonArray("levels") { eq.levels.forEach { add(it) } }
        putJsonArray("custom") { eq.custom.forEach { add(it) } }
        put("preamp", eq.preamp)
        put("bassBoost", eq.bassBoost)
        put("surround", eq.surround)
        put("loudness", eq.loudness)
    }.toString()

    fun fromJson(text: String): EqSettings {
        if (text.isBlank()) return EqSettings()
        val o = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return EqSettings()
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
