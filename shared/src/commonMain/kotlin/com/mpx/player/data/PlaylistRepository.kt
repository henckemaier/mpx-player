package com.mpx.player.data

import com.mpx.player.model.Playlist
import kotlinx.coroutines.flow.StateFlow

/**
 * Playlists/favoritos — fatia comum usada pelos ViewModels migrados (só o que o
 * PlayerViewModel usa por ora). Import/edição completa fica na impl Android.
 */
interface PlaylistRepository {
    val playlists: StateFlow<List<Playlist>>

    /** Alterna a faixa [key] na playlist de favoritos. */
    fun toggleFavorite(key: String)

    /** Adiciona a faixa [key] à playlist [id]. */
    fun addTrack(id: String, key: String)
}
