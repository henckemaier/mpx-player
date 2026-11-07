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
import com.mpx.player.ui.NowPlayingPanel
import com.mpx.player.ui.noopActions
import com.mpx.player.ui.sampleUi
import com.mpx.player.ui.theme.Mpx
import com.mpx.player.ui.theme.MpxTheme
import java.io.File
import java.io.FileOutputStream

/**
 * SÓ-DEBUG (app/src/debug): harness one-off p/ o BASELINE Android do A/B visual da fatia fina
 * de UI (P7). Renderiza EXATAMENTE o mesmo conteúdo do harness iOS offscreen
 * (NowPlayingPanelRenderTest): MpxTheme(UserSettings()){ Box(bg, padding 10dp){
 * NowPlayingPanel(sampleUi, noopActions) } }, a 393x420 px com DENSITY=1f — casando o
 * tamanho/densidade do ImageComposeScene(393,420,Density(1f)) do iOS p/ a Lente comparar o
 * blur do text-shadow lado a lado.
 *
 * Determinismo: a ComposeView tem tamanho fixo (393x420 px); LocalDensity=1f faz dp==px; a
 * captura é no 1º pre-draw (doOnPreDraw) — mesma intenção do "1º frame" do teste iOS
 * (Spectrum tick=0, cursor "_"). NÃO ship em release (vive só no source set debug).
 *
 * NÃO altera a API comum do NowPlayingPanel/sampleUi — só os consome.
 *
 * Captura (coordenada pelo Bigorna no emulador/device):
 *   adb shell am start -n com.mpx.player/com.mpx.player.debug.AbBaselineActivity
 *   adb logcat -d -s AB_BASELINE        # pega a linha "WROTE <path> 393x420"
 *   adb pull <path> ab/nowplaying-android.png
 */
class AbBaselineActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val compose = ComposeView(this).apply {
            setContent {
                // Density fixa 1f: espelha ImageComposeScene(..., density = Density(1f)) do iOS.
                CompositionLocalProvider(LocalDensity provides Density(1f)) {
                    MpxTheme(UserSettings()) {
                        Box(Modifier.fillMaxSize().background(Mpx.colors.bg).padding(10.dp)) {
                            NowPlayingPanel(sampleUi, noopActions)
                        }
                    }
                }
            }
        }

        // ComposeView em tamanho EXATO 393x420 px (com density 1f, == 393x420 dp do painel).
        val container = FrameLayout(this)
        container.addView(compose, FrameLayout.LayoutParams(WIDTH_PX, HEIGHT_PX))
        setContentView(container)

        // 1º pre-draw: conteúdo já composto/medido em 393x420; captura antes dos ticks de
        // animação assentarem (Spectrum 110ms / cursor 530ms) — casa o 1º frame do iOS.
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
        const val HEIGHT_PX = 420
        const val OUT_NAME = "nowplaying-android.png"
        const val TAG = "AB_BASELINE"
    }
}
