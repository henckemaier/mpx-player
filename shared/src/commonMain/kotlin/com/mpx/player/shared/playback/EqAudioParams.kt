package com.mpx.player.shared.playback

import com.mpx.player.model.EqPresets
import com.mpx.player.model.EqSettings

/** Uma banda pronta p/ um filtro paramétrico do AVAudioUnitEQ (centro + ganho). */
data class EqBandParam(val freqHz: Float, val gainDb: Float)

/**
 * Parâmetros de EQ PRONTOS p/ configurar um `AVAudioUnitEQ` no iOS (modelo puro, sem
 * AVFoundation): 10 bandas nas [EqPresets.freqsHz] (centro em [EqBandParam.freqHz], ganho
 * em [EqBandParam.gainDb]) + [globalGainDb] (o preamp, via `AVAudioUnitEQ.globalGain`).
 */
data class EqAudioParams(
    val bands: List<EqBandParam>,
    val globalGainDb: Float,
    val enabled: Boolean,
)

/**
 * Converte o modelo comum [EqSettings] em [EqAudioParams] PRONTO p/ um `AVAudioUnitEQ`.
 * Função PURA (commonMain, sem plataforma) — testável no JVM via commonTest, sem device.
 *
 * FIDELIDADE (espelha o Android, [com.mpx.player.playback.AudioEffects]):
 * - banda i: `gainDb = levelToDb(levels[i])` (−12..+12 dB; falta → nível 6 = 0 dB);
 * - [EqSettings.bassBoost]: soma +3 dB nas 3 primeiras bandas (i<3), igual ao Android —
 *   NÃO é efeito separado, é um reforço somado ao ganho das graves (dobrado no EQ);
 * - [EqSettings.preamp]: vira [globalGainDb] = `preampToDb(preamp)` (−6..+6 dB), análogo
 *   ao `setInputGainAllChannelsTo` do Android (= `AVAudioUnitEQ.globalGain`).
 *
 * DEFERIDO (device-gated, NÃO feito aqui): aplicar estes params num `AVAudioUnitEQ` REAL
 * exige reconstruir o pipeline de áudio (AVAudioEngine + AVAudioPlayerNode) — o
 * [IosAudioPlayer] atual usa `AVQueuePlayer`, que não tem ponto de inserção de EQ. Esta
 * fatia é SÓ o mapeamento puro; nada toca o player. O long-pole do EQ-audível continua
 * diferido até haver device p/ provar que toca.
 *
 * GAP de fidelidade do iOS (decisão (i)): cobre SÓ o EQ de 10 bandas + preamp — o que o
 * `AVAudioUnitEQ` reproduz fielmente. [EqSettings.surround] (Android = `Virtualizer`) e
 * [EqSettings.loudness] (Android = `LoudnessEnhancer`) são efeitos SEPARADOS sem
 * equivalente `AVAudioUnit` 1:1 e NÃO entram em [EqAudioParams]; ficam de fora no iOS
 * (aproximá-los é fatia futura, se o usuário quiser).
 */
fun EqSettings.toAudioParams(): EqAudioParams = EqAudioParams(
    bands = EqPresets.freqsHz.mapIndexed { i, f ->
        var g = EqPresets.levelToDb(levels.getOrElse(i) { 6 }).toFloat()
        if (bassBoost && i < 3) g += 3f // espelha o Android: +3 dB nas 3 graves
        EqBandParam(freqHz = f, gainDb = g)
    },
    globalGainDb = EqPresets.preampToDb(preamp),
    enabled = enabled,
)
