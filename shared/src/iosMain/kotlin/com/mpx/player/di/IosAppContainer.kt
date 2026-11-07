package com.mpx.player.di

import com.mpx.player.data.IosLibraryRepository
import com.mpx.player.data.IosLyricsRepository
import com.mpx.player.data.IosPlaylistRepository
import com.mpx.player.data.IosSettingsRepository
import com.mpx.player.data.LibraryRepository
import com.mpx.player.data.LyricsRepository
import com.mpx.player.data.PlaylistRepository
import com.mpx.player.data.SettingsRepository
import com.mpx.player.shared.PlatformContext
import com.mpx.player.shared.playback.AudioPlayer
import com.mpx.player.shared.playback.createAudioPlayer

/**
 * Contêiner de dependências do iOS: fecha a frente nativa wirando as 5 deps do
 * [AppContainer] comum. Espelho do `AndroidAppContainer` (`:app`), só com o que vive no
 * contrato comum — nada de Context/DataStore/SAF/downloader/covers/updater (Android-only).
 *
 *  - [settings]/[playlists] → `NSUserDefaults`;
 *  - [library] → MPMediaQuery (7.3);
 *  - [lyrics] → busca online (LyricsApi);
 *  - [audioPlayer] → `createAudioPlayer(PlatformContext())` (AVFoundation; o `connect()`
 *    ativa a `AVAudioSession` .playback — não mexemos nisso aqui).
 */
class IosAppContainer : AppContainer {
    override val settings: SettingsRepository = IosSettingsRepository()
    override val library: LibraryRepository = IosLibraryRepository()
    override val lyrics: LyricsRepository = IosLyricsRepository()
    override val playlists: PlaylistRepository = IosPlaylistRepository()
    override val audioPlayer: AudioPlayer = createAudioPlayer(PlatformContext())
}

/**
 * Factory top-level para o host Swift consumir (estilo `MainViewControllerKt`): como mora
 * em `IosAppContainer.kt`, o Swift a chama como `IosAppContainerKt.createAppContainer()`.
 */
fun createAppContainer(): AppContainer = IosAppContainer()
