package com.mpx.player.data

import kotlinx.coroutines.flow.StateFlow

/**
 * Leitura da biblioteca — fatia comum usada pelos ViewModels migrados (por ora só
 * o estado observável). A varredura/edição fica na impl Android (`AndroidLibraryRepository`).
 */
interface LibraryRepository {
    val state: StateFlow<LibraryState>
}
