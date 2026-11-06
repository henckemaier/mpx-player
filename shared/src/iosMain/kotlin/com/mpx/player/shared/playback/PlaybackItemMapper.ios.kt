package com.mpx.player.shared.playback

import com.mpx.player.model.Track

/**
 * Converte uma [Track] do domínio no [PlaybackItem] neutro do contrato comum — versão
 * iOS, SIMÉTRICA à de `androidMain` (`PlaybackItemMapper.kt`), mas com um `artworkUri`
 * **carregável no iOS** (o `content://media/external/audio/albumart/...` do Android não
 * resolve aqui).
 *
 * - `id = Track.key`: mesma convenção do Android (identidade estável do arquivo; a UI
 *   resolve de volta via `LibraryState.trackByKey(id)`).
 * - `artworkUri = iosArtworkUri(this)`: ver a justificativa da seleção nessa função.
 *
 * Vive em `iosMain`; o consumo (carregar a imagem e publicar na tela de bloqueio) é do
 * [IosAudioPlayer.updateNowPlaying]. Esta função é PURA (sem AVFoundation/UIKit) e coberta
 * por teste em `iosTest` (`IosArtworkLogicTest`).
 */
fun Track.toPlaybackItem(): PlaybackItem = PlaybackItem(
    id = key,
    uri = uri,
    title = title,
    artist = artist,
    album = album,
    artworkUri = iosArtworkUri(this),
    durationMs = durationMs,
)

/**
 * Escolhe a fonte da capa no iOS, como um candidato **carregável**:
 *
 *  1. [Track.coverOverride] quando presente — caminho de um arquivo de imagem de capa
 *     custom, carregável direto via `UIImage(contentsOfFile:)`;
 *  2. senão, a própria [Track.uri] do áudio — de onde a arte EMBUTIDA é extraída na
 *     exibição (`AVURLAsset(uri).commonMetadata`, chave `artwork`).
 *
 * Por que `coverOverride ?: uri` (e **não** `coverOverride ?: null`): devolver a `uri`
 * como fallback mantém a arte embutida acessível — a maioria dos arquivos de música a
 * tem. Retornar `null` limitaria a capa apenas aos overrides custom, perdendo a arte
 * embutida. O carregador (no [IosAudioPlayer]) desambigua: tenta imagem-de-arquivo e,
 * falhando, extrai do asset de áudio. Blank vira `null` (nada carregável).
 *
 * LIMITE: se não há `coverOverride` nem arte embutida no arquivo, fica sem capa
 * (degradação graciosa — não é erro).
 */
internal fun iosArtworkUri(track: Track): String? =
    track.coverOverride?.takeIf { it.isNotBlank() }
        ?: track.uri.takeIf { it.isNotBlank() }

/**
 * Anti-race da capa assíncrona: só aplica a capa recém-carregada se a faixa atual ainda
 * é a mesma de quando o load começou. [startedId] é o id capturado no início do load;
 * [currentId] é o id da faixa atual no momento de aplicar. Função PURA (testável).
 *
 * Uma capa que terminou de carregar DEPOIS de o usuário trocar de faixa não pode
 * sobrescrever a capa da faixa nova — nesse caso retorna `false` e a capa velha é
 * descartada.
 */
internal fun shouldApplyArtwork(startedId: String?, currentId: String?): Boolean =
    startedId != null && startedId == currentId
