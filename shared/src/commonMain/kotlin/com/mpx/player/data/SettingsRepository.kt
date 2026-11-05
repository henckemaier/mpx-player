package com.mpx.player.data

import com.mpx.player.model.EqSettings
import kotlinx.coroutines.flow.Flow

/**
 * Contrato comum de settings — por ora só a fatia que o `EqViewModel` usa
 * (equalizador). Cresce a cada ViewModel migrado para o `commonMain`.
 *
 * O Android implementa em `AndroidSettingsRepository` (DataStore); o iOS virá
 * num passo posterior.
 */
interface SettingsRepository {
    /** Configuração atual do equalizador, observável. */
    val eq: Flow<EqSettings>

    /** Árvore SAF (ou caminho) das letras locais; `null` se não configurada. */
    val lrcTreeUri: Flow<String?>

    /** Aplica uma transformação atômica sobre o [EqSettings] persistido. */
    suspend fun updateEq(transform: (EqSettings) -> EqSettings)
}
