package com.mpx.player.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mpx.player.di.AppContainer
import com.mpx.player.model.UserSettings
import com.mpx.player.ui.theme.CUSTOM_THEME
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Estado da tela CONFIG. Os rótulos pré-formatados [lastScanLabel]/[appVersion] são injetados pelo
 * host (decisão B do Maestro): formatar data (SimpleDateFormat) e versão (BuildConfig) é Android-only,
 * então o `SettingsViewModel` comum os deixa no default e o `AndroidSettingsViewModel` os preenche —
 * ZERO `java`/`BuildConfig` no comum.
 */
data class SettingsUi(
    val settings: UserSettings = UserSettings(),
    val files: Int = 0,
    val scanning: Boolean = false,
    val newFiles: Int = 0,
    val lrcCount: Int = 0,
    val message: String? = null,
    val lastScanLabel: String = "NUNCA",
    val appVersion: String = "",
)

/** Progresso da busca de letras faltantes no LRCLIB (consumido pelo slot de importação do :app). */
data class LyricsSyncUi(
    val running: Boolean = false,
    val done: Int = 0,
    val total: Int = 0,
    val found: Int = 0,
    val result: String? = null,
)

/**
 * ViewModel de CONFIG — migrado para `commonMain` na fatia SETTINGS (P7). Molde do
 * [LibraryViewModel]/[EqViewModel]: `(app: AppContainer) : ViewModel()` + `viewModelScope` +
 * `.state(initial)` sobre `stateIn(WhileSubscribed)`. Cobre o que é portável: aparência, cores,
 * fonte, capa, toggles de áudio e o gatilho neutro de (re)varredura.
 *
 * `open` + `protected val app` + `protected message`/`update`/`state`: a subclasse Android
 * ([com.mpx.player.ui.AndroidLibraryViewModel]'s sibling `AndroidSettingsViewModel`) herda este
 * browse e ADICIONA só a superfície Android-only (SAF/Uri, downloader, auto-update, lyrics-sync,
 * contagem de `.lrc` e os rótulos formatados). NADA de Uri/File/context/BuildConfig aqui.
 */
open class SettingsViewModel(protected val app: AppContainer) : ViewModel() {

    protected val message = MutableStateFlow<String?>(null)

    open val ui: StateFlow<SettingsUi> = combine(app.settings.settings, app.library.state, message) { s, lib, msg ->
        SettingsUi(
            settings = s,
            files = lib.tracks.size,
            scanning = lib.scanning,
            newFiles = lib.newSinceLastScan,
            lrcCount = 0,
            message = msg,
        )
    }.state(SettingsUi())

    // ---- aparência / cores / fonte / capa — via update{} atômico (lê o estado corrente em `it`)
    fun setTheme(i: Int) = update { it.copy(theme = i) }
    fun setCustomColor(argb: Int) = update { it.copy(theme = CUSTOM_THEME, customColor = argb) }
    fun setOled(v: Boolean) = update { it.copy(oled = v) }
    fun setFont(i: Int) = update { it.copy(font = i) }
    fun setCover(i: Int) = update { it.copy(cover = i) }
    fun toggleScanlines() = update { it.copy(scanlines = !it.scanlines) }
    fun toggleGlow() = update { it.copy(glow = !it.glow) }
    fun toggleCursor() = update { it.copy(cursor = !it.cursor) }
    fun toggleMono() = update { it.copy(monoCovers = !it.monoCovers) }
    fun toggleCrossfade() = update { it.copy(crossfade = !it.crossfade) }

    fun clearMessage() { message.value = null }

    /**
     * Gatilho neutro de (re)varredura. No comum só dispara o repositório (fire-and-forget); o
     * carimbo de `lastScan` + invalidar letras fica no override Android (que tem o relógio do JVM).
     */
    open fun rescan() { app.library.rescan() }

    /** Transformação atômica do [UserSettings] persistido no `viewModelScope`. */
    protected fun update(transform: (UserSettings) -> UserSettings) {
        viewModelScope.launch { app.settings.update(transform) }
    }

    /** Molde EqViewModel/PlayerViewModel: stateIn sobre o viewModelScope multiplataforma. */
    protected fun <T> Flow<T>.state(initial: T): StateFlow<T> =
        stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)
}
