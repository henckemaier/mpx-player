package com.mpx.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mpx.player.model.EqPresets
import com.mpx.player.model.EqSettings
import com.mpx.player.ui.components.InvertedBar
import com.mpx.player.ui.components.SegmentBar
import com.mpx.player.ui.components.TextButton
import com.mpx.player.ui.components.ToggleRow
import com.mpx.player.ui.components.Txt
import com.mpx.player.ui.components.frame
import com.mpx.player.ui.components.tap
import com.mpx.player.ui.theme.Mpx

data class EqActions(
    val toggleOn: () -> Unit,
    val preset: (String) -> Unit,
    val setBand: (Int, Int) -> Unit,
    val preamp: (Int) -> Unit,
    val bass: () -> Unit,
    val surround: () -> Unit,
    val loudness: () -> Unit,
    val reset: () -> Unit,
    val save: () -> Unit,
)

private fun dbLabel(v: Int) = (if (v > 0) "+" else "") + v

@Composable
fun EqScreen(eq: EqSettings, selected: Int, actions: EqActions) {
    if (Mpx.landscape) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(0.58f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                EqHeader(eq, actions)
                EqPresetRow(eq, actions)
                EqBandInfo(eq, selected)
                EqBands(eq, selected, actions)
            }
            Column(Modifier.weight(0.42f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                EqPreamp(eq, actions)
                EqEffects(eq, actions)
                Spacer(Modifier.weight(1f))
                EqButtons(actions)
            }
        }
    } else {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            EqHeader(eq, actions)
            EqPresetRow(eq, actions)
            EqBandInfo(eq, selected)
            EqBands(eq, selected, actions)
            EqPreamp(eq, actions)
            EqEffects(eq, actions)
            Spacer(Modifier.weight(1f))
            EqButtons(actions)
        }
    }
}

@Composable
private fun EqHeader(eq: EqSettings, actions: EqActions) {
    val c = Mpx.colors
    InvertedBar("> EQUALIZER // 10 BAND", trailing = {
        Box(
            Modifier.height(30.dp).background(c.onAccent).tap(onClick = actions.toggleOn).padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) { Txt(if (eq.enabled) "[ON ]" else "[OFF]", size = 20.sp, display = true) }
    })
}

@Composable
private fun EqPresetRow(eq: EqSettings, actions: EqActions) {
    val c = Mpx.colors
    Row(Modifier.fillMaxWidth().height(36.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        EqPresets.names.forEach { n ->
            TextButton(
                n, Modifier.weight(1f).fillMaxHeight(), filled = eq.preset == n, size = 11.sp,
                border = if (eq.preset == n) c.accent else c.line,
            ) { actions.preset(n) }
        }
    }
}

@Composable
private fun EqBandInfo(eq: EqSettings, selected: Int) {
    val c = Mpx.colors
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Row {
            Txt("> BAND ${EqPresets.freqLabels[selected]}Hz ", size = 12.sp)
            Txt(
                "${dbLabel(EqPresets.levelToDb(eq.levels[selected]))}dB",
                Modifier.background(c.accent).padding(horizontal = 4.dp), size = 12.sp, color = c.onAccent,
            )
        }
        Txt("Q 1.41", size = 12.sp, color = c.dim)
    }
}

@Composable
private fun EqBands(eq: EqSettings, selected: Int, actions: EqActions) {
    val c = Mpx.colors
    Row(Modifier.fillMaxWidth().frame().padding(horizontal = 8.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Column(Modifier.width(26.dp).height((13 * 14).dp), verticalArrangement = Arrangement.SpaceBetween) {
            listOf("+12", "+6", "0", "-6", "-12").forEach { Txt(it, size = 9.sp, color = c.dim) }
        }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (band in 0 until 10) {
                val isSel = band == selected
                val lv = eq.levels[band]
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .height((13 * 14).dp)
                            .pointerInput(band) {
                                fun levelAt(y: Float) = (12 - (y / (size.height / 13f)).toInt()).coerceIn(0, 12)
                                awaitEachGesture {
                                    val d = awaitFirstDown()
                                    var last = levelAt(d.position.y)
                                    actions.setBand(band, last)
                                    while (true) {
                                        val p = awaitPointerEvent().changes.firstOrNull() ?: break
                                        if (!p.pressed) break
                                        val l = levelAt(p.position.y)
                                        if (l != last) {
                                            last = l; actions.setBand(band, l)
                                        }
                                        p.consume()
                                    }
                                }
                            },
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        for (k in 12 downTo 0) {
                            val bg = when {
                                k <= lv -> if (!eq.enabled) c.line else if (isSel) c.bright else c.accent
                                k == 6 -> c.line
                                else -> c.segOff
                            }
                            Box(Modifier.fillMaxWidth().height(12.dp).background(bg))
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Txt(EqPresets.freqLabels[band], size = 9.sp, color = if (isSel) c.accent else c.dim, align = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
private fun EqPreamp(eq: EqSettings, actions: EqActions) {
    val c = Mpx.colors
    Column(Modifier.fillMaxWidth().frame().padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Txt(":: PREAMP", size = 11.sp, color = c.dim)
            val db = EqPresets.preampToDb(eq.preamp)
            Txt((if (db > 0) "+" else "") + String.format(java.util.Locale.US, "%.1f", db) + "dB", size = 11.sp)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TextButton("-", Modifier.width(44.dp).height(44.dp), border = c.accent, size = 26.sp, display = true) { actions.preamp(-1) }
            SegmentBar(24, eq.preamp, Modifier.weight(1f), height = 18.dp, onColor = if (eq.enabled) c.accent else c.line)
            TextButton("+", Modifier.width(44.dp).height(44.dp), border = c.accent, size = 26.sp, display = true) { actions.preamp(1) }
        }
    }
}

@Composable
private fun EqEffects(eq: EqSettings, actions: EqActions) {
    Column(Modifier.fillMaxWidth().frame()) {
        ToggleRow("BASS BOOST", eq.bassBoost, if (eq.bassBoost) "+3dB" else "OFF", actions.bass)
        ToggleRow("3D SURROUND", eq.surround, if (eq.surround) "WIDE" else "OFF", actions.surround)
        ToggleRow("LOUDNESS", eq.loudness, if (eq.loudness) "AUTO" else "OFF", actions.loudness)
    }
}

@Composable
private fun EqButtons(actions: EqActions) {
    Row(Modifier.fillMaxWidth().height(44.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        TextButton("RESET", Modifier.weight(1f).fillMaxHeight(), onClick = actions.reset)
        TextButton("SAVE PRESET.EQ", Modifier.weight(1f).fillMaxHeight(), filled = true, onClick = actions.save)
    }
}
