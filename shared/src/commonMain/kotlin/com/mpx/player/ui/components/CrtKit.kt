package com.mpx.player.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mpx.player.ui.theme.Mpx
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

// Primitivos CRT portáveis (commonMain) consumidos pelas telas e pelo NowPlayingPanel do porte
// iOS. DashedDivider migrou p/ cá na fatia LIBRARY; ToggleRow na fatia SETTINGS (puro; usado por
// EqScreen (:app) e SettingsScreen comum); Panel + fmtThousands na fatia BOOT — com isso o Crt.kt
// do :app esvaziou e foi REMOVIDO (todo o chrome de nav — MpxHeader/Tab/MpxNav — já tinha migrado
// p/ Chrome.kt na fatia NAV). Zero bloco CRT sobrou no :app.

// ---------------------------------------------------------------- texto

@Composable
fun Txt(
    text: String,
    modifier: Modifier = Modifier,
    size: TextUnit = 12.sp,
    color: Color = Mpx.colors.accent,
    display: Boolean = false,
    glow: Boolean = false,
    align: TextAlign = TextAlign.Start,
    maxLines: Int = 1,
    lineHeight: TextUnit = TextUnit.Unspecified,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    family: FontFamily? = null,
) {
    val st = Mpx.style
    val finalSize = if (display) (size.value * st.displayScale).sp else size
    BasicText(
        text = text,
        modifier = modifier,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        style = TextStyle(
            color = color,
            fontSize = finalSize,
            fontFamily = family ?: if (display) st.display else st.mono,
            textAlign = align,
            lineHeight = lineHeight,
            letterSpacing = letterSpacing,
            shadow = if (glow && st.glow) Shadow(color.copy(alpha = 0.6f), Offset.Zero, 18f) else null,
        ),
    )
}

/** "_" piscando como cursor de terminal (respeita a opção CURSOR PISCANTE). */
@Composable
fun blinkingCursor(): String {
    val enabled = Mpx.style.cursor
    var on by remember { mutableStateOf(true) }
    LaunchedEffect(enabled) {
        if (!enabled) return@LaunchedEffect
        while (true) {
            delay(530); on = !on
        }
    }
    return if (!enabled || on) "_" else " "
}

// ---------------------------------------------------------------- toque

fun Modifier.tap(role: Role = Role.Button, onClick: () -> Unit): Modifier =
    this.clickable(interactionSource = null, indication = null, role = role, onClick = onClick)

@Composable
fun Modifier.frame(color: Color = Mpx.colors.line): Modifier = this.border(1.dp, color)

/** Linhas de varredura CRT por cima de tudo (1dp escuro a cada 3dp). */
fun Modifier.scanlines(enabled: Boolean): Modifier = if (!enabled) this else drawWithContent {
    drawContent()
    val step = 3.dp.toPx()
    val h = 1.dp.toPx().coerceAtLeast(1f)
    var y = 0f
    val c = Color.Black.copy(alpha = 0.22f)
    while (y < size.height) {
        drawRect(c, topLeft = Offset(0f, y), size = androidx.compose.ui.geometry.Size(size.width, h))
        y += step
    }
}

/** Halo suave atrás de blocos acesos (equivalente ao box-shadow do protótipo). */
fun Modifier.glow(color: Color, radius: Dp = 14.dp): Modifier = drawWithContent {
    val r = radius.toPx()
    drawRect(
        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
            listOf(color.copy(alpha = 0f), color.copy(alpha = 0.18f), color.copy(alpha = 0f)),
            startY = -r, endY = size.height + r,
        ),
        topLeft = Offset(-r, -r),
        size = androidx.compose.ui.geometry.Size(size.width + 2 * r, size.height + 2 * r),
    )
    drawContent()
}

// ---------------------------------------------------------------- blocos

/** Faixa invertida "> NOW PLAYING  ...  TRK 04/12". */
@Composable
fun InvertedBar(left: String, right: String = "", glow: Boolean = true, trailing: (@Composable () -> Unit)? = null) {
    val c = Mpx.colors
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (glow && Mpx.style.glow) Modifier.glow(c.accent) else Modifier)
            .background(c.accent)
            .padding(start = 10.dp, end = if (trailing != null) 0.dp else 10.dp)
            .height(30.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Txt(left, size = 22.sp, display = true, color = c.onAccent)
        if (trailing != null) trailing() else Txt(right, size = 22.sp, display = true, color = c.onAccent)
    }
}

/**
 * Botão retangular de terminal. filled = fundo aceso. Ao pressionar, inverte as cores.
 */
