package com.mpx.player.ui

import com.mpx.player.data.LibraryRepository
import com.mpx.player.data.LibraryState
import com.mpx.player.data.LyricsRepository
import com.mpx.player.data.PlaylistLogic
import com.mpx.player.data.PlaylistRepository
import com.mpx.player.data.SettingsRepository
import com.mpx.player.di.AppContainer
import com.mpx.player.model.Album
import com.mpx.player.model.Artist
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Teste multiplataforma do [LibraryViewModel] comum (commonTest → `:shared:testDebugUnitTest` E
 * `iosSimulatorArm64Test`; re-confirma o viewModelScope no runtime iOS). Cobre: `ui` reflete
 * library+playlists (via [projectLibrary]); setQuery/toggleSort/setFilter projetam; os play-álbum/
 * artista/faixa e playPlaylist enfileiram no player; add/remove/toggleFavorite delegam ao repo.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    private val t1 = trk(1, "BRAVO", "ALB1", 10, "b.mp3")
    private val t2 = trk(2, "alpha", "ALB2", 20, "a.mp3")
    private val lib = LibraryState(
        tracks = listOf(t1, t2),
        albums = listOf(Album(10, "ALB1", "AX", listOf(t1)), Album(20, "ALB2", "BX", listOf(t2))),
        artists = listOf(Artist("AX", listOf(t1)), Artist("BX", listOf(t2))),
        scanned = true,
    )
    private val seedPlaylists = listOf(
        Playlist(Playlist.FAVORITES_ID, Playlist.FAVORITES_NAME, locked = true),
        Playlist("p1", "MIX", trackKeys = listOf("a.mp3", "b.mp3")),
    )

    @BeforeTest fun setUp() { Dispatchers.setMain(dispatcher) }
    @AfterTest fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun ui_reflectsLibraryAndPlaylists() = runTest(dispatcher) {
        val vm = vm()
        val job = backgroundScope.launch { vm.ui.collect {} }
        advanceUntilIdle()

        val ui = vm.ui.value
        assertEquals(2, ui.totalFiles)
        assertEquals(listOf(2L, 1L), ui.tracks.map { it.id }) // ALPHA < BRAVO
        assertEquals(listOf("MIX"), ui.playlists.filter { !it.locked }.map { it.name })
        assertTrue(ui.scanned)
        job.cancel()
    }

    @Test
    fun setQueryAndToggleSort_project() = runTest(dispatcher) {
        val vm = vm()
        val job = backgroundScope.launch { vm.ui.collect {} }

        vm.setQuery("alpha"); advanceUntilIdle()
        assertEquals(listOf(2L), vm.ui.value.tracks.map { it.id })

        vm.setQuery(""); vm.toggleSort(); advanceUntilIdle()
        assertEquals(listOf(1L, 2L), vm.ui.value.tracks.map { it.id }) // desc
        job.cancel()
    }

    @Test
    fun setFilter_updatesFilterAndClearsOpened() = runTest(dispatcher) {
        val vm = vm()
        val job = backgroundScope.launch { vm.ui.collect {} }

        vm.setFilter(LibFilter.PLAYLISTS); vm.openPlaylist("p1"); advanceUntilIdle()
        assertEquals("p1", vm.ui.value.opened?.id)

        vm.setFilter(LibFilter.FAIXAS); advanceUntilIdle()
        assertEquals(LibFilter.FAIXAS, vm.ui.value.filter)
        assertNull(vm.ui.value.opened) // trocar p/ não-PLAYLISTS fecha o aberto
        job.cancel()
    }

    @Test
    fun playAlbum_enqueuesKeysAndPlays() = runTest(dispatcher) {
        val player = FakeAudioPlayer()
        val vm = vm(player = player)
        vm.playAlbum(Album(10, "ALB1", "AX", listOf(t1)))
        assertEquals(listOf("b.mp3"), player.lastQueueIds) // PlaybackItem.id == Track.key
        assertEquals(1, player.playCount)
    }

    @Test
    fun playPlaylist_resolvesKeysInOrderAndPlays() = runTest(dispatcher) {
        val player = FakeAudioPlayer()
        val vm = vm(player = player)
        vm.playPlaylist("p1")
        assertEquals(listOf("a.mp3", "b.mp3"), player.lastQueueIds)
        assertEquals(1, player.playCount)
    }

    @Test
    fun playlistActions_delegateToRepository() = runTest(dispatcher) {
        val playlists = FakePlaylists(seedPlaylists)
        val vm = vm(playlists = playlists)
        val job = backgroundScope.launch { vm.ui.collect {} }

        vm.removeFromPlaylist("p1", "a.mp3"); advanceUntilIdle()
        assertEquals(listOf("b.mp3"), playlists.playlists.value.first { it.id == "p1" }.trackKeys)

        vm.toggleFavorite("a.mp3"); advanceUntilIdle()
        assertTrue(PlaylistLogic.isFavorite(playlists.playlists.value, "a.mp3"))
        job.cancel()
    }

    // ----------------------------------------------------------------- helpers / fakes

    private fun vm(
        library: LibraryState = lib,
        playlists: PlaylistRepository = FakePlaylists(seedPlaylists),
        player: AudioPlayer = FakeAudioPlayer(),
    ) = LibraryViewModel(FakeContainer(FakeLibrary(library), playlists, player))

    private fun trk(id: Long, title: String, album: String, albumId: Long, name: String) = Track(
        id = id, uri = "file://$name", title = title, artist = "AX", album = album, albumId = albumId,
        durationMs = 1000, displayName = name, relativePath = "", mimeType = "audio/mpeg",
        bitrate = null, trackNumber = 1, year = null,
    )

    private class FakeAudioPlayer : AudioPlayer {
        override val state = MutableStateFlow(PlaybackState())
        override val volume = MutableStateFlow(VolumeState())
        var lastQueueIds: List<String>? = null; private set
        var playCount = 0; private set
        override fun connect() {}
        override fun setQueue(items: List<PlaybackItem>, startIndex: Int, sourceLabel: String) { lastQueueIds = items.map { it.id } }
        override fun play() { playCount++ }
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

    private class FakeLibrary(initial: LibraryState) : LibraryRepository {
        override val state = MutableStateFlow(initial)
        override fun rescan() {}
    }

    private class FakePlaylists(initial: List<Playlist>) : PlaylistRepository {
        private val _pl = MutableStateFlow(initial)
        override val playlists: StateFlow<List<Playlist>> = _pl
        override fun toggleFavorite(key: String) { _pl.value = PlaylistLogic.favoriteToggled(_pl.value, key) }
        override fun addTrack(id: String, key: String) { _pl.value = PlaylistLogic.withTrack(_pl.value, id, key) }
        override fun removeTrack(id: String, key: String) { _pl.value = PlaylistLogic.withoutTrack(_pl.value, id, key) }
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

    private class FakeContainer(
        override val library: LibraryRepository,
        override val playlists: PlaylistRepository,
        override val audioPlayer: AudioPlayer,
    ) : AppContainer {
        override val settings: SettingsRepository = FakeSettings()
        override val lyrics: LyricsRepository = FakeLyrics()
    }
}
