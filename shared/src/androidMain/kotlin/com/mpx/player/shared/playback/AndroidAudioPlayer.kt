package com.mpx.player.shared.playback

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Implementação Android do contrato comum [AudioPlayer]: adapta a lógica Media3
 * do `PlayerController` do `:app` (MediaController conectado ao MediaSessionService)
 * para operar sobre [PlaybackItem]/[PlaybackState] neutros de plataforma.
 *
 * TODO(passo 6/7): ao migrar UI/ViewModels para o `commonMain`, unificar com o
 * `com.mpx.player.playback.PlayerController` do `:app` (hoje duplicado de
 * propósito — o `:app` segue usando o seu enquanto a migração não chega).
 */
internal class AndroidAudioPlayer(context: Context) : AudioPlayer {

    // applicationContext: evita segurar Activity/Service e vazar.
    private val appContext = context.applicationContext
    private val audio = appContext.getSystemService(AudioManager::class.java)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var controller: MediaController? = null
    private var connecting: Job? = null
    private var ticker: Job? = null
    private var volumeTicker: Job? = null // reflete mudanças externas do volume do sistema
    private var pending: Pending? = null // fila chegada antes de conectar

    /** Fila recebida antes de o controller conectar; aplicada em [connect]. */
    private class Pending(
        val items: List<PlaybackItem>,
        val startIndex: Int,
        val sourceLabel: String,
        var play: Boolean = false,
    )

