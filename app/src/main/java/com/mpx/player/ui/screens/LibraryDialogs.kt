package com.mpx.player.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import android.net.Uri
import com.mpx.player.model.Track
import com.mpx.player.ui.LibraryViewModel
import com.mpx.player.ui.PlaylistUi
import com.mpx.player.ui.components.DashedDivider
import com.mpx.player.ui.components.MpxIcons
import com.mpx.player.ui.components.TextButton
import com.mpx.player.ui.components.Txt
import com.mpx.player.ui.components.frame
import com.mpx.player.ui.components.tap
import com.mpx.player.ui.theme.Mpx
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

sealed interface LibDialog {
    data class TrackMenu(val track: Track) : LibDialog
    data class EditTrack(val track: Track) : LibDialog
    data object CreatePlaylist : LibDialog
    data class EditPlaylist(val id: String, val name: String, val photo: String?, val locked: Boolean) : LibDialog
}

@Composable
fun LibraryDialogs(
    dialog: LibDialog?,
    playlists: List<PlaylistUi>,
    vm: LibraryViewModel,
    onDismiss: () -> Unit,
    onEditTrack: (Track) -> Unit,
) {
    when (val d = dialog) {
        null -> Unit
        is LibDialog.TrackMenu -> TrackMenuDialog(
            d.track, playlists,
            onAdd = { id -> vm.addToPlaylist(id, d.track.key); onDismiss() },
            onEdit = { onEditTrack(d.track) },
            onDelete = { vm.requestDeleteTrack(d.track); onDismiss() },
            onDismiss = onDismiss,
        )
        is LibDialog.EditTrack -> TrackEditorDialog(d.track, vm, onDismiss)
        LibDialog.CreatePlaylist -> PlaylistEditorDialog(
            title = "NOVA PLAYLIST", initialName = "", locked = false,
            onSave = { name, uri -> vm.createPlaylist(name, uri); onDismiss() },
            onDelete = null, onDismiss = onDismiss,
        )
        is LibDialog.EditPlaylist -> PlaylistEditorDialog(
            title = "EDITAR PLAYLIST", initialName = d.name, locked = d.locked,
            onSave = { name, uri ->
                if (!d.locked) vm.renamePlaylist(d.id, name)
                uri?.let { vm.setPlaylistPhoto(d.id, it) }
                onDismiss()
            },
            onDelete = if (d.locked) null else ({ vm.deletePlaylist(d.id); onDismiss() }),
            onDismiss = onDismiss,
        )
    }
}

@Composable
private fun RetroDialog(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val c = Mpx.colors
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = true)) {
        Column(
            Modifier.fillMaxWidth().background(c.bg).frame(c.accent).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) { content() }
    }
}

@Composable
private fun TrackMenuDialog(
    track: Track,
    playlists: List<PlaylistUi>,
    onAdd: (String) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = Mpx.colors
    var showPlaylists by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    RetroDialog(onDismiss) {
        Txt("> " + track.title.uppercase(), size = 12.sp, maxLines = 2)
        DashedDivider()
        if (!showPlaylists) {
            MenuItem("ADICIONAR A PLAYLIST") { showPlaylists = true }
            MenuItem("EDITAR") { onEdit() }
            MenuItem(if (confirmDelete) "CONFIRMAR EXCLUSÃO?" else "EXCLUIR", danger = confirmDelete) {
                if (confirmDelete) onDelete() else confirmDelete = true
            }
        } else {
            Txt("ESCOLHA A PLAYLIST:", size = 11.sp, color = c.dim)
            Column(Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState())) {
                playlists.forEach { p ->
                    MenuItem((if (p.locked) "♥ " else "") + p.name.uppercase() + "  (${p.count})") { onAdd(p.id) }
                }
            }
            MenuItem("< VOLTAR") { showPlaylists = false }
        }
    }
}

@Composable
private fun MenuItem(label: String, danger: Boolean = false, onClick: () -> Unit) {
    val c = Mpx.colors
    Txt(
        "  $label",
        Modifier.fillMaxWidth().height(40.dp).tap(onClick = onClick).padding(vertical = 11.dp),
        size = 13.sp,
        color = if (danger) c.bright else c.accent,
    )
}

