package com.mpx.player.ui

import android.app.RecoverableSecurityException
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.lifecycle.viewModelScope
import com.mpx.player.AndroidAppContainer
import com.mpx.player.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * Superfície Android-only da biblioteca — herda o BROWSE comum ([LibraryViewModel]) e adiciona o
 * que toca `Uri`/overrides/IntentSender/arquivo (edição de playlist/faixa, exclusão de arquivo).
 * Fatia LIBRARY (P7): o `LibraryViewModel` virou comum (`:shared`); esta subclasse concentra o
 * resto que NÃO é portável. A factory `mpxViewModel` constrói esta classe.
 */
class AndroidLibraryViewModel(
    private val container: AndroidAppContainer,
) : LibraryViewModel(container) {

    // ---- playlists (edição — Uri/overrides)
    fun createPlaylist(name: String, photo: Uri?) = viewModelScope.launch {
        container.playlists.create(name, photo?.let { container.overrides.savePlaylistPhoto(it) })
    }
    fun renamePlaylist(id: String, name: String) = container.playlists.rename(id, name)
    fun deletePlaylist(id: String) {
        container.playlists.delete(id)
        if (ui.value.opened?.id == id) closePlaylist()
    }
    fun setPlaylistPhoto(id: String, photo: Uri) = viewModelScope.launch {
        container.playlists.setPhoto(id, container.overrides.savePlaylistPhoto(photo))
    }

    // ---- editar faixa (overrides só do MPX)
    fun editTitle(key: String, title: String?) = container.overrides.setTitle(key, title)
    fun editCover(key: String, photo: Uri) = viewModelScope.launch { container.overrides.setCover(key, photo) }
    fun clearCover(key: String) = container.overrides.clearCover(key)
    fun editLyrics(track: Track, text: String) = viewModelScope.launch {
        container.lyrics.save(track, text)
        container.lyrics.invalidate()
    }

    // ---- excluir faixa (apaga o arquivo; UI lança o IntentSender)
    private val _deleteRequests = MutableSharedFlow<IntentSender>(extraBufferCapacity = 1)
    val deleteRequests: SharedFlow<IntentSender> = _deleteRequests.asSharedFlow()
    private var pendingDeleteKey: String? = null

    fun requestDeleteTrack(track: Track) {
        pendingDeleteKey = track.key
        viewModelScope.launch(Dispatchers.IO) {
            val resolver = container.context.contentResolver
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                runCatching { MediaStore.createDeleteRequest(resolver, listOf(Uri.parse(track.uri))).intentSender }
                    .onSuccess { _deleteRequests.emit(it) }
                    .onFailure { cancelDelete() }
            } else {
                try {
                    resolver.delete(Uri.parse(track.uri), null, null)
                    onDeleteConfirmed()
                } catch (e: RecoverableSecurityException) {
                    _deleteRequests.emit(e.userAction.actionIntent.intentSender)
                } catch (_: Exception) {
                    cancelDelete()
                }
            }
        }
    }

    fun onDeleteConfirmed() {
        val key = pendingDeleteKey ?: return
        pendingDeleteKey = null
        container.playlists.purgeKey(key)
        container.library.rescan()
    }

    fun cancelDelete() { pendingDeleteKey = null }
}