    private val _state = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val _volume = MutableStateFlow(readVolume())
    override val volume: StateFlow<VolumeState> = _volume.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = refresh()
    }

    override fun connect() {
        if (controller != null || connecting?.isActive == true) return
        startVolumeTicker() // volume do sistema não depende do controller; reflete mudanças externas
        val component = resolveServiceComponent() ?: return // estado segue desconectado
        connecting = scope.launch {
            val token = SessionToken(appContext, component)
            val c = runCatching { MediaController.Builder(appContext, token).buildAsync().await() }
                .getOrNull() ?: return@launch
            controller = c
            connecting = null // conectou: o guard passa a valer por controller != null
            c.addListener(listener)
            // Aplica fila/solicitação de play que tenham chegado antes de conectar.
            pending?.let { p ->
                applyQueue(c, p.items, p.startIndex, p.play)
                pending = null
            }
            refresh() // só aqui connected vira true
        }
    }

    /**
     * Descobre o MediaSessionService do próprio app por intent (sem referenciar a
     * classe do `:app`, evitando dep circular). Espera exatamente 1 serviço; 0 ou
     * >1 → loga e não conecta (sem crashar).
     */
    private fun resolveServiceComponent(): ComponentName? {
        val intent = Intent(MediaSessionService.SERVICE_INTERFACE).setPackage(appContext.packageName)
        val matches = appContext.packageManager.queryIntentServices(intent, 0)
        return when (matches.size) {
            1 -> matches[0].serviceInfo?.let { ComponentName(it.packageName, it.name) }
            else -> {
                Log.w(TAG, "Esperava 1 MediaSessionService em ${appContext.packageName}, achei ${matches.size}")
                null
            }
        }
    }

    private fun refresh() {
        val c = controller ?: return
        // queueIds em ORDEM DE REPRODUÇÃO real do ExoPlayer (respeita shuffleModeEnabled nativo,
        // preservando o toggle-restore: OFF volta à ordem de inserção). index = posição da faixa
        // atual nessa ordem. Travessia da Timeline com REPEAT_MODE_OFF (ordem finita/linear).
        val tl = c.currentTimeline
        val order = playbackOrder(
            firstIndex = tl.getFirstWindowIndex(c.shuffleModeEnabled),
            unset = C.INDEX_UNSET,
        ) { tl.getNextWindowIndex(it, Player.REPEAT_MODE_OFF, c.shuffleModeEnabled) }
        val ids = order.map { c.getMediaItemAt(it).mediaId }
        _state.update {
            it.copy(
                connected = true,
                queueIds = ids,
                index = if (ids.isEmpty()) -1 else order.indexOf(c.currentMediaItemIndex),
                isPlaying = c.isPlaying,
                positionMs = c.currentPosition.coerceAtLeast(0),
                durationMs = c.duration.takeIf { d -> d > 0 } ?: 0L,
                shuffle = c.shuffleModeEnabled,
                repeat = repeatFromMedia3(c.repeatMode),
            )
        }
        if (c.isPlaying) startTicker() else ticker?.cancel()
    }

    private fun startTicker() {
        if (ticker?.isActive == true) return
        ticker = scope.launch {
            while (isActive) {
                val c = controller ?: break
                _state.update { it.copy(positionMs = c.currentPosition.coerceAtLeast(0)) }
                delay(100)
            }
        }
    }

    /** Poll ~1s do volume do sistema (reflete mudanças externas: botões/painel). */
    private fun startVolumeTicker() {
        if (volumeTicker?.isActive == true) return
        volumeTicker = scope.launch {
            while (isActive) {
                _volume.value = readVolume()
                delay(1_000)
            }
        }
    }

    override fun setQueue(items: List<PlaybackItem>, startIndex: Int, sourceLabel: String) {
        if (items.isEmpty()) return
        // Reflete a fila no estado desde já (paridade com o IosAudioPlayer), mesmo sem controller.
        _state.update {
            it.copy(
                queueIds = items.map { item -> item.id },
                index = startIndex.coerceIn(0, items.lastIndex),
                sourceLabel = sourceLabel,
            )
        }
        val c = controller
        if (c == null) {
            // Ainda conectando: guarda a fila e aplica no connect() — não descarta em silêncio.
            pending = Pending(items, startIndex, sourceLabel)
            return
        }
        applyQueue(c, items, startIndex)
    }

    // Shuffle NÃO é mexido aqui (espelha o IosAudioPlayer): fica a cargo de setShuffle().
    private fun applyQueue(c: MediaController, items: List<PlaybackItem>, startIndex: Int, play: Boolean = false) {
        c.setMediaItems(items.map { it.toMediaItem() }, startIndex.coerceIn(0, items.lastIndex), 0L)
        c.prepare() // carrega a fila; o play fica explícito (semântica do contrato)
        if (play) c.play()
    }

    override fun play() {
        val c = controller
        if (c == null) {
            pending?.play = true // play() antes de conectar: toca quando a fila pendente for aplicada
            return
        }
        if (c.playbackState == Player.STATE_ENDED) c.seekTo(0, 0)
        c.prepare(); c.play()
    }

    override fun pause() { controller?.pause() }

    override fun toggle() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else play()
    }

    override fun next() { controller?.seekToNext() }
    override fun previous() { controller?.seekToPrevious() }
    override fun seekTo(positionMs: Long) { controller?.seekTo(positionMs.coerceAtLeast(0)) }
    override fun seekToFraction(fraction: Float) {
        controller?.run { if (duration > 0) seekTo((duration * fraction.coerceIn(0f, 1f)).toLong()) }
    }
    // [index] é a posição na ORDEM DE REPRODUÇÃO (a UI itera ui.queue). Traduz p/ índice de
    // TIMELINE antes do seekTo (sob shuffle divergem); com shuffle OFF, order=identidade.
    // Selecionar um índice implica tocar (ação explícita do usuário), por isso prepare()+play().
    override fun seekToIndex(index: Int) {
        val c = controller ?: return
        val tl = c.currentTimeline
        val order = playbackOrder(tl.getFirstWindowIndex(c.shuffleModeEnabled), C.INDEX_UNSET) {
            tl.getNextWindowIndex(it, Player.REPEAT_MODE_OFF, c.shuffleModeEnabled)
        }
        val mediaIndex = order.getOrNull(index) ?: return
        c.seekTo(mediaIndex, 0L); c.prepare(); c.play()
    }
    override fun setShuffle(enabled: Boolean) { controller?.let { it.shuffleModeEnabled = enabled } }
    override fun setRepeat(mode: RepeatMode) { controller?.let { it.repeatMode = mode.toMedia3() } }

    /**
     * Reembaralha a cauda mantendo a faixa atual tocando (movida p/ o topo, sem recriar
     * seu item → sem reiniciar). Não toca em shuffleModeEnabled (flag [PlaybackState.shuffle]).
     */
    override fun shuffleQueue() {
        val c = controller ?: return
        val n = c.mediaItemCount
        if (n < 2) return
        val cur = c.currentMediaItemIndex
        if (cur != 0) c.moveMediaItem(cur, 0) // faixa atual no topo, segue tocando
        val order = shuffledTailPositions(n)  // embaralha as posições 1..n-1
        val items = order.map { c.getMediaItemAt(it) }
        c.removeMediaItems(1, n)
        c.addMediaItems(1, items)
    }

    /** Esvazia a fila e para a reprodução (idempotente). */
    override fun clearQueue() {
        val c = controller ?: return
        if (c.mediaItemCount == 0) return
        c.clearMediaItems()
        c.stop()
    }

    /** Esvazia a fila mantendo a faixa atual tocando (remove depois e antes; sem parar). */
    override fun clearUpcoming() {
        val c = controller ?: return
        // Remove a cauda ANTES do começo (remover o começo não desloca índices já contados).
        for ((from, to) in clearUpcomingRemovals(c.mediaItemCount, c.currentMediaItemIndex)) {
            c.removeMediaItems(from, to)
        }
    }

    private fun readVolume(): VolumeState = VolumeState(
        level = audio.getStreamVolume(AudioManager.STREAM_MUSIC),
        max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC),
        muted = audio.isStreamMute(AudioManager.STREAM_MUSIC),
    )

    override fun setVolume(fraction: Float) {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (audio.isStreamMute(AudioManager.STREAM_MUSIC)) {
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0)
        }
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, volumeLevelFor(fraction, max), 0)
        _volume.value = readVolume()
    }

    override fun toggleMute() {
        audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, 0)
        _volume.value = readVolume()
    }

    /** Idempotente: solta o controller e volta ao estado desconectado; permite reconectar. */
    override fun release() {
        connecting?.cancel(); connecting = null
        ticker?.cancel(); ticker = null
        volumeTicker?.cancel(); volumeTicker = null
        controller?.let { it.removeListener(listener); it.release() }
        controller = null
        pending = null
        _state.update { it.copy(connected = false) }
    }

    private fun PlaybackItem.toMediaItem(): MediaItem = MediaItem.Builder()
        .setMediaId(id)
        .setUri(uri)
        .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(Uri.parse(uri)).build())
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .apply { artworkUri?.let { setArtworkUri(Uri.parse(it)) } }
                .setIsPlayable(true)
                .setIsBrowsable(false)
                .build(),
        )
        .build()

    private companion object {
        const val TAG = "AndroidAudioPlayer"
    }
}

