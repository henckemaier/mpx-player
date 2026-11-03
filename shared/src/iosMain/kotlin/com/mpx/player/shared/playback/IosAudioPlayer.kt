package com.mpx.player.shared.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Esqueleto do player iOS. Implementa o contrato comum [AudioPlayer] e compila
 * em Kotlin/Native (macOS + Xcode). O próximo passo é ligar os métodos ao
 * AVFoundation:
 *
 *  - `AVQueuePlayer` + `AVPlayerItem(uri)` para a fila e troca de faixa;
 *  - `AVAudioSession` (categoria `.playback`) para tocar em segundo plano;
 *  - `MPNowPlayingInfoCenter` + `MPRemoteCommandCenter` para tela de bloqueio/controles;
 *  - um observador de tempo (`addPeriodicTimeObserver`) para alimentar `positionMs`.
 *
 * Embaralhamento/repetição ficam na camada de fila (AVQueuePlayer não os expõe
 * nativamente), espelhando a lógica do `PlayerController` do Android. Os métodos
 * de navegação (next/previous/seekToIndex) ainda não refletem `index` no estado
 * — serão completados junto da fila do AVQueuePlayer.
 */
class IosAudioPlayer : AudioPlayer {

    private val _state = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val _volume = MutableStateFlow(VolumeState())
    override val volume: StateFlow<VolumeState> = _volume.asStateFlow()

    private var queue: List<PlaybackItem> = emptyList()

    override fun connect() {
        // TODO(iOS): configurar AVAudioSession e instanciar AVQueuePlayer.
        _state.update { it.copy(connected = true) }
    }

    override fun setQueue(items: List<PlaybackItem>, startIndex: Int, sourceLabel: String) {
        queue = items
        _state.update {
            it.copy(
                queueIds = items.map { item -> item.id },
                index = if (items.isEmpty()) -1 else startIndex.coerceIn(0, items.lastIndex),
                sourceLabel = sourceLabel,
            )
        }
        // TODO(iOS): construir AVPlayerItems e carregar no AVQueuePlayer.
    }

    override fun play() { /* TODO(iOS): avPlayer.play() + ativar AVAudioSession */ }
    override fun pause() { /* TODO(iOS): avPlayer.pause() */ }
    override fun toggle() { if (_state.value.isPlaying) pause() else play() }
    override fun next() { /* TODO(iOS): avPlayer.advanceToNextItem() + atualizar index */ }
    override fun previous() { /* TODO(iOS): recolocar item anterior na fila + atualizar index */ }
    override fun seekTo(positionMs: Long) { /* TODO(iOS): avPlayer.seek(toTime:) */ }
    override fun seekToFraction(fraction: Float) {
        val dur = _state.value.durationMs
        if (dur > 0) seekTo((dur * fraction.coerceIn(0f, 1f)).toLong())
    }
    override fun seekToIndex(index: Int) { /* TODO(iOS): reposicionar fila no índice */ }
    override fun setShuffle(enabled: Boolean) { _state.update { it.copy(shuffle = enabled) } }
    override fun setRepeat(mode: RepeatMode) { _state.update { it.copy(repeat = mode) } }

    override fun setVolume(fraction: Float) { /* TODO(iOS): MPVolumeView / volume de sistema */ }
    override fun toggleMute() { /* TODO(iOS): alternar mudo */ }

    override fun release() { /* TODO(iOS): remover observadores e desativar sessão */ }
}