@Composable
private fun TrackEditorDialog(track: Track, vm: LibraryViewModel, onDismiss: () -> Unit) {
    val c = Mpx.colors
    val ctx = LocalContext.current
    var title by remember { mutableStateOf(track.title) }
    var pickedCover by remember { mutableStateOf<Uri?>(null) }
    var clearedCover by remember { mutableStateOf(false) }

    val initialLyrics by produceState("", track.key) {
        value = withContext(Dispatchers.IO) {
            val f = File(ctx.filesDir, "lyrics/" + track.displayName.substringBeforeLast('.').lowercase() + ".lrc")
            if (f.isFile) runCatching { f.readText() }.getOrDefault("") else ""
        }
    }
    var lyrics by remember(initialLyrics) { mutableStateOf(initialLyrics) }

    val pickCover = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) {
        if (it != null) { pickedCover = it; clearedCover = false }
    }

    RetroDialog(onDismiss) {
        Txt("> EDITAR FAIXA", size = 12.sp, color = c.dim)
        Field("NOME", title) { title = it }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Txt("FOTO", Modifier.width(60.dp), size = 11.sp, color = c.dim)
            TextButton(
                if (pickedCover != null) "SELECIONADA" else "ESCOLHER", Modifier.weight(1f).height(36.dp), size = 11.sp,
            ) { pickCover.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
            TextButton("REMOVER", Modifier.height(36.dp), size = 11.sp) { pickedCover = null; clearedCover = true }
        }
        Txt("LETRA (.LRC)", size = 11.sp, color = c.dim)
        Box(Modifier.fillMaxWidth().heightIn(min = 96.dp, max = 180.dp).frame(c.line).padding(8.dp)) {
            if (lyrics.isEmpty()) Txt("[00:00.00] linha...", size = 11.sp, color = c.dim)
            BasicTextField(
                value = lyrics,
                onValueChange = { lyrics = it },
                textStyle = TextStyle(color = c.accent, fontSize = 11.sp, fontFamily = Mpx.style.mono),
                cursorBrush = SolidColor(c.accent),
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            )
        }
        DialogButtons(onDismiss) {
            vm.editTitle(track.key, title.trim().takeIf { it.isNotEmpty() && it != track.title })
            when {
                pickedCover != null -> vm.editCover(track.key, pickedCover!!)
                clearedCover -> vm.clearCover(track.key)
            }
            vm.editLyrics(track, lyrics)
            onDismiss()
        }
    }
}

@Composable
private fun PlaylistEditorDialog(
    title: String,
    initialName: String,
    locked: Boolean,
    onSave: (String, Uri?) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val c = Mpx.colors
    var name by remember { mutableStateOf(initialName) }
    var picked by remember { mutableStateOf<Uri?>(null) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { if (it != null) picked = it }

    RetroDialog(onDismiss) {
        Txt("> $title", size = 12.sp, color = c.dim)
        if (locked) {
            Txt("NOME", size = 11.sp, color = c.dim)
            Txt(initialName.uppercase() + "  (TRAVADA)", size = 13.sp)
        } else {
            Field("NOME", name) { name = it }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Txt("FOTO", Modifier.width(60.dp), size = 11.sp, color = c.dim)
            TextButton(
                if (picked != null) "SELECIONADA" else "ESCOLHER", Modifier.weight(1f).height(36.dp), size = 11.sp,
            ) { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
        }
        if (onDelete != null) {
            var confirm by remember { mutableStateOf(false) }
            TextButton(
                if (confirm) "CONFIRMAR EXCLUSÃO?" else "EXCLUIR PLAYLIST",
                Modifier.fillMaxWidth().height(36.dp), size = 11.sp, border = c.accent,
            ) { if (confirm) onDelete() else confirm = true }
        }
        DialogButtons(onDismiss, enabled = locked || name.isNotBlank()) { onSave(name, picked) }
    }
}

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit) {
    val c = Mpx.colors
    Column {
        Txt(label, size = 11.sp, color = c.dim)
        Box(Modifier.fillMaxWidth().height(40.dp).frame(c.accent).padding(horizontal = 10.dp), contentAlignment = Alignment.CenterStart) {
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TextStyle(color = c.accent, fontSize = 14.sp, fontFamily = Mpx.style.mono),
                cursorBrush = SolidColor(c.accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun DialogButtons(onDismiss: () -> Unit, enabled: Boolean = true, onSave: () -> Unit) {
    val c = Mpx.colors
    Row(Modifier.fillMaxWidth().height(44.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        TextButton("CANCELAR", Modifier.weight(1f).height(44.dp), onClick = onDismiss)
        TextButton(
            "SALVAR", Modifier.weight(1f).height(44.dp), filled = enabled,
            border = if (enabled) c.accent else c.line,
        ) { if (enabled) onSave() }
    }
}