@Composable
fun MpxButton(
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    border: Color? = null,
    contentColor: Color? = null,
    onClick: () -> Unit,
    content: @Composable BoxScope.(fg: Color) -> Unit,
) {
    val c = Mpx.colors
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val lit = filled != pressed
    val fg = if (lit) c.onAccent else (contentColor ?: c.accent)
    Box(
        modifier
            .background(if (lit) c.accent else c.bg)
            .border(1.dp, border ?: if (filled) c.accent else c.line)
            .clickable(interactionSource = src, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content(fg) }
}

@Composable
fun TextButton(
    text: String,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    size: TextUnit = 12.sp,
    display: Boolean = false,
    border: Color? = null,
    onClick: () -> Unit,
) = MpxButton(modifier, filled, border, onClick = onClick) { fg ->
    Txt(text, Modifier.padding(horizontal = 10.dp), size = size, display = display, color = fg, align = TextAlign.Center)
}

@Composable
fun IconButton(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    border: Color? = null,
    tint: Color? = null,
    iconSize: Dp = 24.dp,
    onClick: () -> Unit,
) = MpxButton(modifier, filled, border, tint, onClick) { fg ->
    Image(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier.size(iconSize),
        colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(fg),
    )
}

/**
 * Barra de segmentos acesos/apagados. Se [onSeek] for informado, toque/arraste devolve a fração 0..1.
 */
@Composable
fun SegmentBar(
    count: Int,
    lit: Int,
    modifier: Modifier = Modifier,
    height: Dp = 16.dp,
    onColor: Color = Mpx.colors.accent,
    onSeek: ((Float) -> Unit)? = null,
) {
    val c = Mpx.colors
    Row(
        modifier
            .fillMaxWidth()
            .height(height)
            .frame()
            .then(
                if (onSeek == null) Modifier else Modifier.pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        onSeek((down.position.x / size.width).coerceIn(0f, 1f))
                        while (true) {
                            val ev = awaitPointerEvent()
                            val p = ev.changes.firstOrNull() ?: break
                            if (!p.pressed) break
                            onSeek((p.position.x / size.width).coerceIn(0f, 1f))
                            p.consume()
                        }
                    }
                },
            )
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        repeat(count) { i ->
            Box(Modifier.weight(1f).fillMaxHeight().background(if (i < lit) onColor else c.segOff))
        }
    }
}

/**
 * Painel emoldurado simples (borda 1dp + padding). Migrou do Crt.kt (:app) na fatia BOOT — é puro
 * (Box/border/padding), consumido pela BootScreen comum (@ECHO/LOGO e o log de boot). MESMO FQN
 * `com.mpx.player.ui.components.Panel`, 1 definição.
 */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    border: Color = Mpx.colors.line,
    padding: Dp = 10.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier.border(1.dp, border).padding(padding), content = content)
}

// String.format/.format() é JVM-only (não existe em common). Reescrito com padStart
// multiplataforma, MANTENDO o formato exato: horas sem pad, min/seg com pad2.
fun formatTime(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    val mm = (s / 60) % 60
    val ss = s % 60
    return if (s >= 3600) "${s / 3600}:${mm.p2()}:${ss.p2()}"
    else "${(s / 60).p2()}:${ss.p2()}"
}

private fun Long.p2(): String = toString().padStart(2, '0')

// String.format("%.1f", x) é JVM-only (não existe em common). Formata com 1 casa decimal
// multiplataforma. Arredonda meio-pra-cima (roundToInt) e evita "-0.0" (sinal só se o valor
// arredondado for != 0). Usado pelo rótulo de PREAMP no EqScreen comum.
fun fmt1(x: Double): String {
    val r = (kotlin.math.abs(x) * 10).roundToInt()
    val sign = if (x < 0 && r != 0) "-" else ""
    return "$sign${r / 10}.${r % 10}"
}

// "%,d".format(n).replace(',', '.') é JVM-only (String.format). Agrupa os dígitos de 3 em 3 com
// '.' em Kotlin puro (multiplataforma), MANTENDO o formato exato: 0 -> "0", 999 -> "999",
// 12345 -> "12.345", 1234567 -> "1.234.567". [n] é sempre >= 0 (contagem) → sem tratamento de sinal.
// Usado pelo rótulo "> INDEX n FILES" do log de boot (BootScreen comum).
fun fmtThousands(n: Int): String {
    val s = n.toString()
    if (s.length <= 3) return s
    val groups = ArrayList<String>()
    val first = s.length % 3
    if (first > 0) groups.add(s.substring(0, first))
    var i = first
    while (i < s.length) {
        groups.add(s.substring(i, i + 3))
        i += 3
    }
    return groups.joinToString(".")
}

/** Divisória tracejada (listas de FAIXAS/ARTISTAS e ToggleRow). Puro — migrou do Crt.kt (:app). */
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

/**
 * Linha "[X] NOME ........ VALOR" usada em EQ e CFG. Puro — migrou do Crt.kt (:app) na fatia
 * SETTINGS (a SettingsScreen comum precisa dela no commonMain; EqScreen (:app) resolve pelo FQN).
 */
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
