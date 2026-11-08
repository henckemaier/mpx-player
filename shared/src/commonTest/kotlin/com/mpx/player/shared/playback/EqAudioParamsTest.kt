package com.mpx.player.shared.playback

import com.mpx.player.model.EqPresets
import com.mpx.player.model.EqSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Prova PURA (JVM, sem device) do mapeamento [EqSettings.toAudioParams] — o prep do
 * EQ-audível do iOS (AVAudioUnitEQ-ready). Molde dos mapping tests
 * ([com.mpx.player.shared.playback.PlaybackStateTest] / AndroidAudioPlayerMappingTest).
 * Reusa [EqPresets.levelToDb]/[EqPresets.preampToDb] (já testados em outro ponto).
 */
class EqAudioParamsTest {

    @Test
    fun flat_todasAsBandasEmZero_preampZero() {
        val p = EqSettings().toAudioParams() // FLAT (levels todos 6), preamp 12, enabled=true
        assertEquals(10, p.bands.size)
        assertEquals(EqPresets.freqsHz, p.bands.map { it.freqHz })
        p.bands.forEach { assertEquals(0f, it.gainDb) }
        assertEquals(0f, p.globalGainDb)
        assertTrue(p.enabled)
    }

    @Test
    fun rock_ganhosPorBanda_viaLevelToDb() {
        val p = EqSettings(preset = "ROCK", levels = EqPresets.ROCK).toAudioParams()
        // levelToDb: (l-6)*2  →  [9,8,7,5,4,5,7,8,9,9] vira:
        val esperado = listOf(6f, 4f, 2f, -2f, -4f, -2f, 2f, 4f, 6f, 6f)
        assertEquals(esperado, p.bands.map { it.gainDb })
    }

    @Test
    fun preamp_mapeiaPreampToDb() {
        assertEquals(6f, EqSettings(preamp = 24).toAudioParams().globalGainDb)
        assertEquals(-6f, EqSettings(preamp = 0).toAudioParams().globalGainDb)
        assertEquals(0f, EqSettings(preamp = 12).toAudioParams().globalGainDb)
    }

    @Test
    fun bassBoost_somaTresDbNasTresGraves_soBassBoost() {
        val p = EqSettings(bassBoost = true).toAudioParams() // sobre FLAT
        assertEquals(3f, p.bands[0].gainDb)
        assertEquals(3f, p.bands[1].gainDb)
        assertEquals(3f, p.bands[2].gainDb)
        for (i in 3 until 10) assertEquals(0f, p.bands[i].gainDb, "banda $i não deve mudar")
    }

    @Test
    fun enabledFalse_propagado() {
        assertFalse(EqSettings(enabled = false).toAudioParams().enabled)
    }

    @Test
    fun surroundELoudness_foraDosParams_gapDocumentado() {
        // GAP de fidelidade iOS: surround/loudness NÃO têm equivalente AVAudioUnit 1:1 e
        // NÃO entram em EqAudioParams (sem campo pra eles). Ligá-los não altera as bandas
        // nem o globalGain — a forma é idêntica ao FLAT puro.
        val base = EqSettings().toAudioParams()
        val comEfeitos = EqSettings(surround = true, loudness = true).toAudioParams()
        assertEquals(base.bands, comEfeitos.bands)
        assertEquals(base.globalGainDb, comEfeitos.globalGainDb)
    }

    @Test
    fun invariantes_dezBandas_freqsNaOrdemCanonica() {
        val p = EqSettings(preset = "VOCAL", levels = EqPresets.VOCAL).toAudioParams()
        assertEquals(10, p.bands.size)
        assertEquals(
            listOf(32f, 64f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f),
            p.bands.map { it.freqHz },
        )
    }

    @Test
    fun levelsCurto_faltantesTratadosComoZeroDb() {
        // Robustez: levels com menos de 10 entradas → bandas faltantes usam nível 6 (0 dB),
        // igual ao getOrElse do Android.
        val p = EqSettings(levels = listOf(12, 0)).toAudioParams()
        assertEquals(12f, p.bands[0].gainDb)   // levelToDb(12) = +12
        assertEquals(-12f, p.bands[1].gainDb)  // levelToDb(0)  = -12
        for (i in 2 until 10) assertEquals(0f, p.bands[i].gainDb)
    }
}
