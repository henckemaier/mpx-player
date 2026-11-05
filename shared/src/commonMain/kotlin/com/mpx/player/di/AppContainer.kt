package com.mpx.player.di

import com.mpx.player.data.SettingsRepository

/**
 * Contêiner de dependências comum aos ViewModels migrados para o `commonMain`.
 *
 * Mínimo por ora (só [settings], usado pelo `EqViewModel`); cresce conforme os
 * próximos ViewModels forem migrados. O Android fornece a impl concreta
 * (`AndroidAppContainer`); o iOS virá num passo posterior.
 */
interface AppContainer {
    val settings: SettingsRepository
}
