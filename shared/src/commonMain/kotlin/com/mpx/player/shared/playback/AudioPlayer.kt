package com.mpx.player.shared.playback

import kotlinx.coroutines.flow.StateFlow

/** Modo de repetição, comum às duas plataformas. */
enum class RepeatMode { OFF, ALL, ONE }

/**
 * Item de reprodução independente de plataforma.
 *
 * Observação: a fonte é um [uri] em String (não `android.net.Uri`), para que o
 * modelo comum não dependa do Android. No Android vira `Uri.parse(uri)`; no iOS,
 * `NSURL(string:)` ou um caminho do sandbox/MPMediaItem.
 */
data class PlaybackItem(
    val id: String,
    val uri: String,
    val title: String,
    val artist: String,
    val album: String,
    val artworkUri: String? = null,
    val durationMs: Long = 0L,
)

/** Estado observável do player, espelhando o que a UI precisa. */
data class PlaybackState(
    val connected: Boolean = false,
    val queueIds: List<String> = emptyList(),
    val index: Int = -1,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val shuffle: Boolean = false,
    val repeat: RepeatMode = RepeatMode.OFF,
    val sourceLabel: String = "",
) {
    val currentId: String? get() = queueIds.getOrNull(index)
}

/**
 * Fronteira `expect/actual` do player.
 *
 * - Android: implementada sobre Media3 (`MediaController` + `PlaybackService`),
 *   adaptando o atual `PlayerController`.
 * - iOS: implementada sobre AVFoundation (`AVQueuePlayer` + `MPNowPlayingInfoCenter`
 *   + `AVAudioSession`).
 *
 * A UI (Compose Multiplatform) e os ViewModels consomem apenas esta interface.
 */
interface AudioPlayer {
    val state: StateFlow<PlaybackState>

    fun setQueue(items: List<PlaybackItem>, startIndex: Int, sourceLabel: String)
    fun play()
    fun pause()
    fun toggle()
    fun next()
    fun previous()
    fun seekTo(positionMs: Long)
    fun seekToFraction(fraction: Float)
    fun seekToIndex(index: Int)
    fun setShuffle(enabled: Boolean)
    fun setRepeat(mode: RepeatMode)
    fun release()
}
