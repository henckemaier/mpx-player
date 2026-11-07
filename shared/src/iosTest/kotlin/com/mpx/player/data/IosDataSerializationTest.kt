package com.mpx.player.data

import com.mpx.player.model.EqSettings
import com.mpx.player.model.Playlist
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Lógica PURA das persistências iOS (sem NSUserDefaults): round-trip JSON de EqSettings
 * ([EqSettingsJson]) e de Playlists ([PlaylistLogic], reusado do Android) + a semântica
 * de favoritos/addTrack em que o [IosPlaylistRepository] se apoia. Roda no simulador via
 * :shared:iosSimulatorArm64Test. Padrão: IosQueueLogicTest/IosLibraryMappingTest.
 */
class IosDataSerializationTest {

    // ---- EqSettings (JSON) ----

    @Test
    fun eq_roundtrip_default() {
        val d = EqSettings()
        assertEquals(d, EqSettingsJson.fromJson(EqSettingsJson.toJson(d)))
    }

    @Test
    fun eq_roundtrip_custom() {
        val e = EqSettings(
            enabled = false,
            preset = "ROCK",
            levels = List(10) { it },
            custom = List(10) { 12 - it },
            preamp = 18,
            bassBoost = true,
            surround = true,
            loudness = true,
        )
        assertEquals(e, EqSettingsJson.fromJson(EqSettingsJson.toJson(e)))
    }

    @Test
    fun eq_blankOuCorrompido_voltaDefault() {
        assertEquals(EqSettings(), EqSettingsJson.fromJson(""))
        assertEquals(EqSettings(), EqSettingsJson.fromJson("   "))
        assertEquals(EqSettings(), EqSettingsJson.fromJson("isto não é json"))
    }

    @Test
    fun eq_levelsComTamanhoErrado_caiNoDefault() {
        val d = EqSettings()
        val parsed = EqSettingsJson.fromJson("""{"preset":"X","levels":[1,2,3]}""")
        assertEquals(d.levels, parsed.levels) // 3 níveis != 10 → default
        assertEquals("X", parsed.preset)      // os demais campos ainda são lidos
    }

    // ---- Playlists (PlaylistLogic, reusado) ----

    @Test
    fun playlist_roundtrip_json() {
        val base = PlaylistLogic.ensureFavorites(emptyList())
        val comLista = PlaylistLogic.withTrack(
            PlaylistLogic.created(base, "p1", "NULL SECTOR", photo = null),
            "p1", "null_sector/04_signal_decay.mp3",
        )
        val round = PlaylistLogic.ensureFavorites(PlaylistLogic.fromJson(PlaylistLogic.toJson(comLista)))
        assertEquals(comLista, round)
    }

    @Test
    fun toggleFavorite_adicionaEremove() {
        val base = PlaylistLogic.ensureFavorites(emptyList())
        val on = PlaylistLogic.favoriteToggled(base, "song.mp3")
        assertTrue(PlaylistLogic.isFavorite(on, "song.mp3"))
        val off = PlaylistLogic.favoriteToggled(on, "song.mp3")
        assertFalse(PlaylistLogic.isFavorite(off, "song.mp3"))
    }

    @Test
    fun addTrack_dedupa() {
        val list = PlaylistLogic.created(PlaylistLogic.ensureFavorites(emptyList()), "p1", "X", photo = null)
        val once = PlaylistLogic.withTrack(list, "p1", "k")
        val twice = PlaylistLogic.withTrack(once, "p1", "k")
        assertEquals(1, twice.first { it.id == "p1" }.trackKeys.size)
    }

    @Test
    fun ensureFavorites_sempreUmLockedNoTopo() {
        val fav = PlaylistLogic.ensureFavorites(emptyList()).first()
        assertEquals(Playlist.FAVORITES_ID, fav.id)
        assertTrue(fav.locked)
    }
}
