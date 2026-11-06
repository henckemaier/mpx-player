package com.mpx.player.data

import com.mpx.player.model.Playlist
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.Foundation.NSUserDefaults

/**
 * Playlists/favoritos no iOS: persiste a lista em `NSUserDefaults` (JSON), atrás do
 * contrato comum [PlaylistRepository] — espelho do `AndroidPlaylistRepository`.
 *
 * TODA a lógica (favoritos dedicado `Playlist.FAVORITES_ID`, dedupe, (de)serialização
 * JSON) é REUSADA de [PlaylistLogic] (commonMain) — a mesma do Android, nada reimplementado.
 * Aqui só troca-se o backend de persistência (arquivo no Android → NSUserDefaults no iOS).
 */
class IosPlaylistRepository : PlaylistRepository {

    private val defaults = NSUserDefaults.standardUserDefaults

    private val _playlists = MutableStateFlow(load())
    override val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    private fun load(): List<Playlist> =
        PlaylistLogic.ensureFavorites(PlaylistLogic.fromJson(defaults.stringForKey(KEY).orEmpty()))

    private fun update(transform: (List<Playlist>) -> List<Playlist>) {
        val next = PlaylistLogic.ensureFavorites(transform(_playlists.value))
        _playlists.value = next
        defaults.setObject(PlaylistLogic.toJson(next), KEY)
    }

    override fun toggleFavorite(key: String) = update { PlaylistLogic.favoriteToggled(it, key) }

    override fun addTrack(id: String, key: String) = update { PlaylistLogic.withTrack(it, id, key) }

    companion object {
        const val KEY = "mpx_playlists"
    }
}
