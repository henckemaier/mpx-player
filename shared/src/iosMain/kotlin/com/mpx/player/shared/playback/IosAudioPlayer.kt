package com.mpx.player.shared.playback

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.convert
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.setActive
import platform.AVFoundation.AVMetadataCommonKeyArtwork
import platform.AVFoundation.AVMetadataItem
import platform.AVFoundation.AVPlayerActionAtItemEndAdvance
import platform.AVFoundation.AVPlayerActionAtItemEndNone
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.AVPlayerItemDidPlayToEndTimeNotification
import platform.AVFoundation.AVQueuePlayer
import platform.AVFoundation.AVURLAsset
import platform.AVFoundation.actionAtItemEnd
import platform.AVFoundation.addPeriodicTimeObserverForInterval
import platform.AVFoundation.commonKey
import platform.AVFoundation.commonMetadata
import platform.AVFoundation.currentItem
import platform.AVFoundation.currentTime
import platform.AVFoundation.dataValue
import platform.AVFoundation.duration
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVFoundation.rate
import platform.AVFoundation.removeTimeObserver
import platform.AVFoundation.seekToTime
import platform.AVFoundation.setVolume
import platform.AVFoundation.volume
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMakeWithSeconds
import platform.Foundation.NSNotification
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSURL
import platform.MediaPlayer.MPMediaItemArtwork
import platform.MediaPlayer.MPMediaItemPropertyAlbumTitle
import platform.MediaPlayer.MPMediaItemPropertyArtist
import platform.MediaPlayer.MPMediaItemPropertyArtwork
import platform.MediaPlayer.MPMediaItemPropertyPlaybackDuration
import platform.MediaPlayer.MPMediaItemPropertyTitle
import platform.MediaPlayer.MPNowPlayingInfoCenter
import platform.MediaPlayer.MPNowPlayingInfoPropertyElapsedPlaybackTime
import platform.MediaPlayer.MPNowPlayingInfoPropertyPlaybackRate
import platform.MediaPlayer.MPRemoteCommandCenter
import platform.MediaPlayer.MPRemoteCommandHandlerStatusSuccess
import platform.UIKit.UIImage
import platform.darwin.DISPATCH_QUEUE_PRIORITY_DEFAULT
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_global_queue
import platform.darwin.dispatch_get_main_queue

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
 *  - tela de bloqueio via `MPNowPlayingInfoCenter` + `MPRemoteCommandCenter`, incluindo
 *    a CAPA (`MPMediaItemPropertyArtwork`): carregada de forma assíncrona a partir de
 *    `PlaybackItem.artworkUri` (capa custom em arquivo ou arte embutida do áudio) e
 *    mesclada no NowPlaying com anti-race (ver [loadArtworkAsync]).
 *
 * LIMITE de validação (device-TODO): VER a capa no lock screen/Control Center real
 * depende de device — o NowPlaying do simulador headless é não-confiável p/ screenshot.
 * O CI valida compilação/link do `iosMain` + a lógica pura ([iosArtworkUri]/
 * [shouldApplyArtwork]) em `iosTest`.
 *
 * Threading: use na thread principal (AVFoundation e `MPNowPlayingInfoCenter`
 * exigem a main thread; os callbacks — observador de tempo, fim de faixa e
 * comandos remotos — já são entregues na main). Ver KDoc de [AudioPlayer].
 *
 * Volume (decisão [D1=1a]): o [volume] aqui é o **GANHO DE PLAYBACK DO APP** —
 * `AVPlayer.volume` (0..1 do `AVQueuePlayer`), que é **settável e audível** (no device).
 * [setVolume] aplica o ganho; [toggleMute] zera/restaura o último ganho não-zero; a fonte
 * da verdade de [refreshVolume] é `player.volume` (NÃO `AVAudioSession.outputVolume`). Isto
 * NÃO é o volume de SISTEMA: o iOS não expõe set de volume de sistema por código (só a UI do
 * `MPVolumeView`), então diverge do `AndroidAudioPlayer` (stream de sistema) por decisão.
 * LIMITE: o CI prova lógica ([volumeLevel]/[applyMuteToggle]) + compile/link do K/N; o efeito
 * AUDÍVEL só se vê no device (simulador headless não reproduz áudio audível).
 */
@OptIn(ExperimentalForeignApi::class)
class IosAudioPlayer : AudioPlayer {

