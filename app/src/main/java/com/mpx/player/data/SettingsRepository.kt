package com.mpx.player.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mpx.player.model.EqPresets
import com.mpx.player.model.EqSettings
import com.mpx.player.model.UserSettings
import com.mpx.player.ui.theme.CUSTOM_THEME
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "config_sys")

class SettingsRepository(private val context: Context) {

    private object K {
        val theme = intPreferencesKey("theme")
        val customColor = intPreferencesKey("custom_color")
        val oled = booleanPreferencesKey("oled")
        val font = intPreferencesKey("font")
        val cover = intPreferencesKey("cover")
        val scanlines = booleanPreferencesKey("scanlines")
        val glow = booleanPreferencesKey("glow")
        val cursor = booleanPreferencesKey("cursor")
        val mono = booleanPreferencesKey("mono_covers")
        val crossfade = booleanPreferencesKey("crossfade")
        val lrcTree = stringPreferencesKey("lrc_tree")
        val lastScan = longPreferencesKey("last_scan")
        val playlists = intPreferencesKey("playlists_imported")
        val eqOn = booleanPreferencesKey("eq_on")
        val eqPreset = stringPreferencesKey("eq_preset")
        val eqLevels = stringPreferencesKey("eq_levels")
        val eqCustom = stringPreferencesKey("eq_custom")
        val preamp = intPreferencesKey("eq_preamp")
        val bass = booleanPreferencesKey("fx_bass")
        val surround = booleanPreferencesKey("fx_surround")
        val loud = booleanPreferencesKey("fx_loud")
    }

    val settings: Flow<UserSettings> = context.dataStore.data.map { it.toSettings() }

    private fun Preferences.toSettings(): UserSettings {
        val d = UserSettings()
        return UserSettings(
            theme = this[K.theme] ?: d.theme,
            customColor = this[K.customColor],
            oled = this[K.oled] ?: d.oled,
            font = this[K.font] ?: d.font,
            cover = this[K.cover] ?: d.cover,
            scanlines = this[K.scanlines] ?: d.scanlines,
            glow = this[K.glow] ?: d.glow,
            cursor = this[K.cursor] ?: d.cursor,
            monoCovers = this[K.mono] ?: d.monoCovers,
            crossfade = this[K.crossfade] ?: d.crossfade,
            lrcTreeUri = this[K.lrcTree],
            lastScan = this[K.lastScan] ?: 0L,
            playlistsImported = this[K.playlists] ?: 0,
            eq = EqSettings(
                enabled = this[K.eqOn] ?: true,
                preset = this[K.eqPreset] ?: "FLAT",
                levels = this[K.eqLevels].toLevels() ?: EqPresets.FLAT,
                custom = this[K.eqCustom].toLevels() ?: EqPresets.FLAT,
                preamp = this[K.preamp] ?: 12,
                bassBoost = this[K.bass] ?: false,
                surround = this[K.surround] ?: false,
                loudness = this[K.loud] ?: false,
            ),
        )
    }

    private fun String?.toLevels(): List<Int>? =
        this?.split(',')?.mapNotNull { it.toIntOrNull() }?.takeIf { it.size == 10 }

    private fun List<Int>.encode() = joinToString(",")

    suspend fun setTheme(v: Int) = context.dataStore.edit { it[K.theme] = v }
    /** Seleciona o tema personalizado e grava a cor (ARGB) numa única escrita. */
    suspend fun setCustomColor(argb: Int) = context.dataStore.edit {
        it[K.theme] = CUSTOM_THEME
        it[K.customColor] = argb
    }
    suspend fun setOled(v: Boolean) = context.dataStore.edit { it[K.oled] = v }
    suspend fun setFont(v: Int) = context.dataStore.edit { it[K.font] = v }
    suspend fun setCover(v: Int) = context.dataStore.edit { it[K.cover] = v }
    suspend fun setScanlines(v: Boolean) = context.dataStore.edit { it[K.scanlines] = v }
    suspend fun setGlow(v: Boolean) = context.dataStore.edit { it[K.glow] = v }
    suspend fun setCursor(v: Boolean) = context.dataStore.edit { it[K.cursor] = v }
    suspend fun setMonoCovers(v: Boolean) = context.dataStore.edit { it[K.mono] = v }
    suspend fun setCrossfade(v: Boolean) = context.dataStore.edit { it[K.crossfade] = v }
    suspend fun setLrcTree(v: String?) = context.dataStore.edit { if (v == null) it.remove(K.lrcTree) else it[K.lrcTree] = v }
    suspend fun setLastScan(v: Long) = context.dataStore.edit { it[K.lastScan] = v }
    suspend fun incPlaylists() = context.dataStore.edit { it[K.playlists] = (it[K.playlists] ?: 0) + 1 }

    suspend fun updateEq(transform: (EqSettings) -> EqSettings) = context.dataStore.edit { p ->
        val cur = p.toSettings().eq
        val n = transform(cur)
        p[K.eqOn] = n.enabled
        p[K.eqPreset] = n.preset
        p[K.eqLevels] = n.levels.encode()
        p[K.eqCustom] = n.custom.encode()
        p[K.preamp] = n.preamp
        p[K.bass] = n.bassBoost
        p[K.surround] = n.surround
        p[K.loud] = n.loudness
    }
}
