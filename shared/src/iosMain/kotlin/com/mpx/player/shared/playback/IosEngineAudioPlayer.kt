package com.mpx.player.shared.playback

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.convert
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import platform.AVFAudio.AVAudioEngine
import platform.AVFAudio.AVAudioFile
import platform.AVFAudio.AVAudioPlayerNode
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.sampleRate
import platform.AVFAudio.setActive
import platform.AVFAudio.startAndReturnError
import platform.AVFAudio.stop
import platform.Foundation.NSURL
// NOTA K/N: os membros do grafo AVAudioEngine/AVAudioPlayerNode/AVAudioFile são MEMBROS DIRETOS
// das @interface (framework AVFAudio) — chamam-se na instância SEM import (attachNode/connect/
// prepare/play/pause/scheduleFile/scheduleSegment/volume/mainMixerNode/lastRenderTime/
// playerTimeForNodeTime/length/processingFormat). Diferente de AVPlayer.volume/setVolume
// (AVFoundation), que são de CATEGORIA → extensions e PRECISAM de import (ver kn-avfoundation-interop).

/**
 * Backend iOS **ALTERNATIVO** sobre `AVAudioEngine` + `AVAudioPlayerNode`, implementando o
 * mesmo contrato [AudioPlayer]. É selecionado por um flag de LAUNCH-TIME (`USE_ENGINE_BACKEND`
 * no `IosAppContainer`), que nasce **OFF**: em produção o player é o [IosAudioPlayer]
 * (`AVQueuePlayer`), INTOCADO. Este backend existe para abrir o caminho do grafo de áudio
 * (pré-requisito do EQ-audível com `AVAudioUnitEQ`), começando por **tocar 1 FAIXA**.
 *
 * ## Escopo desta fatia (engine-flag SUB-1) — FUNCIONAL
 * [connect]/[release] (monta/derruba o grafo: engine + playerNode → mainMixer), [setQueue]
 * (reduzido a 1 item — o de `startIndex`), [play]/[pause]/[toggle], [seekTo]/[seekToFraction]
 * e [setVolume]/[toggleMute] (ganho do nó, `AVAudioPlayerNode.volume` 0..1 — mesma decisão
 * [D1=1a] do [IosAudioPlayer]; reusa os puros [volumeLevel]/[applyMuteToggle]).
 *
 * ## Marcado "// sub-2" (fila/navegação) — NÃO roda em produção (flag OFF)
 * [next]/[previous]/[seekToIndex]/[setShuffle]/[setRepeat]/[shuffleQueue]/[clearUpcoming]
 * são no-op/single-item coerentes nesta fatia. [clearQueue] para e limpa (coerente). A fila
 * real (múltiplos `scheduleSegment` encadeados, next/prev, shuffle, repeat) é a SUB-2, quando
 * o grafo já tiver provado que compila/linka no K/N.
 *
 * ## LIMITE de validação (device-gated)
 * O CI (job iOS no macOS) prova **COMPILE + LINK** do grafo `AVAudioEngine`/`AVAudioPlayerNode`/
 * `AVAudioFile` no K/N + a MATEMÁTICA pura ([secondsToFrames]/[framesToSeconds]/
 * [fractionToSeconds]/[positionFraction]) no `iosSimulatorArm64Test`. Ele **NÃO** prova o ÁUDIO:
 * tocar/seek/decodificar (FLAC etc.) é device-gated (não há Mac/iPhone com áudio aqui). Com o
 * flag OFF, nada disto entra em produção — o `AVQueuePlayer` ([IosAudioPlayer]) segue no ar.
 *
 * Threading: use na thread principal (AVFoundation exige a main). [state]/[volume] podem ser
 * observados de qualquer thread.
 *
 * Posição/duração: sem observador periódico (o `AVAudioEngine` não expõe um como o
 * `AVPlayer.addPeriodicTimeObserverForInterval`); [syncState] recalcula sob demanda a cada
 * mutador. O "tick" contínuo (p/ a barra de progresso correr sozinha) é refinamento de SUB-2+
 * (precisa de um timer de dispatch) e é device-gated de qualquer modo.
 */
@OptIn(ExperimentalForeignApi::class)
class IosEngineAudioPlayer : AudioPlayer {

    private val _state = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    // Paridade com a fatia de volume: ganho inicial 100 (o nó nasce 1.0), reaplicado no connect().
    private val _volume = MutableStateFlow(VolumeState(level = 100, max = 100))
    override val volume: StateFlow<VolumeState> = _volume.asStateFlow()

    private var lastNonZeroVolume: Float = 1f

    private var engine: AVAudioEngine? = null
    private var playerNode: AVAudioPlayerNode? = null
    private var file: AVAudioFile? = null

    // Frame onde o segmento atual foi agendado (base p/ a posição: o sampleTime do nó zera a cada
    // (re)schedule, então a posição absoluta = sampleTime + seekBaseFrames).
    private var seekBaseFrames: Long = 0L
    private var sampleRate: Double = 0.0
    private var durationSec: Double = 0.0
    private var totalFrames: Long = 0L

