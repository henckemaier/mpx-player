package com.mpx.player.data

import android.content.Context
import android.net.Uri
import com.mpx.player.model.TrackOverride
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

// OverrideLogic foi movido para :shared/commonMain (com.mpx.player.data) e é consumido abaixo.

/** Persiste os overrides de faixa (filesDir/overrides.json) e copia as capas personalizadas. */
class OverrideRepository(private val context: Context) {

    private val file: File get() = File(context.filesDir, "overrides.json")
    private val images = ImageStore(context)

    private val _overrides = MutableStateFlow(load())
    val overrides: StateFlow<Map<String, TrackOverride>> = _overrides.asStateFlow()

    private fun load(): Map<String, TrackOverride> =
        runCatching { if (file.exists()) OverrideLogic.fromJson(file.readText()) else emptyMap() }
            .getOrDefault(emptyMap())

    private fun update(transform: (Map<String, TrackOverride>) -> Map<String, TrackOverride>) {
        val next = transform(_overrides.value)
        _overrides.value = next
        file.writeAtomic(OverrideLogic.toJson(next))
    }

    fun setTitle(key: String, title: String?) = update { OverrideLogic.withTitle(it, key, title) }

    /** Copia a imagem escolhida e grava como capa personalizada da faixa. */
    suspend fun setCover(key: String, src: Uri) {
        val path = images.save(src, "covers") ?: return
        update { OverrideLogic.withCover(it, key, path) }
    }

    fun clearCover(key: String) = update { OverrideLogic.withCover(it, key, null) }

    /** Copia uma imagem para servir de foto de playlist; devolve o caminho salvo. */
    suspend fun savePlaylistPhoto(src: Uri): String? = images.save(src, "playlist_covers")
}
