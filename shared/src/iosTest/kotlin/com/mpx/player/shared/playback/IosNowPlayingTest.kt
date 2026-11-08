package com.mpx.player.shared.playback

import kotlin.test.Test
import kotlin.test.assertEquals
import platform.MediaPlayer.MPMediaItemPropertyAlbumTitle
import platform.MediaPlayer.MPMediaItemPropertyArtist
import platform.MediaPlayer.MPMediaItemPropertyPlaybackDuration
import platform.MediaPlayer.MPMediaItemPropertyTitle
import platform.MediaPlayer.MPNowPlayingInfoPropertyElapsedPlaybackTime
import platform.MediaPlayer.MPNowPlayingInfoPropertyPlaybackRate

/**
 * Lógica PURA do now-playing do caminho engine ([buildNowPlayingInfo], sem player/efeito): o
 * dicionário do `MPNowPlayingInfoCenter` tem as 6 chaves certas com os valores dados. Roda no
 * simulador via `:shared:iosSimulatorArm64Test`. A PUBLICAÇÃO real no lock-screen é device-gated
 * (não coberta aqui; ver KDoc de [IosNowPlaying]).
 */
class IosNowPlayingTest {

    @Test
    fun buildNowPlayingInfo_mapeia_as_6_chaves_com_valores() {
        val item = PlaybackItem(
            id = "k1",
            uri = "file:///a.mp3",
            title = "Title",
            artist = "Artist",
            album = "Album",
            artworkUri = null,
            durationMs = 200_000L,
        )
        val info = buildNowPlayingInfo(item, durationSec = 200.0, elapsedSec = 42.0, rate = 1f)
        assertEquals(6, info.size)
        assertEquals("Title", info[MPMediaItemPropertyTitle])
        assertEquals("Artist", info[MPMediaItemPropertyArtist])
        assertEquals("Album", info[MPMediaItemPropertyAlbumTitle])
        assertEquals(200.0, info[MPMediaItemPropertyPlaybackDuration])
        assertEquals(42.0, info[MPNowPlayingInfoPropertyElapsedPlaybackTime])
        assertEquals(1f, info[MPNowPlayingInfoPropertyPlaybackRate])
    }

    @Test
    fun buildNowPlayingInfo_rate_zero_quando_pausado() {
        // rate=0 (pausado) e decorrido=0: os escalares entram como dados, sem clamp/transform.
        val item = PlaybackItem(id = "k2", uri = "u", title = "T", artist = "A", album = "Al")
        val info = buildNowPlayingInfo(item, durationSec = 10.0, elapsedSec = 0.0, rate = 0f)
        assertEquals(0f, info[MPNowPlayingInfoPropertyPlaybackRate])
        assertEquals(0.0, info[MPNowPlayingInfoPropertyElapsedPlaybackTime])
        assertEquals("T", info[MPMediaItemPropertyTitle])
    }
}
