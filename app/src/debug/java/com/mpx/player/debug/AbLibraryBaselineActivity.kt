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
import com.mpx.player.ui.noopLibActions
import com.mpx.player.ui.sampleLibraryPlayer
import com.mpx.player.ui.sampleLibraryUi
import com.mpx.player.ui.screens.LibraryScreen
import com.mpx.player.ui.theme.LocalLandscape
import com.mpx.player.ui.theme.Mpx
import com.mpx.player.ui.theme.MpxTheme
import java.io.File
import java.io.FileOutputStream

/**
 * SÓ-DEBUG (app/src/debug): harness one-off p/ o BASELINE Android do A/B visual da BIBLIOTECA (P7
 * fatia library c2). Renderiza EXATAMENTE o mesmo conteúdo do harness iOS offscreen
 * ([com.mpx.player.ui.LibraryScreenRenderTest], caso PRIMÁRIO = filtro PLAYLISTS): MpxTheme(
 * UserSettings()){ LocalLandscape=false { Box(bg, padding 10dp){ LibraryScreen(sampleLibraryUi,
 * sampleLibraryPlayer, noopLibActions) } } }, a 393x600 px com DENSITY=1f — casando o
 * tamanho/densidade do ImageComposeScene(393,600,Density(1f)) do iOS.
 *
 * NÃO altera a [AbBaselineActivity] (NowPlaying); vive em paralelo. NÃO ship em release.
 *
 * Captura (coordenada pelo Bigorna no emulador/device):
 *   adb shell am force-stop com.mpx.player
 *   adb shell am start -n com.mpx.player/com.mpx.player.debug.AbLibraryBaselineActivity
 *   adb logcat -d -s AB_BASELINE_LIB      # pega a linha "WROTE <path> 393x600"
 *   adb pull <path> ab/library-android.png
 */
class AbLibraryBaselineActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val settings = UserSettings()
        val compose = ComposeView(this).apply {
            setContent {
                // Density fixa 1f: espelha ImageComposeScene(..., density = Density(1f)) do iOS.
                CompositionLocalProvider(
                    LocalDensity provides Density(1f),
                    LocalLandscape provides false,
                ) {
                    MpxTheme(settings) {
                        Box(Modifier.fillMaxSize().background(Mpx.colors.bg).padding(10.dp)) {
                            LibraryScreen(sampleLibraryUi, sampleLibraryPlayer, noopLibActions)
                        }
                    }
                }
            }
        }

        // ComposeView em tamanho EXATO 393x600 px (com density 1f, == 393x600 dp da tela).
        val container = FrameLayout(this)
        container.addView(compose, FrameLayout.LayoutParams(WIDTH_PX, HEIGHT_PX))
        setContentView(container)

        // 1º pre-draw: conteúdo já composto/medido em 393x600; captura antes de ticks animarem —
        // casa o 1º frame do iOS (LazyVerticalGrid com a faixa visível assentada).
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
        const val HEIGHT_PX = 600
        const val OUT_NAME = "library-android.png"
        const val TAG = "AB_BASELINE_LIB"
    }
}
