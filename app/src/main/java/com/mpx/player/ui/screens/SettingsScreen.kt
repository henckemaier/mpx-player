package com.mpx.player.ui.screens

import android.content.Context
import android.content.Intent
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mpx.player.BuildConfig
import com.mpx.player.data.DownloadState
import com.mpx.player.data.DownloadStatus
import com.mpx.player.data.UpdateState
import com.mpx.player.data.UpdateStatus
import com.mpx.player.model.UserSettings
import com.mpx.player.ui.LyricsSyncUi
import com.mpx.player.ui.SettingsUi
import com.mpx.player.ui.SettingsViewModel
import com.mpx.player.ui.components.DashedDivider
import com.mpx.player.ui.components.MpxButton
import com.mpx.player.ui.components.SegmentBar
import com.mpx.player.ui.components.TextButton
import com.mpx.player.ui.components.ToggleRow
import com.mpx.player.ui.components.Txt
import com.mpx.player.ui.components.blinkingCursor
import com.mpx.player.ui.components.frame
import com.mpx.player.ui.components.glow
import com.mpx.player.ui.components.tap
import com.mpx.player.ui.theme.CUSTOM_THEME
import com.mpx.player.ui.theme.JetBrainsMono
import com.mpx.player.ui.theme.Mpx
import com.mpx.player.ui.theme.Phosphors
import com.mpx.player.ui.theme.Vt323
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(ui: SettingsUi, vm: SettingsViewModel, onReboot: () -> Unit) {
    val c = Mpx.colors
    val s = ui.settings
    val context = LocalContext.current
    val dl by vm.download.collectAsStateWithLifecycle()
    val lrcSync by vm.lyricsSync.collectAsStateWithLifecycle()
    val up by vm.update.collectAsStateWithLifecycle()
    var open by rememberSaveable { mutableIntStateOf(0) }

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

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.fillMaxWidth().frame().padding(horizontal = 10.dp, vertical = 8.dp)) {
            Txt("C:\\> EDIT CONFIG.SYS", size = 11.sp, color = c.dim, lineHeight = 17.sp)
            Row {
                Txt(":: SELECIONE UMA OPÇÃO [1-6]", size = 11.sp, color = c.dim, lineHeight = 17.sp)
                val cur = blinkingCursor()
                Txt(cur, Modifier.background(if (cur == "_") c.accent else Color.Transparent), size = 11.sp, color = c.onAccent, lineHeight = 17.sp)
            }
        }

        ui.message?.let { msg ->
            Row(
                Modifier.fillMaxWidth().background(c.accent).tap(onClick = vm::clearMessage).padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Txt(msg, Modifier.weight(1f), size = 11.sp, color = c.onAccent, maxLines = 2)
                Txt("[X]", size = 11.sp, color = c.onAccent)
            }
        }

        Section(1, "APARÊNCIA", "DISPLAY", open) { open = it }
        if (open == 1) SectionBody {
            Group("FONTE DO SISTEMA") {
                Segmented(listOf("VT323", "JB MONO", "COURIER"), s.font, vm::setFont) { i, label, fg ->
                    val fam = when (i) { 0 -> Vt323; 1 -> JetBrainsMono; else -> FontFamily.Monospace }
                    Txt(label, size = if (i == 0) 20.sp else 11.sp, color = fg, family = fam, align = TextAlign.Center)
                }
            }
            Group("ESTILO DA CAPA") {
                Segmented(listOf("DISCO", "DITHER", "ASCII"), s.cover, vm::setCover)
            }
            ToggleRow("SCANLINES CRT", s.scanlines, if (s.scanlines) "ON" else "OFF", { vm.toggleScanlines(s) })
            ToggleRow("GLOW NO TEXTO", s.glow, if (s.glow) "ON" else "OFF", { vm.toggleGlow(s) })
            ToggleRow("CURSOR PISCANTE", s.cursor, if (s.cursor) "_" else "OFF", { vm.toggleCursor(s) })
        }

        Section(2, "CORES", c.themeName, open) { open = it }
        if (open == 2) SectionBody {
            Group("FÓSFORO (COR DE DESTAQUE)") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Phosphors.forEachIndexed { i, p ->
                        val on = s.theme == i
                        Column(
                            Modifier
                                .weight(1f)
                                .height(60.dp)
                                .border(1.dp, if (on) p.accent else Color(0xFF1F2A22))
                                .tap(Role.RadioButton) { vm.setTheme(i) },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
                        ) {
                            Box(Modifier.size(22.dp).glow(p.accent, 8.dp).background(p.accent))
                            Txt((if (on) "▸" else "") + p.name, size = 10.sp, color = p.accent)
                        }
                    }
                }
                val customArgb = s.customColor ?: c.accent.toArgb()
                val customOn = s.theme == CUSTOM_THEME
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .border(1.dp, if (customOn) Color(customArgb) else Color(0xFF1F2A22))
                        .tap(Role.RadioButton) { vm.setCustomColor(customArgb) }
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(Modifier.size(22.dp).glow(Color(customArgb), 8.dp).background(Color(customArgb)))
                    Txt((if (customOn) "▸ " else "") + "PERSONALIZADA", size = 12.sp, color = Color(customArgb))
                    Box(Modifier.weight(1f))
                    Txt("#%06X".format(customArgb and 0xFFFFFF), size = 11.sp, color = c.dim)
                }
                if (customOn) ColorEditor(customArgb) { vm.setCustomColor(it) }
            }
            Group("FUNDO") {
                Segmented(listOf("■ PRETO #040704", "□ OLED #000000"), if (s.oled) 1 else 0, { vm.setOled(it == 1) })
            }
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Txt("PREVIEW", size = 11.sp, color = c.dim)
                Column(Modifier.fillMaxWidth().frame().padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Txt("> NOW PLAYING", Modifier.fillMaxWidth().background(c.accent).padding(horizontal = 6.dp), size = 18.sp, display = true, color = c.onAccent)
                    Txt("SIGNAL//DECAY_", size = 26.sp, display = true, glow = true)
                    Txt("NULL SECTOR — STATIC BLOOM", size = 10.sp, color = c.dim)
                }
            }
        }

        Section(3, "IMPORTAR", "I/O", open) { open = it }
        if (open == 3) SectionBody {
            ActionRow("> PASTA LOCAL", "/sdcard/Music  ·  ${"%,d".format(ui.files).replace(',', '.')} FILES") { vm.scan() }
            ActionRow("> PLAYLISTS .M3U / .PLS", "${s.playlistsImported} IMPORTADAS · TOQUE P/ ABRIR") { pickPlaylist.launch(arrayOf("*/*")) }
            ActionRow(
                "> LETRAS .LRC",
                if (s.lrcTreeUri == null) "SINCRONIZAR COM A PASTA" else "${ui.lrcCount} .LRC · ${Uri.parse(s.lrcTreeUri).lastPathSegment?.substringAfter(':')?.uppercase()}",
            ) { pickLrcFolder.launch(null) }
            LyricsSyncBody(lrcSync, onStart = vm::syncLyrics, onCancel = vm::cancelLyricsSync, onClear = vm::clearLyricsSync)
            ToggleRow("CONVERTER CAPAS P/ MONO", s.monoCovers, if (s.monoCovers) "DITHER 1-BIT" else "ORIGINAL", { vm.toggleMono(s) }, minHeight = 52.dp)
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (ui.scanning) {
                    SegmentBar(20, 12, height = 20.dp)
                    Txt("SCANNING ...", size = 12.sp)
                } else {
                    TextButton("> ESCANEAR BIBLIOTECA", Modifier.fillMaxWidth().height(44.dp), filled = true, onClick = vm::scan)
                }
                val last = if (s.lastScan == 0L) "NUNCA" else SimpleDateFormat("dd/MM HH:mm", Locale.US).format(Date(s.lastScan))
                Txt("ÚLTIMO SCAN: $last · ${ui.newFiles} NOVOS", size = 10.sp, color = c.dim)
            }
        }

        Section(4, "BAIXAR", "NET", open) { open = it }
        if (open == 4) SectionBody {
            DownloadBody(
                dl,
                onStart = vm::startDownload,
                onCancel = vm::cancelDownload,
                onUpdate = vm::updateYtDlp,
                onClear = vm::clearDownload,
            )
        }

        Section(5, "ÁUDIO", "DAC", open) { open = it }
        if (open == 5) SectionBody {
            InfoRow("GAPLESS", "NATIVO")
            ToggleRow("CROSSFADE", s.crossfade, if (s.crossfade) "4s" else "OFF", { vm.toggleCrossfade(s) })
            InfoRow("QUALIDADE", "ORIGINAL ▸")
            InfoRow("SAÍDA", remember(open) { outputDevice(context) } + " ▸", divider = false)
        }

        Section(6, "SOBRE", "INFO", open) { open = it }
        if (open == 6) SectionBody {
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(
                    "APP" to "MPX//PLAYER",
                    "VERSÃO" to "${BuildConfig.VERSION_NAME} (BUILD 2026.10)",
                    "ENGINE" to "MPX AUDIO CORE · MEDIA3",
                    "LICENÇAS" to "OFL (VT323, JETBRAINS MONO)",
                    "AUTOR" to "[SEU NOME]",
                ).forEach { (k, v) ->
                    Row {
                        Txt(k, Modifier.width(96.dp), size = 11.sp, color = c.dim)
                        Txt(v, Modifier.weight(1f), size = 11.sp)
                    }
                }
                Spacer(Modifier.height(2.dp))
                UpdateStatusRow(up, onInstall = vm::installUpdate, onClear = vm::clearUpdate)
            }
            Row(Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, bottom = 10.dp).height(44.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(
                    if (up.status == UpdateStatus.CHECKING) "VERIFICANDO..." else "CHECK UPDATE",
                    Modifier.weight(1f).fillMaxHeight(), onClick = vm::checkUpdate,
                )
                TextButton("REBOOT ⟳", Modifier.weight(1f).fillMaxHeight(), border = c.accent, onClick = onReboot)
            }
        }

        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Txt("(C) 2026 MPX//OS", size = 10.sp, color = c.dim)
            Txt("F10 = SAVE & EXIT", size = 10.sp, color = c.dim)
        }
    }
}

