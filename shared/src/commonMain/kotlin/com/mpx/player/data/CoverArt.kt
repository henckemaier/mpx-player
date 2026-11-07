package com.mpx.player.data

import androidx.compose.ui.graphics.ImageBitmap

/**
 * Capa já processada, pronta para desenhar. Migrada p/ `commonMain` no porte iOS (P7 fatia cover):
 * o bitmap deixa de ser `android.graphics.Bitmap` e passa a ser o [ImageBitmap] comum do Compose
 * (produzido no Android via `asImageBitmap()`, no iOS via skiko). MESMO FQN do antigo `:app`.
 */
sealed interface CoverArt {
    data class Image(val bitmap: ImageBitmap, val pixelated: Boolean) : CoverArt
    data class Ascii(val rows: List<String>) : CoverArt
}
