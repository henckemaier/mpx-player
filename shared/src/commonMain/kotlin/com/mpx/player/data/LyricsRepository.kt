package com.mpx.player.data

import com.mpx.player.model.LyricLine
import com.mpx.player.model.Track
import kotlinx.coroutines.flow.StateFlow

/**
 * Letras — fatia comum usada pelos ViewModels migrados. A busca online já vive em
 * `:shared` (LyricsApi); a leitura de arquivos locais/SAF fica na impl Android.
 */
interface LyricsRepository {
    /** Muda quando a fonte de letras muda, para reavaliar o [load]. */
    val version: StateFlow<Int>

    /** Carrega as linhas de letra da [track] (opcionalmente numa árvore SAF). */
    suspend fun load(track: Track, treeUri: String?): List<LyricLine>
}
