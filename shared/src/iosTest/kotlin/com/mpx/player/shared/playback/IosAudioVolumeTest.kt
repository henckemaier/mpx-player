package com.mpx.player.shared.playback

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Lógica PURA do volume do IosAudioPlayer (sem AVFoundation): mapeamento fração→nível e a
 * transição do mute/unmute. Roda no simulador via :shared:iosSimulatorArm64Test. O efeito
 * AUDÍVEL (AVPlayer.volume no device) NÃO é coberto aqui — só lógica (decisão [D1=1a]).
 */
class IosAudioVolumeTest {

    @Test
    fun volumeLevel_mapeiaEClampa() {
        assertEquals(0, volumeLevel(0f))
        assertEquals(100, volumeLevel(1f))
        assertEquals(50, volumeLevel(0.5f))
        assertEquals(33, volumeLevel(0.337f))   // trunca (toInt), não arredonda
        assertEquals(0, volumeLevel(-0.3f))      // clamp inferior
        assertEquals(100, volumeLevel(2f))       // clamp superior
    }

    @Test
    fun applyMuteToggle_muta_guardandoOGanhoCorrente() {
        // ganho 0.7 em reprodução → muta: novo ganho 0, e guarda 0.7 como último não-zero.
        val (nv, nlz) = applyMuteToggle(current = 0.7f, lastNonZero = 1f)
        assertEquals(0f, nv)
        assertEquals(0.7f, nlz)
    }

    @Test
    fun applyMuteToggle_desmuta_restauraOUltimoNaoZero() {
        // ganho 0 (mutado) com último não-zero 0.7 → desmuta: restaura 0.7 (último intacto).
        val (nv, nlz) = applyMuteToggle(current = 0f, lastNonZero = 0.7f)
        assertEquals(0.7f, nv)
        assertEquals(0.7f, nlz)
    }

    @Test
    fun applyMuteToggle_desmutaSemHistorico_voltaPraCheio() {
        // ganho 0 e nenhum último não-zero válido (0f) → desmuta pra 1f (volume cheio).
        val (nv, nlz) = applyMuteToggle(current = 0f, lastNonZero = 0f)
        assertEquals(1f, nv)
        assertEquals(0f, nlz) // o último não-zero segue inalterado (ainda não houve um)
    }

    @Test
    fun muteToggle_ciclo_voltaAoGanhoOriginal() {
        // Ciclo completo: 0.4 → muta → desmuta volta a 0.4.
        val (muted, keep) = applyMuteToggle(current = 0.4f, lastNonZero = 1f)
        assertEquals(0f, muted)
        val (unmuted, _) = applyMuteToggle(current = muted, lastNonZero = keep)
        assertEquals(0.4f, unmuted)
    }
}
