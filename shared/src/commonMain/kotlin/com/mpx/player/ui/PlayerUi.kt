package com.mpx.player.ui

import com.mpx.player.model.Track
import com.mpx.player.shared.playback.RepeatMode

// Movido de app/.../ui/ViewModels.kt (PlayerUi) e app/.../ui/screens/NowPlayingScreen.kt
// (NowPlayingActions) para :shared/commonMain na fatia fina de UI (P7). Mesmo pacote/FQN
// (com.mpx.player.ui) — os call-sites do :app resolvem o PlayerUi sem edição; o import do
// NowPlayingActions muda de ...ui.screens p/ ...ui. Só referenciam tipos COMUNS (Track,
// RepeatMode), então o estado de UI do player já é compartilhável por Android e iOS.
// TechInfo NÃO entra nesta fatia (fica no :app).

/** Estado de UI do player (Now Playing + fila), puro e comum às duas plataformas. */
data class PlayerUi(
    val current: Track? = null,
    val queue: List<Track> = emptyList(),
    val index: Int = -1,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val shuffle: Boolean = false,
    val repeat: RepeatMode = RepeatMode.OFF,
    val sourceLabel: String = "",
) {
    val totalMs: Long get() = queue.sumOf { it.durationMs }
    val remainingMs: Long
        get() = queue.drop(index.coerceAtLeast(0)).sumOf { it.durationMs } - positionMs.coerceAtMost(current?.durationMs ?: 0)
}

/** Callbacks de transporte/ação do Now Playing. Lambdas puras — sem dependência de plataforma. */
data class NowPlayingActions(
    val toggle: () -> Unit,
    val next: () -> Unit,
    val previous: () -> Unit,
    val seek: (Float) -> Unit,
    val shuffle: () -> Unit,
    val repeat: () -> Unit,
    val setVolume: (Float) -> Unit,
    val mute: () -> Unit,
    val openLibrary: () -> Unit,
    val toggleFavorite: () -> Unit,
    val addToPlaylist: (String) -> Unit,
)
