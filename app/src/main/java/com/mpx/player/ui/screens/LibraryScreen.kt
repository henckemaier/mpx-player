package com.mpx.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mpx.player.model.Album
import com.mpx.player.model.Artist
import com.mpx.player.model.Track
import com.mpx.player.ui.LibFilter
import com.mpx.player.ui.LibraryUi
import com.mpx.player.ui.PlayerUi
import com.mpx.player.ui.components.Cover
import com.mpx.player.ui.components.DashedDivider
import com.mpx.player.ui.components.TextButton
import com.mpx.player.ui.components.Txt
import com.mpx.player.ui.components.formatTime
import com.mpx.player.ui.components.frame
import com.mpx.player.ui.components.tap
import com.mpx.player.ui.theme.Mpx

data class LibraryActions(
    val setQuery: (String) -> Unit,
    val setFilter: (LibFilter) -> Unit,
    val toggleSort: () -> Unit,
    val playAlbum: (Album) -> Unit,
    val playArtist: (Artist) -> Unit,
    val playTrack: (List<Track>, Int) -> Unit,
    val openPlayer: () -> Unit,
)

@Composable
fun LibraryScreen(ui: LibraryUi, player: PlayerUi, actions: LibraryActions) {
    val c = Mpx.colors
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // busca
        Row(
            Modifier.fillMaxWidth().height(44.dp).frame(c.accent).padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Txt("C:\\>", size = 13.sp)
            Box(Modifier.weight(1f)) {
                if (ui.query.isEmpty()) Txt("grep -i \"artista|álbum\"", size = 13.sp, color = c.dim)
                BasicTextField(
                    value = ui.query,
                    onValueChange = actions.setQuery,
                    singleLine = true,
                    textStyle = TextStyle(color = c.accent, fontSize = 13.sp, fontFamily = Mpx.style.mono),
                    cursorBrush = SolidColor(c.accent),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (ui.query.isNotEmpty()) Txt("[X]", Modifier.tap { actions.setQuery("") }, size = 13.sp)
        }

        // filtros
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            LibFilter.entries.forEach { f ->
                TextButton(
                    f.label(), Modifier.height(32.dp).padding(horizontal = 0.dp), filled = ui.filter == f,
                    border = if (ui.filter == f) c.accent else c.line,
                ) { actions.setFilter(f) }
            }
            Box(Modifier.weight(1f))
            Txt(if (ui.ascending) "SORT:A-Z" else "SORT:Z-A", Modifier.tap(onClick = actions.toggleSort).padding(vertical = 8.dp), size = 12.sp, color = c.dim)
        }

        Box(Modifier.weight(1f)) {
            when {
                !ui.scanned -> Txt("> INDEXANDO /music ...", size = 12.sp, color = c.dim)
                ui.totalFiles == 0 -> Column {
                    Txt("> 0 FILES", size = 12.sp)
                    Txt("NENHUM ÁUDIO ENCONTRADO NO APARELHO.", size = 11.sp, color = c.dim, maxLines = 2)
                }
                ui.filter == LibFilter.ALBUNS -> AlbumGrid(ui.albums, player.current?.albumId, actions.playAlbum)
                ui.filter == LibFilter.ARTISTAS -> ArtistList(ui.artists, actions.playArtist)
                else -> TrackList(ui.tracks, player.current?.id) { i -> actions.playTrack(ui.tracks, i) }
            }
        }

        // mini player
        player.current?.let { t ->
            Row(
                Modifier.fillMaxWidth().height(44.dp).background(c.accent).tap(onClick = actions.openPlayer).padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Txt(
                    "${if (player.isPlaying) "▶" else "‖"} ${t.title.uppercase()} — ${t.artist.uppercase()}",
                    Modifier.weight(1f), size = 12.sp, color = c.onAccent,
                )
                Txt(formatTime(player.positionMs), Modifier.padding(start = 8.dp), size = 12.sp, color = c.onAccent)
            }
        }
    }
}

private fun LibFilter.label() = when (this) {
    LibFilter.ALBUNS -> "ÁLBUNS"
    LibFilter.ARTISTAS -> "ARTISTAS"
    LibFilter.FAIXAS -> "FAIXAS"
}

@Composable
private fun AlbumGrid(albums: List<Album>, playingAlbum: Long?, onPlay: (Album) -> Unit) {
    val c = Mpx.colors
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 4.dp),
    ) {
        items(albums, key = { it.id }) { a ->
            val playing = a.id == playingAlbum
            Column(
                Modifier.frame(if (playing) c.accent else c.line).tap { onPlay(a) }.padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box {
                    Cover(a.tracks.firstOrNull(), Modifier.fillMaxWidth().height(112.dp))
                    if (playing) Txt("▶ PLAY", Modifier.padding(4.dp).background(c.accent).padding(horizontal = 4.dp), size = 10.sp, color = c.onAccent)
                }
                Txt(a.title.uppercase(), size = 20.sp, display = true, lineHeight = 20.sp)
                Txt("${a.artist.uppercase()} · ${a.tracks.size} TRK", size = 10.sp, color = c.dim)
            }
        }
    }
}

@Composable
private fun ArtistList(artists: List<Artist>, onPlay: (Artist) -> Unit) {
    val c = Mpx.colors
    LazyColumn(Modifier.fillMaxSize().frame()) {
        items(artists, key = { it.name }) { a ->
            Row(
                Modifier.fillMaxWidth().height(52.dp).tap { onPlay(a) }.padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Txt(a.name.uppercase(), size = 20.sp, display = true)
                    Txt("${a.albumCount} ALB · ${a.tracks.size} TRK", size = 10.sp, color = c.dim)
                }
                Txt("▶", size = 14.sp)
            }
            DashedDivider()
        }
    }
}

@Composable
private fun TrackList(tracks: List<Track>, playingId: Long?, onPlay: (Int) -> Unit) {
    val c = Mpx.colors
    LazyColumn(Modifier.fillMaxSize().frame()) {
        itemsIndexed(tracks, key = { _, t -> t.id }) { i, t ->
            val on = t.id == playingId
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .background(if (on) c.accent else c.bg)
                    .tap { onPlay(i) }
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val fg = if (on) c.onAccent else c.accent
                Column(Modifier.weight(1f)) {
                    Txt((if (on) "▶ " else "") + t.title.uppercase(), size = 12.sp, color = fg)
                    Txt("${t.artist.uppercase()} · ${t.album.uppercase()}", size = 10.sp, color = if (on) c.onAccent else c.dim)
                }
                Txt(formatTime(t.durationMs), Modifier.width(52.dp), size = 12.sp, color = fg, align = androidx.compose.ui.text.style.TextAlign.End)
            }
            DashedDivider()
        }
    }
}