@Composable
private fun Section(n: Int, title: String, tag: String, open: Int, onToggle: (Int) -> Unit) {
    val c = Mpx.colors
    val on = open == n
    Row(
        Modifier
            .fillMaxWidth()
            .height(44.dp)
            .border(1.dp, if (on) c.accent else c.line)
            .background(if (on) c.accent else c.bg)
            .tap { onToggle(if (on) 0 else n) }
            .padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val fg = if (on) c.onAccent else c.accent
        Txt("${if (on) "▾" else "▸"} [$n] $title", size = 22.sp, display = true, color = fg)
        Txt(tag, size = 22.sp, display = true, color = fg)
    }
}

@Composable
private fun SectionBody(content: @Composable ColumnScope.() -> Unit) {
    val c = Mpx.colors
    Column(Modifier.fillMaxWidth().padding(top = 0.dp).border(1.dp, c.accent), content = content)
}

@Composable
private fun Group(label: String, content: @Composable () -> Unit) {
    val c = Mpx.colors
    Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Txt(label, size = 11.sp, color = c.dim)
        content()
    }
    DashedDivider()
}

@Composable
private fun Segmented(
    labels: List<String>,
    selected: Int,
    onPick: (Int) -> Unit,
    label: @Composable (Int, String, Color) -> Unit = { _, l, fg -> Txt(l, size = 11.sp, color = fg, align = TextAlign.Center) },
) {
    val c = Mpx.colors
    Row(Modifier.fillMaxWidth().height(44.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        labels.forEachIndexed { i, l ->
            MpxButton(
                Modifier.weight(1f).fillMaxHeight(), filled = i == selected,
                border = if (i == selected) c.accent else c.line, onClick = { onPick(i) },
            ) { fg -> label(i, l, fg) }
        }
    }
}

@Composable
private fun ActionRow(title: String, sub: String, onClick: () -> Unit) {
    val c = Mpx.colors
    Column(
        Modifier.fillMaxWidth().height(52.dp).tap(onClick = onClick).padding(horizontal = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
    ) {
        Txt(title, size = 12.sp)
        Txt(sub, size = 10.sp, color = c.dim)
    }
    DashedDivider()
}

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
    DashedDivider()
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
private fun ColorEditor(argb: Int, onChange: (Int) -> Unit) {
    // Mantém a cor mais recente acessível mesmo no gesto capturado pela SegmentBar.
    val current by rememberUpdatedState(argb)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ChannelSlider("R", (argb shr 16) and 0xFF, Color(0xFFFF5555)) { v ->
            onChange(argbOf(v, (current shr 8) and 0xFF, current and 0xFF))
        }
        ChannelSlider("G", (argb shr 8) and 0xFF, Color(0xFF55FF55)) { v ->
            onChange(argbOf((current shr 16) and 0xFF, v, current and 0xFF))
        }
        ChannelSlider("B", argb and 0xFF, Color(0xFF5599FF)) { v ->
            onChange(argbOf((current shr 16) and 0xFF, (current shr 8) and 0xFF, v))
        }
    }
}

@Composable
private fun ChannelSlider(label: String, value: Int, tint: Color, onSet: (Int) -> Unit) {
    val c = Mpx.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Txt(label, Modifier.width(14.dp), size = 12.sp)
        SegmentBar(
            count = 32,
            lit = value * 32 / 255,
            modifier = Modifier.weight(1f),
            height = 18.dp,
            onColor = tint,
            onSeek = { f -> onSet((f * 255f).toInt().coerceIn(0, 255)) },
        )
        Txt("%3d".format(value), Modifier.width(30.dp), size = 11.sp, color = c.dim)
    }
}

private fun argbOf(r: Int, g: Int, b: Int): Int =
    (0xFF shl 24) or ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)

@Composable
private fun ResultRow(msg: String, onClear: () -> Unit) {
    val c = Mpx.colors
    Row(
        Modifier.fillMaxWidth().background(c.accent).tap(onClick = onClear).padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Txt(msg, Modifier.weight(1f), size = 11.sp, color = c.onAccent, maxLines = 2)
        Txt("[X]", size = 11.sp, color = c.onAccent)
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

@Composable
private fun InfoRow(k: String, v: String, divider: Boolean = true) {
    val c = Mpx.colors
    Row(
        Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Txt(k, size = 12.sp)
        Txt(v, size = 12.sp, color = c.dim)
    }
    if (divider) DashedDivider()
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
