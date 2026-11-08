package com.mpx.desktop

import java.io.File

/** Bytes de uma capa + seu content-type (p/ GET /art). */
class ArtData(val bytes: ByteArray, val contentType: String)

/**
 * Modelo em memoria servido pelos endpoints, indexado pelo id ESTAVEL. [artResolver] resolve
 * a capa sob demanda (embutida OU folder.jpg) — injetavel p/ manter a Library testavel sem a
 * lib de tags (o default nao resolve nada).
 */
class Library(
    entries: List<LibraryEntry>,
    private val artResolver: (LibraryEntry) -> ArtData? = { null },
) {
    private val byId: Map<String, LibraryEntry> = entries.associateBy { it.dto.id }

    val dtos: List<RemoteTrackDto> = entries.map { it.dto }
    val count: Int get() = byId.size

    fun file(id: String): File? = byId[id]?.file
    fun mime(id: String): String? = byId[id]?.dto?.mime
    fun art(id: String): ArtData? = byId[id]?.let(artResolver)
}
