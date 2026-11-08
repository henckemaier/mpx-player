package com.mpx.player

import android.app.Application
import android.content.Context
import android.os.Build
import com.mpx.player.data.CoverArtLoader
import com.mpx.player.data.CrashLog
import com.mpx.player.data.Downloader
import com.mpx.player.data.LibraryRepository
import com.mpx.player.data.LyricsRepository
import com.mpx.player.data.OverrideRepository
import com.mpx.player.data.PlaylistImporter
import com.mpx.player.data.PlaylistRepository
import com.mpx.player.data.RemoteLibrarySource
import com.mpx.player.data.SettingsRepository
import com.mpx.player.data.UpdateRepository
import com.mpx.player.playback.PlayerController
import java.io.File

/** Injeção manual de dependências: um container por processo. */
class AppContainer(val context: Context, val crashLog: CrashLog) {
    val settings = SettingsRepository(context)
    val overrides = OverrideRepository(context)
    val remote = RemoteLibrarySource(settings)
    val library = LibraryRepository(context, overrides, remote)
    val lyrics = LyricsRepository(context)
    val playlistImporter = PlaylistImporter(context)
    val playlists = PlaylistRepository(context)
    val covers = CoverArtLoader(context)
    val player = PlayerController(context)
    val downloader = Downloader(context, library, lyrics, settings, crashLog)
    val updater = UpdateRepository(context)
}

class MpxApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        val crashLog = CrashLog(
            dir = File(filesDir, "logs"),
            header = "MPX v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
        )
        installCrashHandler(crashLog)
        container = AppContainer(this, crashLog)
    }

    /**
     * PREVENTIVO (crash do downloader, causa raiz NÃO confirmada): grava o stack de qualquer exceção
     * não capturada no log local ANTES de o app fechar e depois repassa ao handler anterior (o do
     * sistema continua mostrando/encerrando o app normalmente). Nada sai do aparelho.
     */
    private fun installCrashHandler(crashLog: CrashLog) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { crashLog.record("FATAL", error, thread.name) }
            if (previous != null) {
                previous.uncaughtException(thread, error)
            } else {
                android.os.Process.killProcess(android.os.Process.myPid())
                kotlin.system.exitProcess(10)
            }
        }
    }
}
