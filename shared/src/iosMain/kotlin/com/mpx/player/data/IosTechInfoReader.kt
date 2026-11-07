package com.mpx.player.data

import com.mpx.player.model.Track
import com.mpx.player.ui.TechInfo
import com.mpx.player.ui.TechInfoReader

/**
 * Leitor de metadados técnicos no iOS. Por ora NO-OP: devolve só o [TechInfo.format] derivado
 * do `Track` (o mapeamento MPMediaItem → Track já traz o formato), deixando bitrate/sampleRate
 * nos defaults. Follow-up real = `AVURLAsset` (ler `tracks`/`formatDescriptions`) — não bloqueia
 * esta fatia: o VM comum já fica verde e o host iOS (Martelo) pode injetar este reader.
 */
class IosTechInfoReader : TechInfoReader {
    override suspend fun read(track: Track): TechInfo = TechInfo(format = track.format)
}
