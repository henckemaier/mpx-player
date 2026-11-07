package com.mpx.player.ui

import com.mpx.player.data.LibraryRepository
import com.mpx.player.data.LibraryState
import com.mpx.player.data.LyricsRepository
import com.mpx.player.data.PlaylistRepository
import com.mpx.player.data.SettingsRepository
import com.mpx.player.di.AppContainer
import com.mpx.player.model.EqSettings
import com.mpx.player.model.LyricLine
import com.mpx.player.model.Playlist
import com.mpx.player.model.Track
import com.mpx.player.model.UserSettings
import com.mpx.player.shared.playback.AudioPlayer
import com.mpx.player.shared.playback.PlaybackItem
import com.mpx.player.shared.playback.PlaybackState
import com.mpx.player.shared.playback.RepeatMode
import com.mpx.player.shared.playback.VolumeState
import com.mpx.player.ui.theme.CUSTOM_THEME
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Teste multiplataforma do [SettingsViewModel] comum (commonTest → `:shared:testDebugUnitTest` E
 * `iosSimulatorArm64Test`; re-confirma o viewModelScope no runtime iOS). Cobre: `ui` reflete
 * settings+library; os setters de aparência/cor e os toggles delegam ao repo via `update{}` com o
 * `copy()` certo (lendo o estado corrente); `setCustomColor` grava `theme=CUSTOM_THEME` + a cor.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @BeforeTest fun setUp() { Dispatchers.setMain(dispatcher) }
    @AfterTest fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun ui_reflectsSettingsAndLibrary() = runTest(dispatcher) {
        val settings = FakeSettings(UserSettings(theme = 2, glow = false))
        val lib = LibraryState(tracks = listOf(trk("a.mp3"), trk("b.mp3")), scanning = true, newSinceLastScan = 3)
        val vm = SettingsViewModel(FakeContainer(settings, FakeLibrary(lib)))
        val job = backgroundScope.launch { vm.ui.collect {} }
        advanceUntilIdle()

        val ui = vm.ui.value
        assertEquals(2, ui.settings.theme)
        assertEquals(2, ui.files)
        assertTrue(ui.scanning)
        assertEquals(3, ui.newFiles)
        assertEquals(0, ui.lrcCount)         // comum: 0 (Android injeta)
        assertEquals("NUNCA", ui.lastScanLabel) // comum: default (Android formata)
        assertEquals("", ui.appVersion)
        job.cancel()
    }

    @Test
    fun setTheme_persistsAndProjects() = runTest(dispatcher) {
        val settings = FakeSettings(UserSettings(theme = 0))
        val vm = SettingsViewModel(FakeContainer(settings))
        val job = backgroundScope.launch { vm.ui.collect {} }

        vm.setTheme(1); advanceUntilIdle()
        assertEquals(1, settings.value.theme)
        assertEquals(1, vm.ui.value.settings.theme)
        job.cancel()
    }

    @Test
    fun setCustomColor_setsCustomThemeAndColor() = runTest(dispatcher) {
        val settings = FakeSettings(UserSettings(theme = 0))
        val vm = SettingsViewModel(FakeContainer(settings))

        vm.setCustomColor(0xFF112233.toInt()); advanceUntilIdle()
        assertEquals(CUSTOM_THEME, settings.value.theme)
        assertEquals(0xFF112233.toInt(), settings.value.customColor)
    }

    @Test
    fun toggleGlow_flipsFromCurrentValue() = runTest(dispatcher) {
        val settings = FakeSettings(UserSettings(glow = true))
        val vm = SettingsViewModel(FakeContainer(settings))

        vm.toggleGlow(); advanceUntilIdle()
        assertFalse(settings.value.glow)
        vm.toggleGlow(); advanceUntilIdle()
        assertTrue(settings.value.glow)
    }

    @Test
    fun toggleCrossfade_flips() = runTest(dispatcher) {
        val settings = FakeSettings(UserSettings(crossfade = false))
        val vm = SettingsViewModel(FakeContainer(settings))

        vm.toggleCrossfade(); advanceUntilIdle()
        assertTrue(settings.value.crossfade)
    }

    // ----------------------------------------------------------------- helpers / fakes

    private fun trk(name: String) = Track(
        id = name.hashCode().toLong(), uri = "file://$name", title = name, artist = "AX", album = "ALB",
        albumId = 0, durationMs = 1000, displayName = name, relativePath = "", mimeType = "audio/mpeg",
        bitrate = null, trackNumber = 1, year = null,
    )

    /** SettingsRepository funcional: `update{}` aplica o transform ao StateFlow observável. */
    private class FakeSettings(initial: UserSettings) : SettingsRepository {
        private val _s = MutableStateFlow(initial)
        val value: UserSettings get() = _s.value
        override val settings: Flow<UserSettings> = _s
        override val eq: Flow<EqSettings> = MutableStateFlow(initial.eq)
        override val lrcTreeUri: Flow<String?> = MutableStateFlow(initial.lrcTreeUri)
        override suspend fun update(transform: (UserSettings) -> UserSettings) { _s.value = transform(_s.value) }
        override suspend fun updateEq(transform: (EqSettings) -> EqSettings) { _s.value = _s.value.copy(eq = transform(_s.value.eq)) }
    }

    private class FakeLibrary(initial: LibraryState = LibraryState()) : LibraryRepository {
        override val state = MutableStateFlow(initial)
        override fun rescan() {}
    }

    private class FakeAudioPlayer : AudioPlayer {
        override val state = MutableStateFlow(PlaybackState())
        override val volume = MutableStateFlow(VolumeState())
        override fun connect() {}
        override fun setQueue(items: List<PlaybackItem>, startIndex: Int, sourceLabel: String) {}
        override fun play() {}
        override fun pause() {}
        override fun toggle() {}
        override fun next() {}
        override fun previous() {}
        override fun seekTo(positionMs: Long) {}
        override fun seekToFraction(fraction: Float) {}
        override fun seekToIndex(index: Int) {}
        override fun setShuffle(enabled: Boolean) {}
        override fun setRepeat(mode: RepeatMode) {}
        override fun shuffleQueue() {}
        override fun clearQueue() {}
        override fun clearUpcoming() {}
        override fun setVolume(fraction: Float) {}
        override fun toggleMute() {}
        override fun release() {}
    }

    private class FakePlaylists : PlaylistRepository {
        override val playlists: StateFlow<List<Playlist>> = MutableStateFlow(emptyList())
        override fun toggleFavorite(key: String) {}
        override fun addTrack(id: String, key: String) {}
        override fun removeTrack(id: String, key: String) {}
    }

    private class FakeLyrics : LyricsRepository {
        override val version: StateFlow<Int> = MutableStateFlow(0)
        override suspend fun load(track: Track, treeUri: String?): List<LyricLine> = emptyList()
    }

    private class FakeContainer(
        override val settings: SettingsRepository,
        override val library: LibraryRepository = FakeLibrary(),
    ) : AppContainer {
        override val lyrics: LyricsRepository = FakeLyrics()
        override val playlists: PlaylistRepository = FakePlaylists()
        override val audioPlayer: AudioPlayer = FakeAudioPlayer()
    }
}
