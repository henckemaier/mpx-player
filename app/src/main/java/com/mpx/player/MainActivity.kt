package com.mpx.player

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mpx.player.model.UserSettings
import com.mpx.player.ui.EqViewModel
import com.mpx.player.ui.LibraryViewModel
import com.mpx.player.ui.PlayerUi
import com.mpx.player.ui.PlayerViewModel
import com.mpx.player.ui.SettingsViewModel
import com.mpx.player.ui.components.HeaderLabel
import com.mpx.player.ui.components.MpxHeader
import com.mpx.player.ui.components.MpxNav
import com.mpx.player.ui.components.MpxNavRail
import com.mpx.player.ui.components.Tab
import com.mpx.player.ui.components.scanlines
import com.mpx.player.ui.mpxViewModel
import com.mpx.player.ui.screens.BootScreen
import com.mpx.player.ui.screens.EqActions
import com.mpx.player.ui.screens.EqScreen
import com.mpx.player.ui.screens.LibraryScreen
import com.mpx.player.ui.screens.NowPlayingActions
import com.mpx.player.ui.screens.NowPlayingScreen
import com.mpx.player.ui.screens.QueueActions
import com.mpx.player.ui.screens.QueueScreen
import com.mpx.player.ui.screens.SettingsScreen
import com.mpx.player.ui.theme.LocalLandscape
import com.mpx.player.ui.theme.Mpx
import com.mpx.player.ui.theme.MpxTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private val container get() = (application as MpxApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        container.player.connect()
        setContent {
            val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = UserSettings())
            MpxTheme(settings) { MpxRoot(container, settings) }
        }
    }

    override fun onStart() {
        super.onStart()
        container.player.connect()
    }
}

private val AUDIO_PERMISSION =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_AUDIO
    else Manifest.permission.READ_EXTERNAL_STORAGE

@Composable
private fun MpxRoot(container: AppContainer, settings: UserSettings) {
    val context = LocalContext.current
    val st = Mpx.style
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, AUDIO_PERMISSION) == PackageManager.PERMISSION_GRANTED)
    }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    val library by container.library.state.collectAsStateWithLifecycle()

    LaunchedEffect(granted) {
        if (granted && !library.scanned) {
            container.library.scan()
            container.settings.setLastScan(System.currentTimeMillis())
        }
    }

    var booted by rememberSaveable { mutableStateOf(false) }
    var tab by rememberSaveable { mutableStateOf(Tab.PLAY) }

    Box(
        Modifier
            .fillMaxSize()
            .background(st.c.bg)
            .scanlines(st.scanlines),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().padding(14.dp)) {
          val landscape = maxWidth > maxHeight
          // Retrato desenhado p/ ~362x760dp; paisagem p/ ~760x360dp. Encolhe tudo por igual em telas menores.
          val base = LocalDensity.current
          val k = if (landscape) minOf(1f, maxWidth.value / 760f, maxHeight.value / 360f).coerceAtLeast(0.7f)
                  else minOf(1f, maxWidth.value / 362f, maxHeight.value / 760f).coerceAtLeast(0.8f)
          CompositionLocalProvider(
              LocalLandscape provides landscape,
              LocalDensity provides Density(base.density * k, base.fontScale),
          ) {
          Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            if (!booted) {
                BootScreen(
                    hasPermission = granted,
                    scanned = library.scanned,
                    fileCount = library.tracks.size,
                    eqPreset = settings.eq.preset,
                    onRequestPermission = { request.launch(AUDIO_PERMISSION) },
                    onEnter = { booted = true },
                )
            } else {
                BackHandler(enabled = tab != Tab.PLAY) { tab = Tab.PLAY }
                MainShell(tab, onTab = { tab = it }, libraryFiles = library.tracks.size, onReboot = {
                    booted = false; tab = Tab.PLAY
                })
            }
        }
          }
        }
    }
}

@Composable
private fun MainShell(tab: Tab, onTab: (Tab) -> Unit, libraryFiles: Int, onReboot: () -> Unit) {
    val playerVm = mpxViewModel { PlayerViewModel(it) }
    val player by playerVm.ui.collectAsStateWithLifecycle()

    if (Mpx.landscape) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MpxNavRail(tab, onTab)
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                MpxHeader { HeaderRight(tab, libraryFiles) }
                MainContent(tab, onTab, player, playerVm, onReboot)
            }
        }
    } else {
        MpxHeader { HeaderRight(tab, libraryFiles) }
        MpxNav(tab, onTab)
        MainContent(tab, onTab, player, playerVm, onReboot)
    }
}

