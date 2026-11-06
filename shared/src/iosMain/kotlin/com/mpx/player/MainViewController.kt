package com.mpx.player

import androidx.compose.ui.window.ComposeUIViewController
import com.mpx.player.ui.SmokeScreen
import platform.UIKit.UIViewController

/**
 * Ponto de entrada iOS do :shared (P7 F7.1). O host SwiftUI (iosApp/) consome este
 * símbolo como `MainViewControllerKt.MainViewController()` via `import Shared`.
 *
 * Mora em iosMain → só é compilado em host macOS (onde os alvos iOS existem); no
 * Windows o source set não tem compilação e o arquivo é ignorado.
 */
fun MainViewController(): UIViewController = ComposeUIViewController { SmokeScreen() }
