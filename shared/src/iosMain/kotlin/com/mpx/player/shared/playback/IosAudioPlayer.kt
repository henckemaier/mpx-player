package com.mpx.player.shared.playback

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.outputVolume
import platform.AVFAudio.setActive
import platform.AVFoundation.AVPlayerActionAtItemEndAdvance
import platform.AVFoundation.AVPlayerActionAtItemEndNone
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.AVPlayerItemDidPlayToEndTimeNotification
import platform.AVFoundation.AVQueuePlayer
import platform.AVFoundation.actionAtItemEnd
import platform.AVFoundation.addPeriodicTimeObserverForInterval
import platform.AVFoundation.currentItem
import platform.AVFoundation.currentTime
import platform.AVFoundation.duration
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVFoundation.rate
import platform.AVFoundation.removeTimeObserver
import platform.AVFoundation.seekToTime
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMakeWithSeconds
import platform.Foundation.NSNotification
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSURL
import platform.MediaPlayer.MPMediaItemPropertyAlbumTitle
import platform.MediaPlayer.MPMediaItemPropertyArtist
import platform.MediaPlayer.MPMediaItemPropertyPlaybackDuration
import platform.MediaPlayer.MPMediaItemPropertyTitle
import platform.MediaPlayer.MPNowPlayingInfoCenter
import platform.MediaPlayer.MPNowPlayingInfoPropertyElapsedPlaybackTime
import platform.MediaPlayer.MPNowPlayingInfoPropertyPlaybackRate
import platform.MediaPlayer.MPRemoteCommandCenter
import platform.MediaPlayer.MPRemoteCommandHandlerStatusSuccess

/**
 * Player iOS sobre AVFoundation, implementando o contrato comum [AudioPlayer] com
 * paridade de comportamento com o `AndroidAudioPlayer`:
 *
 *  - [connect]/[release] idempotentes; `state.connected` só é `true` entre eles;
 *  - fila recebida antes de conectar fica pendente e é aplicada no [connect]
 *    (não é descartada em silêncio);
 *  - shuffle/repetição vivem na camada de fila (o `AVQueuePlayer` não os expõe),
 *    espelhando a lógica do Android;
 *  - `positionMs`/`durationMs` alimentados por um observador periódico de tempo;
 *  - tela de bloqueio via `MPNowPlayingInfoCenter` + `MPRemoteCommandCenter`.
 *
 * Threading: use na thread principal (AVFoundation e `MPNowPlayingInfoCenter`
 * exigem a main thread; os callbacks — observador de tempo, fim de faixa e
 * comandos remotos — já são entregues na main). Ver KDoc de [AudioPlayer].
 *
 * Volume: no iOS o volume do sistema é **somente leitura** aqui ([volume] reflete
 * `AVAudioSession.outputVolume`). [setVolume]/[toggleMute] são no-op — setar volume
 * de sistema exige `MPVolumeView` (UI do usuário), desencorajado pela Apple e sem
 * efeito no simulador. A UI deve tratar o volume como read-only no iOS.
 */
@OptIn(ExperimentalForeignApi::class)
class IosAudioPlayer : AudioPlayer {

    private val _state = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val _volume = MutableStateFlow(VolumeState(max = 100))
    override val volume: StateFlow<VolumeState> = _volume.asStateFlow()

    private var player: AVQueuePlayer? = null
    private var timeObserver: Any? = null
    private var endObserver: Any? = null

    // Modelo de fila (fonte da verdade de index/ordem): [items] na ordem original;
    // [order] é a ordem de reprodução (identidade ou embaralhada); [pos] aponta nela.
    private var items: List<PlaybackItem> = emptyList()
    private var order: List<Int> = emptyList()
    private var pos: Int = -1

    private var pending: Pending? = null // fila chegada antes de conectar

    /** Fila recebida antes de o player existir; aplicada em [connect]. */
    private class Pending(
        val items: List<PlaybackItem>,
        val startIndex: Int,
        val sourceLabel: String,
        var play: Boolean = false,
    )

