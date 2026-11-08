package com.mpx.player.debug

import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.core.view.doOnPreDraw
import androidx.core.view.drawToBitmap
import com.mpx.player.model.UserSettings
import com.mpx.player.ui.sampleBootBarOverride
import com.mpx.player.ui.sampleBootEqPreset
import com.mpx.player.ui.sampleBootFileCount
import com.mpx.player.ui.sampleBootHasPermission
import com.mpx.player.ui.sampleBootScanned
import com.mpx.player.ui.sampleBootStepOverride
import com.mpx.player.ui.sampleBootVersion
import com.mpx.player.ui.screens.BootScreen
import com.mpx.player.ui.theme.LocalLandscape
import com.mpx.player.ui.theme.Mpx
import com.mpx.player.ui.theme.MpxTheme
import java.io.File
import java.io.FileOutputStream

/**
 * SO-DEBUG (app/src/debug): harness one-off p/ o BASELINE Android do 8o par A/B — a tela BOOT no
 * FRAME TERMINAL (P7 fatia boot c2, o ULTIMO par). Renderiza EXATAMENTE o mesmo conteudo do harness
 * iOS offscreen ([com.mpx.player.ui.BootScreenRenderTest]): MpxTheme(UserSettings()){ LocalLandscape=
 * false { Box(bg, padding 10dp){ BootScreen(sampleBoot*, stepOverride=6, barOverride=20) } } }, a
 * 393x800 px com DENSITY=1f — casando o ImageComposeScene(393,800,Density(1f)) do iOS. O fixture
 * (sampleBoot*) vive no :shared commonMain e e COMPARTILHADO -> zero drift.
 *
 * FRAME TERMINAL: stepOverride=6 + barOverride=20 -> os LaunchedEffect NAO rodam (estado "ready"
 * final, deterministico); so o blinkingCursor do "//PLAYER" anima. hasPermission=true + scanned=true.
 * fileCount=12345 -> "12.345". cover=None (Boot sem capa).
 *
 * NAO altera as Activities existentes (os 7 pares intactos); vive em paralelo. NAO ship em release.
 *
 * Captura (coordenada pelo Bigorna no emulador/device):
 *   adb shell am force-stop com.mpx.player
 *   adb shell am start -n com.mpx.player/com.mpx.player.debug.AbBootBaselineActivity
 *   adb logcat -d -s AB_BASELINE_BOOT   # pega a linha "WROTE <path> 393x800"
 *   adb pull <path> ab/boot-android.png
 */
class AbBootBaselineActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val compose = ComposeView(this).apply {
            setContent {
                // Density fixa 1f: espelha ImageComposeScene(..., density = Density(1f)) do iOS.
                CompositionLocalProvider(
                    LocalDensity provides Density(1f),
                    LocalLandscape provides false,
                ) {
                    MpxTheme(UserSettings()) {
                        Box(Modifier.fillMaxSize().background(Mpx.colors.bg).padding(10.dp)) {
                            BootScreen(
                                hasPermission = sampleBootHasPermission,
                                scanned = sampleBootScanned,
                                fileCount = sampleBootFileCount,
                                eqPreset = sampleBootEqPreset,
                                version = sampleBootVersion,
                                onRequestPermission = {},
                                onEnter = {},
                                stepOverride = sampleBootStepOverride,
                                barOverride = sampleBootBarOverride,
                            )
                        }
                    }
                }
            }
        }

        // ComposeView em tamanho EXATO 393x800 px (com density 1f, == 393x800 dp da tela).
        val container = FrameLayout(this)
        container.addView(compose, FrameLayout.LayoutParams(WIDTH_PX, HEIGHT_PX))
        setContentView(container)

        // 1o pre-draw: conteudo ja composto/medido em 393x800; captura antes de ticks animarem —
        // casa o 1o frame do iOS (override congela step/bar, so o cursor piscaria depois).
        compose.doOnPreDraw {
            runCatching {
                val bmp: Bitmap = compose.drawToBitmap()
                val out = File(getExternalFilesDir(null), OUT_NAME)
                FileOutputStream(out).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                Log.i(TAG, "WROTE ${out.absolutePath} ${bmp.width}x${bmp.height}")
            }.onFailure { Log.e(TAG, "FALHA ao gravar baseline: $it") }
        }
    }

    private companion object {
        const val WIDTH_PX = 393
        const val HEIGHT_PX = 800
        const val OUT_NAME = "boot-android.png"
        const val TAG = "AB_BASELINE_BOOT"
    }
}
