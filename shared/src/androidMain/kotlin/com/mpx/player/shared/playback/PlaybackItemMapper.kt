package com.mpx.player.shared.playback

import com.mpx.player.model.Track

/**
 * Converte uma [Track] do domínio no [PlaybackItem] neutro do contrato comum.
 *
 * - `id = Track.key` (debt 3): identidade estável do arquivo. O `PlaybackService` resolve a
 *   URI por `requestMetadata.mediaUri`, **não** pelo mediaId — então usar a key é seguro
 *   (confirmado na fatia 3). A UI resolve de volta via `LibraryState.trackByKey(id)`.
 * - `artworkUri` é a MESMA capa de álbum que o antigo `PlayerController.toMediaItem` usava
 *   (`ContentUris.withAppendedId(ALBUM_ART, albumId)`), aqui como String pura equivalente.
 *
 * Vive em `androidMain` (o esquema de URI de capa é do MediaStore, específico do Android);
 * o `:app` usa no fluxo de reprodução a partir da sub-fatia 4.3. Ainda SEM uso nesta fatia.
 */
fun Track.toPlaybackItem(): PlaybackItem = PlaybackItem(
    id = key,
    uri = uri,
    title = title,
    artist = artist,
    album = album,
    artworkUri = "content://media/external/audio/albumart/$albumId",
    durationMs = durationMs,
)
