package com.mpx.player.data

import androidx.compose.ui.graphics.ImageBitmap

/**
 * Dois pontos de contato dependentes de plataforma do pipeline de capa (P7 fatia cover). Tudo ao
 * redor — scale/luminance/dither/ascii ([CoverTransforms]) e orquestração ([CoverProcessor]/
 * [CoverLoader]) — é comum; só o codec de imagem precisa de `expect`/`actual`.
 */

/**
 * Decodifica bytes de imagem (arte embutida / arquivo / sample do A/B) para um [ImageBitmap],
 * reduzindo p/ ~[maxSide] no maior lado quando a plataforma suportar amostragem no decode.
 * - Android: `BitmapFactory` com `inSampleSize` → `asImageBitmap()`.
 * - iOS: skiko `Image.makeFromEncoded(bytes).toComposeImageBitmap()`.
 * Retorna null quando os bytes não decodificam.
 */
expect suspend fun decodeImageBytes(bytes: ByteArray, maxSide: Int): ImageBitmap?

/**
 * Constrói um [ImageBitmap] a partir de pixels ARGB (`0xAARRGGBB`), a saída do dither.
 * - Android: `Bitmap.createBitmap(argb, w, h, ARGB_8888).asImageBitmap()`.
 * - iOS: skiko `Image.makeRaster(ImageInfo(..RGBA_8888..), bytes, w*4).toComposeImageBitmap()`.
 */
expect fun imageBitmapFromArgb(argb: IntArray, w: Int, h: Int): ImageBitmap
