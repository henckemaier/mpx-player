package com.mpx.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.mpx.player.model.UserSettings
import com.mpx.player.ui.theme.Mpx
import com.mpx.player.ui.theme.MpxTheme
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.runBlocking
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
 * HARNESS A/B OFFSCREEN (gate duro da fatia fina de UI, P7): renderiza o [NowPlayingPanel]
 * real a tamanho FIXO via [ImageComposeScene] (skiko) — determinístico, SEM SpringBoard — e
 * salva um PNG p/ a Lente comparar com a referência Android do MESMO painel. Resolve a dor
 * registrada no gate visual: o screenshot via simulador headless é flaky (foreground); o
 * render offscreen é reproduzível.
 *
 * O gate em si é a asserção de bytes (render não-vazio). O arquivo PNG é o artefato do A/B.
 *
 * WATCH-ITEM (CI): confirma `androidx.compose.ui.ImageComposeScene` disponível no
 * iosSimulatorArm64 (skiko). Se FALTAR, o fallback é render via ComposeUIViewController →
 * imagem offscreen (CALayer) — avisar o Bigorna qual caminho vingou.
 */
class NowPlayingPanelRenderTest {

    private val width = 393
    private val height = 420

    @OptIn(ExperimentalForeignApi::class)
    @Test
    fun renderNowPlayingPanel_offscreen_producesNonEmptyPng() {
        // Capa do A/B: cover=1 (DITHER) + mono → o pipeline 1-bit; pré-resolvida SÍNCRONO (o render
        // é de 1 frame, o produceState assíncrono do Cover não assentaria). Ver sampleCoverArt.
        val settings = UserSettings(cover = 1, monoCovers = true)
        val sampleCover = runBlocking { sampleCoverArt(settings) }
        val scene = ImageComposeScene(width = width, height = height, density = Density(1f)) {
            MpxTheme(settings) {
                Box(Modifier.fillMaxSize().background(Mpx.colors.bg).padding(10.dp)) {
                    NowPlayingPanel(sampleUi, noopActions, sampleTech, sampleCover)
                }
            }
        }
        val bytes: ByteArray
        try {
            val image = scene.render() // 1º frame (t=0): animações no estado inicial → determinístico
            assertEquals(width, image.width, "largura do render")
            assertEquals(height, image.height, "altura do render")
            val data = image.encodeToData(EncodedImageFormat.PNG)
                ?: error("encodeToData(PNG) devolveu null")
            bytes = data.bytes
        } finally {
            scene.close()
        }

        // Heurística (estilo smoke): um PNG com o painel renderizado (texto/segmentos/spectrum
        // em fósforo sobre preto) tem conteúdo real; um render vazio/branco seria trivialmente
        // pequeno. Prova que o pipeline Compose→Skia rodou no iOS.
        assertTrue(bytes.size > 2_000, "PNG pequeno demais (${bytes.size} B) — render provavelmente vazio")

        // Artefato do A/B: grava num caminho conhecido p/ o CI publicar (env MPX_AB_OUT quando
        // definido; senão, tmp). Best-effort — falha de escrita NÃO derruba o gate (que é a
        // asserção de bytes acima).
        val outPath = (getenv("MPX_AB_OUT")?.toKString()?.takeIf { it.isNotBlank() }
            ?: (NSTemporaryDirectory() + "nowplaying-ios.png"))
        val wrote = runCatching { writePng(outPath, bytes) }.getOrDefault(false)
        println("A/B offscreen: PNG ${bytes.size} B, escrito=$wrote em $outPath")
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
