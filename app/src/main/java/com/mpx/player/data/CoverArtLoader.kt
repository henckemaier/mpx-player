package com.mpx.player.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import android.util.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Capa já processada, pronta para desenhar. */
sealed interface CoverArt {
    data class Image(val bitmap: Bitmap, val pixelated: Boolean) : CoverArt
    data class Ascii(val rows: List<String>) : CoverArt
}

class CoverArtLoader(private val context: Context) {

    private val raw = object : LruCache<Long, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: Long, value: Bitmap) = value.byteCount
    }
    private val missing = HashSet<Long>()
    private val processed = LruCache<String, CoverArt>(64)

    /**
     * style: 1 = DITHER (1-bit no tom do tema, ou original se mono=false), 2 = ASCII.
     * Retorna null quando a faixa não tem arte embutida (a UI desenha a capa gerada).
     */
    suspend fun load(albumId: Long, trackUri: Uri, style: Int, mono: Boolean, accent: Int, bg: Int): CoverArt? =
        withContext(Dispatchers.Default) {
            val key = "$albumId/$style/$mono/$accent/$bg"
            processed.get(key)?.let { return@withContext it }
            val src = rawBitmap(albumId, trackUri) ?: return@withContext null
            process(src, key, style, mono, accent, bg)
        }

    /** Carrega uma imagem de arquivo (foto de playlist / capa personalizada) com o mesmo filtro. */
    suspend fun loadFile(file: File, style: Int, mono: Boolean, accent: Int, bg: Int): CoverArt? =
        withContext(Dispatchers.Default) {
            val key = "file:${file.path}:${file.lastModified()}/$style/$mono/$accent/$bg"
            processed.get(key)?.let { return@withContext it }
            val src = fileBitmap(file) ?: return@withContext null
            process(src, key, style, mono, accent, bg)
        }

    private fun process(src: Bitmap, key: String, style: Int, mono: Boolean, accent: Int, bg: Int): CoverArt {
        val art = when (style) {
            2 -> CoverArt.Ascii(ascii(src, 26))
            else -> if (mono) CoverArt.Image(dither(src, 96, accent, bg), pixelated = true)
            else CoverArt.Image(src, pixelated = false)
        }
        processed.put(key, art)
        return art
    }

    private val files = object : LruCache<String, Bitmap>(12 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    private fun fileBitmap(file: File): Bitmap? {
        if (!file.isFile) return null
        files.get(file.path)?.let { return it }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        val minSide = minOf(bounds.outWidth, bounds.outHeight).coerceAtLeast(1)
        val opts = BitmapFactory.Options().apply { inSampleSize = Integer.highestOneBit((minSide / 320).coerceAtLeast(1)) }
        return BitmapFactory.decodeFile(file.path, opts)?.also { files.put(file.path, it) }
    }

    private suspend fun rawBitmap(albumId: Long, uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        raw.get(albumId)?.let { return@withContext it }
        if (albumId in missing) return@withContext null
        // loadThumbnail depende do cache de miniaturas do sistema, que não existe para arquivos
        // recém-inseridos no MediaStore (ex.: baixados pelo app). Nesse caso, lê a arte embutida
        // (ID3 APIC) direto do arquivo.
        val bmp = runCatching { context.contentResolver.loadThumbnail(uri, Size(320, 320), null) }.getOrNull()
            ?: embeddedPicture(uri)
        if (bmp == null) missing += albumId else raw.put(albumId, bmp)
        bmp
    }

    /** Arte embutida (ID3/APIC etc.) lida diretamente do arquivo, reduzida para ~320px. */
    private fun embeddedPicture(uri: Uri): Bitmap? {
        val retr = MediaMetadataRetriever()
        val bytes = try {
            retr.setDataSource(context, uri)
            retr.embeddedPicture
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retr.release() }
        } ?: return null
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            val minDim = minOf(bounds.outWidth, bounds.outHeight).coerceAtLeast(1)
            val opts = BitmapFactory.Options().apply { inSampleSize = Integer.highestOneBit((minDim / 320).coerceAtLeast(1)) }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        }.getOrNull()
    }

    private fun luminance(src: Bitmap, w: Int, h: Int): FloatArray {
        val s = Bitmap.createScaledBitmap(src.copy(Bitmap.Config.ARGB_8888, false), w, h, true)
        val px = IntArray(w * h)
        s.getPixels(px, 0, w, 0, 0, w, h)
        return FloatArray(px.size) { i ->
            val c = px[i]
            (0.299f * Color.red(c) + 0.587f * Color.green(c) + 0.114f * Color.blue(c)) / 255f
        }
    }

    /** Floyd–Steinberg 1-bit em duas cores. */
    private fun dither(src: Bitmap, size: Int, on: Int, off: Int): Bitmap {
        val lum = luminance(src, size, size)
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
        return Bitmap.createBitmap(out, size, size, Bitmap.Config.ARGB_8888)
    }

    private fun ascii(src: Bitmap, cols: Int): List<String> {
        val ramp = " .:-=+*#%@"
        val rows = cols / 2
        val lum = luminance(src, cols, rows)
        return List(rows) { y ->
            buildString { for (x in 0 until cols) append(ramp[(lum[y * cols + x] * (ramp.length - 1)).toInt().coerceIn(0, ramp.length - 1)]) }
        }
    }
}
