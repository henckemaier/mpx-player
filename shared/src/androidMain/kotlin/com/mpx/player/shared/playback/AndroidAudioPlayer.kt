package com.mpx.player.shared.playback

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.util.Log
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
        val ids = List(c.mediaItemCount) { c.getMediaItemAt(it).mediaId }
        _state.update {
            it.copy(
                connected = true,
                queueIds = ids,
                index = if (ids.isEmpty()) -1 else c.currentMediaItemIndex,
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
    // Selecionar um índice implica tocar (ação explícita do usuário), por isso prepare()+play().
    override fun seekToIndex(index: Int) { controller?.run { seekTo(index, 0L); prepare(); play() } }
    override fun setShuffle(enabled: Boolean) { controller?.let { it.shuffleModeEnabled = enabled } }
    override fun setRepeat(mode: RepeatMode) { controller?.let { it.repeatMode = mode.toMedia3() } }

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
