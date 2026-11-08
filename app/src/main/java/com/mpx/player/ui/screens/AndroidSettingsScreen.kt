package com.mpx.player.ui.screens

import android.content.Context
import android.content.Intent
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mpx.player.data.DownloadState
import com.mpx.player.data.DownloadStatus
import com.mpx.player.data.UpdateState
import com.mpx.player.data.UpdateStatus
import com.mpx.player.ui.AndroidSettingsViewModel
import com.mpx.player.ui.LyricsSyncUi
import com.mpx.player.ui.SettingsUi
import com.mpx.player.ui.components.SegmentBar
import com.mpx.player.ui.components.TextButton
import com.mpx.player.ui.components.Txt
import com.mpx.player.ui.components.frame
import com.mpx.player.ui.components.tap
import com.mpx.player.ui.theme.Mpx

/**
 * Wrapper Android da [SettingsScreen] comum (fatia SETTINGS, P7). Concentra o que NÃO é portável: os
 * launchers SAF (import de playlist / pasta de letras), o dispositivo de saída (AudioManager) e os
 * corpos dos slots Android-only (download, auto-update, lyrics-sync). Monta a [SettingsActions]
 * cabeando os browse-actions ao [AndroidSettingsViewModel] e passa o resto pelos slots composable.
 */
@Composable
fun AndroidSettingsScreen(ui: SettingsUi, vm: AndroidSettingsViewModel, onReboot: () -> Unit) {
    val context = LocalContext.current
    val s = ui.settings
    val dl by vm.download.collectAsStateWithLifecycle()
    val lrcSync by vm.lyricsSync.collectAsStateWithLifecycle()
    val up by vm.update.collectAsStateWithLifecycle()

    val pickPlaylist = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importPlaylist(uri)
    }
    val pickLrcFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            vm.setLrcFolder(uri)
        }
    }

    val actions = SettingsActions(
        onSetTheme = vm::setTheme,
        onSetCustomColor = vm::setCustomColor,
        onSetOled = vm::setOled,
        onSetFont = vm::setFont,
        onSetCover = vm::setCover,
        onToggleScanlines = vm::toggleScanlines,
        onToggleGlow = vm::toggleGlow,
        onToggleCursor = vm::toggleCursor,
        onToggleMono = vm::toggleMono,
        onToggleCrossfade = vm::toggleCrossfade,
        onClearMessage = vm::clearMessage,
        onScan = vm::rescan,
    )

    SettingsScreen(
        ui = ui,
        actions = actions,
        onReboot = onReboot,
        onCheckUpdate = vm::checkUpdate,
        downloaderSlot = {
            DownloadBody(
                dl,
                onStart = vm::startDownload,
                onCancel = vm::cancelDownload,
                onUpdate = vm::updateYtDlp,
                onClear = vm::clearDownload,
            )
        },
        importSlot = {
            ActionRow("> PASTA LOCAL", "/sdcard/Music  ·  ${thousands(ui.files)} FILES") { vm.rescan() }
            ActionRow("> PLAYLISTS .M3U / .PLS", "${s.playlistsImported} IMPORTADAS · TOQUE P/ ABRIR") { pickPlaylist.launch(arrayOf("*/*")) }
            ActionRow(
                "> LETRAS .LRC",
                if (s.lrcTreeUri == null) "SINCRONIZAR COM A PASTA"
                else "${ui.lrcCount} .LRC · ${Uri.parse(s.lrcTreeUri).lastPathSegment?.substringAfter(':')?.uppercase()}",
            ) { pickLrcFolder.launch(null) }
            LyricsSyncBody(lrcSync, onStart = vm::syncLyrics, onCancel = vm::cancelLyricsSync, onClear = vm::clearLyricsSync)
        },
        updateSlot = {
            UpdateStatusRow(up, onInstall = vm::installUpdate, onClear = vm::clearUpdate)
        },
        outputDevice = remember(context) { outputDevice(context) },
    )
}

private fun thousands(n: Int): String = "%,d".format(n).replace(',', '.')

