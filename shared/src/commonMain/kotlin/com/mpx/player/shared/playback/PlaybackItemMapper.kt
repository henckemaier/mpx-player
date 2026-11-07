package com.mpx.player.shared.playback

import com.mpx.player.model.Track

/**
 * Converte uma [Track] do domínio no [PlaybackItem] neutro do contrato comum.
 *
 * É `expect`/`actual` porque o `artworkUri` DIFERE por plataforma (não era comum, apesar do
 * mesmo FQN): no Android é a URI de albumart do MediaStore (`content://.../albumart/<albumId>`),
 * no iOS é um candidato carregável (`coverOverride` ou a própria `uri` p/ arte embutida). O resto
 * (`id = Track.key`, uri/title/artist/album/durationMs) é idêntico nos dois lados. Promovido a
 * `expect`/`actual` na fatia LIBRARY (P7) para o `LibraryViewModel` comum poder enfileirar.
 */
expect fun Track.toPlaybackItem(): PlaybackItem
