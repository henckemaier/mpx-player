package com.mpx.player.spike

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

/**
 * Ponto de entrada iOS do spike (consumido por um host Xcode — que é a Fase 3).
 * Para a Fase 1 basta o framework LINKAR (CI: linkDebugFrameworkIosSimulatorArm64).
 */
fun MainViewController(): UIViewController = ComposeUIViewController { CrtSpikeRoot() }
