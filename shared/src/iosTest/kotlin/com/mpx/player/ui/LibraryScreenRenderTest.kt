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
import com.mpx.player.ui.screens.LibraryScreen
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
 * HARNESS A/B OFFSCREEN da BIBLIOTECA (P7 fatia library c2) — espelha o
 * [NowPlayingPanelRenderTest]. Renderiza a [LibraryScreen] REAL a tamanho FIXO via
 * [ImageComposeScene] (skiko, SEM SpringBoard) e grava um PNG p/ o pixel-diff comparar com o
 * baseline Android do MESMO conteúdo.
 *
 * WATCH-ITEM (MAIOR) que este teste prova: `LazyVerticalGrid` (filtro PLAYLISTS, baseline
 * committed) e `LazyColumn` (filtro FAIXAS, smoke) COMPÕEM e MEDEM a faixa visível no 1º frame
 * offscreen no CMP 1.7.1 iOS. A asserção de bytes não-vazios (>2000) descarta um render
 * vazio/branco; o determinismo entre runs (fora de regiões dinâmicas) é cobrado pelo pixel-diff
 * do PRIMÁRIO contra o baseline (ab/library-android.png). Se o grid viesse VAZIO, o PNG seria
 * trivialmente pequeno e a asserção quebraria aqui — logo o teste é gate duro do "lazy assenta
 * em 1 frame", mesmo antes do baseline existir.
 *
 * `LocalLandscape provides false`: fixa a grade em 2 colunas (retrato), casando iOS × Android.
 */
class LibraryScreenRenderTest {

    private val width = 393
    private val height = 600

    @OptIn(ExperimentalForeignApi::class)
    @Test
    fun renderLibraryScreen_playlists_offscreen_producesNonEmptyPng() {
        val bytes = renderLibrary(sampleLibraryUi)
        // Grava num caminho conhecido p/ o CI publicar e comparar (env MPX_AB_LIB_OUT quando
        // definido; senão, tmp). Padrão de stdout PARSEÁVEL igual ao NowPlaying.
        val outPath = (getenv("MPX_AB_LIB_OUT")?.toKString()?.takeIf { it.isNotBlank() }
            ?: (NSTemporaryDirectory() + "library-ios.png"))
        val wrote = runCatching { writePng(outPath, bytes) }.getOrDefault(false)
        println("A/B library: PNG ${bytes.size} B, escrito=$wrote em $outPath")
    }

    @Test
    fun renderLibraryScreen_tracks_lazyColumn_composesOffscreen() {
        // SMOKE do LazyColumn (filtro FAIXAS): sem baseline — só prova que compõe no 1º frame.
        val bytes = renderLibrary(sampleLibraryTracksUi)
        assertTrue(bytes.size > 2_000, "LazyColumn (FAIXAS): PNG pequeno demais (${bytes.size} B) — render vazio?")
        println("A/B library smoke (FAIXAS/LazyColumn): PNG ${bytes.size} B")
    }

    /** Renderiza a LibraryScreen a 393x600 @ Density(1f), 1º frame, e devolve o PNG (não-vazio). */
    private fun renderLibrary(ui: LibraryUi): ByteArray {
        val settings = UserSettings()
        val scene = ImageComposeScene(width = width, height = height, density = Density(1f)) {
            MpxTheme(settings) {
                CompositionLocalProvider(LocalLandscape provides false) {
                    Box(Modifier.fillMaxSize().background(Mpx.colors.bg).padding(10.dp)) {
                        LibraryScreen(ui, sampleLibraryPlayer, noopLibActions)
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
