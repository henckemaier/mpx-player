package com.mpx.player.shared.playback

import com.mpx.player.model.Track
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Lógica pura da capa da tela de bloqueio no iOS (sem AVFoundation/UIKit): seleção da
 * fonte do artwork ([iosArtworkUri]) e o anti-race ([shouldApplyArtwork]). Roda no
 * simulador via :shared:iosSimulatorArm64Test. O load real da imagem e a publicação no
 * MPNowPlayingInfoCenter são validados só em device (ver KDoc do IosAudioPlayer).
 */
class IosArtworkLogicTest {

    private fun track(uri: String = "file:///music/song.mp3", coverOverride: String? = null) = Track(
        id = 1,
        uri = uri,
        title = "T",
        artist = "A",
        album = "Alb",
        albumId = 10,
        durationMs = 1000,
        displayName = "song.mp3",
        relativePath = "music/",
        mimeType = "audio/mpeg",
        bitrate = null,
        trackNumber = 1,
        year = null,
        coverOverride = coverOverride,
    )

    // --- iosArtworkUri ---

    @Test fun artworkUri_prefereCoverOverrideQuandoPresente() {
        val t = track(uri = "file:///music/song.mp3", coverOverride = "/covers/custom.png")
        assertEquals("/covers/custom.png", iosArtworkUri(t))
    }

    @Test fun artworkUri_semOverride_caiNaUriDoAudio() {
        val t = track(uri = "file:///music/song.mp3", coverOverride = null)
        // Fallback na própria mídia: a arte embutida é extraída dela na exibição.
        assertEquals("file:///music/song.mp3", iosArtworkUri(t))
    }

    @Test fun artworkUri_overrideEmBranco_ignorado_caiNaUri() {
        val t = track(uri = "file:///music/song.mp3", coverOverride = "   ")
        assertEquals("file:///music/song.mp3", iosArtworkUri(t))
    }

    @Test fun artworkUri_tudoEmBranco_retornaNull() {
        val t = track(uri = "", coverOverride = "")
        assertNull(iosArtworkUri(t))
    }

    // --- shouldApplyArtwork (anti-race) ---

    @Test fun shouldApply_mesmaFaixa_aplica() {
        assertTrue(shouldApplyArtwork(startedId = "k1", currentId = "k1"))
    }

    @Test fun shouldApply_faixaTrocou_descarta() {
        assertFalse(shouldApplyArtwork(startedId = "k1", currentId = "k2"))
    }

    @Test fun shouldApply_semFaixaAtual_descarta() {
        // Fila esvaziada enquanto a capa carregava (currentId == null): não aplica.
        assertFalse(shouldApplyArtwork(startedId = "k1", currentId = null))
    }

    @Test fun shouldApply_startedNull_descarta() {
        assertFalse(shouldApplyArtwork(startedId = null, currentId = null))
        assertFalse(shouldApplyArtwork(startedId = null, currentId = "k1"))
    }
}
