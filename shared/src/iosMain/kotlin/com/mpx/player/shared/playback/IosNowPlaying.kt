@file:OptIn(ExperimentalForeignApi::class)

package com.mpx.player.shared.playback

import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFoundation.AVMetadataCommonKeyArtwork
import platform.AVFoundation.AVMetadataItem
import platform.AVFoundation.AVURLAsset
import platform.AVFoundation.commonKey
import platform.AVFoundation.commonMetadata
import platform.AVFoundation.dataValue
import platform.Foundation.NSURL
import platform.MediaPlayer.MPMediaItemPropertyAlbumTitle
import platform.MediaPlayer.MPMediaItemPropertyArtist
import platform.MediaPlayer.MPMediaItemPropertyPlaybackDuration
import platform.MediaPlayer.MPMediaItemPropertyTitle
import platform.MediaPlayer.MPNowPlayingInfoPropertyElapsedPlaybackTime
import platform.MediaPlayer.MPNowPlayingInfoPropertyPlaybackRate
import platform.UIKit.UIImage

/**
 * Now-playing / tela de bloqueio do caminho **engine** (`IosEngineAudioPlayer`, backend
 * `AVAudioEngine` atrás do flag `USE_ENGINE_BACKEND`=OFF).
 *
 * Contém o helper **PURO** [buildNowPlayingInfo] (montagem do dicionário do `MPNowPlayingInfoCenter`,
 * testável sem device em `iosTest`) e a dup estreita de [loadArtwork]/[extractEmbeddedArtwork] (o
 * EFEITO de resolver a capa numa `UIImage`).
 *
 * ## Por que a dup (decisão [SD]=(b) da sub-3)
 * O [IosAudioPlayer] de PRODUÇÃO (`AVQueuePlayer`) tem suas PRÓPRIAS `loadArtwork`/
 * `extractEmbeddedArtwork` privadas. Movê-las p/ cá orfanaria 7 imports AVFoundation/UIKit naquele
 * arquivo (`UIImage`/`AVURLAsset`/`AVMetadataItem`/`commonKey`/`commonMetadata`/
 * `AVMetadataCommonKeyArtwork`/`dataValue`, usados SÓ por elas) → um *import-ripple* além da
 * relocação trivial. O limite da sub-3 é ZERO mudança de comportamento/fiação na produção, então
 * DUPLICAMOS aqui e deixamos o `IosAudioPlayer` BYTE-IDÊNTICO. Dup estreita e descartável: quando (se)
 * o engine graduar a padrão num device, o backend `AVQueuePlayer` + esta dup saem juntos (ver o
 * TODO(engine-graduation) no [IosEngineAudioPlayer]).
 *
 * device-gated: ver a capa/metadata reais no lock-screen depende de device; o CI prova compile+link
 * + o PURO [buildNowPlayingInfo] + [shouldApplyArtwork]/[iosArtworkUri] (já testados).
 */

/**
 * Monta o dicionário do `MPNowPlayingInfoCenter` a partir de um [PlaybackItem] + os 3 escalares
 * (duração/decorrido em segundos, [rate] 0/1). PURO (sem player/efeito): mesmas CHAVES e semântica
 * do `updateNowPlaying` da produção — título/artista/álbum + duração + tempo decorrido + rate.
 * device-gated: só a PUBLICAÇÃO (`center.nowPlayingInfo = ...`) toca MediaPlayer; montar o map não.
 */
internal fun buildNowPlayingInfo(
    item: PlaybackItem,
    durationSec: Double,
    elapsedSec: Double,
    rate: Float,
): Map<Any?, Any?> = mapOf(
    MPMediaItemPropertyTitle to item.title,
    MPMediaItemPropertyArtist to item.artist,
    MPMediaItemPropertyAlbumTitle to item.album,
    MPMediaItemPropertyPlaybackDuration to durationSec,
    MPNowPlayingInfoPropertyElapsedPlaybackTime to elapsedSec,
    MPNowPlayingInfoPropertyPlaybackRate to rate,
)

/**
 * Resolve o `artworkUri` numa [UIImage]: primeiro como arquivo de imagem (capa custom
 * `coverOverride`); senão, extrai a arte EMBUTIDA do asset de áudio (`AVURLAsset.commonMetadata`,
 * chave `artwork`). `null` = sem capa carregável. Dup estreita da versão de produção (ver KDoc acima).
 */
internal fun loadArtwork(artworkUri: String): UIImage? {
    UIImage(contentsOfFile = artworkUri)?.let { return it }
    return extractEmbeddedArtwork(artworkUri)
}

private fun extractEmbeddedArtwork(uri: String): UIImage? {
    val url = NSURL.URLWithString(uri) ?: NSURL.fileURLWithPath(uri)
    val asset = AVURLAsset(uRL = url, options = null)
    for (entry in asset.commonMetadata.orEmpty()) {
        val meta = entry as? AVMetadataItem ?: continue
        if (meta.commonKey == AVMetadataCommonKeyArtwork) {
            val data = meta.dataValue ?: continue
            UIImage(data = data)?.let { return it }
        }
    }
    return null
}
