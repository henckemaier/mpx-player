package com.mpx.player.debug

import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.core.view.doOnPreDraw
import androidx.core.view.drawToBitmap
import com.mpx.player.model.UserSettings
import com.mpx.player.ui.NavShellAbSample
import com.mpx.player.ui.theme.LocalLandscape
import com.mpx.player.ui.theme.MpxTheme
import java.io.File
import java.io.FileOutputStream

/**
 * SÓ-DEBUG (app/src/debug): harness one-off p/ o BASELINE Android do 4º par A/B — o CHROME de
 * navegação (P7 fatia nav c2). Renderiza EXATAMENTE o mesmo conteúdo do harness iOS offscreen
 * ([com.mpx.player.ui.NavShellRenderTest], caso PRIMÁRIO = RETRATO): MpxTheme(UserSettings()){
 * LocalLandscape=false { NavShellAbSample() } }, a 393x200 px com DENSITY=1f — casando o
 * ImageComposeScene(393,200,Density(1f)) do iOS. A sub-árvore [NavShellAbSample] (MpxShell real +
 * conteúdo dummy "CONTENT") vive no :shared commonMain e é COMPARTILHADA pelos dois lados → zero
 * drift entre baseline e render.
 *
 * NÃO altera a [AbBaselineActivity] (NowPlaying), a [AbLibraryBaselineActivity] nem a
 * [AbSettingsBaselineActivity]; vive em paralelo. NÃO ship em release.
 *
 * Captura (coordenada pelo Bigorna no emulador/device):
 *   adb shell am force-stop com.mpx.player
 *   adb shell am start -n com.mpx.player/com.mpx.player.debug.AbNavBaselineActivity
 *   adb logcat -d -s AB_BASELINE_NAV   # pega a linha "WROTE <path> 393x200"
 *   adb pull <path> ab/nav-android.png
 */
class AbNavBaselineActivity : ComponentActivity() {

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
                        NavShellAbSample()
                    }
                }
            }
        }

        // ComposeView em tamanho EXATO 393x200 px (com density 1f, == 393x200 dp da tela).
        val container = FrameLayout(this)
        container.addView(compose, FrameLayout.LayoutParams(WIDTH_PX, HEIGHT_PX))
        setContentView(container)

        // 1º pre-draw: conteúdo já composto/medido em 393x200; captura antes de ticks animarem —
        // casa o 1º frame do iOS (chrome estático, sem transição).
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
        const val HEIGHT_PX = 200
        const val OUT_NAME = "nav-android.png"
        const val TAG = "AB_BASELINE_NAV"
    }
}
