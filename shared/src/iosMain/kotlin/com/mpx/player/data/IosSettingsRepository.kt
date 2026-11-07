package com.mpx.player.data

import com.mpx.player.model.EqSettings
import com.mpx.player.model.UserSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import platform.Foundation.NSUserDefaults

/**
 * Settings no iOS: persiste o [UserSettings] inteiro como UM objeto JSON em `NSUserDefaults`
 * (chave [KEY_SETTINGS]), atrás do contrato comum [SettingsRepository] — espelho do
 * `AndroidSettingsRepository` (DataStore). Fonte única de verdade: `_settings`; `eq`/`lrcTreeUri`
 * derivam dela. A (de)serialização é a função pura [UserSettingsJson] (commonMain, testável).
 *
 * O `lrcTreeUri` existe só por paridade de contrato: no iOS não há SAF para configurá-lo, então
 * fica no que estiver salvo (normalmente `null`) — a leitura local de letras é Android-only
 * (ver `IosLyricsRepository`).
 */
class IosSettingsRepository : SettingsRepository {

    private val defaults = NSUserDefaults.standardUserDefaults

    private val _settings = MutableStateFlow(load())
    override val settings: Flow<UserSettings> = _settings.asStateFlow()
    override val eq: Flow<EqSettings> = _settings.map { it.eq }
    override val lrcTreeUri: Flow<String?> = _settings.map { it.lrcTreeUri }

    override suspend fun update(transform: (UserSettings) -> UserSettings) {
        val next = transform(_settings.value)
        _settings.value = next
        defaults.setObject(UserSettingsJson.toJson(next), KEY_SETTINGS)
    }

    override suspend fun updateEq(transform: (EqSettings) -> EqSettings) {
        update { it.copy(eq = transform(it.eq)) }
    }

    private fun load(): UserSettings =
        UserSettingsJson.fromJson(defaults.stringForKey(KEY_SETTINGS).orEmpty())

    companion object {
        const val KEY_SETTINGS = "mpx_user_settings"
    }
}
