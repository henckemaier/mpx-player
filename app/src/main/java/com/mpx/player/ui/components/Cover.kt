package com.mpx.player.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.mpx.player.MpxApp
import com.mpx.player.data.CoverArt
import com.mpx.player.model.Track
import com.mpx.player.ui.theme.Mpx
import com.mpx.player.ui.theme.MpxColors
import androidx.compose.ui.platform.LocalContext
import kotlin.math.abs
import kotlin.math.min

/**
 * Capa conforme CFG > ESTILO DA CAPA: 0 DISCO (vetorial gerada), 1 DITHER (arte real 1-bit),
 * 2 ASCII. Sem arte embutida, cai na capa gerada.
 */
@Composable
fun Cover(track: Track?, modifier: Modifier = Modifier, disc: Boolean = false, scanlines: Boolean = false) {
    val st = Mpx.style
    val c = st.c
    val loader = (LocalContext.current.applicationContext as MpxApp).container.covers
    val art by produceState<CoverArt?>(null, track?.albumId, st.coverStyle, st.monoCovers, c.accent, c.bg) {
        value = if (track == null || st.coverStyle == 0) null
        else loader.load(track.albumId, track.uri, st.coverStyle, st.monoCovers, c.accent.toArgb(), c.panel.toArgb())
    }
    Box(modifier.background(c.panel), contentAlignment = Alignment.Center) {
        when (val a = art) {
            is CoverArt.Image -> Image(
                bitmap = a.bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                filterQuality = if (a.pixelated) FilterQuality.None else FilterQuality.Medium,
            )
            is CoverArt.Ascii -> AsciiArt(a.rows)
            null -> GeneratedCover(seed = track?.album ?: "", title = track?.album ?: "MPX", disc = disc, colors = c)
        }
        if (scanlines) Box(Modifier.fillMaxSize().scanlines(true))
    }
}

@Composable
private fun AsciiArt(rows: List<String>) {
    val c = Mpx.colors
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val density = LocalDensity.current
        val cols = rows.firstOrNull()?.length ?: 1
        // largura do glifo mono ≈ 0,6 em
        val fontPx = min(constraints.maxWidth / (cols * 0.6f), constraints.maxHeight / (rows.size * 1.0f))
        val fs = with(density) { fontPx.toSp() }
        BasicText(
            rows.joinToString("\n"),
            style = TextStyle(color = c.accent, fontSize = fs, lineHeight = fs, fontFamily = Mpx.style.mono, textAlign = TextAlign.Center),
        )
    }
}

/** Seis desenhos inspirados nas capas do protótipo; a variante vem do hash do álbum. */
@Composable
fun GeneratedCover(seed: String, title: String, disc: Boolean, colors: MpxColors) {
    val measurer = rememberTextMeasurer()
    val variant = if (disc) 0 else abs(seed.hashCode()) % 6
    val initials = title.split(' ', '_', '-').filter { it.isNotBlank() }.take(2)
        .joinToString("") { it.take(2) }.uppercase().take(4).ifEmpty { "MPX" }
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        // sistema de coordenadas do protótipo: 160 x 112 (ou 160 x 160 no disco)
        val vbH = if (disc) 160f else 112f
        val k = maxOf(w / 160f, h / vbH)
        val ox = (w - 160f * k) / 2f
        val oy = (h - vbH * k) / 2f
        if (variant == 5) {
            // texto desenhado em pixels reais (fora da escala) para não borrar
            val small = TextStyle(color = colors.dim, fontSize = (9f * k).toSp())
            listOf(16f to "01001010 11010010", 28f to "10110100 00101101", 40f to "01101001 10010110", 100f to "11100010 01011010")
                .forEach { (y, line) -> drawText(measurer, line, Offset(ox + 8f * k, oy + (y - 9f) * k), small) }
            val big = measurer.measure(initials, TextStyle(color = colors.accent, fontSize = (34f * k).toSp(), fontWeight = FontWeight.Bold))
            drawText(big, topLeft = Offset(w / 2f - big.size.width / 2f, oy + 64f * k - big.size.height / 2f))
        }
        translate(ox, oy) {
            scale(k, k, pivot = Offset.Zero) {
                when (variant) {
                    0 -> drawDisc(colors, vbH, big = disc)
                    1 -> drawGrid(colors)
                    2 -> drawWaves(colors)
                    3 -> drawTriangle(colors)
                    4 -> drawPixels(colors)
                    else -> Unit
                }
            }
        }
    }
}

