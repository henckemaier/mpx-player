package com.mpx.player.data

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap

/**
 * Processa uma capa FONTE ([ImageBitmap]) no estilo do app → [CoverArt], espelhando o `process()`
 * do antigo `CoverArtLoader` (`:app`): style 2 → ASCII; style 1 & mono → dither 1-bit (pixelated);
 * senão a imagem original. Comum aos dois lados — os pixels da fonte saem por `toPixelMap()` (API
 * comum) e o resto é [CoverTransforms] puro + [imageBitmapFromArgb] (expect/actual).
 *
 * Cache simples em memória (sem `android.util.LruCache`): LinkedHashMap com descarte do mais antigo.
 */
class CoverProcessor {

    private val cache = LinkedHashMap<String, CoverArt>()

    /** Resultado já processado p/ a chave, se houver (deixa o [CoverLoader] evitar reler a fonte). */
    fun cached(key: String): CoverArt? = cache[key]

    fun process(src: ImageBitmap, key: String, style: Int, mono: Boolean, accent: Int, bg: Int): CoverArt {
        cache[key]?.let { return it }
        val art = when (style) {
            2 -> CoverArt.Ascii(asciiOf(src))
            else -> if (mono) {
                CoverArt.Image(imageBitmapFromArgb(ditherOf(src, accent, bg), DITHER, DITHER), pixelated = true)
            } else {
                CoverArt.Image(src, pixelated = false)
            }
        }
        if (cache.size >= MAX) cache.remove(cache.keys.iterator().next())
        cache[key] = art
        return art
    }

    private fun ditherOf(src: ImageBitmap, on: Int, off: Int): IntArray {
        val lum = CoverTransforms.luminance(scaledArgb(src, DITHER, DITHER))
        return CoverTransforms.dither(lum, DITHER, on, off)
    }

    private fun asciiOf(src: ImageBitmap): List<String> {
        val lum = CoverTransforms.luminance(scaledArgb(src, ASCII_COLS, ASCII_ROWS))
        return CoverTransforms.ascii(lum, ASCII_COLS, ASCII_ROWS)
    }

    /** Lê os pixels da fonte (ARGB) e reescala p/ `dw`×`dh` com o scaler puro. */
    private fun scaledArgb(src: ImageBitmap, dw: Int, dh: Int): IntArray {
        val pm = src.toPixelMap()
        val sw = src.width
        val sh = src.height
        val srcArgb = IntArray(sw * sh) { i -> pm[i % sw, i / sw].toArgb() }
        return CoverTransforms.scale(srcArgb, sw, sh, dw, dh)
    }

    private companion object {
        const val DITHER = 96      // dither(src, 96, ...) no original
        const val ASCII_COLS = 26  // ascii(src, 26) → rows = cols/2
        const val ASCII_ROWS = 13
        const val MAX = 64         // LruCache<String, CoverArt>(64) no original
    }
}
