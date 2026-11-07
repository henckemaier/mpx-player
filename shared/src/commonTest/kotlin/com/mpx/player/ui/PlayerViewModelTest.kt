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
import com.mpx.player.shared.playback.AudioPlayer
import com.mpx.player.shared.playback.PlaybackItem
import com.mpx.player.shared.playback.PlaybackState
import com.mpx.player.shared.playback.RepeatMode
import com.mpx.player.shared.playback.VolumeState
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
import kotlin.test.assertTrue

/**
 * Teste multiplataforma do [PlayerViewModel] (commonTest → roda em :shared:testDebugUnitTest E
 * iosSimulatorArm64Test). Além de checar o mapeamento PlaybackState→PlayerUi e a delegação das
 * ações ao player, este é o teste que confirma o **viewModelScope multiplataforma funcionando no
 * runtime iOS**: o `.state()` (stateIn/WhileSubscribed) vive no viewModelScope e, com um coletor
 * ativo, precisa emitir no iOS igual ao Android.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setUp() {
        // viewModelScope usa Dispatchers.Main; aponta p/ o test dispatcher (mesmo scheduler do runTest).
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun ui_reflectsPlaybackState() = runTest(dispatcher) {
        val player = FakeAudioPlayer()
        val tracks = listOf(track("a.mp3"), track("b.mp3"))
        val vm = PlayerViewModel(FakeContainer(player, FakeLibrary(LibraryState(tracks = tracks))), NoopTechReader())

        // ui é stateIn(WhileSubscribed) → precisa de coletor ativo p/ a cadeia ligar.
        val job = backgroundScope.launch { vm.ui.collect {} }

        player.state.value = PlaybackState(
            queueIds = listOf("a.mp3", "b.mp3"),
            index = 1,
            isPlaying = true,
            positionMs = 1234,
            durationMs = 5000,
            shuffle = true,
            repeat = RepeatMode.ALL,
        )
        advanceUntilIdle()

        val ui = vm.ui.value
        assertEquals("b.mp3", ui.current?.key)
        assertEquals(2, ui.queue.size)
        assertEquals(1, ui.index)
        assertEquals(1234L, ui.positionMs)
        assertEquals(5000L, ui.durationMs)
        assertTrue(ui.isPlaying)
        assertTrue(ui.shuffle)
        assertEquals(RepeatMode.ALL, ui.repeat)
        job.cancel()
    }

    @Test
    fun durationFallsBackToTrackWhenStateHasNone() = runTest(dispatcher) {
        val player = FakeAudioPlayer()
        val vm = PlayerViewModel(
            FakeContainer(player, FakeLibrary(LibraryState(tracks = listOf(track("a.mp3", durationMs = 180_000))))),
            NoopTechReader(),
        )
        val job = backgroundScope.launch { vm.ui.collect {} }
        player.state.value = PlaybackState(queueIds = listOf("a.mp3"), index = 0, durationMs = 0)
        advanceUntilIdle()
        assertEquals(180_000L, vm.ui.value.durationMs) // durationMs=0 no state → cai p/ a da faixa
        job.cancel()
    }

    @Test
    fun actions_delegateToPlayer() = runTest(dispatcher) {
        val player = FakeAudioPlayer()
        val vm = PlayerViewModel(FakeContainer(player, FakeLibrary(LibraryState())), NoopTechReader())

        vm.toggle()
        assertEquals(1, player.toggleCount)
        vm.next()
        assertEquals(1, player.nextCount)
        vm.previous()
        assertEquals(1, player.previousCount)
        vm.seek(0.5f)
        assertEquals(0.5f, player.lastSeekFraction)
        vm.playIndex(3)
        assertEquals(3, player.lastSeekIndex)
        vm.shuffleQueue()
        assertEquals(1, player.shuffleQueueCount)

        // toggleShuffle lê o state atual (shuffle=false) e manda o inverso.
        vm.toggleShuffle()
        assertEquals(true, player.lastShuffle)
        // cycleRepeat: OFF -> ALL.
        vm.cycleRepeat()
        assertEquals(RepeatMode.ALL, player.lastRepeat)
    }
}

// ----------------------------------------------------------------- helpers / fakes

private fun track(name: String, durationMs: Long = 0L): Track = Track(
    id = name.hashCode().toLong(),
    uri = "file://$name",
    title = name,
    artist = "ARTIST",
    album = "ALBUM",
    albumId = 0L,
    durationMs = durationMs,
    displayName = name,
    relativePath = "",
    mimeType = "audio/mpeg",
    bitrate = null,
    trackNumber = 1,
    year = null,
) // key = (relativePath + displayName).lowercase() = name

private class NoopTechReader : TechInfoReader {
    override suspend fun read(track: Track): TechInfo = TechInfo(format = track.format)
}

private class FakeAudioPlayer : AudioPlayer {
    override val state = MutableStateFlow(PlaybackState())
    override val volume = MutableStateFlow(VolumeState())

    var toggleCount = 0; private set
    var nextCount = 0; private set
    var previousCount = 0; private set
    var shuffleQueueCount = 0; private set
    var lastSeekFraction: Float? = null; private set
    var lastSeekIndex: Int? = null; private set
    var lastShuffle: Boolean? = null; private set
    var lastRepeat: RepeatMode? = null; private set

    override fun connect() {}
    override fun setQueue(items: List<PlaybackItem>, startIndex: Int, sourceLabel: String) {}
    override fun play() {}
    override fun pause() {}
    override fun toggle() { toggleCount++ }
    override fun next() { nextCount++ }
    override fun previous() { previousCount++ }
    override fun seekTo(positionMs: Long) {}
    override fun seekToFraction(fraction: Float) { lastSeekFraction = fraction }
    override fun seekToIndex(index: Int) { lastSeekIndex = index }
    override fun setShuffle(enabled: Boolean) { lastShuffle = enabled }
    override fun setRepeat(mode: RepeatMode) { lastRepeat = mode }
    override fun shuffleQueue() { shuffleQueueCount++ }
    override fun clearQueue() {}
    override fun clearUpcoming() {}
    override fun setVolume(fraction: Float) {}
    override fun toggleMute() {}
    override fun release() {}
}

private class FakeLibrary(initial: LibraryState) : LibraryRepository {
    override val state = MutableStateFlow(initial)
    override fun rescan() {}
}

private class FakeSettings : SettingsRepository {
    override val eq: Flow<EqSettings> = MutableStateFlow(EqSettings())
    override val lrcTreeUri: Flow<String?> = MutableStateFlow<String?>(null)
    override suspend fun updateEq(transform: (EqSettings) -> EqSettings) {}
}

private class FakeLyrics : LyricsRepository {
    override val version: StateFlow<Int> = MutableStateFlow(0)
    override suspend fun load(track: Track, treeUri: String?): List<LyricLine> = emptyList()
}

private class FakePlaylists : PlaylistRepository {
    override val playlists: StateFlow<List<Playlist>> = MutableStateFlow(emptyList())
    override fun toggleFavorite(key: String) {}
    override fun addTrack(id: String, key: String) {}
}

private class FakeContainer(
    override val audioPlayer: AudioPlayer,
    override val library: LibraryRepository,
) : AppContainer {
    override val settings: SettingsRepository = FakeSettings()
    override val lyrics: LyricsRepository = FakeLyrics()
    override val playlists: PlaylistRepository = FakePlaylists()
}
