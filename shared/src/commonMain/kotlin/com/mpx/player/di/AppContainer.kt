package com.mpx.player.di

import com.mpx.player.data.LibraryRepository
import com.mpx.player.data.LyricsRepository
import com.mpx.player.data.PlaylistRepository
import com.mpx.player.data.SettingsRepository

/**
 * Contêiner de dependências comum aos ViewModels migrados para o `commonMain`.
 *
 * Cresce conforme os ViewModels migram; por ora expõe as fatias de dados usadas
 * pelo EqViewModel e as que o PlayerViewModel vai usar (biblioteca/letras/playlists).
 * O player ainda NÃO entra aqui (reconciliação PlayerController↔AudioPlayer é fatia
 * posterior). O Android fornece a impl concreta (`AndroidAppContainer`).
 */
interface AppContainer {
    val settings: SettingsRepository
    val library: LibraryRepository
    val lyrics: LyricsRepository
    val playlists: PlaylistRepository
}
