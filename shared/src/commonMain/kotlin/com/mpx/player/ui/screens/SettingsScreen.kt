package com.mpx.player.ui.screens

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mpx.player.ui.SettingsUi
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
import com.mpx.player.ui.theme.Mpx
import com.mpx.player.ui.theme.Phosphors
import com.mpx.player.ui.theme.jetBrainsMonoFamily
import com.mpx.player.ui.theme.vt323Family

/**
 * Ações portáveis de CONFIG (molde [LibraryActions]): aparência/cores/fonte/capa, toggles de áudio,
 * limpar mensagem e disparar scan. As ações Android-only (SAF/import, download, auto-update, letras)
 * NÃO entram aqui — vêm pelos SLOTS composable que o `:app` preenche no [AndroidSettingsScreen].
 */
data class SettingsActions(
    val onSetTheme: (Int) -> Unit,
    val onSetCustomColor: (Int) -> Unit,
    val onSetOled: (Boolean) -> Unit,
    val onSetFont: (Int) -> Unit,
    val onSetCover: (Int) -> Unit,
    val onToggleScanlines: () -> Unit,
    val onToggleGlow: () -> Unit,
    val onToggleCursor: () -> Unit,
    val onToggleMono: () -> Unit,
    val onToggleCrossfade: () -> Unit,
    val onClearMessage: () -> Unit,
    val onScan: () -> Unit,
)

/**
 * Tela CONFIG (CONFIG.SYS) — migrada para `commonMain` na fatia SETTINGS (P7). Stateless: recebe
 * [ui] + [actions] e renderiza as 6 seções. As partes Android-only entram por slots nuláveis
 * (null = some): [importSlot] (PASTA/PLAYLISTS/LETRAS/lyrics-sync), [downloaderSlot] (BAIXAR inteiro),
 * [updateSlot] (status de auto-update) e [outputDevice] (SAÍDA de áudio). [onReboot]/[onCheckUpdate]
 * nuláveis: no iOS ambos são null → a linha de botões CHECK UPDATE/REBOOT some (nenhum botão morto).
 * ZERO API Android aqui (formatação de hex/valor é manual; data/versão vêm prontas em [ui]).
 */
