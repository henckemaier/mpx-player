package com.mpx.player.model

/** Playlist do usuário. `locked` = Favoritos (não renomeia nem exclui). `photo` = caminho da capa. */
data class Playlist(
    val id: String,
    val name: String,
    val photo: String? = null,
    val trackKeys: List<String> = emptyList(),
    val locked: Boolean = false,
) {
    companion object {
        const val FAVORITES_ID = "favoritos"
        const val FAVORITES_NAME = "FAVORITOS"
    }
}

/** Personalização de uma faixa, só dentro do MPX (não altera o arquivo). */
data class TrackOverride(
    val title: String? = null,
    val cover: String? = null,
)