@Composable
private fun HeaderRight(tab: Tab, libraryFiles: Int) {
    when (tab) {
        Tab.PLAY -> StatusIndicators()
        Tab.LIB -> HeaderLabel("${"%,d".format(libraryFiles).replace(',', '.')} FILES")
        Tab.QUEUE -> HeaderLabel("QUEUE.BAT")
        Tab.EQ -> HeaderLabel("EQ.EXE")
        Tab.CFG -> HeaderLabel("CONFIG.SYS")
    }
}

@Composable
private fun MainContent(
    tab: Tab,
    onTab: (Tab) -> Unit,
    player: PlayerUi,
    playerVm: PlayerViewModel,
    onReboot: () -> Unit,
) {
    when (tab) {
        Tab.PLAY -> {
            val tech by playerVm.tech.collectAsStateWithLifecycle()
            val lyrics by playerVm.lyrics.collectAsStateWithLifecycle()
            val volume by playerVm.volume.collectAsStateWithLifecycle()
            val favorite by playerVm.favorite.collectAsStateWithLifecycle()
            val playlists by playerVm.playlists.collectAsStateWithLifecycle()
            NowPlayingScreen(
                player, tech, lyrics, volume, favorite, playlists,
                NowPlayingActions(
                    toggle = playerVm::toggle,
                    next = { playerVm.next() },
                    previous = { playerVm.previous() },
                    seek = { playerVm.seek(it) },
                    shuffle = { playerVm.toggleShuffle() },
                    repeat = { playerVm.cycleRepeat() },
                    setVolume = playerVm::setVolume,
                    mute = playerVm::toggleMute,
                    openLibrary = { onTab(Tab.LIB) },
                    toggleFavorite = playerVm::toggleFavorite,
                    addToPlaylist = playerVm::addCurrentToPlaylist,
                ),
            )
        }
        Tab.LIB -> {
            val vm = mpxViewModel { LibraryViewModel(it) }
            val ui by vm.ui.collectAsStateWithLifecycle()
            LibraryScreen(ui, player, vm, onOpenPlayer = { onTab(Tab.PLAY) })
        }
        Tab.QUEUE -> QueueScreen(
            player,
            QueueActions(
                pick = playerVm::playIndex,
                shuffle = playerVm::shuffleQueue,
                clear = playerVm::clearQueue,
                play = { playerVm.play(); onTab(Tab.PLAY) },
            ),
        )
        Tab.EQ -> {
            val vm = mpxViewModel { EqViewModel(it) }
            val eq by vm.eq.collectAsStateWithLifecycle()
            val sel by vm.selectedBand.collectAsStateWithLifecycle()
            EqScreen(
                eq, sel,
                EqActions(
                    toggleOn = vm::toggleOn,
                    preset = vm::pickPreset,
                    setBand = vm::setBand,
                    preamp = vm::preamp,
                    bass = vm::toggleBass,
                    surround = vm::toggleSurround,
                    loudness = vm::toggleLoudness,
                    reset = vm::reset,
                    save = vm::saveCustom,
                ),
            )
        }
        Tab.CFG -> {
            val vm = mpxViewModel { SettingsViewModel(it) }
            val ui by vm.ui.collectAsStateWithLifecycle()
            SettingsScreen(ui, vm, onReboot)
        }
    }
}

/** Bateria em 4 blocos + saída de áudio, no lugar do "PORT......3" do protótipo. */
@Composable
private fun StatusIndicators() {
    val c = Mpx.colors
    val context = LocalContext.current
    val battery by produceState(100) {
        while (true) {
            val bm = context.getSystemService(android.os.BatteryManager::class.java)
            value = bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
            delay(60_000)
        }
    }
    val blocks = ((battery + 12) / 25).coerceIn(0, 4)
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(4) { i ->
            Box(
                Modifier
                    .size(9.dp)
                    .border(1.dp, c.accent)
                    .then(if (i < blocks) Modifier.background(c.accent) else Modifier),
            )
        }
    }
    HeaderLabel("BAT....%3d".format(battery).replace(' ', '.'))
}