@Composable
fun SettingsScreen(
    ui: SettingsUi,
    actions: SettingsActions,
    onReboot: (() -> Unit)?,
    onCheckUpdate: (() -> Unit)? = null,
    downloaderSlot: (@Composable () -> Unit)? = null,
    importSlot: (@Composable () -> Unit)? = null,
    updateSlot: (@Composable () -> Unit)? = null,
    outputDevice: String? = null,
) {
    val c = Mpx.colors
    val s = ui.settings
    var open by rememberSaveable { mutableIntStateOf(0) }

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
                Modifier.fillMaxWidth().background(c.accent).tap(onClick = actions.onClearMessage).padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Txt(msg, Modifier.weight(1f), size = 11.sp, color = c.onAccent, maxLines = 2)
                Txt("[X]", size = 11.sp, color = c.onAccent)
            }
        }

        // ---- 1 APARÊNCIA
        Section(1, "APARÊNCIA", "DISPLAY", open) { open = it }
        if (open == 1) SectionBody {
            Group("FONTE DO SISTEMA") {
                Segmented(listOf("VT323", "JB MONO", "COURIER"), s.font, actions.onSetFont) { i, label, fg ->
                    // vt323Family/jetBrainsMonoFamily são @Composable (Font via Compose Resources);
                    // chamadas incondicionalmente e só então escolhidas, evitando composable em `when`.
                    val vt = vt323Family()
                    val jb = jetBrainsMonoFamily()
                    val fam = when (i) { 0 -> vt; 1 -> jb; else -> FontFamily.Monospace }
                    Txt(label, size = if (i == 0) 20.sp else 11.sp, color = fg, family = fam, align = TextAlign.Center)
                }
            }
            Group("ESTILO DA CAPA") {
                Segmented(listOf("DISCO", "DITHER", "ASCII"), s.cover, actions.onSetCover)
            }
            ToggleRow("SCANLINES CRT", s.scanlines, if (s.scanlines) "ON" else "OFF", actions.onToggleScanlines)
            ToggleRow("GLOW NO TEXTO", s.glow, if (s.glow) "ON" else "OFF", actions.onToggleGlow)
            ToggleRow("CURSOR PISCANTE", s.cursor, if (s.cursor) "_" else "OFF", actions.onToggleCursor)
        }

        // ---- 2 CORES
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
                                .tap(Role.RadioButton) { actions.onSetTheme(i) },
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
                        .tap(Role.RadioButton) { actions.onSetCustomColor(customArgb) }
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(Modifier.size(22.dp).glow(Color(customArgb), 8.dp).background(Color(customArgb)))
                    Txt((if (customOn) "▸ " else "") + "PERSONALIZADA", size = 12.sp, color = Color(customArgb))
                    Box(Modifier.weight(1f))
                    Txt("#" + hex6(customArgb), size = 11.sp, color = c.dim)
                }
                if (customOn) ColorEditor(customArgb) { actions.onSetCustomColor(it) }
            }
            Group("FUNDO") {
                Segmented(listOf("■ PRETO #040704", "□ OLED #000000"), if (s.oled) 1 else 0, { actions.onSetOled(it == 1) })
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

        // ---- 3 IMPORTAR (CAPAS MONO + ESCANEAR sempre comuns; PASTA/PLAYLISTS/LETRAS/sync no slot)
        Section(3, "IMPORTAR", "I/O", open) { open = it }
        if (open == 3) SectionBody {
            importSlot?.invoke()
            ToggleRow("CONVERTER CAPAS P/ MONO", s.monoCovers, if (s.monoCovers) "DITHER 1-BIT" else "ORIGINAL", actions.onToggleMono, minHeight = 52.dp)
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (ui.scanning) {
                    SegmentBar(20, 12, height = 20.dp)
                    Txt("SCANNING ...", size = 12.sp)
                } else {
                    TextButton("> ESCANEAR BIBLIOTECA", Modifier.fillMaxWidth().height(44.dp), filled = true, onClick = actions.onScan)
                }
                Txt("ÚLTIMO SCAN: ${ui.lastScanLabel} · ${ui.newFiles} NOVOS", size = 10.sp, color = c.dim)
            }
        }

        // ---- 4 BAIXAR (inteiro no slot; null = seção some, p.ex. iOS)
        if (downloaderSlot != null) {
            Section(4, "BAIXAR", "NET", open) { open = it }
            if (open == 4) SectionBody { downloaderSlot() }
        }

        // ---- 5 ÁUDIO
        Section(5, "ÁUDIO", "DAC", open) { open = it }
        if (open == 5) SectionBody {
            InfoRow("GAPLESS", "NATIVO")
            ToggleRow("CROSSFADE", s.crossfade, if (s.crossfade) "4s" else "OFF", actions.onToggleCrossfade)
            InfoRow("QUALIDADE", "ORIGINAL ▸", divider = outputDevice != null)
            if (outputDevice != null) InfoRow("SAÍDA", "$outputDevice ▸", divider = false)
        }

        // ---- 6 SOBRE
        Section(6, "SOBRE", "INFO", open) { open = it }
        if (open == 6) SectionBody {
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(
                    "APP" to "MPX//PLAYER",
                    "VERSÃO" to ui.appVersion,
                    "ENGINE" to "MPX AUDIO CORE · MEDIA3",
                    "LICENÇAS" to "OFL (VT323, JETBRAINS MONO)",
                    "AUTOR" to "Henckemaier",
                ).forEach { (k, v) ->
                    Row {
                        Txt(k, Modifier.width(96.dp), size = 11.sp, color = c.dim)
                        Txt(v, Modifier.weight(1f), size = 11.sp)
                    }
                }
                Spacer(Modifier.height(2.dp))
                updateSlot?.invoke()
            }
            if (onReboot != null) {
                Row(Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, bottom = 10.dp).height(44.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (onCheckUpdate != null) {
                        TextButton("CHECK UPDATE", Modifier.weight(1f).fillMaxHeight(), onClick = onCheckUpdate)
                    }
                    TextButton("REBOOT ⟳", Modifier.weight(1f).fillMaxHeight(), border = c.accent, onClick = onReboot)
                }
            }
        }

        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Txt("(C) 2026 MPX//OS", size = 10.sp, color = c.dim)
            Txt("F10 = SAVE & EXIT", size = 10.sp, color = c.dim)
        }
    }
}

// ----------------------------------------------------------------- scaffolding puro (comum)

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

/**
 * Linha de ação "título / subtítulo" com divisória. Pública (não `private`): o `:app` reusa este
 * mesmo FQN nos slots Android (DownloadBody/ActualizarYtDlp) — zero duplicata entre módulos.
 */
@Composable
fun ActionRow(title: String, sub: String, onClick: () -> Unit) {
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

/**
 * Faixa de resultado invertida com "[X]" para limpar. Pública: reusada pelos slots Android
 * (DownloadBody/LyricsSyncBody) via mesmo FQN.
 */
@Composable
fun ResultRow(msg: String, onClear: () -> Unit) {
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
        Txt(value.toString().padStart(3, ' '), Modifier.width(30.dp), size = 11.sp, color = c.dim)
    }
}

private fun argbOf(r: Int, g: Int, b: Int): Int =
    (0xFF shl 24) or ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)

/** "%06X" multiplataforma (JVM `String.format` não existe em common): 6 hex-dígitos maiúsculos. */
private fun hex6(argb: Int): String = (argb and 0xFFFFFF).toString(16).padStart(6, '0').uppercase()