// --- Mapeamentos puros (testáveis sem device; só constantes int do Media3) ---

internal fun RepeatMode.toMedia3(): Int = when (this) {
    RepeatMode.OFF -> Player.REPEAT_MODE_OFF
    RepeatMode.ALL -> Player.REPEAT_MODE_ALL
    RepeatMode.ONE -> Player.REPEAT_MODE_ONE
}

internal fun repeatFromMedia3(mode: Int): RepeatMode = when (mode) {
    Player.REPEAT_MODE_ALL -> RepeatMode.ALL
    Player.REPEAT_MODE_ONE -> RepeatMode.ONE
    else -> RepeatMode.OFF
}

/** fraction 0..1 → nível de volume do sistema (0..max). */
internal fun volumeLevelFor(fraction: Float, max: Int): Int =
    (fraction.coerceIn(0f, 1f) * max).toInt().coerceIn(0, max)

/**
 * Posições da cauda (1..count-1) embaralhadas, usadas pelo [AndroidAudioPlayer.shuffleQueue]
 * (a posição 0 — faixa atual — fica fixa). Vazio se count < 2.
 */
internal fun shuffledTailPositions(count: Int): List<Int> =
    if (count < 2) emptyList() else (1 until count).shuffled()

/**
 * Remoções (from, toExclusive) para o clearUpcoming, na ordem de aplicação: primeiro a
 * cauda [current+1, count) e depois o começo [0, current) — remover o começo por último
 * não desloca índices ainda não processados. Vazio se count <= 1 (nada a remover).
 */
internal fun clearUpcomingRemovals(count: Int, current: Int): List<Pair<Int, Int>> {
    if (count <= 1) return emptyList()
    val cur = current.coerceIn(0, count - 1)
    val out = ArrayList<Pair<Int, Int>>(2)
    if (cur + 1 < count) out += (cur + 1) to count // depois da atual
    if (cur > 0) out += 0 to cur                    // antes da atual
    return out
}

/**
 * Travessia da ordem de reprodução da Timeline (caminho B do shuffle): a partir de
 * [firstIndex], encadeia [next] até [unset]. [unset] é passado (C.INDEX_UNSET) para o
 * helper ficar puro/testável. O guard de visitados evita laço infinito caso [next] cicle.
 */
internal inline fun playbackOrder(firstIndex: Int, unset: Int, next: (Int) -> Int): List<Int> {
    if (firstIndex == unset) return emptyList()
    val out = ArrayList<Int>()
    val seen = HashSet<Int>()
    var i = firstIndex
    while (i != unset && seen.add(i)) {
        out += i
        i = next(i)
    }
    return out
}
