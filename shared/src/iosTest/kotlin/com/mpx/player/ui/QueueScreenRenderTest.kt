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
import com.mpx.player.ui.screens.QueueScreen
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
 * HARNESS A/B OFFSCREEN da tela QUEUE (P7 fatia queue c2) — 6o par, espelha o
 * [EqScreenRenderTest]/[LibraryScreenRenderTest]. Renderiza a [QueueScreen] REAL a tamanho FIXO via
 * [ImageComposeScene] (skiko, SEM SpringBoard) e grava um PNG p/ o pixel-diff comparar com o baseline
 * Android do MESMO conteudo (ab/queue-android.png, [com.mpx.player.debug.AbQueueBaselineActivity]).
 *
 * WATCH-ITEMS (c2):
 *  - (a) COVER deterministico: os Tracks do fixture nao tem capa e o MpxTheme usa UserSettings()
 *    (cover=0) -> o Cover(disc=true) curto-circuita p/ GeneratedCover SINCRONO (sem loader async).
 *    Por isso NAO e preciso prover LocalCoverLoader (default = NullAlbumArtSource; e com coverStyle=0
 *    nem chega a chama-lo) — mesmo caminho da Library.
 *  - (b)/(c) a LazyColumn (6 rows, index=1 -> sem animateScrollToItem) COMPOE/MEDE a faixa visivel no
 *    1o frame offscreen (LazyColumn ja provado na Library); a assercao de bytes nao-vazios descarta
 *    render vazio.
 *
 * `LocalLandscape provides false` + `Density(1f)` + `Box(bg, padding 10dp)` casam EXATAMENTE o
 * baseline Android. O SMOKE ([queueScreen_landscape_composesOffscreen]) prova que o PAISAGEM (2
 * colunas) tambem assenta em 1 frame (sem baseline).
 */
class QueueScreenRenderTest {

    private val width = 393
    // H p/ o retrato da QUEUE caber: QueueHeader(cover 92dp) + InvertedBar + ColHeaders + QueueList
    // (6 rows, weight(1f)) + Totals + Buttons + espacamentos. Calibrado na fatia c3 com os pixels reais.
    private val height = 720

    @OptIn(ExperimentalForeignApi::class)
    @Test
    fun renderQueueScreen_offscreen_producesNonEmptyPng() {
        val bytes = renderQueue(landscape = false)
        // Caminho conhecido p/ o CI publicar e comparar (env MPX_AB_QUEUE_OUT quando definido; senao
        // tmp). Padrao de stdout PARSEAVEL igual aos outros 5 pares.
        val outPath = (getenv("MPX_AB_QUEUE_OUT")?.toKString()?.takeIf { it.isNotBlank() }
            ?: (NSTemporaryDirectory() + "queue-ios.png"))
        val wrote = runCatching { writePng(outPath, bytes) }.getOrDefault(false)
        println("A/B queue: PNG ${bytes.size} B, escrito=$wrote em $outPath")
    }

    @Test
    fun queueScreen_landscape_composesOffscreen() {
        // SMOKE (paisagem = 2 colunas): tambem compoe no 1o frame (sem baseline).
        val bytes = renderQueue(landscape = true)
        assertTrue(bytes.size > 2_000, "QUEUE paisagem: PNG pequeno demais (${bytes.size} B) — render vazio?")
        println("A/B queue smoke (paisagem): PNG ${bytes.size} B")
    }

    /** Renderiza a QueueScreen a 393xH @ Density(1f), 1o frame, e devolve o PNG (nao-vazio). */
    private fun renderQueue(landscape: Boolean): ByteArray {
        val scene = ImageComposeScene(width = width, height = height, density = Density(1f)) {
            MpxTheme(UserSettings()) {
                CompositionLocalProvider(LocalLandscape provides landscape) {
                    Box(Modifier.fillMaxSize().background(Mpx.colors.bg).padding(10.dp)) {
                        QueueScreen(sampleQueueUi, noopQueueActions)
                    }
                }
            }
        }
        return try {
            val image = scene.render() // 1o frame (t=0): cover sincrono + lista no topo -> determinístico
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