    override fun connect() {
        if (player != null) return
        activateSession() // degrada graciosamente se falhar (não crasha)

        val p = AVQueuePlayer()
        player = p
        applyActionAtItemEnd(p)
        observeTime(p)
        observeItemEnd()
        setupRemoteCommands()
        _state.update { it.copy(connected = true) }
        refreshVolume()

        // Aplica fila/solicitação de play que chegaram antes de conectar.
        pending?.let { pnd ->
            loadQueue(pnd.items, pnd.startIndex, pnd.sourceLabel)
            if (pnd.play) play()
            pending = null
        }
    }

    /** Sessão de áudio .playback (toca no silencioso e em 2º plano). Falha não derruba o connect. */
    private fun activateSession() {
        val ok = runCatching {
            val s = AVAudioSession.sharedInstance()
            s.setCategory(AVAudioSessionCategoryPlayback, null)
            s.setActive(true, null)
        }.getOrElse {
            println("IosAudioPlayer: falha ao configurar AVAudioSession: $it")
            false
        }
        if (!ok) println("IosAudioPlayer: AVAudioSession não ativou; seguindo mesmo assim")
    }

    override fun setQueue(items: List<PlaybackItem>, startIndex: Int, sourceLabel: String) {
        if (items.isEmpty()) return
        // Reflete a fila no estado já (paridade com o Android), mesmo sem player.
        _state.update {
            it.copy(
                queueIds = items.map { item -> item.id },
                index = startIndex.coerceIn(0, items.lastIndex),
                sourceLabel = sourceLabel,
            )
        }
        if (player == null) {
            pending = Pending(items, startIndex, sourceLabel) // aplica no connect()
            return
        }
        loadQueue(items, startIndex, sourceLabel)
    }

    /** Carrega a fila no player SEM tocar (o play fica explícito — semântica do contrato). */
    private fun loadQueue(newItems: List<PlaybackItem>, startIndex: Int, sourceLabel: String) {
        items = newItems
        val start = startIndex.coerceIn(0, newItems.lastIndex)
        // Shuffle NÃO é resetado aqui (espelha o Android/contrato): mantém o modo atual.
        order = buildPlayOrder(newItems.size, start, _state.value.shuffle)
        pos = order.indexOf(start).coerceAtLeast(0)
        rebuildPlayerItems()
        _state.update { it.copy(sourceLabel = sourceLabel) }
        syncState()
        updateNowPlaying()
    }

    /** Recarrega os AVPlayerItem a partir de [pos] (o AVQueuePlayer não anda pra trás). */
    private fun rebuildPlayerItems() {
        val p = player ?: return
        p.removeAllItems()
        for (k in pos until order.size) {
            val uri = items.getOrNull(order[k])?.uri ?: continue
            val url = NSURL.URLWithString(uri) ?: NSURL.fileURLWithPath(uri)
            p.insertItem(AVPlayerItem(uRL = url), null) // afterItem=null → anexa ao fim
        }
    }

    override fun play() {
        val p = player
        if (p == null) {
            pending?.play = true // play() antes de conectar: toca quando a fila pendente entrar
            return
        }
        p.play()
        syncState()
        updateNowPlaying()
    }

    override fun pause() {
        player?.pause()
        syncState()
        updateNowPlaying()
    }

    override fun toggle() {
        val p = player ?: return
        if (p.rate != 0f) pause() else play()
    }

    override fun next() {
        val p = player ?: return
        val np = manualNextPos(pos, order.size, _state.value.repeat) ?: return
        if (np == pos + 1) {
            p.advanceToNextItem() // o item já está carregado logo após o atual
            pos = np
        } else {
            pos = np // wrap para 0 (repeat ALL): reconstrói e toca
            rebuildPlayerItems()
            p.play()
        }
        syncState()
        updateNowPlaying()
    }

