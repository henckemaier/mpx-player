package com.mpx.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ComposeUIViewController
import com.mpx.player.data.IosTechInfoReader
import com.mpx.player.di.AppContainer
import com.mpx.player.di.createAppContainer
import com.mpx.player.model.UserSettings
import com.mpx.player.ui.NowPlayingActions
import com.mpx.player.ui.NowPlayingPanel
import com.mpx.player.ui.PlayerViewModel
import com.mpx.player.ui.theme.Mpx
import com.mpx.player.ui.theme.MpxTheme
import platform.UIKit.UIViewController

/**
 * Ponto de entrada iOS do :shared. O host SwiftUI (iosApp/) consome este símbolo como
 * `MainViewControllerKt.MainViewController()` via `import Shared`.
 *
 * Fatia "estado real" (P7): o host agora liga o [NowPlayingPanel] ao [PlayerViewModel] REAL
 * (migrado p/ commonMain pelo Forja) em vez dos dados [sampleUi] estáticos — o painel passa a
 * refletir o estado vivo do player (`vm.ui`). O teste A/B offscreen (NowPlayingPanelRenderTest)
 * CONTINUA usando sampleUi determinístico — o HOST renderiza live, o TESTE renderiza sample;
 * não se misturam.
 *
 * ESPERADO no simulador: a biblioteca iOS é vazia (MPMediaQuery sem device) → `ui = PlayerUi()`
 * → o painel mostra NO_SIGNAL. Conteúdo real só no iPhone. Isso é registro, não falha.
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

fun MainViewController(): UIViewController = ComposeUIViewController {
    val vm = playerViewModel
    val ui by vm.ui.collectAsState()
    // vm é singleton estável → as ações (method refs) são montadas uma vez, não a cada recomposição.
    val actions = remember { iosActions(vm) }
    MpxTheme(UserSettings()) {
        Box(Modifier.fillMaxSize().background(Mpx.colors.bg).padding(10.dp)) {
            NowPlayingPanel(ui, actions)
        }
    }
}

/**
 * Mapeia os 11 callbacks do [NowPlayingActions] para o [PlayerViewModel]. As 6 do painel
 * (toggle/next/previous/seek/shuffle/repeat) + volume/mute/favorito/playlist; `openLibrary`
 * é no-op nesta fatia (navegação entra depois).
 */
private fun iosActions(vm: PlayerViewModel): NowPlayingActions = NowPlayingActions(
    toggle = vm::toggle,
    next = vm::next,
    previous = vm::previous,
    seek = vm::seek,
    shuffle = vm::toggleShuffle,
    repeat = vm::cycleRepeat,
    setVolume = vm::setVolume,
    mute = vm::toggleMute,
    openLibrary = {},
    toggleFavorite = vm::toggleFavorite,
    addToPlaylist = vm::addCurrentToPlaylist,
)
