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
import com.mpx.player.ui.screens.BootScreen
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
 * HARNESS A/B OFFSCREEN da tela BOOT (P7 fatia boot c2) — 8o par (o ULTIMO, 4/4), espelha o
 * [NowPlayingFullRenderTest]/[QueueScreenRenderTest]. Renderiza a [BootScreen] REAL no FRAME TERMINAL
 * a tamanho FIXO via [ImageComposeScene] (skiko, SEM SpringBoard) e grava um PNG p/ o pixel-diff
 * comparar com o baseline Android do MESMO conteudo (ab/boot-android.png, [com.mpx.player.debug.AbBootBaselineActivity]).
 *
 * FRAME TERMINAL DETERMINISTICO (ver [sampleBootStepOverride]/[sampleBootBarOverride]):
 *  - stepOverride=6 + barOverride=20 -> os LaunchedEffect da BootScreen NAO rodam (guard do c1) -> a
 *    tela usa os valores FIXOS (estado "ready" final). O UNICO bit animado = o blinkingCursor do
 *    "//PLAYER" (display 24sp), que arranca "_" nos dois lados no frame 0.
 *  - hasPermission=true + scanned=true -> 6 linhas de log + USER_PROFILE LOADED + barra cheia + 100%
 *    + "[ PRESS ENTER ]" filled. fileCount=12345 -> "12.345" (fmtThousands). cover=None (Boot sem capa).
 *
 * `LocalLandscape provides false` + `Density(1f)` + `Box(bg, padding 10dp)` casam EXATAMENTE o
 * baseline Android. O SMOKE ([bootScreen_landscape_composesOffscreen]) prova que o PAISAGEM (2
 * colunas) tambem assenta em 1 frame (sem baseline).
 */
class BootScreenRenderTest {

    private val width = 393
    // H p/ o retrato do BOOT caber: BootHeader + BootBranding(Panel + LOGO ascii 6 linhas + //PLAYER)
    // + BootLog (Panel, weight(1f) absorve a folga) + BootButton(54dp) + BootFooter + espacamentos.
    // Calibrado na captura do baseline (mesmo H do npfull).
    private val height = 800

    @OptIn(ExperimentalForeignApi::class)
    @Test
    fun renderBootScreen_offscreen_producesNonEmptyPng() {
        val bytes = renderBoot(landscape = false)
        // Caminho conhecido p/ o CI publicar e comparar (env MPX_AB_BOOT_OUT quando definido; senao
        // tmp). Padrao de stdout PARSEAVEL igual aos outros 7 pares.
        val outPath = (getenv("MPX_AB_BOOT_OUT")?.toKString()?.takeIf { it.isNotBlank() }
            ?: (NSTemporaryDirectory() + "boot-ios.png"))
        val wrote = runCatching { writePng(outPath, bytes) }.getOrDefault(false)
        println("A/B boot: PNG ${bytes.size} B, escrito=$wrote em $outPath")
    }

    @Test
    fun bootScreen_landscape_composesOffscreen() {
        // SMOKE (paisagem = 2 colunas): tambem compoe no 1o frame (sem baseline).
        val bytes = renderBoot(landscape = true)
        assertTrue(bytes.size > 2_000, "BOOT paisagem: PNG pequeno demais (${bytes.size} B) — render vazio?")
        println("A/B boot smoke (paisagem): PNG ${bytes.size} B")
    }

    /** Renderiza a BootScreen (frame terminal) a 393xH @ Density(1f), 1o frame, e devolve o PNG (nao-vazio). */
    private fun renderBoot(landscape: Boolean): ByteArray {
        val scene = ImageComposeScene(width = width, height = height, density = Density(1f)) {
            MpxTheme(UserSettings()) {
                CompositionLocalProvider(LocalLandscape provides landscape) {
                    Box(Modifier.fillMaxSize().background(Mpx.colors.bg).padding(10.dp)) {
                        BootScreen(
                            hasPermission = sampleBootHasPermission,
                            scanned = sampleBootScanned,
                            fileCount = sampleBootFileCount,
                            eqPreset = sampleBootEqPreset,
                            version = sampleBootVersion,
                            onRequestPermission = {},
                            onEnter = {},
                            stepOverride = sampleBootStepOverride,
                            barOverride = sampleBootBarOverride,
                        )
                    }
                }
            }
        }
        return try {
            val image = scene.render() // 1o frame (t=0): override congela step/bar -> so o cursor anima
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
