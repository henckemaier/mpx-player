package com.mpx.player.playback

import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import android.util.Log
import com.mpx.player.model.EqPresets
import com.mpx.player.model.EqSettings
import kotlin.math.sqrt

/**
 * EQ de 10 bandas real via DynamicsProcessing (pré-EQ + ganho de entrada como preamp),
 * mais Virtualizer (3D SURROUND) e LoudnessEnhancer (LOUDNESS), todos na sessão do player.
 */
class AudioEffects(private val sessionId: Int) {

    private val dp: DynamicsProcessing? = runCatching {
        val config = DynamicsProcessing.Config.Builder(
            DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
            2,
            true, BANDS,
            false, 0,
            false, 0,
            false,
        ).build()
        DynamicsProcessing(0, sessionId, config)
    }.onFailure { Log.w(TAG, "DynamicsProcessing indisponível", it) }.getOrNull()

    private val virtualizer: Virtualizer? = runCatching { Virtualizer(0, sessionId) }
        .onFailure { Log.w(TAG, "Virtualizer indisponível", it) }.getOrNull()

    private val loudness: LoudnessEnhancer? = runCatching { LoudnessEnhancer(sessionId) }
        .onFailure { Log.w(TAG, "LoudnessEnhancer indisponível", it) }.getOrNull()

    fun apply(eq: EqSettings) {
        dp?.let { p ->
            runCatching {
                val bands = DynamicsProcessing.Eq(true, true, BANDS)
                for (i in 0 until BANDS) {
                    var gain = EqPresets.levelToDb(eq.levels.getOrElse(i) { 6 }).toFloat()
                    if (eq.bassBoost && i < 3) gain += 3f
                    val cutoff = if (i == BANDS - 1) 20_000f else EqPresets.freqsHz[i] * sqrt(2f)
                    bands.setBand(i, DynamicsProcessing.EqBand(true, cutoff, gain))
                }
                p.setPreEqAllChannelsTo(bands)
                p.setInputGainAllChannelsTo(EqPresets.preampToDb(eq.preamp))
                p.enabled = eq.enabled
            }.onFailure { Log.w(TAG, "falha ao aplicar EQ", it) }
        }
        virtualizer?.runCatching {
            if (strengthSupported) setStrength(if (eq.surround) 1000 else 0)
            enabled = eq.enabled && eq.surround
        }
        loudness?.runCatching {
            setTargetGain(if (eq.loudness) 600 else 0)
            enabled = eq.enabled && eq.loudness
        }
    }

    fun release() {
        dp?.release()
        virtualizer?.release()
        loudness?.release()
    }

    private companion object {
        const val TAG = "MpxEffects"
        const val BANDS = 10
    }
}
