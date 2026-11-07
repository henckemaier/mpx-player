package com.mpx.player.data

import kotlinx.coroutines.flow.StateFlow

/**
 * Leitura da biblioteca — fatia comum usada pelos ViewModels migrados. Expõe o estado
 * observável e um gatilho neutro de (re)varredura. A varredura em si fica na impl de
 * plataforma (`AndroidLibraryRepository` via MediaStore; iOS via Media Library na 7.3).
 */
interface LibraryRepository {
    val state: StateFlow<LibraryState>

    /**
     * Dispara uma (re)varredura da biblioteca. Fire-and-forget: a implementação lança
     * no próprio escopo e publica o resultado em [state] — nenhum detalhe de plataforma
     * (permissões, MediaStore/Media Library) vaza pelo contrato comum.
     */
    fun rescan()
}
