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
import com.mpx.player.ui.screens.SettingsScreen
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
 * HARNESS A/B OFFSCREEN da tela CONFIG (P7 fatia settings c2) — 3º par, espelha o
 * [NowPlayingPanelRenderTest]/[LibraryScreenRenderTest]. Renderiza a [SettingsScreen] REAL a tamanho
 * FIXO via [ImageComposeScene] (skiko, SEM SpringBoard) e grava um PNG p/ o pixel-diff comparar com
 * o baseline Android do MESMO conteúdo (ab/settings-android.png).
 *
 * WATCH-ITEM que este teste prova: a [SettingsScreen] usa `Column.verticalScroll` (não Lazy). O
 * `verticalScroll` COMPÕE E MEDE TODOS os filhos no 1º frame offscreen (diferente do Lazy, que só
 * mede a faixa visível) — a asserção de bytes não-vazios (>2000) descarta um render vazio/branco e
 * prova que o scroll-container assenta em 1 frame determinístico no CMP 1.7.1 iOS. O scroll começa
 * no topo (offset 0 em t=0): determinístico entre runs.
 *
 * `initialOpen = 2`: abre a seção CORES → renderiza o PREVIEW "SIGNAL//DECAY_" com Txt(glow=true)
 * (único glow real da seção) + os 6 swatches de fósforo com `.glow`. Sem isso o render só traria
 * headers colapsados (zero glow a cobrar pelo check GLOW do perfil 'settings').
 *
 * `LocalLandscape provides false` + `Density(1f)` + slots Android = null: casam EXATAMENTE o
 * baseline Android ([com.mpx.player.debug.AbSettingsBaselineActivity]).
 */
class SettingsScreenRenderTest {

    private val width = 393
    // H escolhido p/ a seção CORES + o PREVIEW "SIGNAL//DECAY_" caberem INTEIROS no frame (o resto
    // da tela colapsada clipa estável nos 2 lados). Calibrado na fatia c3 com os pixels reais.
    private val height = 760

    @OptIn(ExperimentalForeignApi::class)
    @Test
    fun renderSettingsScreen_coresOpen_offscreen_producesNonEmptyPng() {
        val bytes = renderSettings(initialOpen = 2)
        // Caminho conhecido p/ o CI publicar e comparar (env MPX_AB_SETTINGS_OUT quando definido;
        // senão tmp). Padrão de stdout PARSEÁVEL igual ao NowPlaying/Library.
        val outPath = (getenv("MPX_AB_SETTINGS_OUT")?.toKString()?.takeIf { it.isNotBlank() }
            ?: (NSTemporaryDirectory() + "settings-ios.png"))
        val wrote = runCatching { writePng(outPath, bytes) }.getOrDefault(false)
        println("A/B settings: PNG ${bytes.size} B, escrito=$wrote em $outPath")
    }

    @Test
    fun renderSettingsScreen_collapsed_composesOffscreen() {
        // SMOKE (initialOpen=0): a tela TODA colapsada também compõe no 1º frame (sem baseline).
        val bytes = renderSettings(initialOpen = 0)
        assertTrue(bytes.size > 2_000, "CONFIG colapsada: PNG pequeno demais (${bytes.size} B) — render vazio?")
        println("A/B settings smoke (colapsada): PNG ${bytes.size} B")
    }

    /** Renderiza a SettingsScreen a 393xH @ Density(1f), 1º frame, e devolve o PNG (não-vazio). */
    private fun renderSettings(initialOpen: Int): ByteArray {
        val settings = UserSettings()
        val scene = ImageComposeScene(width = width, height = height, density = Density(1f)) {
            MpxTheme(settings) {
                CompositionLocalProvider(LocalLandscape provides false) {
                    Box(Modifier.fillMaxSize().background(Mpx.colors.bg).padding(10.dp)) {
                        SettingsScreen(
                            ui = sampleSettingsUi,
                            actions = noopSettingsActions,
                            onReboot = null,
                            initialOpen = initialOpen,
                        )
                    }
                }
            }
        }
        return try {
            val image = scene.render() // 1º frame (t=0): determinístico
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
