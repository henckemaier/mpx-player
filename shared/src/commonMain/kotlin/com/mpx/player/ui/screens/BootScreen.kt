package com.mpx.player.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mpx.player.ui.components.HeaderLabel
import com.mpx.player.ui.components.Panel
import com.mpx.player.ui.components.SegmentBar
import com.mpx.player.ui.components.TextButton
import com.mpx.player.ui.components.Txt
import com.mpx.player.ui.components.blinkingCursor
import com.mpx.player.ui.components.fmtThousands
import com.mpx.player.ui.components.frame
import com.mpx.player.ui.theme.Mpx
import kotlinx.coroutines.delay

private const val LOGO = """███╗   ███╗██████╗ ██╗  ██╗
████╗ ████║██╔══██╗╚██╗██╔╝
██╔████╔██║██████╔╝ ╚███╔╝
██║╚██╔╝██║██╔═══╝  ██╔██╗
██║ ╚═╝ ██║██║     ██╔╝ ██╗
╚═╝     ╚═╝╚═╝     ╚═╝  ╚═╝"""

/**
 * Splash/BIOS de abertura (RETRATO e PAISAGEM). Migrou de `:app` p/ commonMain na fatia BOOT do
 * porte iOS (P7, MESMO FQN `com.mpx.player.ui.screens.BootScreen` → call-site do `:app` resolve sem
 * mudança). NÃO é android-only estrutural: a permissão é só `hasPermission: Boolean` + callback, e o
 * `version` entra por PARÂMETRO (Android passa `BuildConfig.VERSION_NAME`; iOS passa "1.0").
 *
 * [stepOverride]/[barOverride] (ambos default null) servem SÓ ao gate A/B: quando null (Android E o
 * splash iOS) o `step`/`bar` ANIMAM de verdade pelos [LaunchedEffect] — comportamento byte-idêntico
 * ao de antes. Quando fornecidos (A/B passa 6/20) a tela usa os valores FIXOS e os [LaunchedEffect]
 * NÃO rodam (frame TERMINAL determinístico): o ÚNICO bit animado no frame terminal é o cursor
 * piscante do "//PLAYER".
 */
@Composable
fun BootScreen(
    hasPermission: Boolean,
    scanned: Boolean,
    fileCount: Int,
    eqPreset: String,
    version: String,
    onRequestPermission: () -> Unit,
    onEnter: () -> Unit,
    stepOverride: Int? = null,
    barOverride: Int? = null,
) {
    val c = Mpx.colors
    // quantas linhas do log de boot já "rodaram". Sem override: anima via LaunchedEffect (trava em
    // step=2 sem permissão / step=3 sem scan). Com override: valor FIXO, LaunchedEffect não roda.
    var stepState by remember { mutableIntStateOf(0) }
    if (stepOverride == null) {
        LaunchedEffect(hasPermission, scanned) {
            while (stepState < 6) {
                if (stepState == 2 && !hasPermission) return@LaunchedEffect
                if (stepState == 3 && !scanned) return@LaunchedEffect
                delay(260)
                stepState++
            }
        }
    }
    val step = stepOverride ?: stepState
    val ready = step >= 6
    var barState by remember { mutableIntStateOf(0) }
    if (barOverride == null) {
        LaunchedEffect(step) {
            val target = (step * 20 / 6).coerceAtMost(20)
            while (barState < target) {
                delay(40); barState++
            }
        }
    }
    val bar = barOverride ?: barState

    if (Mpx.landscape) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            BootHeader(version)
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    BootBranding()
                }
                BootLog(Modifier.weight(1f).fillMaxHeight(), hasPermission, scanned, fileCount, eqPreset, step, bar, ready)
            }
            BootButton(hasPermission, ready, onRequestPermission, onEnter)
            BootFooter()
        }
    } else {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            BootHeader(version)
            BootBranding()
            BootLog(Modifier.fillMaxWidth().weight(1f), hasPermission, scanned, fileCount, eqPreset, step, bar, ready)
            BootButton(hasPermission, ready, onRequestPermission, onEnter)
            BootFooter()
        }
    }
}

