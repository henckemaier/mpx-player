package com.mpx.player.data

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Trava os transforms PUROS da capa ([CoverTransforms]) sobre `IntArray` FIXO, com saída esperada
 * HARDCODED. Como é Kotlin puro e roda em `:shared:testDebugUnitTest` E `iosSimulatorArm64Test`,
 * prova que scale/luminância/dither/ascii dão bytes IDÊNTICOS nos dois lados — independente do
 * decode de imagem (que é expect/actual e não entra aqui).
 */
class CoverTransformsTest {

    private val ON = 0xFF3DFF8F.toInt()
    private val OFF = 0xFF061209.toInt()

    @Test
    fun luminance_usesRec601Weights() {
        val argb = intArrayOf(
            0xFFFF0000.toInt(), // vermelho → 0.299
            0xFF00FF00.toInt(), // verde    → 0.587
            0xFF0000FF.toInt(), // azul     → 0.114
            0xFFFFFFFF.toInt(), // branco   → 1.0
            0xFF000000.toInt(), // preto    → 0.0
        )
        val lum = CoverTransforms.luminance(argb)
        assertEquals(0.299f, lum[0], 1e-4f)
        assertEquals(0.587f, lum[1], 1e-4f)
        assertEquals(0.114f, lum[2], 1e-4f)
        assertEquals(1.0f, lum[3], 1e-4f)
        assertEquals(0.0f, lum[4], 1e-4f)
    }

    @Test
    fun dither_thresholdAndErrorDiffusion() {
        // 2x2: só o pixel 0 (0.6 > 0.5) acende; a difusão do erro (negativo) derruba os vizinhos.
        val lum = floatArrayOf(0.6f, 0.0f, 0.0f, 0.0f)
        val out = CoverTransforms.dither(lum, 2, ON, OFF)
        assertEquals(listOf(ON, OFF, OFF, OFF), out.toList())
    }

    @Test
    fun ascii_mapsLuminanceToRamp() {
        // rampa " .:-=+*#%@" (len 10): 0.0→' ', 0.5→'=', 1.0→'@'.
        val lum = floatArrayOf(0.0f, 0.5f, 1.0f)
        assertEquals(listOf(" =@"), CoverTransforms.ascii(lum, cols = 3, rows = 1))
    }

    @Test
    fun scale_identityReturnsSamePixels() {
        val src = intArrayOf(0x11111111, 0x22222222, 0x33333333, 0x44444444)
        assertEquals(src.toList(), CoverTransforms.scale(src, 2, 2, 2, 2).toList())
    }

    @Test
    fun scale_constantImageStaysConstant() {
        val px = 0xFF804020.toInt()
        val src = IntArray(4) { px } // 2x2 chapado
        val out = CoverTransforms.scale(src, 2, 2, 3, 3) // upscale 3x3
        assertEquals(List(9) { px }, out.toList())
    }

    @Test
    fun scale_bilinearInterpolatesMidpoint() {
        // linha 2px: preto → (100,100,100). Upscale p/ 3px: ponta preta, meio ~(50,50,50), fim 100.
        val src = intArrayOf(0xFF000000.toInt(), 0xFF646464.toInt())
        val out = CoverTransforms.scale(src, sw = 2, sh = 1, dw = 3, dh = 1)
        assertEquals(listOf(0xFF000000.toInt(), 0xFF323232.toInt(), 0xFF646464.toInt()), out.toList())
    }
}
