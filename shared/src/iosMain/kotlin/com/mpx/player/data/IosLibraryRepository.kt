package com.mpx.player.data

import com.mpx.player.model.Track
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import platform.Foundation.NSNumber
import platform.Foundation.NSURL
import platform.MediaPlayer.MPMediaItem
import platform.MediaPlayer.MPMediaItemPropertyAlbumPersistentID
import platform.MediaPlayer.MPMediaItemPropertyAlbumTitle
import platform.MediaPlayer.MPMediaItemPropertyAlbumTrackNumber
import platform.MediaPlayer.MPMediaItemPropertyArtist
import platform.MediaPlayer.MPMediaItemPropertyAssetURL
import platform.MediaPlayer.MPMediaItemPropertyPersistentID
import platform.MediaPlayer.MPMediaItemPropertyPlaybackDuration
import platform.MediaPlayer.MPMediaItemPropertyTitle
import platform.MediaPlayer.MPMediaLibrary
import platform.MediaPlayer.MPMediaLibraryAuthorizationStatusAuthorized
import platform.MediaPlayer.MPMediaQuery

/**
 * Leitura da biblioteca no iOS via **MPMediaQuery** (Media Player framework), atrás do
 * contrato comum [LibraryRepository] — espelho do `AndroidLibraryRepository` (MediaStore).
 *
 * O glue de cinterop (ler os campos de cada [MPMediaItem]) fica confinado aqui; o
 * mapeamento campo→[Track] é a função PURA [trackFromMediaFields] (testada em iosTest
 * sem AVFoundation). O agrupamento em álbuns/artistas reusa [buildAlbumsAndArtists]
 * do commonMain (idêntico ao Android — não reimplementa).
 *
 * **Permissão/vazio (gracioso, sem crash):** [rescan] pede autorização; se negada/
 * restrita, publica um [LibraryState] vazio com `scanned=true`. No **simulador** não há
 * biblioteca de música → a query volta vazia e o prompt pode nem aparecer: isso é
 * ESPERADO (o CI valida só compilação/link + o teste do mapa puro). Carregar a
 * biblioteca real + o prompt de permissão exige iPhone físico (TODO device).
 *
 * Threading: a query roda fora da main (escopo próprio em [Dispatchers.Default]); o
 * resultado é publicado via [StateFlow] (thread-safe).
 */
@OptIn(ExperimentalForeignApi::class)
class IosLibraryRepository : LibraryRepository {

    private val _state = MutableStateFlow(LibraryState())
    override val state: StateFlow<LibraryState> = _state.asStateFlow()

    // Fire-and-forget: escopo próprio da varredura (MPMediaQuery não precisa da main).
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun rescan() {
        _state.update { it.copy(scanning = true) }
        scope.launch { load() }
    }

    private fun load() {
        // NOTA cinterop: o nome K/N do seletor ObjC `requestAuthorization:` é
        // `requestAuthorization` (método de classe → companion). Se o CI acusar
        // "unresolved reference", a variante é `requestAuthorizationWithHandler` — flip
        // de 1 linha. O handler roda numa fila arbitrária; publicamos via StateFlow.
        MPMediaLibrary.requestAuthorization { status ->
            if (status == MPMediaLibraryAuthorizationStatusAuthorized) {
                val items = MPMediaQuery.songsQuery().items.orEmpty()
                val tracks = items.mapNotNull { el ->
                    (el as? MPMediaItem)?.let { trackFromMediaItem(it) }
                }
                publish(tracks)
            } else {
                // Negado/restrito: estado vazio e marcado como varrido (sem crash).
                _state.value = LibraryState(scanned = true)
            }
        }
    }

    private fun publish(tracks: List<Track>) {
        val (albums, artists) = buildAlbumsAndArtists(tracks)
        _state.value = LibraryState(
            tracks = tracks,
            albums = albums,
            artists = artists,
            scanning = false,
            scanned = true,
            newSinceLastScan = 0,
        )
    }

