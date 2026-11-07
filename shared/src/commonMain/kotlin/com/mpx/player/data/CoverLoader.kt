package com.mpx.player.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Cola comum entre a aquisição ([AlbumArtSource]) e o processamento ([CoverProcessor]). Reproduz a
 * API pública do antigo `CoverArtLoader` (`load`/`loadFile`) para que o [com.mpx.player.ui.components.Cover]
 * mude o mínimo. Mantém o cache de "não tem arte" (o antigo `missing`) p/ não reler a fonte à toa,
 * e roda o processamento (CPU) fora da Main via `Dispatchers.Default`.
 */
class CoverLoader(
    private val source: AlbumArtSource,
    private val processor: CoverProcessor = CoverProcessor(),
) {
    private val misses = HashSet<String>()

    /** Capa da faixa (style: 1 = DITHER, 2 = ASCII). null → UI desenha a capa gerada. */
    suspend fun load(albumId: Long, trackUri: String, style: Int, mono: Boolean, accent: Int, bg: Int): CoverArt? {
        val key = "$albumId/$style/$mono/$accent/$bg"
        processor.cached(key)?.let { return it }
        if (key in misses) return null
        val src = source.sourceArt(albumId, trackUri) ?: run { misses += key; return null }
        return withContext(Dispatchers.Default) { processor.process(src, key, style, mono, accent, bg) }
    }

    /** Imagem de arquivo (capa personalizada / foto de playlist) filtrada no mesmo estilo. */
    suspend fun loadFile(path: String, style: Int, mono: Boolean, accent: Int, bg: Int): CoverArt? {
        val key = "file:$path/$style/$mono/$accent/$bg"
        processor.cached(key)?.let { return it }
        if (key in misses) return null
        val src = source.sourceFile(path) ?: run { misses += key; return null }
        return withContext(Dispatchers.Default) { processor.process(src, key, style, mono, accent, bg) }
    }
}