    private val _state = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    // Ganho inicial = 100 (coerente com o AVPlayer, que nasce volume=1.0): a VolumeRow não mostra
    // 0 e depois salta. Reaplicado ao player no connect() (ver lá) p/ preservar um alvo pré-conexão.
    private val _volume = MutableStateFlow(VolumeState(level = 100, max = 100))
    override val volume: StateFlow<VolumeState> = _volume.asStateFlow()

    // Último ganho não-zero, p/ o mute/unmute (estado puro consumido por [applyMuteToggle]).
    private var lastNonZeroVolume: Float = 1f

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
        // O AVQueuePlayer nasce com volume=1.0; reaplica o alvo que o _volume já carrega (default
        // 100 → 1.0, no-op; ou um setVolume/mute feito ANTES de conectar) p/ não dar salto. Em
        // seguida o refreshVolume() reflete exatamente esse ganho.
        p.setVolume(_volume.value.level / 100f)
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
        // debt1: [index] é a posição na ORDEM DE REPRODUÇÃO (= pos), o mesmo espaço do
        // queueIds exposto e do Android (c.seekTo(index, 0)). Antes era o índice ORIGINAL
        // (order.indexOf(index)) — ficaria inconsistente com o novo queueIds/index.
        if (index !in order.indices) return
        pos = index
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

    /**
     * Esvazia a fila MANTENDO a faixa atual TOCANDO (não reinicia, não para): remove
     * do [AVQueuePlayer] todos os itens à frente do atual e colapsa o modelo para só a
     * faixa atual (`items=[atual]`, `order=[0]`, `pos=0`). No-op se a fila tem ≤1 item.
     *
     * NÃO chama [rebuildPlayerItems]: reinserir o item atual o recriaria e reiniciaria a
     * reprodução. O `AVQueuePlayer` guarda `[atual, ...próximos]`, então basta remover
     * todos menos o primeiro. Paridade com o `AndroidAudioPlayer` (remove a cauda sem
     * recriar/reiniciar a faixa atual).
     */
    override fun clearUpcoming() {
        val p = player ?: return
        if (items.size <= 1) return
        // Índice ORIGINAL da faixa atual → o PlaybackItem que será mantido.
        val current = items.getOrNull(order.getOrNull(pos) ?: -1) ?: return
        // Remove do player tudo à frente do atual (índice 0 = currentItem), sem reconstruir.
        for (item in p.items().drop(1)) {
            (item as? AVPlayerItem)?.let { p.removeItem(it) }
        }
        // Colapsa o modelo: só a faixa atual permanece (pos=0).
        items = listOf(current)
        order = listOf(0)
        pos = 0
        syncState()       // debt1: emite index=0 e queueIds=[id da atual]; isPlaying preservado
        updateNowPlaying()
    }

    override fun setRepeat(mode: RepeatMode) {
        _state.update { it.copy(repeat = mode) }
        player?.let { applyActionAtItemEnd(it) }
    }

    override fun setVolume(fraction: Float) {
        // Ganho de playback do app (AVPlayer.volume). Com player null, guarda só o alvo no _volume;
        // o connect() o reaplica. [lastNonZeroVolume] NÃO é tocado aqui: o mute captura o ganho
        // corrente no momento do toggle.
        val f = fraction.coerceIn(0f, 1f)
        player?.setVolume(f)
        _volume.update { it.copy(level = volumeLevel(f), max = 100, muted = f == 0f) }
    }

    override fun toggleMute() {
        // Mute = zera o ganho guardando o último não-zero; unmute = restaura. Fonte do ganho
        // corrente: o player (ou o _volume, se ainda não conectou). Lógica pura em [applyMuteToggle].
        val cur = player?.volume ?: (_volume.value.level / 100f)
        val (nv, nlz) = applyMuteToggle(cur, lastNonZeroVolume)
        lastNonZeroVolume = nlz
        player?.setVolume(nv)
        _volume.update { it.copy(level = volumeLevel(nv), max = 100, muted = nv == 0f) }
    }

    /** Re-sincroniza o _volume com o GANHO DO APP (player.volume). Sem player, mantém o alvo atual. */
    private fun refreshVolume() {
        val v = player?.volume ?: return
        _volume.update { it.copy(level = volumeLevel(v), max = 100, muted = v == 0f) }
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
        // Publica os campos textuais imediatamente (a capa chega depois, assíncrona).
        MPNowPlayingInfoCenter.defaultCenter().nowPlayingInfo = info
        loadArtworkAsync(item)
    }

