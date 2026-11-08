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
import com.mpx.player.ui.noopNpFullActions
import com.mpx.player.ui.sampleNpFullFavorite
import com.mpx.player.ui.sampleNpFullLyrics
import com.mpx.player.ui.sampleNpFullPlaylists
import com.mpx.player.ui.sampleNpFullTech
import com.mpx.player.ui.sampleNpFullUi
import com.mpx.player.ui.sampleNpFullVolume
import com.mpx.player.ui.screens.NowPlayingScreen
import com.mpx.player.ui.theme.LocalLandscape
import com.mpx.player.ui.theme.Mpx
import com.mpx.player.ui.theme.MpxTheme
import java.io.File
import java.io.FileOutputStream

/**
 * SO-DEBUG (app/src/debug): harness one-off p/ o BASELINE Android do 7o par A/B — a tela NOW PLAYING
 * CHEIA (P7 fatia nowplaying-cheia c2). Renderiza EXATAMENTE o mesmo conteudo do harness iOS offscreen
 * ([com.mpx.player.ui.NowPlayingFullRenderTest]): MpxTheme(UserSettings()){ LocalLandscape=false {
 * Box(bg, padding 10dp){ NowPlayingScreen(sampleNpFull*, noopNpFullActions) } } }, a 393x800 px com
 * DENSITY=1f — casando o ImageComposeScene(393,800,Density(1f)) do iOS. O fixture (sampleNpFull*)
 * vive no :shared commonMain e e COMPARTILHADO -> zero drift.
 *
 * ESTADO ESTATICO: isPlaying=FALSE (Spectrum em repouso) + console COLAPSADO + aba CMD.LOG + dialog
 * FECHADO -> so os 2 blinkingCursor (titulo + cmdlog) animam. COVER: Track sem art + cover=0 ->
 * GeneratedCover(disc) SINCRONO/deterministico (sem loader async). NAO precisa LocalCoverLoader.
 *
 * NAO altera as Activities existentes (NowPlaying-painel/Library/Settings/Nav/Eq/Queue); vive em
 * paralelo. NAO ship em release.
 *
 * Captura (coordenada pelo Bigorna no emulador/device):
 *   adb shell am force-stop com.mpx.player
 *   adb shell am start -n com.mpx.player/com.mpx.player.debug.AbNowPlayingFullBaselineActivity
 *   adb logcat -d -s AB_BASELINE_NPFULL   # pega a linha "WROTE <path> 393x800"
 *   adb pull <path> ab/nowplaying-full-android.png
 */
class AbNowPlayingFullBaselineActivity : ComponentActivity() {

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
                            NowPlayingScreen(
                                ui = sampleNpFullUi,
                                tech = sampleNpFullTech,
                                lyrics = sampleNpFullLyrics,
                                volume = sampleNpFullVolume,
                                favorite = sampleNpFullFavorite,
                                playlists = sampleNpFullPlaylists,
                                actions = noopNpFullActions,
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
        // casa o 1o frame do iOS (isPlaying=false congela tudo, cover sincrono, console colapsado).
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
        const val OUT_NAME = "nowplaying-full-android.png"
        const val TAG = "AB_BASELINE_NPFULL"
    }
}
