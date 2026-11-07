package com.mpx.player.data

import com.mpx.player.model.LyricLine
import com.mpx.player.model.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Letras no iOS: busca ONLINE (LRCLIB → NetEase) via [LyricsApi] (commonMain, Ktor
 * Darwin já configurado no iosMain), atrás do contrato comum [LyricsRepository].
 *
 * A leitura LOCAL (arquivos `.lrc` numa árvore escolhida por `treeUri`) é **Android-only**:
 * depende de Storage Access Framework / DocumentFile, que não existem no iOS. Por isso,
 * aqui o `treeUri` é ignorado e, quando o online não acha nada, devolvemos lista vazia
 * (mínimo viável; sincronizar/baixar letras locais fica para um passo futuro de device).
 */
class IosLyricsRepository : LyricsRepository {

    private val api = LyricsApi()

    // No iOS não há fonte local que mude em runtime; o version fica constante (mínimo viável).
    private val _version = MutableStateFlow(0)
    override val version: StateFlow<Int> = _version.asStateFlow()

    override suspend fun load(track: Track, treeUri: String?): List<LyricLine> {
        // treeUri (SAF) ignorado no iOS — só busca online.
        val lrc = api.fetchSynced(
            title = track.title,
            artist = track.artist,
            album = track.album,
            durationSec = track.durationMs / 1000,
        ) ?: return emptyList()
        return LyricsApi.parse(lrc)
    }
}
