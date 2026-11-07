package com.mpx.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ComposeUIViewController
import com.mpx.player.data.CoverLoader
import com.mpx.player.data.IosAlbumArtSource
import com.mpx.player.data.IosTechInfoReader
import com.mpx.player.di.AppContainer
import com.mpx.player.di.createAppContainer
import com.mpx.player.ui.LibraryViewModel
import com.mpx.player.ui.PlayerViewModel
import com.mpx.player.ui.SettingsViewModel
import com.mpx.player.ui.components.LocalCoverLoader
import com.mpx.player.ui.components.TextButton
import com.mpx.player.ui.screens.LibraryActions
import com.mpx.player.ui.screens.LibraryScreen
import com.mpx.player.ui.screens.SettingsActions
import com.mpx.player.ui.screens.SettingsScreen
import com.mpx.player.ui.theme.LocalLandscape
import com.mpx.player.ui.theme.Mpx
import com.mpx.player.ui.theme.MpxTheme
import platform.UIKit.UIViewController

/**
 * Ponto de entrada iOS do :shared. O host SwiftUI (iosApp/) consome este símbolo como
 * `MainViewControllerKt.MainViewController()` via `import Shared`.
 *
 * Fatia LIBRARY (P7): o host renderiza a [LibraryScreen] REAL sobre um [LibraryViewModel]
 * (comum) ligado ao container iOS, com browse real (filtro/busca/sort/play/playlist). Os 3
 * gatilhos de diálogo (edição/criação/exclusão) são no-op no iOS (dependem de device/Uri — TODO).
 * O mini-player reusa `vm.ui` do [PlayerViewModel] já ligado. O teste A/B offscreen
 * (NowPlayingPanelRenderTest) é INDEPENDENTE do host (renderiza `sampleUi`) e segue intacto.
 *
 * Fatia SETTINGS (P7): seletor-de-raiz MÍNIMO (decisão A do Maestro) — uma barrinha CRT de 2 botões
 * alterna entre [LibraryScreen] e [SettingsScreen]. NÃO é navegação real (sem back-stack, sem
 * transição; a nav real é fatia futura) — apenas um `root` em `remember`. Nenhum host regride: a
 * Biblioteca continua acessível. O [SettingsViewModel] comum persiste em NSUserDefaults (toggles de
 * tema/cor/fonte aparecem AO VIVO porque o [MpxTheme] é dirigido pelo `settings` REAL do VM). Os
 * slots Android-only (BAIXAR/IMPORTAR/UPDATE/SAÍDA) e CHECK UPDATE/REBOOT ficam null no iOS → somem
 * (nenhum botão morto). ESPERADO no simulador: toggles persistem; contadores de biblioteca 0
 * (MPMediaQuery vazia). Registro, não bug.
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
private val settingsViewModel: SettingsViewModel by lazy { SettingsViewModel(appContainer) }

// Loader de capa do host LIVE: fonte REAL do iOS (MPMediaItemArtwork é follow-up → no-op por ora,
// logo o painel cai na capa gerada no simulador). O sample do A/B NÃO passa por aqui (ver
// NowPlayingPanelRenderTest + sampleCoverArt). Singleton de arquivo, criado 1x.
private val coverLoader: CoverLoader by lazy { CoverLoader(IosAlbumArtSource()) }

/** Raiz exibida pelo host iOS. Seletor mínimo (não é back-stack/navegação real — fatia futura). */
private enum class Root { LIBRARY, SETTINGS }

fun MainViewController(): UIViewController = ComposeUIViewController {
    val libUi by libraryViewModel.ui.collectAsState()
    val playerUi by playerViewModel.ui.collectAsState()
    val settingsUi by settingsViewModel.ui.collectAsState()
    // VMs singletons estáveis → as ações (method refs) são montadas uma vez, não a cada recomposição.
    val libActions = remember { iosLibActions(libraryViewModel) }
    val setActions = remember { iosSettingsActions(settingsViewModel) }
    var root by remember { mutableStateOf(Root.LIBRARY) }
    // MpxTheme dirigido pelo `settings` REAL do VM: os toggles de tema/cor/fonte da tela CONFIG
    // recolorem a UI inteira ao vivo (não um UserSettings() fixo).
    MpxTheme(settingsUi.settings) {
        CompositionLocalProvider(
            LocalCoverLoader provides coverLoader,
            LocalLandscape provides false,
        ) {
            Column(Modifier.fillMaxSize().background(Mpx.colors.bg).padding(10.dp)) {
                // Barrinha CRT de troca de raiz (SEM back-stack/transição; não é nav real).
                Row(
                    Modifier.fillMaxWidth().height(40.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    TextButton(
                        "BIBLIOTECA", Modifier.weight(1f).fillMaxHeight(),
                        filled = root == Root.LIBRARY, display = true,
                        onClick = { root = Root.LIBRARY },
                    )
                    TextButton(
                        "CONFIG", Modifier.weight(1f).fillMaxHeight(),
                        filled = root == Root.SETTINGS, display = true,
                        onClick = { root = Root.SETTINGS },
                    )
                }
                Spacer(Modifier.height(10.dp))
                Box(Modifier.fillMaxSize()) {
                    when (root) {
                        Root.LIBRARY -> LibraryScreen(libUi, playerUi, libActions)
                        // Slots Android = null (BAIXAR/IMPORTAR/UPDATE/SAÍDA somem); onReboot/
                        // onCheckUpdate = null (linha de botões some). Sem botão morto no iOS.
                        Root.SETTINGS -> SettingsScreen(settingsUi, setActions, onReboot = null)
                    }
                }
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

/**
 * [SettingsActions] do host iOS: aparência/cores/fonte/capa + toggles de áudio delegados ao
 * [SettingsViewModel] comum (persistem em NSUserDefaults via `IosSettingsRepository`). `onScan`
 * dispara a (re)varredura neutra do repositório. As ações Android-only (SAF/import, download,
 * auto-update, lyrics-sync) NÃO existem aqui — chegam pelos slots, que são null no iOS.
 */
private fun iosSettingsActions(vm: SettingsViewModel): SettingsActions = SettingsActions(
    onSetTheme = vm::setTheme,
    onSetCustomColor = vm::setCustomColor,
    onSetOled = vm::setOled,
    onSetFont = vm::setFont,
    onSetCover = vm::setCover,
    onToggleScanlines = vm::toggleScanlines,
    onToggleGlow = vm::toggleGlow,
    onToggleCursor = vm::toggleCursor,
    onToggleMono = vm::toggleMono,
    onToggleCrossfade = vm::toggleCrossfade,
    onClearMessage = vm::clearMessage,
    onScan = vm::rescan,
)
