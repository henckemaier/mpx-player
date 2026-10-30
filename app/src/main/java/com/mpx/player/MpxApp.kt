package com.mpx.player

import android.app.Application
import android.content.Context
import com.mpx.player.data.CoverArtLoader
import com.mpx.player.data.Downloader
import com.mpx.player.data.LibraryRepository
import com.mpx.player.data.LyricsRepository
import com.mpx.player.data.PlaylistImporter
import com.mpx.player.data.SettingsRepository
import com.mpx.player.playback.PlayerController

/** Injeção manual de dependências: um container por processo. */
class AppContainer(val context: Context) {
    val settings = SettingsRepository(context)
    val library = LibraryRepository(context)
    val lyrics = LyricsRepository(context)
    val playlists = PlaylistImporter(context)
    val covers = CoverArtLoader(context)
    val player = PlayerController(context)
    val downloader = Downloader(context, library, lyrics, settings)
}

class MpxApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
