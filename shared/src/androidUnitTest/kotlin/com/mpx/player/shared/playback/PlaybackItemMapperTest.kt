package com.mpx.player.shared.playback

import com.mpx.player.model.Track
import kotlin.test.Test
import kotlin.test.assertEquals

/** Mapeamento puro Track -> PlaybackItem (sem framework Android / Robolectric). */
class PlaybackItemMapperTest {

    private fun sampleTrack() = Track(
        id = 42L,
        uri = "content://media/external/audio/media/42",
        title = "Signal Decay",
        artist = "Null Sector",
        album = "Dead Air",
        albumId = 7L,
        durationMs = 215_000L,
        displayName = "04_signal_decay.mp3",
        relativePath = "Music/Null Sector/",
        mimeType = "audio/mpeg",
        bitrate = 320_000,
        trackNumber = 4,
        year = 2024,
    )

    @Test
    fun mapeia_campos_e_usa_key_como_id() {
        val t = sampleTrack()
        val item = t.toPlaybackItem()

        assertEquals(t.key, item.id)                 // debt3: id estável = Track.key
        assertEquals("music/null sector/04_signal_decay.mp3", item.id) // key = (relativePath+displayName).lowercase()
        assertEquals(t.uri, item.uri)
        assertEquals("Signal Decay", item.title)
        assertEquals("Null Sector", item.artist)
        assertEquals("Dead Air", item.album)
        assertEquals(215_000L, item.durationMs)
    }

    @Test
    fun artworkUri_derivada_do_albumId() {
        val item = sampleTrack().toPlaybackItem()
        assertEquals("content://media/external/audio/albumart/7", item.artworkUri)
    }
}
