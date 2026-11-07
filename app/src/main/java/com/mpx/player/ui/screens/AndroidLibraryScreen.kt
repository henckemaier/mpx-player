package com.mpx.player.ui.screens

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.mpx.player.ui.AndroidLibraryViewModel
import com.mpx.player.ui.LibraryUi
import com.mpx.player.ui.PlayerUi

/**
 * Wrapper Android da [LibraryScreen] comum (fatia LIBRARY, P7). Concentra o que NÃO é portável: o
 * estado do diálogo ([LibDialog]), o launcher de exclusão (IntentSender) e os [LibraryDialogs] que
 * tocam Uri/File. Renderiza a tela comum passando uma [LibraryActions] que cabeia os browse-actions
 * ao [AndroidLibraryViewModel] e os 3 gatilhos de diálogo ao estado local.
 */
@Composable
fun AndroidLibraryScreen(ui: LibraryUi, player: PlayerUi, vm: AndroidLibraryViewModel, onOpenPlayer: () -> Unit) {
    var dialog by remember { mutableStateOf<LibDialog?>(null) }

    // Lança o diálogo do sistema para excluir o arquivo (Android 11+).
    val deleteLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) vm.onDeleteConfirmed() else vm.cancelDelete()
    }
    LaunchedEffect(Unit) {
        vm.deleteRequests.collect { deleteLauncher.launch(IntentSenderRequest.Builder(it).build()) }
    }

    val actions = LibraryActions(
        onFilter = vm::setFilter,
        onQuery = vm::setQuery,
        onToggleSort = vm::toggleSort,
        onPlayAlbum = vm::playAlbum,
        onPlayArtist = vm::playArtist,
        onPlayTrack = vm::playTrack,
        onOpenPlaylist = vm::openPlaylist,
        onClosePlaylist = vm::closePlaylist,
        onPlayPlaylist = vm::playPlaylist,
        onRemoveFromPlaylist = vm::removeFromPlaylist,
        onOpenPlayer = onOpenPlayer,
        onTrackLong = { dialog = LibDialog.TrackMenu(it) },
        onNewPlaylist = { dialog = LibDialog.CreatePlaylist },
        onEditPlaylist = { op -> dialog = LibDialog.EditPlaylist(op.id, op.name, op.photo, op.locked) },
    )

    LibraryScreen(ui, player, actions)
    LibraryDialogs(dialog, ui.playlists, vm, onDismiss = { dialog = null }, onEditTrack = { dialog = LibDialog.EditTrack(it) })
}
