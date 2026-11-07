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
import com.mpx.player.ui.screens.EqScreen
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
 * HARNESS A/B OFFSCREEN da tela EQUALIZER (P7 fatia eq c2) — 5o par, espelha o
 * [NowPlayingPanelRenderTest]/[LibraryScreenRenderTest]/[SettingsScreenRenderTest]/[NavShellRenderTest].
 * Renderiza a [EqScreen] REAL a tamanho FIXO via [ImageComposeScene] (skiko, SEM SpringBoard) e grava
 * um PNG p/ o pixel-diff comparar com o baseline Android do MESMO conteudo (ab/eq-android.png,
 * [com.mpx.player.debug.AbEqBaselineActivity]).
 *
 * WATCH-ITEM (c2): a EqScreen e ESTATICA (sem cursor/marquee/spectrum/animacao) -> o 1o frame (t=0)
 * JA e o estado final. O drag das bandas e so input (pointerInput), nao afeta o render. A assercao
 * de bytes nao-vazios prova que o layout (Column/Row/Box + fills de segmento) compoe e MEDE em 1
 * frame offscreen no CMP 1.7.1 iOS. Fixture compartilhado [sampleEqSettings]/[sampleEqSelected].
 *
 * `LocalLandscape provides false` + `Density(1f)` + `Box(bg, padding 10dp)` casam EXATAMENTE o
 * baseline Android. O SMOKE ([eqScreen_landscape_composesOffscreen]) prova que o PAISAGEM (layout em
 * 2 colunas) tambem assenta em 1 frame (sem baseline — so gate de composicao).
 */
class EqScreenRenderTest {

    private val width = 393
    // H p/ o layout RETRATO da EQ caber inteiro (Header+Preset+BandInfo+Bands[10x13 segmentos]+
    // Preamp+Effects[3 ToggleRow]+Buttons + Spacer). Calibrado na fatia c3 com os pixels reais.
    private val height = 720

    @OptIn(ExperimentalForeignApi::class)
    @Test
    fun renderEqScreen_offscreen_producesNonEmptyPng() {
        val bytes = renderEq(landscape = false)
        // Caminho conhecido p/ o CI publicar e comparar (env MPX_AB_EQ_OUT quando definido; senao
        // tmp). Padrao de stdout PARSEAVEL igual aos outros 4 pares.
        val outPath = (getenv("MPX_AB_EQ_OUT")?.toKString()?.takeIf { it.isNotBlank() }
            ?: (NSTemporaryDirectory() + "eq-ios.png"))
        val wrote = runCatching { writePng(outPath, bytes) }.getOrDefault(false)
        println("A/B eq: PNG ${bytes.size} B, escrito=$wrote em $outPath")
    }

    @Test
    fun eqScreen_landscape_composesOffscreen() {
        // SMOKE (paisagem = 2 colunas): tambem compoe no 1o frame (sem baseline).
        val bytes = renderEq(landscape = true)
        assertTrue(bytes.size > 2_000, "EQ paisagem: PNG pequeno demais (${bytes.size} B) — render vazio?")
        println("A/B eq smoke (paisagem): PNG ${bytes.size} B")
    }

    /** Renderiza a EqScreen a 393xH @ Density(1f), 1o frame (= estado final; EQ estatica), e devolve o PNG. */
    private fun renderEq(landscape: Boolean): ByteArray {
        val scene = ImageComposeScene(width = width, height = height, density = Density(1f)) {
            MpxTheme(UserSettings()) {
                CompositionLocalProvider(LocalLandscape provides landscape) {
                    Box(Modifier.fillMaxSize().background(Mpx.colors.bg).padding(10.dp)) {
                        EqScreen(sampleEqSettings, sampleEqSelected, noopEqActions)
                    }
                }
            }
        }
        return try {
            val image = scene.render() // 1o frame (t=0): EQ estatica -> determinístico
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