    // Item/solicitação de play chegados antes de o grafo existir (aplicados no connect()).
    private var pendingItem: PlaybackItem? = null
    private var pendingPlay: Boolean = false

    override fun connect() {
        if (engine != null) return
        activateSession() // degrada graciosamente se falhar (não crasha)

        val e = AVAudioEngine()
        val node = AVAudioPlayerNode()
        e.attachNode(node)
        // format=null: usa o formato de saída do nó. Casar com o processingFormat do arquivo é
        // refinamento de áudio (device-gated) — aqui o foco é montar/linkar o grafo.
        e.connect(node, to = e.mainMixerNode, format = null)
        e.prepare()
        val started = runCatching { e.startAndReturnError(null) }.getOrDefault(false)
        if (!started) println("IosEngineAudioPlayer: AVAudioEngine não iniciou; seguindo mesmo assim")

        engine = e
        playerNode = node
        node.volume = _volume.value.level / 100f // reaplica alvo pré-conexão (no-op no default 100)
        _state.update { it.copy(connected = true) }

        pendingItem?.let { pnd ->
            loadSingle(pnd)
            if (pendingPlay) play()
            pendingItem = null
        }
    }

    /** Sessão de áudio .playback (molde do [IosAudioPlayer]). Falha não derruba o connect. */
    private fun activateSession() {
        runCatching {
            val s = AVAudioSession.sharedInstance()
            s.setCategory(AVAudioSessionCategoryPlayback, null)
            s.setActive(true, null)
        }.onFailure { println("IosEngineAudioPlayer: AVAudioSession não ativou: $it") }
    }

    override fun setQueue(items: List<PlaybackItem>, startIndex: Int, sourceLabel: String) {
        if (items.isEmpty()) return
        // SUB-1: só a faixa de startIndex entra (fila completa = sub-2). Reflete no estado já.
        val item = items[startIndex.coerceIn(0, items.lastIndex)]
        _state.update {
            it.copy(queueIds = listOf(item.id), index = 0, sourceLabel = sourceLabel)
        }
        if (playerNode == null) {
            pendingItem = item // aplica no connect()
            return
        }
        loadSingle(item)
    }

    /** Abre o arquivo da faixa e agenda no nó SEM tocar (play fica explícito — semântica do contrato). */
    private fun loadSingle(item: PlaybackItem) {
        val node = playerNode ?: return
        val url = NSURL.URLWithString(item.uri) ?: NSURL.fileURLWithPath(item.uri)
        val f = runCatching { AVAudioFile(forReading = url, error = null) }.getOrNull()
        if (f == null) {
            println("IosEngineAudioPlayer: falha ao abrir AVAudioFile de ${item.uri}")
            return
        }
        file = f
        sampleRate = f.processingFormat.sampleRate
        totalFrames = f.length
        durationSec = framesToSeconds(totalFrames, sampleRate)
        seekBaseFrames = 0L
        node.stop() // descarta qualquer agendamento anterior antes de reagendar
        node.scheduleFile(f, atTime = null, completionHandler = null)
        _state.update {
            it.copy(
                queueIds = listOf(item.id),
                index = 0,
                isPlaying = false,
                positionMs = 0L,
                durationMs = secondsToMs(durationSec),
            )
        }
    }

    override fun play() {
        val node = playerNode
        if (node == null || file == null) {
            pendingPlay = true // toca quando o grafo/arquivo entrar
            return
        }
        node.play()
        _state.update { it.copy(isPlaying = true) }
        syncState()
    }

    override fun pause() {
        playerNode?.pause()
        _state.update { it.copy(isPlaying = false) }
        syncState()
    }

    override fun toggle() {
        if (_state.value.isPlaying) pause() else play()
    }

    override fun seekTo(positionMs: Long) {
        seekToSeconds(positionMs.coerceAtLeast(0) / 1000.0)
    }

    override fun seekToFraction(fraction: Float) {
        seekToSeconds(fractionToSeconds(fraction, durationSec))
    }

    /** Reagenda o segmento a partir do frame alvo (o nó não "busca": para, reagenda do ponto e retoma). */
    private fun seekToSeconds(targetSec: Double) {
        val node = playerNode ?: return
        val f = file ?: return
        val startFrame = secondsToFrames(targetSec.coerceIn(0.0, durationSec), sampleRate)
            .coerceIn(0L, totalFrames)
        val remaining = totalFrames - startFrame
        if (remaining <= 0L) return
        val wasPlaying = _state.value.isPlaying
        node.stop()
        node.scheduleSegment(
            f,
            startingFrame = startFrame,
            frameCount = remaining.convert(),
            atTime = null,
            completionHandler = null,
        )
        seekBaseFrames = startFrame
        if (wasPlaying) node.play()
        syncState()
    }

