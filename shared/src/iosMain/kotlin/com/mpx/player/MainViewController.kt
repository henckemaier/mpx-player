package com.mpx.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ComposeUIViewController
import com.mpx.player.data.CoverLoader
import com.mpx.player.data.IosAlbumArtSource
import com.mpx.player.data.IosTechInfoReader
import com.mpx.player.di.AppContainer
import com.mpx.player.di.createAppContainer
import com.mpx.player.model.UserSettings
import com.mpx.player.ui.LibraryViewModel
import com.mpx.player.ui.PlayerViewModel
import com.mpx.player.ui.components.LocalCoverLoader
import com.mpx.player.ui.screens.LibraryActions
import com.mpx.player.ui.screens.LibraryScreen
import com.mpx.player.ui.theme.LocalLandscape
import com.mpx.player.ui.theme.Mpx
import com.mpx.player.ui.theme.MpxTheme
import platform.UIKit.UIViewController

/**
 * Ponto de entrada iOS do :shared. O host SwiftUI (iosApp/) consome este símbolo como
 * `MainViewControllerKt.MainViewController()` via `import Shared`.
 *
 * Fatia LIBRARY (P7): o host agora renderiza a [LibraryScreen] REAL sobre um [LibraryViewModel]
 * (comum) ligado ao container iOS, com browse real (filtro/busca/sort/play/playlist). Os 3
 * gatilhos de diálogo (edição/criação/exclusão) são no-op no iOS (dependem de device/Uri — TODO).
 * O mini-player reusa `vm.ui` do [PlayerViewModel] já ligado. O teste A/B offscreen
 * (NowPlayingPanelRenderTest) é INDEPENDENTE do host (renderiza `sampleUi`) e segue intacto.
 *
 * ESPERADO no simulador: a biblioteca iOS é vazia (MPMediaQuery sem device) → `LibraryUi` cai em
 * `totalFiles == 0` ("> 0 FILES / NENHUM ÁUDIO..."). Conteúdo real só no iPhone. Registro, não falha.
 *
 * Mora em iosMain → só compila em host macOS; no Windows o source set é ignorado.
 */

// Container + VM criados UMA vez por processo (lazy singletons de arquivo), nunca por
// recomposição. O connect() (ativa a AVAudioSession .playback) roda 1x, aqui, no momento em
// que o container nasce — é o "init/once" pedido pelo wiring.
private val appContainer: AppContainer by lazy {
    createAppContainer().also { it.audioPlayer.connect() }
}
private val playerViewModel: PlayerViewModel by lazy {
    PlayerViewModel(appContainer, IosTechInfoReader())
}
private val libraryViewModel: LibraryViewModel by lazy { LibraryViewModel(appContainer) }

// Loader de capa do host LIVE: fonte REAL do iOS (MPMediaItemArtwork é follow-up → no-op por ora,
// logo o painel cai na capa gerada no simulador). O sample do A/B NÃO passa por aqui (ver
// NowPlayingPanelRenderTest + sampleCoverArt). Singleton de arquivo, criado 1x.
private val coverLoader: CoverLoader by lazy { CoverLoader(IosAlbumArtSource()) }

fun MainViewController(): UIViewController = ComposeUIViewController {
    val libUi by libraryViewModel.ui.collectAsState()
    val playerUi by playerViewModel.ui.collectAsState()
    // VMs singletons estáveis → as ações (method refs) são montadas uma vez, não a cada recomposição.
    val actions = remember { iosLibActions(libraryViewModel) }
    MpxTheme(UserSettings()) {
        CompositionLocalProvider(
            LocalCoverLoader provides coverLoader,
            LocalLandscape provides false,
        ) {
            Box(Modifier.fillMaxSize().background(Mpx.colors.bg).padding(10.dp)) {
                LibraryScreen(libUi, playerUi, actions)
            }
        }
    }
}

/**
 * [LibraryActions] do host iOS: browse REAL delegado ao [LibraryViewModel] (filtro/busca/sort/
 * play álbum-artista-faixa-playlist/abrir-fechar/remover). Os 3 gatilhos de diálogo
 * (onTrackLong/onNewPlaylist/onEditPlaylist) são NO-OP — a edição/exclusão depende de device/Uri
 * (TODO). `onOpenPlayer` é no-op nesta fatia (navegação entra depois).
 */
private fun iosLibActions(vm: LibraryViewModel): LibraryActions = LibraryActions(
    onFilter = vm::setFilter,
    onQuery = vm::setQuery,
    onToggleSort = vm::toggleSort,
    onPlayAlbum = vm::playAlbum,
    onPlayArtist = vm::playArtist,
    onPlayTrack = vm::playTrack,
    onOpenPlaylist = vm::openPlaylist,
    onClosePlaylist = vm::closePlaylist,
    onPlayPlaylist = vm::playPlaylist,
    onRemoveFromPlaylist = vm::removeFromPlaylist,
    onOpenPlayer = {},
    onTrackLong = {},
    onNewPlaylist = {},
    onEditPlaylist = {},
)
