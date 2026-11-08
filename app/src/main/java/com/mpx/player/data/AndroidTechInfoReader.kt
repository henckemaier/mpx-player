package com.mpx.player.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import com.mpx.player.model.Track
import com.mpx.player.ui.TechInfo
import com.mpx.player.ui.TechInfoReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Leitor de metadados técnicos no Android: MediaMetadataRetriever sobre o `uri` da faixa.
 * Corpo herdado do antigo `PlayerViewModel.readTech` (o único trecho Android-only do VM,
 * agora atrás do contrato comum [TechInfoReader] para o VM viver no `commonMain`).
 */
class AndroidTechInfoReader(private val context: Context) : TechInfoReader {
    override suspend fun read(t: Track): TechInfo = withContext(Dispatchers.IO) {
        var rate = "---"
        var bitrate = t.bitrate?.let { "${it / 1000}K" } ?: "---"
        val retr = MediaMetadataRetriever()
        try {
            retr.setDataSource(context, Uri.parse(t.uri))
            if (bitrate == "---") retr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull()?.let { bitrate = "${it / 1000}K" }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                retr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)?.toIntOrNull()?.let {
                    rate = if (it % 1000 == 0) "${it / 1000}kHz" else String.format(Locale.US, "%.1fkHz", it / 1000f)
                }
            }
        } catch (_: Exception) {
        } finally {
            runCatching { retr.release() }
        }
        TechInfo(
            format = t.format,
            bitrate = bitrate,
            sampleRate = rate,
            extra = t.year?.let { "ANO - $it" } ?: "TRK - %02d".format(t.trackNumber.coerceAtLeast(1)),
        )
    }
}