@Composable
private fun BootHeader(version: String) {
    Row(
        Modifier.fillMaxWidth().frame().padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        HeaderLabel("MPX BIOS (TM)"); HeaderLabel("RESEARCH"); HeaderLabel("VER $version")
    }
}

@Composable
private fun BootBranding() {
    val c = Mpx.colors
    Panel(Modifier.fillMaxWidth()) {
        Column {
            listOf("@ECHO OFF", "TITLE MPX//PLAYER", "MODE CON: COLS=80 LINES=30", "COLOR 0A", "CLS")
                .forEach { Txt(it, size = 11.sp, color = c.dim, lineHeight = 17.sp) }
        }
    }
    Txt(
        LOGO, Modifier.fillMaxWidth(), size = 13.sp, glow = true, align = TextAlign.Center,
        maxLines = 6, lineHeight = 15.sp,
    )
    Txt("//PLAYER" + blinkingCursor(), Modifier.fillMaxWidth(), size = 24.sp, display = true, align = TextAlign.Center, letterSpacing = 6.sp)
}

@Composable
private fun BootLog(
    modifier: Modifier,
    hasPermission: Boolean,
    scanned: Boolean,
    fileCount: Int,
    eqPreset: String,
    step: Int,
    bar: Int,
    ready: Boolean,
) {
    val c = Mpx.colors
    Panel(modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val lines = listOf(
                "> CHECK AUDIO_DAC" to "[ OK ]",
                "> MOUNT /music" to if (hasPermission) "[ OK ]" else "[WAIT]",
                "> INDEX ${fmtThousands(fileCount)} FILES" to if (scanned) "[ OK ]" else "[....]",
                "> LOAD CODEC mp3/flac" to "[ OK ]",
                "> EQ PRESET ${eqPreset.lowercase()}" to "[ OK ]",
                "> INTERNET CONNECTION" to "[NOPE]",
            )
            lines.take(step.coerceAtLeast(1)).forEachIndexed { i, (l, r) ->
                val muted = i == 5 || (i == 1 && !hasPermission)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Txt(l, size = 12.sp, color = if (muted) c.dim else c.accent)
                    Txt(r, size = 12.sp, color = if (muted) c.dim else c.accent)
                }
            }
            if (!hasPermission) {
                Spacer(Modifier.height(8.dp))
                Txt("ACESSO NEGADO A /music", size = 12.sp, color = c.accent)
                Txt("O MPX PRECISA LER SEUS ARQUIVOS DE ÁUDIO.", size = 11.sp, color = c.dim, maxLines = 2)
            } else {
                Spacer(Modifier.height(10.dp))
                Txt(if (ready) "USER_PROFILE LOADED" else "LOADING USER_PROFILE ...", size = 12.sp, color = c.dim)
                SegmentBar(20, bar, height = 20.dp)
                Txt("${bar * 5}%", size = 20.sp, display = true)
            }
        }
    }
}

@Composable
private fun BootButton(hasPermission: Boolean, ready: Boolean, onRequestPermission: () -> Unit, onEnter: () -> Unit) {
    when {
        !hasPermission -> TextButton(
            "[ GRANT ACCESS ]", Modifier.fillMaxWidth().height(54.dp), filled = true, size = 26.sp, display = true,
            onClick = onRequestPermission,
        )
        else -> TextButton(
            if (ready) "[ PRESS ENTER ]" else "[ BOOTING... ]", Modifier.fillMaxWidth().height(54.dp),
            filled = ready, size = 26.sp, display = true,
            onClick = { if (ready) onEnter() },
        )
    }
}

@Composable
private fun BootFooter() {
    val c = Mpx.colors
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Txt("(C) 2026 MPX//OS", size = 10.sp, color = c.dim)
        Txt("MEM 640K OK", size = 10.sp, color = c.dim)
    }
}
