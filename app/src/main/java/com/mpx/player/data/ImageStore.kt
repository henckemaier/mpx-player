package com.mpx.player.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Copia uma imagem escolhida (content Uri) para filesDir/<subdir>, reduzida (~512px) e em JPEG,
 * para servir de foto de playlist ou capa personalizada de faixa. O filtro visual (dither/ASCII/
 * mono) é aplicado depois, no render, pelo [CoverArtLoader].
 */
class ImageStore(private val context: Context) {

    suspend fun save(src: Uri, subdir: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.filesDir, subdir).apply { mkdirs() }
            val dest = File(dir, UUID.randomUUID().toString() + ".jpg")
            val bmp = oriented(src, decodeScaled(src, 512) ?: return@runCatching null)
            val ok = dest.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            bmp.recycle()
            if (!ok) { dest.delete(); return@runCatching null }
            dest.absolutePath
        }.getOrNull()
    }

    /** Aplica a orientação EXIF (fotos de câmera vêm rotacionadas via tag, não nos pixels). */
    private fun oriented(src: Uri, bmp: Bitmap): Bitmap {
        val deg = runCatching {
            context.contentResolver.openInputStream(src)?.use {
                when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
        }.getOrDefault(0f)
        if (deg == 0f) return bmp
        return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(deg) }, true)
            .also { if (it != bmp) bmp.recycle() }
    }

    private fun decodeScaled(src: Uri, maxDim: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(src)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val minSide = minOf(bounds.outWidth, bounds.outHeight).coerceAtLeast(1)
        val opts = BitmapFactory.Options().apply {
            inSampleSize = Integer.highestOneBit((minSide / maxDim).coerceAtLeast(1))
        }
        return context.contentResolver.openInputStream(src)?.use { BitmapFactory.decodeStream(it, null, opts) }
    }
}
