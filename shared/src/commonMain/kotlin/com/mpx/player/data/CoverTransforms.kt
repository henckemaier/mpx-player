package com.mpx.player.data

/**
 * Transforms PUROS da capa, sem qualquer dependência de plataforma: escala, luminância, dither
 * Floyd–Steinberg 1-bit e ASCII, operando sobre `IntArray` ARGB (alfa no byte alto, `0xAARRGGBB`).
 * Por serem Kotlin puro, produzem bytes IDÊNTICOS nos dois lados (Android × iOS) — é isso que o
 * `CoverTransformsTest` trava (roda em `:shared:testDebugUnitTest` E `iosSimulatorArm64Test`).
 *
 * Espelham EXATAMENTE a lógica do antigo `CoverArtLoader` (`:app`): pesos Floyd–Steinberg
 * 7/3/5/1 ÷16 com limiar 0.5, rampa ASCII `" .:-=+*#%@"`, luminância 0.299/0.587/0.114. O único
 * ponto que não era puro — `Bitmap.createScaledBitmap(..., filter = true)` — vira o [scale]
 * bilinear abaixo (mesma intenção; a diferença mínima de amostragem vs. o scaler nativo do Android
 * é coberta pelo gate visual A/B).
 */
object CoverTransforms {

    // R/G/B por bitmask do ARGB (sem android.graphics.Color, que é JVM/Android-only).
    private fun red(c: Int) = (c shr 16) and 0xFF
    private fun green(c: Int) = (c shr 8) and 0xFF
    private fun blue(c: Int) = c and 0xFF

    /**
     * Escala bilinear pura de `src` (`sw`×`sh`) para `dw`×`dh`, preservando ARGB (alfa interpolado
     * junto). Substitui `Bitmap.createScaledBitmap(bmp, dw, dh, true)`. Amostragem no centro do
     * pixel (`(i + 0.5) * escala - 0.5`), como os filtros bilineares padrão.
     */
    fun scale(src: IntArray, sw: Int, sh: Int, dw: Int, dh: Int): IntArray {
        if (sw == dw && sh == dh) return src.copyOf()
        val out = IntArray(dw * dh)
        val fx = if (dw > 1) sw.toFloat() / dw else 0f
        val fy = if (dh > 1) sh.toFloat() / dh else 0f
        for (y in 0 until dh) {
            val sy = ((y + 0.5f) * fy - 0.5f).coerceIn(0f, (sh - 1).toFloat())
            val y0 = sy.toInt()
            val y1 = minOf(y0 + 1, sh - 1)
            val wy = sy - y0
            for (x in 0 until dw) {
                val sx = ((x + 0.5f) * fx - 0.5f).coerceIn(0f, (sw - 1).toFloat())
                val x0 = sx.toInt()
                val x1 = minOf(x0 + 1, sw - 1)
                val wx = sx - x0
                out[y * dw + x] = lerpArgb(
                    src[y0 * sw + x0], src[y0 * sw + x1],
                    src[y1 * sw + x0], src[y1 * sw + x1],
                    wx, wy,
                )
            }
        }
        return out
    }

    private fun lerpArgb(c00: Int, c10: Int, c01: Int, c11: Int, wx: Float, wy: Float): Int {
        fun ch(shift: Int): Int {
            val a = ((c00 shr shift) and 0xFF).toFloat()
            val b = ((c10 shr shift) and 0xFF).toFloat()
            val c = ((c01 shr shift) and 0xFF).toFloat()
            val d = ((c11 shr shift) and 0xFF).toFloat()
            val top = a + (b - a) * wx
            val bot = c + (d - c) * wx
            return (top + (bot - top) * wy + 0.5f).toInt().coerceIn(0, 255)
        }
        return (ch(24) shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    /** Luminância 0..1 (0.299/0.587/0.114) por pixel de um ARGB já no tamanho alvo. */
    fun luminance(argb: IntArray): FloatArray = FloatArray(argb.size) { i ->
        val c = argb[i]
        (0.299f * red(c) + 0.587f * green(c) + 0.114f * blue(c)) / 255f
    }

    /**
     * Floyd–Steinberg 1-bit em duas cores ARGB (`on`/`off`). CONSOME/MUTA `lum` (difusão do erro);
     * `lum` tem `size`×`size`. Retorna ARGB `size`×`size`. Pesos 7/3/5/1 ÷16, limiar 0.5 — EXATO.
     */
    fun dither(lum: FloatArray, size: Int, on: Int, off: Int): IntArray {
        val out = IntArray(size * size)
        for (y in 0 until size) for (x in 0 until size) {
            val i = y * size + x
            val old = lum[i]
            val new = if (old > 0.5f) 1f else 0f
            out[i] = if (new > 0f) on else off
            val err = old - new
            if (x + 1 < size) lum[i + 1] += err * 7 / 16
            if (y + 1 < size) {
                if (x > 0) lum[i + size - 1] += err * 3 / 16
                lum[i + size] += err * 5 / 16
                if (x + 1 < size) lum[i + size + 1] += err * 1 / 16
            }
        }
        return out
    }

    /** ASCII: `rows` linhas de `cols` colunas a partir de `lum` (`cols`×`rows`), rampa fixa. */
    fun ascii(lum: FloatArray, cols: Int, rows: Int): List<String> {
        val ramp = " .:-=+*#%@"
        return List(rows) { y ->
            buildString {
                for (x in 0 until cols) {
                    append(ramp[(lum[y * cols + x] * (ramp.length - 1)).toInt().coerceIn(0, ramp.length - 1)])
                }
            }
        }
    }
}