    override fun previous() {
        val p = player ?: return
        // >3s na faixa atual: reinicia; senão, vai pra anterior (espelha o seekToPrevious
        // do Android). No começo da fila com repeat ALL, dá a volta pra última.
        when {
            currentSeconds(p) > 3.0 -> p.seekToTime(CMTimeMakeWithSeconds(0.0, TIMESCALE))
            pos > 0 -> { pos--; rebuildPlayerItems(); p.play() }
            _state.value.repeat == RepeatMode.ALL && order.isNotEmpty() -> {
                pos = order.lastIndex; rebuildPlayerItems(); p.play()
            }
            else -> p.seekToTime(CMTimeMakeWithSeconds(0.0, TIMESCALE))
        }
        syncState()
        updateNowPlaying()
    }

    override fun seekTo(positionMs: Long) {
        val p = player ?: return
        p.seekToTime(CMTimeMakeWithSeconds(positionMs.coerceAtLeast(0) / 1000.0, TIMESCALE))
        syncState()
    }

    override fun seekToFraction(fraction: Float) {
        val p = player ?: return
        val durSec = durationSeconds(p)
        if (durSec > 0) p.seekToTime(CMTimeMakeWithSeconds(durSec * fraction.coerceIn(0f, 1f), TIMESCALE))
        syncState()
    }

    override fun seekToIndex(index: Int) {
        val p = player ?: return
        val newPos = order.indexOf(index)
        if (newPos < 0) return
        pos = newPos
        rebuildPlayerItems()
        p.play() // selecionar faixa implica tocar (ação explícita do usuário)
        syncState()
        updateNowPlaying()
    }

    override fun setShuffle(enabled: Boolean) {
        _state.update { it.copy(shuffle = enabled) }
        val p = player ?: return
        if (items.isEmpty()) return
        val current = order.getOrNull(pos) ?: 0
        val resumeSec = currentSeconds(p)       // preserva a posição da faixa atual
        val wasPlaying = p.rate != 0f
        order = buildPlayOrder(items.size, current, enabled)
        // Mantém a faixa ATUAL (no shuffle ela fica no topo→0; na identidade, no índice dela);
        // pos=0 fixo pularia pra faixa 0 ao DESLIGAR o shuffle.
        pos = order.indexOf(current).coerceAtLeast(0)
        rebuildPlayerItems()
        if (resumeSec > 0) p.seekToTime(CMTimeMakeWithSeconds(resumeSec, TIMESCALE))
        if (wasPlaying) p.play()
        syncState()
    }

    /**
     * Embaralha a fila ATUAL sem alterar o flag [PlaybackState.shuffle] (ação pontual
     * "shuffle agora", distinta de ligar/desligar o modo em [setShuffle]).
     *
     * Reconstrói [order] com `buildPlayOrder(..., shuffle = true)` SEMPRE embaralhando
     * (mesmo com o flag em `false`), mantendo a faixa atual no topo e preservando
     * posição/estado de reprodução (`resumeSec`/`wasPlaying`). No-op se a fila vazia.
     * Paridade com o Android.
     */
    override fun shuffleQueue() {
        val p = player ?: return
        if (items.size < 2) return // paridade com o Android: fila de 0/1 faixa não reembaralha (evita blip)
        val current = order.getOrNull(pos) ?: 0  // índice ORIGINAL da faixa atual
        val resumeSec = currentSeconds(p)         // preserva a posição da faixa atual
        val wasPlaying = p.rate != 0f
        // SEMPRE embaralha (independe do flag shuffle); a faixa atual vai pro topo (→0).
        order = buildPlayOrder(items.size, current, shuffle = true)
        pos = order.indexOf(current).coerceAtLeast(0)
        rebuildPlayerItems()
        if (resumeSec > 0) p.seekToTime(CMTimeMakeWithSeconds(resumeSec, TIMESCALE))
        if (wasPlaying) p.play()
        syncState()
        updateNowPlaying()
        // NÃO altera _state.shuffle (é o diferencial em relação a setShuffle).
    }

    /**
     * Esvazia a fila: pausa, remove todos os itens do player, zera o modelo
     * (`items`/`order`/`pos=-1`) e o pendente, reflete a fila vazia em [state]
     * (`queueIds=[]`, `index=-1`, `isPlaying=false`) e limpa o NowPlaying da tela
     * de bloqueio. No-op coerente se já vazia. Paridade com o Android.
     */
    override fun clearQueue() {
        player?.let { p ->
            p.pause()
            p.removeAllItems()
        }
        items = emptyList()
        order = emptyList()
        pos = -1
        pending = null
        _state.update { it.copy(queueIds = emptyList(), index = -1, isPlaying = false) }
        MPNowPlayingInfoCenter.defaultCenter().nowPlayingInfo = null
    }

