package com.mpx.player.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mpx.player.di.AppContainer
import com.mpx.player.model.EqPresets
import com.mpx.player.model.EqSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel do equalizador — primeiro VM migrado para o `commonMain` (passo 6).
 * Depende só de [AppContainer.settings] (eq/updateEq); lógica pura reusa [EqPresets].
 */
class EqViewModel(private val app: AppContainer) : ViewModel() {
    val eq: StateFlow<EqSettings> = app.settings.eq.state(EqSettings())
    val selectedBand = MutableStateFlow(2)

    private fun edit(t: (EqSettings) -> EqSettings) {
        viewModelScope.launch { app.settings.updateEq(t) }
    }

    fun toggleOn() = edit { it.copy(enabled = !it.enabled) }
    fun pickPreset(name: String) = edit { it.copy(preset = name, levels = EqPresets.levelsFor(name, it.custom)) }
    fun setBand(band: Int, level: Int) {
        selectedBand.value = band
        edit { e -> e.copy(preset = "CUSTOM", levels = e.levels.toMutableList().also { it[band] = level.coerceIn(0, 12) }) }
    }
    fun preamp(delta: Int) = edit { it.copy(preamp = (it.preamp + delta).coerceIn(0, 24)) }
    fun toggleBass() = edit { it.copy(bassBoost = !it.bassBoost) }
    fun toggleSurround() = edit { it.copy(surround = !it.surround) }
    fun toggleLoudness() = edit { it.copy(loudness = !it.loudness) }
    fun reset() = edit { it.copy(preset = "FLAT", levels = EqPresets.FLAT, preamp = 12) }
    fun saveCustom() = edit { it.copy(custom = it.levels, preset = "CUSTOM") }

    /** Mesmo padrão do `.state()` do :app, mas sobre o viewModelScope multiplataforma. */
    private fun <T> Flow<T>.state(initial: T): StateFlow<T> =
        stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)
}
