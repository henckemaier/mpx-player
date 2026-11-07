package com.mpx.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ComposeUIViewController
import com.mpx.player.model.UserSettings
import com.mpx.player.ui.NowPlayingPanel
import com.mpx.player.ui.noopActions
import com.mpx.player.ui.sampleUi
import com.mpx.player.ui.theme.Mpx
import com.mpx.player.ui.theme.MpxTheme
import platform.UIKit.UIViewController

/**
 * Ponto de entrada iOS do :shared. O host SwiftUI (iosApp/) consome este símbolo como
 * `MainViewControllerKt.MainViewController()` via `import Shared`.
 *
 * Fatia fina de UI (P7): renderiza o [NowPlayingPanel] real (tema GREEN/VT323 default, dados
 * [sampleUi] determinísticos, ações no-op) no lugar da SmokeScreen — é o painel que o gate
 * visual A/B compara com o Android. A `SmokeScreen()` (em SmokeScreen.kt) NÃO foi removida;
 * só deixou de ser o entrypoint.
 *
 * Mora em iosMain → só compila em host macOS; no Windows o source set é ignorado.
 */
fun MainViewController(): UIViewController = ComposeUIViewController {
    MpxTheme(UserSettings()) {
        Box(Modifier.fillMaxSize().background(Mpx.colors.bg).padding(10.dp)) {
            NowPlayingPanel(sampleUi, noopActions)
        }
    }
}
