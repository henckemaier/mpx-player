package com.mpx.player.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mpx.player.BuildConfig
import com.mpx.player.ui.theme.Mpx

// Blocos CRT que ficam no :app (dependem de Android/BuildConfig ou são navegação específica do
// app). Os primitivos portáveis (Txt, frame, tap, glow, InvertedBar, SegmentBar, MpxButton,
// IconButton, TextButton, blinkingCursor, scanlines, formatTime) foram para :shared/commonMain
// (CrtKit.kt), no MESMO pacote `com.mpx.player.ui.components` — por isso são usados aqui sem import.

// ---------------------------------------------------------------- blocos

@Composable
fun MpxHeader(right: @Composable RowScope.() -> Unit) {
    val c = Mpx.colors
    Row(
        Modifier
            .fillMaxWidth()
            .frame()
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Txt("MPX//OS (TM) v${BuildConfig.VERSION_NAME}", size = 11.sp, color = c.dim, letterSpacing = 1.sp)
        right()
    }
}

@Composable
fun HeaderLabel(text: String) = Txt(text, size = 11.sp, color = Mpx.colors.dim, letterSpacing = 1.sp)

enum class Tab(val label: String) { PLAY("PLAY"), LIB("LIB"), QUEUE("QUEUE"), EQ("EQ"), CFG("CFG") }

@Composable
fun MpxNav(selected: Tab, onSelect: (Tab) -> Unit) {
    val c = Mpx.colors
    Row(Modifier.fillMaxWidth().height(44.dp).frame()) {
        Tab.entries.forEachIndexed { i, tab ->
            val on = tab == selected
            if (i > 0) Box(Modifier.width(1.dp).fillMaxHeight().background(c.line))
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(if (on) c.accent else Color.Transparent)
                    .tap { onSelect(tab) },
                contentAlignment = Alignment.Center,
            ) {
                Txt(tab.label, size = 20.sp, display = true, color = if (on) c.onAccent else c.accent)
            }
        }
    }
}

/** Navegação vertical (barra lateral) para o modo paisagem. Mesmas abas/realce do [MpxNav]. */
@Composable
fun MpxNavRail(selected: Tab, onSelect: (Tab) -> Unit) {
    val c = Mpx.colors
    Column(Modifier.width(60.dp).fillMaxHeight().frame()) {
        Tab.entries.forEachIndexed { i, tab ->
            val on = tab == selected
            if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(if (on) c.accent else Color.Transparent)
                    .tap { onSelect(tab) },
                contentAlignment = Alignment.Center,
            ) {
                Txt(tab.label, size = 16.sp, display = true, color = if (on) c.onAccent else c.accent, align = TextAlign.Center)
            }
        }
    }
}

@Composable
fun Panel(
    modifier: Modifier = Modifier,
    border: Color = Mpx.colors.line,
    padding: Dp = 10.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier.border(1.dp, border).padding(padding), content = content)
}

/** Linha "[X] NOME ........ VALOR" usada em EQ e CFG. */
@Composable
fun ToggleRow(name: String, checked: Boolean, value: String, onClick: () -> Unit, minHeight: Dp = 44.dp) {
    val c = Mpx.colors
    Row(
        Modifier
            .fillMaxWidth()
            .height(minHeight)
            .tap(Role.Checkbox, onClick)
            .padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Txt("[${if (checked) "X" else " "}] $name", size = 12.sp)
        Txt(value, size = 12.sp, color = c.dim)
    }
    DashedDivider()
}

@Composable
fun DashedDivider() {
    val c = Mpx.colors
    Canvas(Modifier.fillMaxWidth().height(1.dp)) {
        val dash = 3.dp.toPx()
        var x = 0f
        while (x < size.width) {
            drawLine(c.dash, Offset(x, 0f), Offset((x + dash).coerceAtMost(size.width), 0f), strokeWidth = size.height)
            x += dash * 2
        }
    }
}
