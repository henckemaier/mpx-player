package com.mpx.player.di

import com.mpx.player.data.LibraryRepository
import com.mpx.player.data.LyricsRepository
import com.mpx.player.data.PlaylistRepository
import com.mpx.player.data.SettingsRepository
import com.mpx.player.shared.playback.AudioPlayer

/**
 * Contêiner de dependências comum aos ViewModels migrados para o `commonMain`.
 *
 * Cresce conforme os ViewModels migram; expõe as fatias de dados (settings/biblioteca/
 * letras/playlists) e o [audioPlayer].
 *
 * [audioPlayer] é o player do contrato comum e o único caminho de reprodução do `:app`
 * (o antigo `PlayerController` foi removido na fatia 4). O Android fornece a impl
 * concreta (`AndroidAppContainer`).
 */
interface AppContainer {
    val settings: SettingsRepository
    val library: LibraryRepository
    val lyrics: LyricsRepository
    val playlists: PlaylistRepository
    val audioPlayer: AudioPlayer
}
