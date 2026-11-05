package com.mpx.player

import android.app.Application
import android.content.Context
import com.mpx.player.data.AndroidLibraryRepository
import com.mpx.player.data.AndroidLyricsRepository
import com.mpx.player.data.AndroidPlaylistRepository
import com.mpx.player.data.AndroidSettingsRepository
import com.mpx.player.data.CoverArtLoader
import com.mpx.player.data.Downloader
import com.mpx.player.data.OverrideRepository
import com.mpx.player.data.PlaylistImporter
import com.mpx.player.data.UpdateRepository
import com.mpx.player.di.AppContainer
import com.mpx.player.playback.PlayerController
import com.mpx.player.shared.PlatformContext
import com.mpx.player.shared.playback.AudioPlayer
import com.mpx.player.shared.playback.createAudioPlayer

/**
 * Injeção manual de dependências: um container por processo.
 * Implementa o [AppContainer] comum (fatia usada pelos ViewModels migrados ao
 * `:shared`); expõe também os repos Android ainda não migrados.
 */
class AndroidAppContainer(val context: Context) : AppContainer {
    override val settings = AndroidSettingsRepository(context)
    val overrides = OverrideRepository(context)
    override val library = AndroidLibraryRepository(context, overrides)
    override val lyrics = AndroidLyricsRepository(context)
    val playlistImporter = PlaylistImporter(context)
    override val playlists = AndroidPlaylistRepository(context)
    val covers = CoverArtLoader(context)
    val player = PlayerController(context)
    // Player do contrato comum: coexiste OCIOSO (não conectado) com o PlayerController
    // até a fatia 4, que fará a troca. Ver KDoc de AppContainer.
    override val audioPlayer: AudioPlayer = createAudioPlayer(PlatformContext(context))
    val downloader = Downloader(context, library, lyrics, settings)
    val updater = UpdateRepository(context)
}

class MpxApp : Application() {
    lateinit var container: AndroidAppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AndroidAppContainer(this)
    }
}
