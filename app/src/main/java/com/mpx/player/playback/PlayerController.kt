package com.mpx.player.playback

import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.mpx.player.model.Track
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

enum class RepeatMode { OFF, ALL, ONE }

data class PlayerState(
    val connected: Boolean = false,
    /** IDs MediaStore da fila, na ordem de reprodução exibida. */
    val queue: List<Long> = emptyList(),
    val index: Int = -1,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val shuffle: Boolean = false,
    val repeat: RepeatMode = RepeatMode.OFF,
    val sourceLabel: String = "",
    /** Último erro do player (texto p/ o CMD.LOG); null = sem erro. Limpo ao tocar/trocar de faixa. */
    val error: String? = null,
) {
    val currentId: Long? get() = queue.getOrNull(index)
}

data class VolumeState(val level: Int = 0, val max: Int = 15, val muted: Boolean = false)

/** Fachada da UI sobre o MediaController conectado ao [PlaybackService]. */
class PlayerController(
    private val context: Context,
    /** Log local p/ falhas de reprodução (o CrashLog mascara ?token= da URI do agente). */
    private val crashLog: com.mpx.player.data.CrashLog? = null,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var controller: MediaController? = null
    private var connecting: Job? = null
    private var ticker: Job? = null
    private val audio = context.getSystemService(AudioManager::class.java)

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private val _volume = MutableStateFlow(readVolume())
    val volume: StateFlow<VolumeState> = _volume.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = refresh()

        // Antes nada tratava o erro: com o agente desktop parado a faixa remota ficava em 00:00,
        // PAUSE e sem nenhuma mensagem. Agora vira linha "> ERRO ..." no CMD.LOG e vai p/ o log.
        override fun onPlayerError(error: PlaybackException) {
            val id = _state.value.currentId
            val remote = id != null && id < 0 && id != -1L
            val msg = playbackErrorMessage(error.errorCode, remote)
            _state.update { it.copy(error = reducePlayerError(it.error, PlayerErrorEvent.ERROR, msg)) }
            // errorCodeName (ex.: ERROR_CODE_IO_NETWORK_CONNECTION_FAILED) não é minificado pelo R8,
            // ao contrário do nome da classe; a causa original segue no stack (URI c/ token mascarada).
            // record faz I/O (append + releitura p/ contagem, até ~256 KB): fora da main thread.
            val tag = if (remote) "PLAYER-DESKTOP" else "PLAYER"
            val wrapped = IllegalStateException("${error.errorCodeName}: ${error.message}", error)
            crashLog?.let { log -> scope.launch(Dispatchers.IO) { log.record(tag, wrapped) } }
        }

        // Transição NÃO limpa o erro: o MediaController reentrega a transição (mascarada no
        // setMediaItems) junto com o estado real da sessão, às vezes depois do onPlayerError.
        // Ver reducePlayerError (reteste da Lupa: a mensagem sumia da tela).
    }

    fun connect() {
        if (controller != null || connecting?.isActive == true) return
        connecting = scope.launch {
            val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
            val c = runCatching { MediaController.Builder(context, token).buildAsync().await() }.getOrNull()
                ?: return@launch
            controller = c
            c.addListener(listener)
            refresh()
        }
    }

    private fun refresh() {
        val c = controller ?: return
        val ids = List(c.mediaItemCount) { c.getMediaItemAt(it).mediaId.toLongOrNull() ?: -1L }
        _state.update {
            it.copy(
                connected = true,
                queue = ids,
                index = if (ids.isEmpty()) -1 else c.currentMediaItemIndex,
                isPlaying = c.isPlaying,
                error = if (c.isPlaying) reducePlayerError(it.error, PlayerErrorEvent.STARTED_PLAYING) else it.error,
                positionMs = c.currentPosition.coerceAtLeast(0),
                durationMs = c.duration.takeIf { d -> d > 0 } ?: 0L,
                shuffle = c.shuffleModeEnabled,
                repeat = when (c.repeatMode) {
                    Player.REPEAT_MODE_ALL -> RepeatMode.ALL
                    Player.REPEAT_MODE_ONE -> RepeatMode.ONE
                    else -> RepeatMode.OFF
                },
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

    fun play(tracks: List<Track>, startIndex: Int, label: String) {
        val c = controller ?: return
        if (tracks.isEmpty()) return
        _state.update { it.copy(sourceLabel = label, error = null) }
        c.setMediaItems(tracks.map { it.toMediaItem() }, startIndex.coerceIn(0, tracks.lastIndex), 0L)
        c.shuffleModeEnabled = false
        c.prepare()
        c.play()
    }

    fun toggle() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else {
            _state.update { it.copy(error = null) }
            if (c.playbackState == Player.STATE_ENDED) c.seekTo(0, 0)
            c.prepare(); c.play()
        }
    }

    fun play() = controller?.run { prepare(); play() }
    private fun clearErrorByUser() = _state.update { it.copy(error = reducePlayerError(it.error, PlayerErrorEvent.USER_ACTION)) }

    fun next() { clearErrorByUser(); controller?.seekToNext() }
    fun previous() { clearErrorByUser(); controller?.seekToPrevious() }
    fun seekToFraction(f: Float) = controller?.run { if (duration > 0) seekTo((duration * f.coerceIn(0f, 1f)).toLong()) }
    fun seekToIndex(i: Int) { clearErrorByUser(); controller?.run { seekTo(i, 0L); prepare(); play() } }
    fun toggleShuffle() = controller?.run { shuffleModeEnabled = !shuffleModeEnabled }
    fun cycleRepeat() = controller?.run {
        repeatMode = when (repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    /** Embaralha a fila mantendo a faixa atual no topo e sem interromper a reprodução. */
    fun shuffleQueue() {
        val c = controller ?: return
        val n = c.mediaItemCount
        if (n < 2) return
        val cur = c.currentMediaItemIndex
        if (cur != 0) c.moveMediaItem(cur, 0)
        val order = (1 until n).shuffled()
        val items = order.map { c.getMediaItemAt(it) }
        c.removeMediaItems(1, n)
        c.addMediaItems(1, items)
    }

    /** Remove tudo da fila exceto a faixa atual. */
    fun clearQueue() {
        val c = controller ?: return
        val cur = c.currentMediaItemIndex
        if (c.mediaItemCount == 0) return
        if (cur + 1 < c.mediaItemCount) c.removeMediaItems(cur + 1, c.mediaItemCount)
        if (cur > 0) c.removeMediaItems(0, cur)
    }

    fun readVolume(): VolumeState = VolumeState(
        level = audio.getStreamVolume(AudioManager.STREAM_MUSIC),
        max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC),
        muted = audio.isStreamMute(AudioManager.STREAM_MUSIC),
    )

    fun pollVolume() {
        _volume.value = readVolume()
    }

    /** fraction 0..1 → volume de mídia do sistema. */
    fun setVolume(fraction: Float) {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (audio.isStreamMute(AudioManager.STREAM_MUSIC)) {
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0)
        }
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, (fraction * max).toInt().coerceIn(0, max), 0)
        pollVolume()
    }

    fun toggleMute() {
        audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, 0)
        pollVolume()
    }

    private fun Track.toMediaItem(): MediaItem = MediaItem.Builder()
        .setMediaId(id.toString())
        .setUri(uri)
        .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(uri).build())
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .setArtworkUri(artUri ?: ContentUris.withAppendedId(ALBUM_ART, albumId))
                .setIsPlayable(true)
                .setIsBrowsable(false)
                .setExtras(Bundle().apply { putLong("albumId", albumId) })
                .build(),
        )
        .build()

    private companion object {
        val ALBUM_ART: Uri = Uri.parse("content://media/external/audio/albumart")
    }
}
