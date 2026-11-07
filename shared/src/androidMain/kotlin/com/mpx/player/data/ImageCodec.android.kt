package com.mpx.player.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual suspend fun decodeImageBytes(bytes: ByteArray, maxSide: Int): ImageBitmap? =
    withContext(Dispatchers.Default) {
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            val maxDim = maxOf(bounds.outWidth, bounds.outHeight).coerceAtLeast(1)
            val opts = BitmapFactory.Options().apply {
                inSampleSize = Integer.highestOneBit((maxDim / maxSide).coerceAtLeast(1))
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)?.asImageBitmap()
        }.getOrNull()
    }

actual fun imageBitmapFromArgb(argb: IntArray, w: Int, h: Int): ImageBitmap =
    Bitmap.createBitmap(argb, w, h, Bitmap.Config.ARGB_8888).asImageBitmap()