private fun DrawScope.dots(c: MpxColors, step: Float, dot: Float, color: Color, alpha: Float, w: Float, h: Float) {
    var y = 0f
    while (y < h) {
        var x = 0f
        while (x < w) {
            drawRect(color.copy(alpha = alpha), Offset(x, y), Size(dot, dot)); x += step
        }
        y += step
    }
}

private fun DrawScope.drawDisc(c: MpxColors, vbH: Float, big: Boolean) {
    dots(c, 4f, 1.4f, c.accent, 0.35f, 160f, vbH)
    val cy = vbH / 2f
    val center = Offset(80f, cy)
    val r = if (big) 62f else 44f
    drawCircle(c.bg, r, center)
    drawCircle(c.accent, r, center, style = Stroke(1.5f))
    val rings = if (big) listOf(52f, 44f, 36f) else listOf(32f)
    rings.forEach { drawCircle(c.dim, it, center, style = Stroke(0.8f)) }
    drawCircle(c.accent, if (big) 20f else 14f, center)
    drawCircle(c.bg, if (big) 4f else 3f, center)
    if (big) drawArc(c.bright, -90f, 55f, false, Offset(80f - r, cy - r), Size(2 * r, 2 * r), style = Stroke(2f))
}

private fun DrawScope.drawGrid(c: MpxColors) {
    listOf(70f, 80f, 92f, 106f).forEach { drawLine(c.dim, Offset(0f, it), Offset(160f, it), 0.8f) }
    listOf(0f, 40f, 80f, 120f, 160f).forEach { drawLine(c.dim, Offset(80f, 62f), Offset(it, 112f), 0.8f) }
    drawCircle(c.accent, 22f, Offset(80f, 44f), style = Stroke(1.5f))
    drawLine(c.accent, Offset(58f, 44f), Offset(102f, 44f), 1f)
    drawLine(c.accent, Offset(62f, 36f), Offset(98f, 36f), 1f)
    drawLine(c.accent, Offset(62f, 52f), Offset(98f, 52f), 1f)
}

private fun DrawScope.drawWaves(c: MpxColors) {
    listOf(30f, 46f, 62f, 78f, 94f).forEachIndexed { i, y ->
        val p = Path().apply {
            moveTo(0f, y)
            var x = 0f
            var up = true
            while (x < 160f) {
                quadraticTo(x + 20f, if (up) y - 20f else y + 20f, x + 40f, y)
                x += 40f; up = !up
            }
        }
        drawPath(p, if (i % 2 == 0) c.accent else c.dim, style = Stroke(1f))
    }
}

private fun DrawScope.drawTriangle(c: MpxColors) {
    var y = 0f
    while (y < 112f) {
        var x = 0f
        while (x < 160f) {
            drawRect(c.dim.copy(alpha = 0.6f), Offset(x, y), Size(2f, 2f)); x += 6f
        }
        y += 6f
    }
    val outer = Path().apply { moveTo(80f, 14f); lineTo(124f, 94f); lineTo(36f, 94f); close() }
    drawPath(outer, c.bg)
    drawPath(outer, c.accent, style = Stroke(1.5f))
    drawPath(Path().apply { moveTo(80f, 44f); lineTo(100f, 80f); lineTo(60f, 80f); close() }, c.accent)
}

private fun DrawScope.drawPixels(c: MpxColors) {
    fun r(x: Float, y: Float, w: Float, h: Float, a: Float = 1f) = drawRect(c.accent.copy(alpha = a), Offset(x, y), Size(w, h))
    r(56f, 24f, 8f, 8f); r(96f, 24f, 8f, 8f)
    r(64f, 32f, 32f, 8f); r(56f, 40f, 48f, 8f, 0.5f)
    r(48f, 48f, 64f, 8f); r(48f, 56f, 8f, 16f); r(104f, 56f, 8f, 16f)
    r(64f, 56f, 8f, 8f); r(88f, 56f, 8f, 8f)
    r(64f, 72f, 32f, 8f)
    var x = 0f
    while (x < 160f) {
        drawLine(c.dim, Offset(x, 92f), Offset(x + 2f, 92f), 1f); x += 5f
    }
}
