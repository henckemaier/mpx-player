package com.mpx.desktop

import kotlinx.serialization.Serializable

/**
 * CONTRATO JSON servido pelo agente e parseado pela sub-2 (cliente Android). Os nomes e
 * tipos destes campos sao FIXOS — a sub-2 depende deles. Espelha o `Track` do :app:
 * - [format] = `Track.format` (MP3/FLAC/AAC/OGG/OPUS/WAV, derivado do [mime]);
 * - [relPathHint] = caminho relativo (com '/') de onde a sub-2 deriva a `key` estavel
 *   (`Track.key` do app = `(relativePath + displayName).lowercase()`);
 * - [id] = hash ESTAVEL do caminho relativo (SHA-1 hex) — nao muda entre scans; o
 *   id-sintetico-Long do app pode vir do hash deste `id` string.
 */
@Serializable
data class RemoteTrackDto(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val trackNumber: Int,
    val year: Int?,
    val mime: String,
    val format: String,
    val bitrate: Int?,
    val hasArt: Boolean,
    val relPathHint: String,
)

/** Resposta do handshake/health em GET /ping. */
@Serializable
data class PingDto(
    val name: String,
    val version: String,
    val trackCount: Int,
)
