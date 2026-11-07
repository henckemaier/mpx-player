package com.mpx.player.data

import androidx.compose.ui.graphics.ImageBitmap

/**
 * Aquisição de bitmap de capa, dependente de plataforma (a camada Android-only do antigo
 * `CoverArtLoader`). Devolve já um [ImageBitmap] comum; o filtro (dither/ascii/mono) é aplicado
 * depois pelo [CoverProcessor].
 *
 * - Android (`AndroidAlbumArtSource`, `:app`): `loadThumbnail` do MediaStore + arte embutida
 *   (ID3/APIC via `MediaMetadataRetriever`) + `decodeFile`.
 * - iOS (`IosAlbumArtSource`): arte do device (`MPMediaItemArtwork`) é FOLLOW-UP → retorna null
 *   por ora (a UI cai na `GeneratedCover`).
 */
interface AlbumArtSource {
    /** Arte do álbum/faixa (thumbnail do sistema ou arte embutida). null se não houver. */
    suspend fun sourceArt(albumId: Long, trackUri: String): ImageBitmap?

    /** Imagem de um arquivo por caminho (capa personalizada / foto de playlist). null se ausente. */
    suspend fun sourceFile(path: String): ImageBitmap?
}
