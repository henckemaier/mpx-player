package com.mpx.player.data

import com.mpx.player.model.Playlist
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlaylistLogicTest {

    private val FAV = Playlist.FAVORITES_ID

    private fun fav(list: List<Playlist>) = list.first { it.id == FAV }
    private fun pl(list: List<Playlist>, id: String) = list.first { it.id == id }

    // ---- ensureFavorites

    @Test fun ensureFavorites_addsWhenMissing() {
        val r = PlaylistLogic.ensureFavorites(emptyList())
        assertEquals(1, r.size)
        assertEquals(FAV, r[0].id)
        assertTrue(r[0].locked)
        assertEquals(Playlist.FAVORITES_NAME, r[0].name)
    }

    @Test fun ensureFavorites_keepsSingleAndMovesFirst() {
        val input = listOf(Playlist("a", "A"), Playlist(FAV, "x", locked = true, trackKeys = listOf("k1")))
        val r = PlaylistLogic.ensureFavorites(input)
        assertEquals(2, r.size)
        assertEquals(FAV, r[0].id)                 // favoritos sempre primeiro
        assertEquals(listOf("k1"), r[0].trackKeys) // preserva as faixas
        assertEquals(Playlist.FAVORITES_NAME, r[0].name) // nome sempre forçado
        assertTrue(r[0].locked)
    }

    @Test fun ensureFavorites_isIdempotent() {
        val once = PlaylistLogic.ensureFavorites(listOf(Playlist("a", "A")))
        val twice = PlaylistLogic.ensureFavorites(once)
        assertEquals(once, twice)
        assertEquals(1, twice.count { it.id == FAV })
    }

    @Test fun ensureFavorites_forcesLockedEvenIfJsonSaidFalse() {
        val input = listOf(Playlist(FAV, "qualquer", locked = false))
        val r = PlaylistLogic.ensureFavorites(input)
        assertTrue(fav(r).locked)
    }

    // ---- created

    @Test fun created_appendsTrimmed() {
        val base = PlaylistLogic.ensureFavorites(emptyList())
        val r = PlaylistLogic.created(base, "id1", "  Rock  ", null)
        assertEquals(2, r.size)
        assertEquals("Rock", pl(r, "id1").name)
        assertFalse(pl(r, "id1").locked)
    }

    @Test fun created_rejectsBlankName() {
        val base = PlaylistLogic.ensureFavorites(emptyList())
        assertEquals(base, PlaylistLogic.created(base, "id1", "   ", null))
    }

    @Test fun created_allowsDuplicateNamesWithDistinctIds() {
        var r = PlaylistLogic.ensureFavorites(emptyList())
        r = PlaylistLogic.created(r, "id1", "Mix", null)
        r = PlaylistLogic.created(r, "id2", "Mix", null)
        assertEquals(2, r.count { it.name == "Mix" })
    }

    // ---- renamed / deleted respeitam locked

    @Test fun renamed_changesUserPlaylist() {
        var r = PlaylistLogic.ensureFavorites(emptyList())
        r = PlaylistLogic.created(r, "id1", "A", null)
        r = PlaylistLogic.renamed(r, "id1", "B")
        assertEquals("B", pl(r, "id1").name)
    }

    @Test fun renamed_cannotRenameFavorites() {
        val r = PlaylistLogic.renamed(PlaylistLogic.ensureFavorites(emptyList()), FAV, "OUTRO")
        assertEquals(Playlist.FAVORITES_NAME, fav(r).name)
    }

    @Test fun renamed_rejectsBlank() {
        var r = PlaylistLogic.ensureFavorites(emptyList())
        r = PlaylistLogic.created(r, "id1", "A", null)
        assertEquals("A", pl(PlaylistLogic.renamed(r, "id1", "  "), "id1").name)
    }

    @Test fun deleted_removesUserPlaylist() {
        var r = PlaylistLogic.ensureFavorites(emptyList())
        r = PlaylistLogic.created(r, "id1", "A", null)
        r = PlaylistLogic.deleted(r, "id1")
        assertTrue(r.none { it.id == "id1" })
    }

    @Test fun deleted_cannotDeleteFavorites() {
        val r = PlaylistLogic.deleted(PlaylistLogic.ensureFavorites(emptyList()), FAV)
        assertEquals(1, r.count { it.id == FAV })
    }

    // ---- faixas

    @Test fun withTrack_addsOnceNoDuplicate() {
        var r = PlaylistLogic.ensureFavorites(emptyList())
        r = PlaylistLogic.created(r, "id1", "A", null)
        r = PlaylistLogic.withTrack(r, "id1", "k1")
        r = PlaylistLogic.withTrack(r, "id1", "k1")
        assertEquals(listOf("k1"), pl(r, "id1").trackKeys)
    }

    @Test fun withoutTrack_removes() {
        var r = PlaylistLogic.ensureFavorites(emptyList())
        r = PlaylistLogic.created(r, "id1", "A", null)
        r = PlaylistLogic.withTrack(r, "id1", "k1")
        r = PlaylistLogic.withoutTrack(r, "id1", "k1")
        assertTrue(pl(r, "id1").trackKeys.isEmpty())
    }

    @Test fun sameTrackCanBeInMultiplePlaylists() {
        var r = PlaylistLogic.ensureFavorites(emptyList())
        r = PlaylistLogic.created(r, "id1", "A", null)
        r = PlaylistLogic.created(r, "id2", "B", null)
        r = PlaylistLogic.withTrack(r, "id1", "k")
        r = PlaylistLogic.withTrack(r, "id2", "k")
        r = PlaylistLogic.favoriteToggled(r, "k")
        assertTrue("k" in pl(r, "id1").trackKeys)
        assertTrue("k" in pl(r, "id2").trackKeys)
        assertTrue(PlaylistLogic.isFavorite(r, "k"))
    }

    @Test fun favoriteToggled_addsThenRemoves() {
        val base = PlaylistLogic.ensureFavorites(emptyList())
        val on = PlaylistLogic.favoriteToggled(base, "k")
        assertTrue(PlaylistLogic.isFavorite(on, "k"))
        val off = PlaylistLogic.favoriteToggled(on, "k")
        assertFalse(PlaylistLogic.isFavorite(off, "k"))
    }

    @Test fun purgeKey_removesFromAllPlaylists() {
        var r = PlaylistLogic.ensureFavorites(emptyList())
        r = PlaylistLogic.created(r, "id1", "A", null)
        r = PlaylistLogic.created(r, "id2", "B", null)
        r = PlaylistLogic.withTrack(r, "id1", "k")
        r = PlaylistLogic.withTrack(r, "id2", "k")
        r = PlaylistLogic.favoriteToggled(r, "k")
        r = PlaylistLogic.purgeKey(r, "k")
        assertTrue(r.all { "k" !in it.trackKeys })
    }

    // ---- serialização

    @Test fun jsonRoundTrip() {
        var r = PlaylistLogic.ensureFavorites(emptyList())
        r = PlaylistLogic.created(r, "id1", "Rock", "/data/photo.jpg")
        r = PlaylistLogic.withTrack(r, "id1", "music/a.mp3")
        r = PlaylistLogic.favoriteToggled(r, "music/b.mp3")
        val back = PlaylistLogic.ensureFavorites(PlaylistLogic.fromJson(PlaylistLogic.toJson(r)))
        assertEquals(r, back)
        assertEquals("/data/photo.jpg", pl(back, "id1").photo)
        assertTrue(fav(back).locked)
    }

    @Test fun fromJson_blankOrGarbageIsEmpty() {
        assertTrue(PlaylistLogic.fromJson("").isEmpty())
        assertTrue(PlaylistLogic.fromJson("not json {[").isEmpty())
    }

    @Test fun fromJson_skipsBadEntryKeepsTheRest() {
        // 2ª entrada sem "id" (malformada): deve ser pulada, não derrubar a lista toda.
        val json = """[{"id":"favoritos","name":"FAVORITOS","locked":true,"trackKeys":["s1","s2"]},{"name":"SemId","trackKeys":["s3"]},{"id":"ok","name":"OK"}]"""
        val r = PlaylistLogic.fromJson(json)
        assertEquals(2, r.size)
        assertEquals(listOf("s1", "s2"), r.first { it.id == FAV }.trackKeys)
        assertTrue(r.any { it.id == "ok" })
    }

    @Test fun photoNullRoundTrips() {
        var r = PlaylistLogic.ensureFavorites(emptyList())
        r = PlaylistLogic.created(r, "id1", "A", null)
        val back = PlaylistLogic.fromJson(PlaylistLogic.toJson(r))
        assertNull(pl(back, "id1").photo)
    }
}