@Composable
private fun DownloadBody(
    dl: DownloadState,
    onStart: (String) -> Unit,
    onCancel: () -> Unit,
    onUpdate: () -> Unit,
    onClear: () -> Unit,
) {
    val c = Mpx.colors
    var url by rememberSaveable { mutableStateOf("") }
    val running = dl.running

    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Txt("LINK DO YOUTUBE MUSIC (PLAYLIST / ÁLBUM / MÚSICA)", size = 11.sp, color = c.dim, maxLines = 2)
        Row(
            Modifier.fillMaxWidth().height(44.dp).frame(c.accent).padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Txt("C:\\>", size = 13.sp)
            Box(Modifier.weight(1f)) {
                if (url.isEmpty()) Txt("cole o link aqui", size = 13.sp, color = c.dim)
                BasicTextField(
                    value = url,
                    onValueChange = { url = it },
                    singleLine = true,
                    enabled = !running,
                    textStyle = TextStyle(color = c.accent, fontSize = 13.sp, fontFamily = Mpx.style.mono),
                    cursorBrush = SolidColor(c.accent),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (url.isNotEmpty() && !running) Txt("[X]", Modifier.tap { url = "" }, size = 13.sp)
        }

        if (running) {
            val lit = (dl.progress * 20).toInt().coerceIn(0, 20)
            SegmentBar(20, lit, height = 20.dp)
            Txt(dl.overall.ifEmpty { "..." }, size = 12.sp)
            if (dl.line.isNotEmpty()) Txt(dl.line, size = 10.sp, color = c.dim, maxLines = 2)
            TextButton("CANCELAR", Modifier.fillMaxWidth().height(44.dp), border = c.accent, onClick = onCancel)
        } else {
            TextButton(
                "> BAIXAR",
                Modifier.fillMaxWidth().height(44.dp),
                filled = url.isNotBlank(),
            ) { if (url.isNotBlank()) onStart(url.trim()) }
        }

        when (dl.status) {
            DownloadStatus.DONE -> {
                val existed = if (dl.skipped > 0) " · ${dl.skipped} JÁ EXISTIAM" else ""
                val unavail = if (dl.unavailable > 0) " · ${dl.unavailable} INDISPONÍVEIS" else ""
                val msg = when {
                    dl.tracks > 0 -> "OK: ${dl.tracks} NOVAS · ${dl.lyrics} COM LETRA$existed$unavail"
                    dl.skipped > 0 -> "NENHUMA FAIXA NOVA$existed$unavail"
                    dl.unavailable > 0 -> "NENHUMA FAIXA · ${dl.unavailable} INDISPONÍVEIS"
                    else -> dl.overall.ifEmpty { "OK" }
                }
                ResultRow(msg, onClear)
            }
            DownloadStatus.ERROR -> ResultRow("ERRO: ${dl.error ?: "FALHA NO DOWNLOAD"}", onClear)
            DownloadStatus.CANCELED -> ResultRow("CANCELADO", onClear)
            else -> {}
        }
    }
    ActionRow("> ATUALIZAR YT-DLP", "CORRIGE A EXTRAÇÃO QUANDO O YOUTUBE MUDA", onUpdate)
    Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Txt("LETRA VIA LRCLIB · CAPA FILTRADA NO ESTILO DO APP", size = 10.sp, color = c.dim, maxLines = 2)
        Txt("RESPEITE OS TERMOS DE USO E OS DIREITOS AUTORAIS", size = 10.sp, color = c.dim, maxLines = 2)
    }
}

/** Busca letras faltantes no LRCLIB para a biblioteca inteira, com progresso e cancelamento. */
@Composable
private fun LyricsSyncBody(st: LyricsSyncUi, onStart: () -> Unit, onCancel: () -> Unit, onClear: () -> Unit) {
    val c = Mpx.colors
    Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Txt("BUSCAR LETRAS FALTANTES NO LRCLIB (SEM REBAIXAR O ÁUDIO)", size = 10.sp, color = c.dim, maxLines = 2)
        if (st.running) {
            val lit = if (st.total > 0) (st.done * 20 / st.total).coerceIn(0, 20) else 0
            SegmentBar(20, lit, height = 20.dp)
            Txt("BUSCANDO ${st.done}/${st.total} · ${st.found} ACHADAS", size = 12.sp)
            TextButton("CANCELAR", Modifier.fillMaxWidth().height(44.dp), border = c.accent, onClick = onCancel)
        } else {
            TextButton("> BUSCAR LETRAS FALTANTES", Modifier.fillMaxWidth().height(44.dp), filled = true, onClick = onStart)
            st.result?.let { ResultRow(it, onClear) }
        }
    }
}

@Composable
private fun UpdateStatusRow(up: UpdateState, onInstall: () -> Unit, onClear: () -> Unit) {
    val c = Mpx.colors
    when (up.status) {
        UpdateStatus.IDLE -> Unit
        UpdateStatus.CHECKING -> Txt("> VERIFICANDO ATUALIZAÇÕES ...", size = 11.sp, color = c.dim, maxLines = 2)
        UpdateStatus.UP_TO_DATE -> Txt("> NA ÚLTIMA VERSÃO (v${up.latest})", size = 11.sp, color = c.dim, maxLines = 2)
        UpdateStatus.AVAILABLE -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Txt("> NOVA VERSÃO v${up.latest} DISPONÍVEL", size = 11.sp, maxLines = 2)
            if (up.notes.isNotBlank()) Txt(up.notes, size = 10.sp, color = c.dim, maxLines = 3)
            up.error?.let { Txt(it, size = 10.sp, color = c.dim, maxLines = 2) }
            TextButton("> BAIXAR E INSTALAR", Modifier.fillMaxWidth().height(40.dp), filled = true, onClick = onInstall)
        }
        UpdateStatus.DOWNLOADING -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Txt("> BAIXANDO v${up.latest} ...", size = 11.sp, maxLines = 1)
            SegmentBar(20, (up.progress * 20).toInt().coerceIn(0, 20), height = 16.dp)
        }
        UpdateStatus.ERROR -> Row(
            Modifier.fillMaxWidth().tap(onClick = onClear),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Txt("> ${up.error ?: "ERRO"}", Modifier.weight(1f), size = 11.sp, color = c.dim, maxLines = 2)
            Txt("[X]", size = 11.sp, color = c.dim)
        }
    }
}

private fun outputDevice(context: Context): String {
    val am = context.getSystemService(AudioManager::class.java)
    val types = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).map { it.type }.toSet()
    return when {
        AudioDeviceInfo.TYPE_USB_HEADSET in types || AudioDeviceInfo.TYPE_USB_DEVICE in types -> "USB DAC"
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES in types || AudioDeviceInfo.TYPE_WIRED_HEADSET in types -> "FONE P2"
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP in types -> "BLUETOOTH"
        else -> "ALTO-FALANTE"
    }
}
