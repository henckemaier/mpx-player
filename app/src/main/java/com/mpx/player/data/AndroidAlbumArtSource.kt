package com.mpx.player.data

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Aquisição Android de arte de capa — a camada Android-only do antigo `CoverArtLoader`: thumbnail
 * do MediaStore, arte embutida (ID3/APIC via `MediaMetadataRetriever`) e decode de arquivo. Entrega
 * um [ImageBitmap] comum; o filtro (dither/ascii/mono) fica no `CoverProcessor` (`commonMain`).
 */
class AndroidAlbumArtSource(private val context: Context) : AlbumArtSource {

    override suspend fun sourceArt(albumId: Long, trackUri: String): ImageBitmap? = withContext(Dispatchers.IO) {
        val uri = Uri.parse(trackUri)
        // loadThumbnail depende do cache de miniaturas do sistema, que não existe p/ arquivos
        // recém-inseridos no MediaStore (ex.: baixados pelo app); nesse caso lê a arte embutida.
        val thumb = runCatching { context.contentResolver.loadThumbnail(uri, Size(320, 320), null) }.getOrNull()
        if (thumb != null) return@withContext thumb.asImageBitmap()
        val bytes = embeddedBytes(uri) ?: return@withContext null
        decodeImageBytes(bytes, 320)
    }

    override suspend fun sourceFile(path: String): ImageBitmap? = withContext(Dispatchers.IO) {
        val file = File(path)
        if (!file.isFile) return@withContext null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        val minSide = minOf(bounds.outWidth, bounds.outHeight).coerceAtLeast(1)
        val opts = BitmapFactory.Options().apply {
            inSampleSize = Integer.highestOneBit((minSide / 320).coerceAtLeast(1))
        }
        BitmapFactory.decodeFile(path, opts)?.asImageBitmap()
    }

    /** Arte embutida (ID3/APIC etc.) lida direto do arquivo via retriever. */
    private fun embeddedBytes(uri: Uri): ByteArray? {
        val retr = MediaMetadataRetriever()
        return try {
            retr.setDataSource(context, uri)
            retr.embeddedPicture
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retr.release() }
        }
    }
}