    /** Glue de cinterop: extrai os campos crus do item e delega ao mapa puro. */
    private fun trackFromMediaItem(item: MPMediaItem): Track? {
        val persistentId = (item.valueForProperty(MPMediaItemPropertyPersistentID) as? NSNumber)
            ?.unsignedLongLongValue?.toLong() ?: 0L
        val assetUrl = (item.valueForProperty(MPMediaItemPropertyAssetURL) as? NSURL)?.absoluteString
        val title = item.valueForProperty(MPMediaItemPropertyTitle) as? String
        val artist = item.valueForProperty(MPMediaItemPropertyArtist) as? String
        val album = item.valueForProperty(MPMediaItemPropertyAlbumTitle) as? String
        val albumPersistentId = (item.valueForProperty(MPMediaItemPropertyAlbumPersistentID) as? NSNumber)
            ?.unsignedLongLongValue?.toLong() ?: 0L
        val durationSec = (item.valueForProperty(MPMediaItemPropertyPlaybackDuration) as? NSNumber)
            ?.doubleValue ?: 0.0
        val trackNumber = (item.valueForProperty(MPMediaItemPropertyAlbumTrackNumber) as? NSNumber)
            ?.intValue ?: 0
        return trackFromMediaFields(
            persistentId = persistentId,
            assetUrl = assetUrl,
            title = title,
            artist = artist,
            album = album,
            albumPersistentId = albumPersistentId,
            durationSec = durationSec,
            trackNumber = trackNumber,
        )
    }
}

/**
 * Mapa PURO de campos de um `MPMediaItem` → [Track] (sem cinterop; testável em iosTest).
 *
 * - `assetUrl == null` → **null** (item de nuvem/DRM sem arquivo local: não é tocável,
 *   PULA — como o `mapNotNull` do chamador).
 * - Normalização igual ao Android: artista/álbum nulos/em branco/`<unknown>` →
 *   "DESCONHECIDO"; título ausente cai no nome do arquivo; `mimeType` inferido da
 *   extensão (ou "").
 * - `durationSec` (Double) → `durationMs` (Long) por *1000.
 * - `uri` recebe o `assetUrl` (referência neutra do [Track], interpretada por plataforma).
 */
internal fun trackFromMediaFields(
    persistentId: Long,
    assetUrl: String?,
    title: String?,
    artist: String?,
    album: String?,
    albumPersistentId: Long,
    durationSec: Double,
    trackNumber: Int,
): Track? {
    if (assetUrl == null) return null
    val name = lastPathComponentOf(assetUrl) ?: title?.takeIf { it.isNotBlank() } ?: ""
    return Track(
        id = persistentId,
        uri = assetUrl,
        title = title?.takeIf { it.isNotBlank() } ?: name,
        artist = artist.orUnknown(),
        album = album.orUnknown(),
        albumId = albumPersistentId,
        durationMs = (durationSec * 1000.0).toLong(),
        displayName = name,
        relativePath = "",
        mimeType = mimeFromExtension(name),
        bitrate = null,
        trackNumber = trackNumber,
        year = null,
    )
}

/** Mesma regra do Android: nulo/em branco/`<unknown>` vira "DESCONHECIDO". */
private fun String?.orUnknown(): String =
    if (this.isNullOrBlank() || this == "<unknown>") "DESCONHECIDO" else this

/** Último componente de caminho de uma URL-string (query removida), ou null se vazio. */
internal fun lastPathComponentOf(url: String): String? =
    url.substringBefore('?').substringAfterLast('/').takeIf { it.isNotBlank() }

/** mimeType inferido da extensão do arquivo; "" quando desconhecida. */
internal fun mimeFromExtension(name: String): String =
    when (name.substringAfterLast('.', "").lowercase()) {
        "mp3" -> "audio/mpeg"
        "flac" -> "audio/flac"
        "m4a", "aac", "mp4" -> "audio/mp4"
        "ogg" -> "audio/ogg"
        "opus" -> "audio/opus"
        "wav" -> "audio/wav"
        else -> ""
    }
