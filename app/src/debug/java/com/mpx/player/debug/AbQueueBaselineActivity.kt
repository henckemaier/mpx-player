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
import com.mpx.player.ui.noopQueueActions
import com.mpx.player.ui.sampleQueueUi
import com.mpx.player.ui.screens.QueueScreen
import com.mpx.player.ui.theme.LocalLandscape
import com.mpx.player.ui.theme.Mpx
import com.mpx.player.ui.theme.MpxTheme
import java.io.File
import java.io.FileOutputStream

/**
 * SO-DEBUG (app/src/debug): harness one-off p/ o BASELINE Android do 6o par A/B — a tela QUEUE
 * (P7 fatia queue c2). Renderiza EXATAMENTE o mesmo conteudo do harness iOS offscreen
 * ([com.mpx.player.ui.QueueScreenRenderTest]): MpxTheme(UserSettings()){ LocalLandscape=false {
 * Box(bg, padding 10dp){ QueueScreen(sampleQueueUi, noopQueueActions) } } }, a 393x720 px com
 * DENSITY=1f — casando o ImageComposeScene(393,720,Density(1f)) do iOS. O fixture
 * (sampleQueueUi/noopQueueActions) vive no :shared commonMain e e COMPARTILHADO -> zero drift.
 *
 * COVER: os Tracks nao tem capa e UserSettings() usa cover=0 -> o Cover(disc=true) curto-circuita
 * p/ GeneratedCover SINCRONO (sem loader async), deterministico nos dois lados. NAO precisa
 * LocalCoverLoader explicito (default = NullAlbumArtSource; coverStyle=0 nem o chama) — igual Library.
 *
 * NAO altera as Activities existentes (NowPlaying/Library/Settings/Nav/Eq); vive em paralelo. NAO
 * ship em release.
 *
 * Captura (coordenada pelo Bigorna no emulador/device):
 *   adb shell am force-stop com.mpx.player
 *   adb shell am start -n com.mpx.player/com.mpx.player.debug.AbQueueBaselineActivity
 *   adb logcat -d -s AB_BASELINE_QUEUE   # pega a linha "WROTE <path> 393x720"
 *   adb pull <path> ab/queue-android.png
 */
class AbQueueBaselineActivity : ComponentActivity() {

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
                            QueueScreen(sampleQueueUi, noopQueueActions)
                        }
                    }
                }
            }
        }

        // ComposeView em tamanho EXATO 393x720 px (com density 1f, == 393x720 dp da tela).
        val container = FrameLayout(this)
        container.addView(compose, FrameLayout.LayoutParams(WIDTH_PX, HEIGHT_PX))
        setContentView(container)

        // 1o pre-draw: conteudo ja composto/medido em 393x720; captura antes de ticks animarem —
        // casa o 1o frame do iOS (cover sincrono, lista no topo, sem animacao).
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
        const val HEIGHT_PX = 720
        const val OUT_NAME = "queue-android.png"
        const val TAG = "AB_BASELINE_QUEUE"
    }
}
