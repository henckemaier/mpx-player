package com.mpx.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.mpx.player.model.UserSettings
import com.mpx.player.ui.screens.NowPlayingScreen
import com.mpx.player.ui.theme.LocalLandscape
import com.mpx.player.ui.theme.Mpx
import com.mpx.player.ui.theme.MpxTheme
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import org.jetbrains.skia.EncodedImageFormat
import platform.Foundation.NSTemporaryDirectory
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fwrite
import platform.posix.getenv
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * HARNESS A/B OFFSCREEN da tela NOW PLAYING CHEIA (P7 fatia nowplaying-cheia c2) — 7o par, espelha o
 * [QueueScreenRenderTest]/[EqScreenRenderTest]. Renderiza a [NowPlayingScreen] REAL (tela inteira, nao
 * o painel do 1o par) a tamanho FIXO via [ImageComposeScene] (skiko, SEM SpringBoard) e grava um PNG
 * p/ o pixel-diff comparar com o baseline Android do MESMO conteudo (ab/nowplaying-full-android.png,
 * [com.mpx.player.debug.AbNowPlayingFullBaselineActivity]).
 *
 * ESTADO ESTATICO (o que torna o par barato — ver [sampleNpFullUi]):
 *  - isPlaying=FALSE -> Spectrum em repouso (sem tick); medidores L/R vazios.
 *  - console COLAPSADO (expand=0f) + aba CMD.LOG (lyricMode=false) + dialog FECHADO -> nada anima
 *    alem dos 2 blinkingCursor (titulo + cmdlog), que arrancam em "_" nos dois lados no frame 0.
 *  - COVER: Track sem art + cover=0 (UserSettings) -> GeneratedCover(disc) SINCRONO/deterministico
 *    (como Queue/Library) -> NAO precisa LocalCoverLoader.
 *
 * `LocalLandscape provides false` + `Density(1f)` + `Box(bg, padding 10dp)` casam EXATAMENTE o
 * baseline Android. O SMOKE ([npFull_landscape_composesOffscreen]) prova que o PAISAGEM (2 colunas)
 * tambem assenta em 1 frame (sem baseline).
 */
class NowPlayingFullRenderTest {

    private val width = 393
    // H p/ a tela-cheia RETRATO caber: InvertedBar + NpCoverMeta(cover 128dp) + NpTitle + NpProgress
    // + NpTransport(50dp) + regiao do Console (weight(1f), com Spectrum+Volume ~134dp ao fundo e o
    // console colapsado por cima) + espacamentos + padding. Calibrado na captura do baseline.
    private val height = 800

    @OptIn(ExperimentalForeignApi::class)
    @Test
    fun renderNowPlayingFull_offscreen_producesNonEmptyPng() {
        val bytes = renderNpFull(landscape = false)
        // Caminho conhecido p/ o CI publicar e comparar (env MPX_AB_NPFULL_OUT quando definido; senao
        // tmp). Padrao de stdout PARSEAVEL igual aos outros 6 pares.
        val outPath = (getenv("MPX_AB_NPFULL_OUT")?.toKString()?.takeIf { it.isNotBlank() }
            ?: (NSTemporaryDirectory() + "nowplaying-full-ios.png"))
        val wrote = runCatching { writePng(outPath, bytes) }.getOrDefault(false)
        println("A/B nowplaying-full: PNG ${bytes.size} B, escrito=$wrote em $outPath")
    }

    @Test
    fun npFull_landscape_composesOffscreen() {
        // SMOKE (paisagem = 2 colunas): tambem compoe no 1o frame (sem baseline).
        val bytes = renderNpFull(landscape = true)
        assertTrue(bytes.size > 2_000, "NP-cheia paisagem: PNG pequeno demais (${bytes.size} B) — render vazio?")
        println("A/B nowplaying-full smoke (paisagem): PNG ${bytes.size} B")
    }

    /** Renderiza a NowPlayingScreen CHEIA a 393xH @ Density(1f), 1o frame, e devolve o PNG (nao-vazio). */
    private fun renderNpFull(landscape: Boolean): ByteArray {
        val scene = ImageComposeScene(width = width, height = height, density = Density(1f)) {
            MpxTheme(UserSettings()) {
                CompositionLocalProvider(LocalLandscape provides landscape) {
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
        return try {
            val image = scene.render() // 1o frame (t=0): isPlaying=false congela tudo menos os cursores
            assertEquals(width, image.width, "largura do render")
            assertEquals(height, image.height, "altura do render")
            val data = image.encodeToData(EncodedImageFormat.PNG)
                ?: error("encodeToData(PNG) devolveu null")
            val bytes = data.bytes
            assertTrue(bytes.size > 2_000, "PNG pequeno demais (${bytes.size} B) — render provavelmente vazio")
            bytes
        } finally {
            scene.close()
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun writePng(path: String, bytes: ByteArray): Boolean {
        if (bytes.isEmpty()) return false
        val file = fopen(path, "wb") ?: return false
        return try {
            val written = bytes.usePinned { pinned ->
                fwrite(pinned.addressOf(0), 1.convert(), bytes.size.convert(), file)
            }
            written.toULong() == bytes.size.toULong()
        } finally {
            fclose(file)
        }
    }
}