    override fun setRepeat(mode: RepeatMode) {
        _state.update { it.copy(repeat = mode) }
        player?.let { applyActionAtItemEnd(it) }
    }

    override fun setVolume(fraction: Float) {
        // No-op: iOS não expõe set de volume de sistema (só MPVolumeView). Ver KDoc da classe.
        refreshVolume()
    }

    override fun toggleMute() {
        // No-op: sem mute de sistema programático no iOS. Ver KDoc da classe.
        refreshVolume()
    }

    private fun refreshVolume() {
        val v = AVAudioSession.sharedInstance().outputVolume
        val level = (v * 100).toInt().coerceIn(0, 100)
        _volume.update { it.copy(level = level, max = 100, muted = level == 0) }
    }

    override fun release() {
        // Ordem importa p/ não vazar: observer de tempo ANTES de soltar o player.
        player?.let { p ->
            timeObserver?.let { p.removeTimeObserver(it) }
            p.pause()
            p.removeAllItems()
        }
        timeObserver = null
        endObserver?.let { NSNotificationCenter.defaultCenter.removeObserver(it) }
        endObserver = null
        teardownRemoteCommands()
        runCatching { AVAudioSession.sharedInstance().setActive(false, null) }
        player = null
        items = emptyList(); order = emptyList(); pos = -1
        pending = null
        _state.update { it.copy(connected = false, isPlaying = false) }
    }

    // --- Observadores ---

    private fun observeTime(p: AVQueuePlayer) {
        // ~0.1s (na main queue): alimenta positionMs e espelha isPlaying/duration/volume.
        val interval = CMTimeMakeWithSeconds(0.1, TIMESCALE)
        timeObserver = p.addPeriodicTimeObserverForInterval(interval, null) { _ ->
            syncState()
            refreshVolume()
        }
    }

    private fun observeItemEnd() {
        endObserver = NSNotificationCenter.defaultCenter.addObserverForName(
            name = AVPlayerItemDidPlayToEndTimeNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
        ) { _: NSNotification? -> onItemEnd() }
    }

    /** Fim de faixa: repeat ONE reinicia; senão avança o índice e trata o fim da fila. */
    private fun onItemEnd() {
        val p = player ?: return
        val repeat = _state.value.repeat
        if (repeat == RepeatMode.ONE) {
            // actionAtItemEnd=None impede o avanço; reiniciamos a mesma faixa.
            p.seekToTime(CMTimeMakeWithSeconds(0.0, TIMESCALE))
            p.play()
            syncState(); updateNowPlaying()
            return
        }
        val wasLast = pos >= order.lastIndex
        val np = endAdvancePos(pos, order.size, repeat)
        when {
            np == null -> { /* fim da fila sem repeat: fica parado no último */ }
            wasLast -> { pos = np; rebuildPlayerItems(); p.play() } // wrap (repeat ALL): fila esvaziou
            else -> pos = np // meio da fila: o AVQueuePlayer já avançou sozinho
        }
        syncState()
        updateNowPlaying()
    }

    private fun applyActionAtItemEnd(p: AVQueuePlayer) {
        p.actionAtItemEnd =
            if (_state.value.repeat == RepeatMode.ONE) AVPlayerActionAtItemEndNone
            else AVPlayerActionAtItemEndAdvance
    }

    // --- Tela de bloqueio ---

    private fun setupRemoteCommands() {
        val cc = MPRemoteCommandCenter.sharedCommandCenter()
        cc.playCommand.addTargetWithHandler { play(); MPRemoteCommandHandlerStatusSuccess }
        cc.pauseCommand.addTargetWithHandler { pause(); MPRemoteCommandHandlerStatusSuccess }
        cc.togglePlayPauseCommand.addTargetWithHandler { toggle(); MPRemoteCommandHandlerStatusSuccess }
        cc.nextTrackCommand.addTargetWithHandler { next(); MPRemoteCommandHandlerStatusSuccess }
        cc.previousTrackCommand.addTargetWithHandler { previous(); MPRemoteCommandHandlerStatusSuccess }
    }

