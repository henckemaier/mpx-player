package com.mpx.player.spike

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

// SPIKE CMP — Fase 2. CÓPIA ADAPTADA (não compartilhada) dos efeitos CRT de maior risco
// visual de app/src/main/java/com/mpx/player/ui/components/Crt.kt + theme/MpxTheme.kt.
// Adaptações p/ commonMain: sem Mpx/CompositionLocal (SpikeStyle passado explícito), sem
// BuildConfig, sem material3, fontes via org.jetbrains.compose.resources.Font. A lógica dos
// efeitos (glow/scanlines/shadow-blur/dash/cursor/lerp) é IDÊNTICA à do :app.

// ---------------------------------------------------------------- tema (self-contained)

/** Espelha MpxColors (subset usado no spike). */
@Immutable
data class SpikeColors(
    val accent: Color,
    val dim: Color,
    val line: Color,
    val bg: Color,
    val panel: Color,
    val segOff: Color,
    val dash: Color,
    val onAccent: Color,
    val bright: Color,
)

/** Espelha MpxStyle (subset usado no spike). */
@Immutable
data class SpikeStyle(
    val c: SpikeColors,
    val display: FontFamily,
    val displayScale: Float,
    val mono: FontFamily,
    val glow: Boolean,
    val cursor: Boolean,
    val scanlines: Boolean,
)

/**
 * colorsFor(theme=0 GREEN, oled=true) do MpxTheme, derivando os tons pelo MESMO lerp.
 * accent/dim/line = Phosphor("PHOSPHOR"); bg = preto (oled).
 */
fun spikeColorsGreenOled(): SpikeColors {
    val accent = Color(0xFF3DFF8F)
    val dim = Color(0xFF27A35C)
    val line = Color(0xFF1F5C36)
    val bg = Color.Black
    return SpikeColors(
        accent = accent,
        dim = dim,
        line = line,
        bg = bg,
        panel = lerp(bg, accent, 0.03f),
        segOff = lerp(bg, accent, 0.12f),
        dash = lerp(bg, accent, 0.18f),
        onAccent = lerp(Color.Black, accent, 0.06f),
        bright = lerp(accent, Color.White, 0.55f),
    )
}

// ---------------------------------------------------------------- texto

@Composable
fun Txt(
    text: String,
    style: SpikeStyle,
    modifier: Modifier = Modifier,
    size: TextUnit = 12.sp,
    color: Color = style.c.accent,
    display: Boolean = false,
    glow: Boolean = false,
    align: TextAlign = TextAlign.Start,
    maxLines: Int = 1,
    lineHeight: TextUnit = TextUnit.Unspecified,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    family: FontFamily? = null,
) {
    val finalSize = if (display) (size.value * style.displayScale).sp else size
    BasicText(
        text = text,
        modifier = modifier,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        style = TextStyle(
            color = color,
            fontSize = finalSize,
            fontFamily = family ?: if (display) style.display else style.mono,
            textAlign = align,
            lineHeight = lineHeight,
            letterSpacing = letterSpacing,
            // FOCO do teste de fidelidade no iOS/Skia: blurRadius=18f no Shadow do texto display.
            shadow = if (glow && style.glow) Shadow(color.copy(alpha = 0.6f), Offset.Zero, 18f) else null,
        ),
    )
}

/** "_" piscando como cursor de terminal (LaunchedEffect com delay 530ms). */
@Composable
fun blinkingCursor(enabled: Boolean): String {
    var on by remember { mutableStateOf(true) }
    LaunchedEffect(enabled) {
        if (!enabled) return@LaunchedEffect
        while (true) {
            delay(530); on = !on
        }
    }
    return if (!enabled || on) "_" else " "
}

// ---------------------------------------------------------------- efeitos CRT

/** Linhas de varredura CRT por cima de tudo (1dp preto alpha 0.22 a cada 3dp). */
fun Modifier.scanlines(enabled: Boolean): Modifier = if (!enabled) this else drawWithContent {
    drawContent()
    val step = 3.dp.toPx()
    val h = 1.dp.toPx().coerceAtLeast(1f)
    var y = 0f
    val col = Color.Black.copy(alpha = 0.22f)
    while (y < size.height) {
        drawRect(col, topLeft = Offset(0f, y), size = Size(size.width, h))
        y += step
    }
}

/** Halo suave atrás de blocos acesos (Brush.verticalGradient via drawWithContent). */
fun Modifier.glow(color: Color, radius: Dp = 14.dp): Modifier = drawWithContent {
    val r = radius.toPx()
    drawRect(
        brush = Brush.verticalGradient(
            listOf(color.copy(alpha = 0f), color.copy(alpha = 0.18f), color.copy(alpha = 0f)),
            startY = -r, endY = size.height + r,
        ),
        topLeft = Offset(-r, -r),
        size = Size(size.width + 2 * r, size.height + 2 * r),
    )
    drawContent()
}

// ---------------------------------------------------------------- blocos

/** Faixa invertida "> NOW PLAYING  ...  TRK 04/12" (fundo accent + texto onAccent + glow). */
@Composable
fun InvertedBar(left: String, right: String, style: SpikeStyle, glow: Boolean = true) {
    val c = style.c
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (glow && style.glow) Modifier.glow(c.accent) else Modifier)
            .background(c.accent)
            .padding(horizontal = 10.dp)
            .height(30.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Txt(left, style, size = 22.sp, display = true, color = c.onAccent)
        Txt(right, style, size = 22.sp, display = true, color = c.onAccent)
    }
}

/** Barra de segmentos acesos/apagados. onSeek opcional (não tocado no screenshot). */
@Composable
fun SegmentBar(
    count: Int,
    lit: Int,
    style: SpikeStyle,
    modifier: Modifier = Modifier,
    height: Dp = 16.dp,
    onSeek: ((Float) -> Unit)? = null,
) {
    val c = style.c
    Row(
        modifier
            .fillMaxWidth()
            .height(height)
            .border(1.dp, c.line)
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
            Box(Modifier.weight(1f).fillMaxHeight().background(if (i < lit) c.accent else c.segOff))
        }
    }
}

/** Divisória tracejada (Canvas.drawLine tracejado). */
@Composable
fun DashedDivider(style: SpikeStyle) {
    val c = style.c
    Canvas(Modifier.fillMaxWidth().height(1.dp)) {
        val dash = 3.dp.toPx()
        var x = 0f
        while (x < size.width) {
            drawLine(c.dash, Offset(x, 0f), Offset((x + dash).coerceAtMost(size.width), 0f), strokeWidth = size.height)
            x += dash * 2
        }
    }
}
