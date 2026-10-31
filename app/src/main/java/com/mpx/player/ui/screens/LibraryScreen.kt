package com.mpx.player.ui.screens

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mpx.player.model.Album
import com.mpx.player.model.Artist
import com.mpx.player.model.Track
import com.mpx.player.ui.LibFilter
import com.mpx.player.ui.LibraryUi
import com.mpx.player.ui.LibraryViewModel
import com.mpx.player.ui.OpenedPlaylist
import com.mpx.player.ui.PlayerUi
import com.mpx.player.ui.PlaylistUi
import com.mpx.player.ui.components.Cover
import com.mpx.player.ui.components.DashedDivider
import com.mpx.player.ui.components.MpxIcons
import com.mpx.player.ui.components.PlaylistCover
import com.mpx.player.ui.components.TextButton
import com.mpx.player.ui.components.Txt
import com.mpx.player.ui.components.formatTime
import com.mpx.player.ui.components.frame
import com.mpx.player.ui.components.tap
import com.mpx.player.ui.theme.Mpx

@Composable
fun LibraryScreen(ui: LibraryUi, player: PlayerUi, vm: LibraryViewModel, onOpenPlayer: () -> Unit) {
    val c = Mpx.colors
    var dialog by remember { mutableStateOf<LibDialog?>(null) }

    // Lança o diálogo do sistema para excluir o arquivo (Android 11+).
    val deleteLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) vm.onDeleteConfirmed() else vm.cancelDelete()
    }
    LaunchedEffect(Unit) {
        vm.deleteRequests.collect { deleteLauncher.launch(IntentSenderRequest.Builder(it).build()) }
    }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SearchRow(ui, vm)
        FilterRow(ui.filter, vm::setFilter)

        Box(Modifier.weight(1f)) {
            when {
                !ui.scanned -> Txt("> INDEXANDO /music ...", size = 12.sp, color = c.dim)
                ui.filter == LibFilter.PLAYLISTS && ui.opened != null ->
                    PlaylistDetail(
                        ui.opened, player.current?.id,
                        onBack = vm::closePlaylist,
                        onPlay = { vm.playPlaylist(ui.opened.id); onOpenPlayer() },
                        onPlayTrack = { list, i -> vm.playTrack(list, i); onOpenPlayer() },
                        onRemove = { key -> vm.removeFromPlaylist(ui.opened.id, key) },
                        onLong = { dialog = LibDialog.TrackMenu(it) },
                        onEditPhoto = { dialog = LibDialog.EditPlaylist(ui.opened.id, ui.opened.name, ui.opened.photo, ui.opened.locked) },
                    )
                ui.filter == LibFilter.PLAYLISTS ->
                    PlaylistGrid(
                        ui.playlists.filter { ui.query.isBlank() || it.name.contains(ui.query.trim(), true) }
                            .let { if (ui.ascending) it else it.reversed() },
                        onOpen = vm::openPlaylist,
                        onNew = { dialog = LibDialog.CreatePlaylist },
                    )
                ui.totalFiles == 0 -> Column {
                    Txt("> 0 FILES", size = 12.sp)
                    Txt("NENHUM ÁUDIO ENCONTRADO NO APARELHO.", size = 11.sp, color = c.dim, maxLines = 2)
                }
                ui.filter == LibFilter.ALBUNS -> AlbumGrid(ui.albums, player.current?.albumId) { vm.playAlbum(it); onOpenPlayer() }
                ui.filter == LibFilter.ARTISTAS -> ArtistList(ui.artists) { vm.playArtist(it); onOpenPlayer() }
                else -> TrackList(
                    ui.tracks, player.current?.id, ui.favoriteKeys,
                    onPlay = { i -> vm.playTrack(ui.tracks, i); onOpenPlayer() },
                    onLong = { dialog = LibDialog.TrackMenu(it) },
                )
            }
        }

        player.current?.let { t ->
            Row(
                Modifier.fillMaxWidth().height(44.dp).background(c.accent).tap(onClick = onOpenPlayer).padding(horizontal = 10.dp),
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

    LibraryDialogs(dialog, ui.playlists, vm, onDismiss = { dialog = null }, onEditTrack = { dialog = LibDialog.EditTrack(it) })
}

@Composable
private fun SearchRow(ui: LibraryUi, vm: LibraryViewModel) {
    val c = Mpx.colors
    Row(
        Modifier.fillMaxWidth().height(44.dp).frame(c.accent).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Txt("C:\\>", size = 13.sp)
        Box(Modifier.weight(1f)) {
            if (ui.query.isEmpty()) Txt("grep -i \"nome\"", size = 13.sp, color = c.dim)
            BasicTextField(
                value = ui.query,
                onValueChange = vm::setQuery,
                singleLine = true,
                textStyle = TextStyle(color = c.accent, fontSize = 13.sp, fontFamily = Mpx.style.mono),
                cursorBrush = SolidColor(c.accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (ui.query.isNotEmpty()) Txt("[X]", Modifier.tap { vm.setQuery("") }, size = 13.sp)
        Txt(if (ui.ascending) "A-Z" else "Z-A", Modifier.tap(onClick = vm::toggleSort), size = 12.sp, color = c.dim)
    }
}

@Composable
private fun FilterRow(current: LibFilter, onPick: (LibFilter) -> Unit) {
    val c = Mpx.colors
    Row(Modifier.fillMaxWidth().height(32.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        LibFilter.entries.forEach { f ->
            TextButton(
                f.label(), Modifier.weight(1f).height(32.dp), filled = current == f, size = 10.sp,
                border = if (current == f) c.accent else c.line,
            ) { onPick(f) }
        }
    }
}

private fun LibFilter.label() = when (this) {
    LibFilter.PLAYLISTS -> "PLAYLISTS"
    LibFilter.ALBUNS -> "ÁLBUNS"
    LibFilter.ARTISTAS -> "ARTISTAS"
    LibFilter.FAIXAS -> "FAIXAS"
}

// ---------------------------------------------------------------- PLAYLISTS

@Composable
private fun PlaylistGrid(playlists: List<PlaylistUi>, onOpen: (String) -> Unit, onNew: () -> Unit) {
    val c = Mpx.colors
    LazyVerticalGrid(
        columns = GridCells.Fixed(if (Mpx.landscape) 4 else 2),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 4.dp),
    ) {
        item {
            Column(
                Modifier.frame(c.line).tap(onClick = onNew).padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(Modifier.fillMaxWidth().height(112.dp).frame(c.line), contentAlignment = Alignment.Center) {
                    Image(MpxIcons.Plus, null, Modifier.size(40.dp), colorFilter = ColorFilter.tint(c.accent))
                }
                Txt("NOVA PLAYLIST", size = 20.sp, display = true, lineHeight = 20.sp)
                Txt("CRIAR", size = 10.sp, color = c.dim)
            }
        }
        items(playlists, key = { it.id }) { p ->
            Column(
                Modifier.frame(c.line).tap { onOpen(p.id) }.padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                PlaylistCover(p.photo, p.name, Modifier.fillMaxWidth().height(112.dp))
                Txt((if (p.locked) "♥ " else "") + p.name.uppercase(), size = 20.sp, display = true, lineHeight = 20.sp)
                Txt("${p.count} TRK", size = 10.sp, color = c.dim)
            }
        }
    }
}

@Composable
private fun PlaylistDetail(
    pl: OpenedPlaylist,
    playingId: Long?,
    onBack: () -> Unit,
    onPlay: () -> Unit,
    onPlayTrack: (List<Track>, Int) -> Unit,
    onRemove: (String) -> Unit,
    onLong: (Track) -> Unit,
    onEditPhoto: () -> Unit,
) {
    val c = Mpx.colors
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth().frame().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PlaylistCover(pl.photo, pl.name, Modifier.size(92.dp).frame(c.accent).tap(onClick = onEditPhoto))
            Column(Modifier.height(92.dp).weight(1f), verticalArrangement = Arrangement.SpaceBetween) {
                Txt((if (pl.locked) "♥ " else "") + pl.name.uppercase(), size = 26.sp, display = true, lineHeight = 26.sp, maxLines = 2)
                Txt("${pl.tracks.size} FAIXAS", size = 11.sp, color = c.dim)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton("< VOLTAR", Modifier.height(30.dp), size = 11.sp, onClick = onBack)
                    TextButton("▶ PLAY", Modifier.height(30.dp), size = 11.sp, filled = true, onClick = onPlay)
                }
            }
        }
        if (pl.tracks.isEmpty()) {
            Txt("> PLAYLIST VAZIA. USE O TOQUE LONGO EM FAIXAS PARA ADICIONAR.", size = 11.sp, color = c.dim, maxLines = 3)
        } else {
            LazyColumn(Modifier.weight(1f).fillMaxWidth().frame()) {
                itemsIndexed(pl.tracks, key = { _, t -> t.key }) { i, t ->
                    TrackRow(
                        t, t.id == playingId, favorite = false,
                        onPlay = { onPlayTrack(pl.tracks, i) },
                        onLong = { onLong(t) },
                        trailing = { Txt("[—]", Modifier.tap { onRemove(t.key) }.padding(start = 8.dp), size = 12.sp, color = c.dim) },
                    )
                    DashedDivider()
                }
            }
        }
    }
}

// ---------------------------------------------------------------- ÁLBUNS / ARTISTAS

@Composable
private fun AlbumGrid(albums: List<Album>, playingAlbum: Long?, onPlay: (Album) -> Unit) {
    val c = Mpx.colors
    LazyVerticalGrid(
        columns = GridCells.Fixed(if (Mpx.landscape) 4 else 2),
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

// ---------------------------------------------------------------- FAIXAS

@Composable
private fun TrackList(
    tracks: List<Track>,
    playingId: Long?,
    favoriteKeys: Set<String>,
    onPlay: (Int) -> Unit,
    onLong: (Track) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize().frame()) {
        itemsIndexed(tracks, key = { _, t -> t.id }) { i, t ->
            TrackRow(t, t.id == playingId, favorite = t.key in favoriteKeys, onPlay = { onPlay(i) }, onLong = { onLong(t) })
            DashedDivider()
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrackRow(
    t: Track,
    playing: Boolean,
    favorite: Boolean,
    onPlay: () -> Unit,
    onLong: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    val c = Mpx.colors
    val fg = if (playing) c.onAccent else c.accent
    Row(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(if (playing) c.accent else c.bg)
            .combinedClickable(role = Role.Button, onClick = onPlay, onLongClick = onLong)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Txt((if (playing) "▶ " else "") + t.title.uppercase(), size = 12.sp, color = fg)
            Txt("${t.artist.uppercase()} · ${t.album.uppercase()}", size = 10.sp, color = if (playing) c.onAccent else c.dim)
        }
        if (favorite) Image(MpxIcons.BookmarkOn, null, Modifier.size(12.dp).padding(start = 4.dp), colorFilter = ColorFilter.tint(fg))
        if (trailing != null) trailing()
        else Txt(formatTime(t.durationMs), Modifier.width(52.dp), size = 12.sp, color = fg, align = TextAlign.End)
    }
}