    private fun teardownRemoteCommands() {
        val cc = MPRemoteCommandCenter.sharedCommandCenter()
        cc.playCommand.removeTarget(null)
        cc.pauseCommand.removeTarget(null)
        cc.togglePlayPauseCommand.removeTarget(null)
        cc.nextTrackCommand.removeTarget(null)
        cc.previousTrackCommand.removeTarget(null)
        MPNowPlayingInfoCenter.defaultCenter().nowPlayingInfo = null
    }

    // TODO(iOS): capa na tela de bloqueio — MPMediaItemPropertyArtwork a partir de
    // PlaybackItem.artworkUri (carregar UIImage async → MPMediaItemArtwork). Passo seguinte.
    private fun updateNowPlaying() {
        val p = player ?: return
        val item = items.getOrNull(order.getOrNull(pos) ?: -1) ?: return
        val info = mutableMapOf<Any?, Any?>(
            MPMediaItemPropertyTitle to item.title,
            MPMediaItemPropertyArtist to item.artist,
            MPMediaItemPropertyAlbumTitle to item.album,
            MPMediaItemPropertyPlaybackDuration to durationSeconds(p),
            MPNowPlayingInfoPropertyElapsedPlaybackTime to currentSeconds(p),
            MPNowPlayingInfoPropertyPlaybackRate to p.rate,
        )
        MPNowPlayingInfoCenter.defaultCenter().nowPlayingInfo = info
    }

    // --- Estado/tempo ---

    private fun syncState() {
        val p = player ?: return
        _state.update {
            it.copy(
                index = order.getOrNull(pos) ?: -1,
                isPlaying = p.rate != 0f,
                positionMs = secondsToMs(currentSeconds(p)),
                durationMs = secondsToMs(durationSeconds(p)),
            )
        }
    }

    private fun currentSeconds(p: AVQueuePlayer): Double = safeSeconds(CMTimeGetSeconds(p.currentTime()))

    private fun durationSeconds(p: AVQueuePlayer): Double {
        val d = p.currentItem?.duration ?: return 0.0
        return safeSeconds(CMTimeGetSeconds(d))
    }

    private companion object {
        const val TIMESCALE = 1000 // milissegundos
    }
}

// --- Lógica pura da fila (sem AVFoundation) — testável no iosSimulatorArm64Test ---

/** Ordem de reprodução: identidade, ou embaralhada com [start] no topo. */
internal fun buildPlayOrder(size: Int, start: Int, shuffle: Boolean): List<Int> {
    if (size <= 0) return emptyList()
    val s = start.coerceIn(0, size - 1)
    if (!shuffle) return (0 until size).toList()
    val rest = (0 until size).filter { it != s }.shuffled()
    return listOf(s) + rest
}

/** Próximo [pos] no `next()` MANUAL (ONE conta como OFF). `null` = no-op (fim sem repeat). */
internal fun manualNextPos(pos: Int, size: Int, repeat: RepeatMode): Int? = when {
    size <= 0 -> null
    pos < size - 1 -> pos + 1
    repeat == RepeatMode.ALL -> 0
    else -> null
}

/** Próximo [pos] no FIM automático de uma faixa. ONE reinicia (mesmo pos); `null` = parar. */
internal fun endAdvancePos(pos: Int, size: Int, repeat: RepeatMode): Int? = when {
    size <= 0 -> null
    repeat == RepeatMode.ONE -> pos
    pos < size - 1 -> pos + 1
    repeat == RepeatMode.ALL -> 0
    else -> null
}

internal fun safeSeconds(s: Double): Double = if (s.isNaN() || s.isInfinite() || s < 0) 0.0 else s
internal fun secondsToMs(s: Double): Long = (safeSeconds(s) * 1000).toLong()
