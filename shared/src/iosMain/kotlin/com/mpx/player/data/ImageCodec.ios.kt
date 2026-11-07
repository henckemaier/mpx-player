package com.mpx.player.data

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo

// skiko é a engine de render do Compose Multiplatform no iOS (o mesmo Skia do Android). Estes
// símbolos (`org.jetbrains.skia.*` + `toComposeImageBitmap()`) vêm transitivos de compose.ui/
// foundation no target iosSimulatorArm64. WATCH-ITEM da fatia: o CI (macOS) é quem confirma
// compile+link deste actual; o render funcional do decode é exercitado pelo A/B do Martelo.

actual suspend fun decodeImageBytes(bytes: ByteArray, maxSide: Int): ImageBitmap? =
    withContext(Dispatchers.Default) {
        // maxSide é um hint de memória; o skiko decodifica em tamanho cheio e o CoverProcessor
        // reescala p/ 96/26 antes do dither/ascii. Amostragem no decode = follow-up.
        runCatching { Image.makeFromEncoded(bytes).toComposeImageBitmap() }.getOrNull()
    }

actual fun imageBitmapFromArgb(argb: IntArray, w: Int, h: Int): ImageBitmap {
    // ARGB int (0xAARRGGBB) → bytes RGBA_8888 (ordem R,G,B,A) que o skiko espera no makeRaster.
    val bytes = ByteArray(w * h * 4)
    var j = 0
    for (c in argb) {
        bytes[j++] = ((c shr 16) and 0xFF).toByte() // R
        bytes[j++] = ((c shr 8) and 0xFF).toByte()  // G
        bytes[j++] = (c and 0xFF).toByte()           // B
        bytes[j++] = ((c shr 24) and 0xFF).toByte()  // A
    }
    val info = ImageInfo(w, h, ColorType.RGBA_8888, ColorAlphaType.UNPREMUL)
    return Image.makeRaster(info, bytes, w * 4).toComposeImageBitmap()
}
