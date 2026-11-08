package com.mpx.player.shared.playback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

    // --- SUB-2: advance / token / posição com fila / previous ---

    @Test
    fun advanceState_OFF_paraNoFim() {
        // Fim da fila sem repeat → null (parar).
        assertEquals(null, advanceState(2, 3, RepeatMode.OFF, trackStartSampleTime = 1000L, endedFrames = 500L))
        // No meio, OFF avança uma posição.
        val mid = advanceState(0, 3, RepeatMode.OFF, trackStartSampleTime = 0L, endedFrames = 441_000L)!!
        assertEquals(1, mid.pos)
    }

    @Test
    fun advanceState_ALL_daVoltaParaZero() {
        val wrapped = advanceState(2, 3, RepeatMode.ALL, trackStartSampleTime = 0L, endedFrames = 100L)!!
        assertEquals(0, wrapped.pos) // ALL no fim → volta ao início
    }

    @Test
    fun advanceState_ONE_mesmoPos() {
        val one = advanceState(1, 3, RepeatMode.ONE, trackStartSampleTime = 0L, endedFrames = 100L)!!
        assertEquals(1, one.pos) // ONE repete a mesma faixa
    }

    @Test
    fun advanceState_somaTrackStart_eZeraSeekBase() {
        val adv = advanceState(0, 3, RepeatMode.OFF, trackStartSampleTime = 1_000L, endedFrames = 441_000L)!!
        assertEquals(442_000L, adv.trackStartSampleTime) // trackStart += frames da faixa que terminou
        assertEquals(0.0, adv.seekBaseSeconds)           // seek offset zera na nova faixa
        // endedFrames negativo é saneado (não subtrai).
        val safe = advanceState(0, 3, RepeatMode.OFF, trackStartSampleTime = 10L, endedFrames = -5L)!!
        assertEquals(10L, safe.trackStartSampleTime)
    }

    @Test
    fun shouldAdvance_token_descartaObsoleto_aceitaCorrente() {
        assertTrue(shouldAdvance(completionGen = 7, currentGen = 7))   // mesmo gen → age
        assertFalse(shouldAdvance(completionGen = 6, currentGen = 7))  // gen velho → descarta
        assertFalse(shouldAdvance(completionGen = 8, currentGen = 7))
    }

    @Test
    fun trackPositionSeconds_compoeTrackStartESeekBase() {
        // 44100 frames decorridos a 44100 Hz = 1s; + seekBase 0 → 1.0.
        assertEquals(1.0, trackPositionSeconds(44_100L, 0L, sr, 0.0))
        // sampleTime 88200, trackStart 44100 → 1s decorrido na faixa; + seekBase 10s → 11.0.
        assertEquals(11.0, trackPositionSeconds(88_200L, 44_100L, sr, 10.0))
        // sampleTime < trackStart (logo após advance) não dá negativo: rel clampado a 0 → só a base.
        assertEquals(5.0, trackPositionSeconds(100L, 500L, sr, 5.0))
        // sampleRate inválida → só a base.
        assertEquals(3.0, trackPositionSeconds(44_100L, 0L, 0.0, 3.0))
    }

    @Test
    fun previousAction_regras() {
        // >3s na faixa → reinicia (independe da posição).
        assertEquals(PrevAction.Restart, previousAction(2, 5, currentSec = 4.0, repeat = RepeatMode.OFF))
        // ≤3s no meio → faixa anterior.
        assertEquals(PrevAction.GoTo(1), previousAction(2, 5, currentSec = 1.0, repeat = RepeatMode.OFF))
        // ≤3s no começo com repeat ALL → dá a volta p/ a última.
        assertEquals(PrevAction.GoTo(4), previousAction(0, 5, currentSec = 1.0, repeat = RepeatMode.ALL))
        // ≤3s no começo sem repeat → reinicia a atual.
        assertEquals(PrevAction.Restart, previousAction(0, 5, currentSec = 1.0, repeat = RepeatMode.OFF))
        // fila vazia → reinicia (robustez).
        assertEquals(PrevAction.Restart, previousAction(0, 0, currentSec = 0.0, repeat = RepeatMode.ALL))
    }

    @Test
    fun shuffleConvencoes_flagVsAgora() {
        // FLAG (setShuffle true) usa buildShuffleAll = permutação completa (atual NÃO presa no topo).
        val flag = buildShuffleAll(5)
        assertEquals(5, flag.size)
        assertEquals((0..4).toSet(), flag.toSet())
        // "shuffle agora" (shuffleQueue) usa buildPlayOrder(shuffle=true) = atual no TOPO.
        val agora = buildPlayOrder(5, start = 3, shuffle = true)
        assertEquals(3, agora.first())
        assertEquals((0..4).toSet(), agora.toSet())
    }
}
