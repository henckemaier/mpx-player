package com.mpx.player.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.mpx.player.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ImportedPlaylist(val name: String, val tracks: List<Track>, val missing: Int)

/** Lê playlists .m3u/.m3u8/.pls e casa cada entrada com uma faixa da biblioteca pelo nome do arquivo. */
class PlaylistImporter(private val context: Context) {

    suspend fun import(uri: Uri, library: List<Track>): ImportedPlaylist = withContext(Dispatchers.IO) {
        val name = displayName(uri) ?: "playlist.m3u"
        val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
        val entries = text.lineSequence()
            .map { it.trim() }
            .mapNotNull { line ->
                when {
                    line.isEmpty() || line.startsWith("#") || line.startsWith("[") -> null
                    line.startsWith("File", ignoreCase = true) && line.contains('=') -> line.substringAfter('=')
                    line.contains('=') -> null
                    else -> line
                }
            }
            .map { Uri.decode(it).replace('\\', '/').substringAfterLast('/').lowercase() }
            .toList()
        val byName = library.associateBy { it.displayName.lowercase() }
        val tracks = entries.mapNotNull { byName[it] }
        ImportedPlaylist("/" + name.lowercase(), tracks, entries.size - tracks.size)
    }

    private fun displayName(uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
}
