package com.mpx.player.shared.playback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Lógica PURA de frame/tempo do [IosEngineAudioPlayer] (sem AVFoundation): conversões
 * segundos↔frames, fração→segundos e posição→fração. Roda no simulador via
 * :shared:iosSimulatorArm64Test. O ÁUDIO (tocar/seek no grafo AVAudioEngine) NÃO é
 * coberto aqui — device-gated, flag OFF (ver KDoc da classe).
 */
class IosEngineLogicTest {

    private val sr = 44_100.0

    @Test
    fun secondsToFrames_converteEFazFloor() {
        assertEquals(0L, secondsToFrames(0.0, sr))
        assertEquals(44_100L, secondsToFrames(1.0, sr))
        assertEquals(22_050L, secondsToFrames(0.5, sr))
        // 1.00001s * 44100 = 44100.441 → floor 44100
        assertEquals(44_100L, secondsToFrames(1.00001, sr))
    }

    @Test
    fun secondsToFrames_naoPositivosEInvalidosViram0() {
        assertEquals(0L, secondsToFrames(-1.0, sr))
        assertEquals(0L, secondsToFrames(5.0, 0.0))
        assertEquals(0L, secondsToFrames(5.0, -44_100.0))
    }

    @Test
    fun framesToSeconds_converteETrataSampleRateInvalida() {
        assertEquals(1.0, framesToSeconds(44_100L, sr))
        assertEquals(0.5, framesToSeconds(22_050L, sr))
        assertEquals(0.0, framesToSeconds(0L, sr))
        assertEquals(0.0, framesToSeconds(44_100L, 0.0)) // sampleRate inválida → 0
    }

    @Test
    fun roundTrip_segundos_frames_segundos() {
        // Em fronteiras de frame a ida-e-volta é exata; no meio, dentro de 1 frame.
        val back = framesToSeconds(secondsToFrames(3.0, sr), sr)
        assertEquals(3.0, back)
        val mid = framesToSeconds(secondsToFrames(2.7, sr), sr)
        assertTrue(kotlin.math.abs(mid - 2.7) < 1.0 / sr)
    }

    @Test
    fun fractionToSeconds_mapeiaEClampa() {
        assertEquals(0.0, fractionToSeconds(0f, 200.0))
        assertEquals(200.0, fractionToSeconds(1f, 200.0))
        assertEquals(50.0, fractionToSeconds(0.25f, 200.0))
        assertEquals(0.0, fractionToSeconds(-0.5f, 200.0)) // clamp inferior
        assertEquals(200.0, fractionToSeconds(2f, 200.0))  // clamp superior
        assertEquals(0.0, fractionToSeconds(0.5f, 0.0))    // sem duração → 0
    }

    @Test
    fun positionFraction_mapeiaEClampa() {
        assertEquals(0f, positionFraction(0.0, 200.0))
        assertEquals(1f, positionFraction(200.0, 200.0))
        assertEquals(0.5f, positionFraction(100.0, 200.0))
        assertEquals(1f, positionFraction(500.0, 200.0)) // além do fim → 1 (clamp)
        assertEquals(0f, positionFraction(100.0, 0.0))   // duração ≤0 → 0
    }
}
