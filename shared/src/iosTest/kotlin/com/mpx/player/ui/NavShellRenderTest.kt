package com.mpx.player.ui

import androidx.compose.ui.ImageComposeScene
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.Density
import com.mpx.player.model.UserSettings
import com.mpx.player.ui.theme.LocalLandscape
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
 * HARNESS A/B OFFSCREEN do CHROME de navegação (P7 fatia nav c2) — 4º par, espelha o
 * [NowPlayingPanelRenderTest]/[LibraryScreenRenderTest]/[SettingsScreenRenderTest]. Renderiza o
 * [MpxShell] REAL com conteúdo DUMMY ([NavShellAbSample]) a tamanho FIXO via [ImageComposeScene]
 * (skiko, SEM SpringBoard) e grava um PNG p/ o pixel-diff comparar com o baseline Android do MESMO
 * conteúdo (ab/nav-android.png, [com.mpx.player.debug.AbNavBaselineActivity]).
 *
 * O par 'nav' isola o CHROME (barra de abas + cabeçalho CRT), NÃO uma tela de conteúdo — por isso o
 * slot é um frame "CONTENT", não uma tela real. O render é ESTÁTICO (sem animação de transição):
 * `NavState()` → current=PLAY no 1º frame, determinístico entre runs.
 *
 * WATCH-ITEM (c2): confirma que o [MpxShell] (só Column/Row/Box — mais simples que o verticalScroll
 * do Settings) compõe e MEDE no 1º frame offscreen no CMP 1.7.1 iOS (asserção de bytes não-vazios).
 *
 * O caso PRIMÁRIO é RETRATO (barra horizontal [com.mpx.player.ui.components.MpxNav]); o SMOKE
 * ([navShell_landscape_composesOffscreen]) prova que o PAISAGEM (trilho
 * [com.mpx.player.ui.components.MpxNavRail]) também assenta em 1 frame (sem baseline — só gate de
 * composição).
 */
class NavShellRenderTest {

    private val width = 393
    // H p/ caber cabeçalho (~31px) + barra (44dp) + placeholder (80dp) + os espaçamentos (10dp) e o
    // padding (10dp) do wrapper, sem clip. Calibrado na fatia c3 com os pixels reais.
    private val height = 200

    @OptIn(ExperimentalForeignApi::class)
    @Test
    fun renderNavShell_portrait_offscreen_producesNonEmptyPng() {
        val bytes = renderNavShell(landscape = false)
        // Caminho conhecido p/ o CI publicar e comparar (env MPX_AB_NAV_OUT quando definido; senão
        // tmp). Padrão de stdout PARSEÁVEL igual aos outros 3 pares.
        val outPath = (getenv("MPX_AB_NAV_OUT")?.toKString()?.takeIf { it.isNotBlank() }
            ?: (NSTemporaryDirectory() + "nav-ios.png"))
        val wrote = runCatching { writePng(outPath, bytes) }.getOrDefault(false)
        println("A/B nav: PNG ${bytes.size} B, escrito=$wrote em $outPath")
    }

    @Test
    fun navShell_landscape_composesOffscreen() {
        // SMOKE (paisagem=trilho MpxNavRail): também compõe no 1º frame (sem baseline).
        val bytes = renderNavShell(landscape = true)
        assertTrue(bytes.size > 2_000, "NAV paisagem: PNG pequeno demais (${bytes.size} B) — render vazio?")
        println("A/B nav smoke (paisagem/trilho): PNG ${bytes.size} B")
    }

    /** Renderiza o MpxShell (dummy) a 393xH @ Density(1f), 1º frame, e devolve o PNG (não-vazio). */
    private fun renderNavShell(landscape: Boolean): ByteArray {
        val scene = ImageComposeScene(width = width, height = height, density = Density(1f)) {
            MpxTheme(UserSettings()) {
                CompositionLocalProvider(LocalLandscape provides landscape) {
                    NavShellAbSample()
                }
            }
        }
        return try {
            val image = scene.render() // 1º frame (t=0): determinístico (sem transição)
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