    /**
     * Carrega a capa de [item] FORA da main (AVFoundation/UIImage podem bloquear) e, na
     * MAIN, MESCLA [MPMediaItemPropertyArtwork] no dicionário atual do NowPlaying — sem
     * derrubar os campos textuais. Anti-race: captura o id da faixa no início e só aplica
     * se ela ainda for a atual ao concluir (capa velha não sobrescreve a nova). Falha de
     * load degrada graciosamente (fica sem capa).
     */
    private fun loadArtworkAsync(item: PlaybackItem) {
        val uri = item.artworkUri ?: return
        val startedId = item.id // capturado p/ o anti-race
        dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT.convert(), 0.convert())) {
            val image = loadArtwork(uri) ?: return@dispatch_async
            dispatch_async(dispatch_get_main_queue()) {
                val currentId = items.getOrNull(order.getOrNull(pos) ?: -1)?.id
                if (!shouldApplyArtwork(startedId, currentId)) return@dispatch_async // faixa trocou: descarta
                val center = MPNowPlayingInfoCenter.defaultCenter()
                // Mescla no dicionário MAIS RECENTE (não num snapshot velho): preserva os
                // campos textuais/tempo já publicados por chamadas posteriores.
                val merged = HashMap<Any?, Any?>()
                center.nowPlayingInfo?.let { merged.putAll(it) }
                merged[MPMediaItemPropertyArtwork] = MPMediaItemArtwork(boundsSize = image.size) { _ -> image }
                center.nowPlayingInfo = merged
            }
        }
    }

    /**
     * Resolve o [artworkUri] numa [UIImage]: primeiro como arquivo de imagem (capa custom
     * `coverOverride`); senão, extrai a arte EMBUTIDA do asset de áudio
     * (`AVURLAsset.commonMetadata`, chave `artwork`). `null` = sem capa carregável.
     */
    private fun loadArtwork(artworkUri: String): UIImage? {
        UIImage(contentsOfFile = artworkUri)?.let { return it }
        return extractEmbeddedArtwork(artworkUri)
    }

    private fun extractEmbeddedArtwork(uri: String): UIImage? {
        val url = NSURL.URLWithString(uri) ?: NSURL.fileURLWithPath(uri)
        val asset = AVURLAsset(uRL = url, options = null)
        for (entry in asset.commonMetadata.orEmpty()) {
            val meta = entry as? AVMetadataItem ?: continue
            if (meta.commonKey == AVMetadataCommonKeyArtwork) {
                val data = meta.dataValue ?: continue
                UIImage(data = data)?.let { return it }
            }
        }
        return null
    }

    // --- Estado/tempo ---

    private fun syncState() {
        val p = player ?: return
        _state.update {
            it.copy(
                // debt1: queueIds/index na ORDEM DE REPRODUÇÃO (alinha com o Android, que
                // expõe c.getMediaItemAt(i).mediaId + currentMediaItemIndex). [pos] é a
                // posição na [order], então queueIds[index] == faixa atual (currentId ok).
                queueIds = queueIdsInPlayOrder(items, order),
                index = pos,
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

/**
 * IDs da fila na ORDEM DE REPRODUÇÃO (debt1 — alinha o iOS ao Android, que expõe o
 * `queueIds` na ordem do player). [order] mapeia posição de reprodução → índice
 * original em [items]; o resultado é a lista de ids já reordenada. Entradas fora de
 * faixa são ignoradas (robustez), então o tamanho acompanha a [order] válida.
 */
internal fun queueIdsInPlayOrder(items: List<PlaybackItem>, order: List<Int>): List<String> =
    order.mapNotNull { items.getOrNull(it)?.id }

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

// --- Lógica pura do volume (sem AVFoundation) — testável no iosSimulatorArm64Test ---

/** Fração de ganho 0..1 → nível 0..100 da [VolumeState] (clamp nas duas pontas). */
internal fun volumeLevel(fraction: Float): Int = (fraction.coerceIn(0f, 1f) * 100).toInt().coerceIn(0, 100)

/**
 * Transição do mute/unmute: dado o ganho [current] e o último não-zero [lastNonZero], devolve
 * `(novoGanho, novoUltimoNaoZero)`. Se [current] > 0 → MUTA (ganho 0, guardando [current]); se
 * [current] == 0 → DESMUTA (restaura [lastNonZero], ou 1f se não há um válido; [lastNonZero] intacto).
 */
internal fun applyMuteToggle(current: Float, lastNonZero: Float): Pair<Float, Float> =
    if (current > 0f) 0f to current
    else (if (lastNonZero > 0f) lastNonZero else 1f) to lastNonZero
