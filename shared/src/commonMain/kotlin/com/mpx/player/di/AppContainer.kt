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
 * [audioPlayer] é o player do contrato comum. Na fatia 3 ele COEXISTE ocioso com o
 * `PlayerController` do `:app` (que segue dirigindo a reprodução); a troca real — o
 * `:app` passar a enfileirar/observar por ele e o `PlayerController` sair — é a fatia 4.
 * O Android fornece a impl concreta (`AndroidAppContainer`).
 */
interface AppContainer {
    val settings: SettingsRepository
    val library: LibraryRepository
    val lyrics: LyricsRepository
    val playlists: PlaylistRepository
    val audioPlayer: AudioPlayer
}
