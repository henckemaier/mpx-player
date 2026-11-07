package com.mpx.player.data

import com.mpx.player.model.EqSettings
import com.mpx.player.model.UserSettings
import kotlinx.coroutines.flow.Flow

/**
 * Contrato comum de settings — cresce a cada ViewModel migrado para o `commonMain`.
 * Na fatia SETTINGS (P7) passou a expor o [UserSettings] inteiro ([settings]) e uma
 * transformação atômica ([update]), usados pelo `SettingsViewModel` comum. A fatia do
 * equalizador ([eq]/[updateEq]) segue intacta.
 *
 * Android implementa em `AndroidSettingsRepository` (DataStore); iOS em
 * `IosSettingsRepository` (NSUserDefaults + [UserSettingsJson]).
 */
interface SettingsRepository {
    /** Configuração do usuário inteira, observável (aparência/cores/áudio/eq/…). */
    val settings: Flow<UserSettings>

    /** Configuração atual do equalizador, observável (fatia derivada de [settings]). */
    val eq: Flow<EqSettings>

    /** Árvore SAF (ou caminho) das letras locais; `null` se não configurada. */
    val lrcTreeUri: Flow<String?>

    /** Aplica uma transformação atômica sobre o [UserSettings] persistido. */
    suspend fun update(transform: (UserSettings) -> UserSettings)

    /** Aplica uma transformação atômica sobre o [EqSettings] persistido. */
    suspend fun updateEq(transform: (EqSettings) -> EqSettings)
}
