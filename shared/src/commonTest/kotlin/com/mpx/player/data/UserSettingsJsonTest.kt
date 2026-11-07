package com.mpx.player.data

import com.mpx.player.model.EqSettings
import com.mpx.player.model.UserSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Trava a (de)serialização PURA do [UserSettingsJson] (fatia SETTINGS, P7) — fonte de verdade da
 * persistência iOS. Round-trip de TODOS os campos (inclusive o bloco `eq`, que delega ao
 * [EqSettingsJson]) + resiliência (vazio/corrompido/campos faltando → `UserSettings()` por campo).
 * Roda em `:shared:testDebugUnitTest` E `iosSimulatorArm64Test`.
 */
class UserSettingsJsonTest {

    @Test
    fun roundTrip_allFields() {
        val full = UserSettings(
            theme = 3,
            customColor = 0xFF112233.toInt(),
            oled = true,
            font = 2,
            cover = 1,
            scanlines = false,
            glow = false,
            cursor = false,
            monoCovers = false,
            crossfade = true,
            lrcTreeUri = "content://tree/primary:Music",
            lastScan = 1_700_000_000_000L,
            playlistsImported = 7,
            eq = EqSettings(
                enabled = false,
                preset = "ROCK",
                levels = List(10) { it },
                custom = List(10) { 9 - it },
                preamp = 5,
                bassBoost = true,
                surround = true,
                loudness = true,
            ),
        )
        assertEquals(full, UserSettingsJson.fromJson(UserSettingsJson.toJson(full)))
    }

    @Test
    fun roundTrip_defaults_keepsNulls() {
        // customColor=null + lrcTreeUri=null + eq default devem sobreviver ao ciclo.
        val d = UserSettings()
        val back = UserSettingsJson.fromJson(UserSettingsJson.toJson(d))
        assertEquals(d, back)
        assertNull(back.customColor)
        assertNull(back.lrcTreeUri)
    }

    @Test
    fun resilient_blankAndCorrupt_fallBackToDefault() {
        assertEquals(UserSettings(), UserSettingsJson.fromJson(""))
        assertEquals(UserSettings(), UserSettingsJson.fromJson("   "))
        assertEquals(UserSettings(), UserSettingsJson.fromJson("isto não é json {"))
    }

    @Test
    fun resilient_missingFields_defaultPerField() {
        val partial = UserSettingsJson.fromJson("""{"theme":2,"glow":false}""")
        val d = UserSettings()
        assertEquals(2, partial.theme)
        assertEquals(false, partial.glow)
        assertEquals(d.oled, partial.oled)       // ausente → default
        assertEquals(d.monoCovers, partial.monoCovers)
        assertEquals(d.lastScan, partial.lastScan)
        assertEquals(d.eq, partial.eq)           // bloco eq ausente → EqSettings() default
        assertNull(partial.customColor)
    }
}
