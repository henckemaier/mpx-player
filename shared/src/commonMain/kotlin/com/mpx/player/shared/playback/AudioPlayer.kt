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
    /**
     * Ids na ORDEM DE REPRODUÇÃO (a mesma em que as faixas vão tocar); [index] aponta
     * a posição atual nessa ordem. Convenção comum às duas plataformas.
     */
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

/** Volume do sistema (Android: STREAM_MUSIC; iOS: volume de mídia/`AVAudioSession`). */
data class VolumeState(val level: Int = 0, val max: Int = 15, val muted: Boolean = false)

/**
 * Contrato de player comum à UI (Compose Multiplatform) e aos ViewModels.
 *
 * É uma interface pura; cada plataforma fornece sua implementação:
 * - Android: adaptador sobre Media3 (`MediaController` + `PlaybackService`),
 *   herdado do antigo `PlayerController` (removido na fatia 4).
 * - iOS: sobre AVFoundation (`AVQueuePlayer` + `MPNowPlayingInfoCenter`
 *   + `AVAudioSession`).
 *
 * Como obter a instância: fábrica `createAudioPlayer(PlatformContext)` (passo 4).
 *
 * Threading: os métodos mutadores devem ser chamados na thread principal do app
 * (no Android o `MediaController` exige a thread em que foi criado; a implementação
 * assume a main). [state] e [volume] podem ser observados de qualquer thread.
 *
 * [setQueue] e [play] podem ser chamados antes de [connect] concluir: a fila é
 * refletida em [state] na hora e aplicada ao backend quando a conexão fica pronta
 * (não é descartada em silêncio).
 */
interface AudioPlayer {
    val state: StateFlow<PlaybackState>
    val volume: StateFlow<VolumeState>

    /**
     * Inicia/conecta o backend (no Android a conexão ao `MediaController` é
     * assíncrona; `state.connected` passa a `true` quando pronta). Idempotente.
     */
    fun connect()

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

    /**
     * Reembaralha a ordem de reprodução da fila atual mantendo a faixa atual tocando
     * (ela vira o topo da nova ordem; posição dentro da faixa preservada). NÃO altera
     * o flag [PlaybackState.shuffle]. No-op se a fila estiver vazia.
     */
    fun shuffleQueue()

    /**
     * Esvazia a fila e para a reprodução: [PlaybackState] volta a queueIds vazio,
     * index=-1, isPlaying=false. Idempotente; no-op se já estiver vazia.
     */
    fun clearQueue()

    /**
     * Esvazia a fila MANTENDO a faixa atual tocando: remove tudo antes e depois da
     * faixa atual, sem reiniciar nem parar a reprodução dela. Após isto a fila tem no
     * máximo 1 item (a atual), index = 0. Paridade com o antigo PlayerController.clearQueue
     * do :app. No-op se a fila já tiver 0/1 item. Distinto de [clearQueue] (esvazia E para).
     */
    fun clearUpcoming()

    /** fraction 0..1 → volume de mídia do sistema. */
    fun setVolume(fraction: Float)
    fun toggleMute()

    fun release()
}
