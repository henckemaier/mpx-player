package com.mpx.player.data

import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Prova de RUNTIME (iosSimulatorArm64Test) do caminho skiko do WATCH-ITEM #1: o actual iOS de
 * [imageBitmapFromArgb] usa `Image.makeRaster(ImageInfo(..RGBA_8888..), bytes, w*4)` +
 * `toComposeImageBitmap()`. Confirma que o raster skiko EXISTE e FUNCIONA no CMP 1.7.1 no iOS —
 * dimensões certas e ordem de canais correta no round-trip (pega inversão R↔B).
 *
 * Mora em `iosTest` (não em `commonTest`) porque o actual ANDROID equivalente chama
 * `Bitmap.createBitmap`, que lança "not mocked" no unit test JVM do Android. O decode de bytes
 * (`makeFromEncoded`) é exercitado funcionalmente pelo harness A/B do Martelo.
 */
class ImageCodecIosTest {

    @Test
    fun imageBitmapFromArgb_roundTripsSizeAndChannels() {
        val argb = intArrayOf(
            0xFFFF0000.toInt(), 0xFF00FF00.toInt(), // vermelho, verde
            0xFF0000FF.toInt(), 0xFFFFFFFF.toInt(), // azul, branco
        )
        val bmp = imageBitmapFromArgb(argb, 2, 2)
        assertEquals(2, bmp.width)
        assertEquals(2, bmp.height)

        val pm = bmp.toPixelMap()
        assertEquals(0xFFFF0000.toInt(), pm[0, 0].toArgb())
        assertEquals(0xFF00FF00.toInt(), pm[1, 0].toArgb())
        assertEquals(0xFF0000FF.toInt(), pm[0, 1].toArgb())
        assertEquals(0xFFFFFFFF.toInt(), pm[1, 1].toArgb())
    }
}
