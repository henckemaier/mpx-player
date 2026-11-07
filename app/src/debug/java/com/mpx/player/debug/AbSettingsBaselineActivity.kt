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
import com.mpx.player.ui.noopSettingsActions
import com.mpx.player.ui.sampleSettingsUi
import com.mpx.player.ui.screens.SettingsScreen
import com.mpx.player.ui.theme.LocalLandscape
import com.mpx.player.ui.theme.Mpx
import com.mpx.player.ui.theme.MpxTheme
import java.io.File
import java.io.FileOutputStream

/**
 * SÓ-DEBUG (app/src/debug): harness one-off p/ o BASELINE Android do A/B visual da tela CONFIG (P7
 * fatia settings c2). Renderiza EXATAMENTE o mesmo conteúdo do harness iOS offscreen
 * ([com.mpx.player.ui.SettingsScreenRenderTest], caso PRIMÁRIO = seção CORES aberta): MpxTheme(
 * UserSettings()){ LocalLandscape=false { Box(bg, padding 10dp){ SettingsScreen(sampleSettingsUi,
 * noopSettingsActions, onReboot=null, initialOpen=2) } } }, a 393x760 px com DENSITY=1f — casando o
 * tamanho/densidade do ImageComposeScene(393,760,Density(1f)) do iOS.
 *
 * EQUIVALÊNCIA: os slots Android (BAIXAR/IMPORTAR/UPDATE/SAÍDA) e onReboot/onCheckUpdate ficam NULL
 * — o par A/B tem que renderizar o MESMO conteúdo dos 2 lados (no iOS esses slots sumiriam de toda
 * forma). NÃO usa o AndroidSettingsScreen (que injetaria os slots reais).
 *
 * NÃO altera a [AbBaselineActivity] (NowPlaying) nem a [AbLibraryBaselineActivity]; vive em
 * paralelo. NÃO ship em release.
 *
 * Captura (coordenada pelo Bigorna no emulador/device):
 *   adb shell am force-stop com.mpx.player
 *   adb shell am start -n com.mpx.player/com.mpx.player.debug.AbSettingsBaselineActivity
 *   adb logcat -d -s AB_BASELINE_SETTINGS   # pega a linha "WROTE <path> 393x760"
 *   adb pull <path> ab/settings-android.png
 */
class AbSettingsBaselineActivity : ComponentActivity() {

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
                            SettingsScreen(
                                ui = sampleSettingsUi,
                                actions = noopSettingsActions,
                                onReboot = null,
                                initialOpen = 2,
                            )
                        }
                    }
                }
            }
        }

        // ComposeView em tamanho EXATO 393x760 px (com density 1f, == 393x760 dp da tela).
        val container = FrameLayout(this)
        container.addView(compose, FrameLayout.LayoutParams(WIDTH_PX, HEIGHT_PX))
        setContentView(container)

        // 1º pre-draw: conteúdo já composto/medido em 393x760; captura antes de ticks animarem —
        // casa o 1º frame do iOS (verticalScroll no topo, seção CORES + PREVIEW assentados).
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
        const val HEIGHT_PX = 760
        const val OUT_NAME = "settings-android.png"
        const val TAG = "AB_BASELINE_SETTINGS"
    }
}
