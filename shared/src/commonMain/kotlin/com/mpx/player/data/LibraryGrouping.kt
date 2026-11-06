package com.mpx.player.data

import com.mpx.player.model.Album
import com.mpx.player.model.Artist
import com.mpx.player.model.Track

/**
 * Agrupamento PURO de faixas em álbuns e artistas — comum a Android/iOS.
 *
 * Regras (idênticas às do antigo `AndroidLibraryRepository.rebuild()`):
 * - Álbuns: agrupados por [Track.albumId]; título = álbum da 1ª faixa do grupo; artista =
 *   o artista mais frequente do grupo; faixas ordenadas por `trackNumber` e depois
 *   `displayName`. Lista de álbuns ordenada por título (uppercase).
 * - Artistas: agrupados por [Track.artist]; faixas ordenadas por `album` e depois
 *   `trackNumber`. Lista de artistas ordenada por nome (uppercase).
 *
 * Função sem estado nem dependência de plataforma: recebe as faixas já resolvidas
 * (overrides aplicados) e devolve os índices derivados. A varredura e a aplicação de
 * overrides ficam na impl de plataforma, que delega a este agrupamento.
 */
fun buildAlbumsAndArtists(tracks: List<Track>): Pair<List<Album>, List<Artist>> {
    val albums = tracks.groupBy { it.albumId }.map { (id, list) ->
        Album(
            id = id,
            title = list.first().album,
            artist = list.groupingBy { it.artist }.eachCount().maxBy { it.value }.key,
            tracks = list.sortedWith(compareBy({ it.trackNumber }, { it.displayName })),
        )
    }.sortedBy { it.title.uppercase() }
    val artists = tracks.groupBy { it.artist }.map { (name, list) ->
        Artist(name, list.sortedWith(compareBy({ it.album }, { it.trackNumber })))
    }.sortedBy { it.name.uppercase() }
    return albums to artists
}
