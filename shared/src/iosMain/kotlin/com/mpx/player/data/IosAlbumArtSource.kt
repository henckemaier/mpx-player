package com.mpx.player.data

import androidx.compose.ui.graphics.ImageBitmap

/**
 * Fonte de arte de capa no iOS. A arte do device (`MPMediaItemArtwork` da biblioteca) é FOLLOW-UP
 * (como a camada de dados nativa fechou lendo MPMediaQuery, a imagem entra numa fatia posterior).
 * Por ora devolve null nos dois caminhos → a UI cai na `GeneratedCover`.
 *
 * O SAMPLE do gate A/B NÃO passa por aqui: o harness do Martelo decodifica a imagem de resources
 * direto por [decodeImageBytes] e chama o [CoverProcessor].
 */
class IosAlbumArtSource : AlbumArtSource {
    override suspend fun sourceArt(albumId: Long, trackUri: String): ImageBitmap? = null
    override suspend fun sourceFile(path: String): ImageBitmap? = null
}