    override fun setVolume(fraction: Float) {
        val fr = fraction.coerceIn(0f, 1f)
        // AVAudioPlayerNode.volume é @property direta → atribuição (K/N expõe como var; não há
        // setVolume de categoria como no AVPlayer). Safe-call-assign não existe → via let.
        playerNode?.let { it.volume = fr }
        _volume.update { it.copy(level = volumeLevel(fr), max = 100, muted = fr == 0f) }
    }

    override fun toggleMute() {
        val cur = playerNode?.volume ?: (_volume.value.level / 100f)
        val (nv, nlz) = applyMuteToggle(cur, lastNonZeroVolume)
        lastNonZeroVolume = nlz
        playerNode?.let { it.volume = nv }
        _volume.update { it.copy(level = volumeLevel(nv), max = 100, muted = nv == 0f) }
    }

    // --- Fila/navegação: SUB-2 (flag OFF nunca as expõe em produção) ---

    /** sub-2: navegação de fila (só 1 item nesta fatia). */
    override fun next() { /* sub-2 */ }

    /** sub-2: navegação de fila (só 1 item nesta fatia). */
    override fun previous() { /* sub-2 */ }

    /** sub-2: seleção por índice numa fila de >1 item. */
    override fun seekToIndex(index: Int) { /* sub-2 */ }

    /** sub-2: shuffle precisa da fila completa (reusará buildShuffleAll/buildPlayOrder). */
    override fun setShuffle(enabled: Boolean) {
        _state.update { it.copy(shuffle = enabled) } // reflete o flag; reordenação real = sub-2
    }

    /** sub-2: repeat precisa do encadeamento de faixas (completionHandler por segmento). */
    override fun setRepeat(mode: RepeatMode) {
        _state.update { it.copy(repeat = mode) } // reflete o modo; efeito no fim de faixa = sub-2
    }

    /** sub-2: reembaralhar exige a fila completa. */
    override fun shuffleQueue() { /* sub-2 */ }

    /** sub-2: não há "próximos" numa fila de 1 item. */
    override fun clearUpcoming() { /* sub-2 */ }

    override fun clearQueue() {
        playerNode?.stop()
        file = null
        sampleRate = 0.0
        durationSec = 0.0
        totalFrames = 0L
        seekBaseFrames = 0L
        pendingItem = null
        pendingPlay = false
        _state.update { it.copy(queueIds = emptyList(), index = -1, isPlaying = false, positionMs = 0L) }
    }

    override fun release() {
        playerNode?.stop()
        engine?.stop()
        playerNode = null
        engine = null
        file = null
        sampleRate = 0.0
        durationSec = 0.0
        totalFrames = 0L
        seekBaseFrames = 0L
        pendingItem = null
        pendingPlay = false
        runCatching { AVAudioSession.sharedInstance().setActive(false, null) }
        _state.update { it.copy(connected = false, isPlaying = false) }
    }

    /** Recalcula posição/duração no [state] sob demanda (sem observador periódico — ver KDoc da classe). */
    private fun syncState() {
        _state.update {
            it.copy(
                positionMs = secondsToMs(currentPositionSeconds()),
                durationMs = secondsToMs(durationSec),
            )
        }
    }

    /**
     * Posição absoluta em segundos: o `sampleTime` do nó zera a cada (re)schedule, então somamos
     * [seekBaseFrames]. `playerTimeForNodeTime(lastRenderTime)` pode ser null antes de tocar → base.
     */
    private fun currentPositionSeconds(): Double {
        val node = playerNode ?: return 0.0
        if (sampleRate <= 0.0) return 0.0
        val nodeTime = node.lastRenderTime ?: return framesToSeconds(seekBaseFrames, sampleRate)
        val playerTime = node.playerTimeForNodeTime(nodeTime) ?: return framesToSeconds(seekBaseFrames, sampleRate)
        return framesToSeconds(playerTime.sampleTime + seekBaseFrames, sampleRate)
    }
}

// --- Lógica pura de frame/tempo (sem AVFoundation) — testável no iosSimulatorArm64Test ---
// (reusa volumeLevel/applyMuteToggle e secondsToMs de IosAudioPlayer.kt — mesmo módulo/pacote.)

/** Segundos → frames (floor). Não-positivos ou sampleRate inválida → 0. */
internal fun secondsToFrames(sec: Double, sampleRate: Double): Long =
    if (sec <= 0.0 || sampleRate <= 0.0) 0L else (sec * sampleRate).toLong()

/** Frames → segundos. sampleRate inválida → 0. */
internal fun framesToSeconds(frames: Long, sampleRate: Double): Double =
    if (sampleRate > 0.0) frames / sampleRate else 0.0

/** fraction 0..1 × duração → segundos (clamp em 0..dur, nunca negativo). */
internal fun fractionToSeconds(frac: Float, durSec: Double): Double =
    (frac.coerceIn(0f, 1f) * durSec).coerceAtLeast(0.0)

/** Posição/duração → fração 0..1 (duração ≤0 → 0). */
internal fun positionFraction(posSec: Double, durSec: Double): Float =
    if (durSec > 0.0) (posSec / durSec).toFloat().coerceIn(0f, 1f) else 0f
