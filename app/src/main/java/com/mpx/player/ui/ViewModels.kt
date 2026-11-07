package com.mpx.player.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mpx.player.AndroidAppContainer
import com.mpx.player.MpxApp

/**
 * Factory dos ViewModels do :app: injeta o [AndroidAppContainer] (via `APPLICATION_KEY`) no
 * construtor do VM. É o único ponto Android-only que sobra aqui — todos os ViewModels em si
 * (Player/Library/Eq/Settings) migraram para `:shared/commonMain` (mesmo FQN) ou têm subclasse
 * Android (`AndroidLibraryViewModel`/`AndroidSettingsViewModel`).
 */
@Composable
inline fun <reified VM : ViewModel> mpxViewModel(crossinline create: (AndroidAppContainer) -> VM): VM =
    viewModel(factory = viewModelFactory { initializer { create((this[APPLICATION_KEY] as MpxApp).container) } })

// ============================================================ PLAYER (Now Playing + Fila)

// PlayerUi foi movido para :shared/commonMain (com.mpx.player.ui.PlayerUi) na fatia fina de UI
// (P7). Na fatia "estado real" (P7), PlayerViewModel + TechInfo + a interface TechInfoReader
// também foram para :shared/commonMain (com.mpx.player.ui) — mesmo pacote/FQN, resolvem no :app
// sem import. O único trecho Android-only (ler metadados técnicos via MediaMetadataRetriever)
// virou AndroidTechInfoReader (com.mpx.player.data), injetado no VM pela factory (MainActivity).

// ============================================================ BIBLIOTECA

// LibFilter/PlaylistUi/OpenedPlaylist/LibraryUi + LibraryViewModel foram para :shared/commonMain
// na fatia LIBRARY (P7): tipos em com.mpx.player.ui (LibraryUi.kt), projeção pura em
// projectLibrary (LibraryProjection.kt) e o VM de BROWSE em com.mpx.player.ui.LibraryViewModel
// (mesmo FQN). A superfície Android-only (edição de playlist/faixa + exclusão de arquivo via
// Uri/overrides/IntentSender) virou a subclasse AndroidLibraryViewModel (com.mpx.player.ui),
// construída pela factory mpxViewModel.

// ============================================================ EQ

// EqViewModel foi movido para :shared/commonMain (com.mpx.player.ui.EqViewModel) — passo 6, fatia 1.

// ============================================================ CONFIG

// SettingsUi/LyricsSyncUi + SettingsViewModel foram para :shared/commonMain na fatia SETTINGS (P7):
// tipos + VM comum em com.mpx.player.ui (SettingsViewModel.kt) e a tela stateless em
// com.mpx.player.ui.screens.SettingsScreen (mesmo FQN). A superfície Android-only (SAF/import,
// downloader, auto-update, lyrics-sync, contagem de .lrc e rótulos data/versão formatados) virou a
// subclasse AndroidSettingsViewModel (com.mpx.player.ui) + o wrapper AndroidSettingsScreen, ambos
// construídos/consumidos pelo :app.
