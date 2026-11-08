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
import com.mpx.player.shared.playback.IosEngineAudioPlayer
import com.mpx.player.shared.playback.createAudioPlayer

/**
 * Flag de LAUNCH-TIME do backend de áudio do iOS. **OFF por padrão** → produção usa o
 * `AVQueuePlayer` (`createAudioPlayer` → `IosAudioPlayer`), INTOCADO. Ligado, troca para o
 * backend experimental `AVAudioEngine` (`IosEngineAudioPlayer`), que abre o caminho do grafo
 * de áudio (pré-requisito do EQ-audível). É escolha de BUILD/LAUNCH — não há switch ao vivo;
 * fica OFF até haver device p/ validar o áudio (o CI só prova compile/link/matemática, não som).
 */
private const val USE_ENGINE_BACKEND = false

/**
 * Contêiner de dependências do iOS: fecha a frente nativa wirando as 5 deps do
 * [AppContainer] comum. Espelho do `AndroidAppContainer` (`:app`), só com o que vive no
 * contrato comum — nada de Context/DataStore/SAF/downloader/covers/updater (Android-only).
 *
 *  - [settings]/[playlists] → `NSUserDefaults`;
 *  - [library] → MPMediaQuery (7.3);
 *  - [lyrics] → busca online (LyricsApi);
 *  - [audioPlayer] → [USE_ENGINE_BACKEND] ? `IosEngineAudioPlayer(settings.eq)` (AVAudioEngine +
 *    AVAudioUnitEQ observando `settings.eq`, experimental, OFF) : `createAudioPlayer(PlatformContext())`
 *    (AVFoundation/`AVQueuePlayer`, padrão; o `connect()` ativa a `AVAudioSession` .playback — não
 *    mexemos nisso aqui). `settings` é inicializado ANTES → disponível p/ a injeção do EQ.
 */
class IosAppContainer : AppContainer {
    override val settings: SettingsRepository = IosSettingsRepository()
    override val library: LibraryRepository = IosLibraryRepository()
    override val lyrics: LyricsRepository = IosLyricsRepository()
    override val playlists: PlaylistRepository = IosPlaylistRepository()
    override val audioPlayer: AudioPlayer =
        if (USE_ENGINE_BACKEND) IosEngineAudioPlayer(settings.eq) else createAudioPlayer(PlatformContext())
}

/**
 * Factory top-level para o host Swift consumir (estilo `MainViewControllerKt`): como mora
 * em `IosAppContainer.kt`, o Swift a chama como `IosAppContainerKt.createAppContainer()`.
 */
fun createAppContainer(): AppContainer = IosAppContainer()
