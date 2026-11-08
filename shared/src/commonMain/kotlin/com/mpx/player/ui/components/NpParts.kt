package com.mpx.player.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mpx.player.data.CoverArt
import com.mpx.player.shared.playback.RepeatMode
import com.mpx.player.ui.NowPlayingActions
import com.mpx.player.ui.PlayerUi
import com.mpx.player.ui.TechInfo
import com.mpx.player.ui.theme.Mpx
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * Sub-composables COMPARTILHADOS do Now Playing (RETRATO), extraídos p/ commonMain na fatia
 * NowPlaying-cheia do porte iOS (P7). ANTES eram cópias fiéis duplicadas em dois lugares:
 * [com.mpx.player.ui.NowPlayingPanel] (unidade do gate A/B 'nowplaying') e
 * [com.mpx.player.ui.screens.NowPlayingScreen] (tela cheia). Agora há UMA definição de cada
 * (mesmo FQN) — zero duplicata. O render é BYTE-A-BYTE o de antes nos dois chamadores:
 *  - [NpCoverMeta] carrega o parâmetro [CoverArt.Image]? do painel (A/B). Quando null (tela e host
 *    LIVE) cai no [Cover] assíncrono normal — idêntico ao que a tela fazia.
 *  - [NpTransport] ganha um slot `trailing` opcional: o PAINEL não passa nada (5 botões, igual ao
 *    gate A/B); a TELA passa o Bookmark como 6º botão (mesmo weight), igual a hoje.
 * Primitivos (Cover/IconButton/Txt/SegmentBar/InvertedBar/frame/formatTime/blinkingCursor/scanlines)
 * são do mesmo pacote (CrtKit/Cover/Icons) — sem import.
 */

/** "TRK 04/12" — common-safe (sem String.format, que é JVM-only; zero-pad via padStart). */
internal fun trkLabel(ui: PlayerUi): String =
    if (ui.queue.isEmpty()) "TRK --/--"
    else "TRK ${(ui.index + 1).toString().padStart(2, '0')}/${ui.queue.size.toString().padStart(2, '0')}"

/**
 * Row emoldurado: capa 128dp (disco + scanlines) à esquerda; ART/ALB em cima e FMT/RATE/extra
 * embaixo. Quando [sampleCover] != null (gate A/B) desenha a capa pré-resolvida direto; senão usa
 * o [Cover] assíncrono normal (caminho da tela cheia e do host LIVE).
 */
@Composable
internal fun NpCoverMeta(ui: PlayerUi, tech: TechInfo, sampleCover: CoverArt.Image? = null) {
    val c = Mpx.colors
    val t = ui.current
    Row(Modifier.fillMaxWidth().frame().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        val coverMod = Modifier.size(128.dp).frame(c.accent)
        if (sampleCover != null) CoverImage(sampleCover, coverMod, scanlines = true)
        else Cover(t, coverMod, disc = true, scanlines = true)
        Column(Modifier.height(128.dp).weight(1f), verticalArrangement = Arrangement.SpaceBetween) {
            Column {
                Txt("ART -", size = 12.sp, color = c.dim, lineHeight = 17.sp)
                Txt(t?.artist?.uppercase() ?: "---", size = 12.sp, lineHeight = 17.sp)
                Txt("ALB -", size = 12.sp, color = c.dim, lineHeight = 17.sp)
                Txt(t?.album?.uppercase() ?: "---", size = 12.sp, lineHeight = 17.sp)
            }
            Column {
                Txt("FMT - ${tech.format} ${tech.bitrate}", size = 12.sp, color = c.dim, lineHeight = 17.sp)
                Txt("RATE - ${tech.sampleRate}", size = 12.sp, color = c.dim, lineHeight = 17.sp)
                Txt(tech.extra.ifEmpty { "---" }, size = 12.sp, color = c.dim, lineHeight = 17.sp)
            }
        }
    }
}

/**
 * Desenha uma [CoverArt.Image] já resolvida, espelhando o visual do [Cover] (fundo painel, Image
 * Crop com nearest-neighbor quando pixelated, scanlines por cima). Usado só pelo caminho do A/B,
 * onde a capa é pré-resolvida fora de composição (render de 1 frame).
 */
@Composable
private fun CoverImage(art: CoverArt.Image, modifier: Modifier, scanlines: Boolean) {
    val c = Mpx.colors
    Box(modifier.background(c.panel), contentAlignment = Alignment.Center) {
        Image(
            bitmap = art.bitmap,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            filterQuality = if (art.pixelated) FilterQuality.None else FilterQuality.Medium,
        )
        if (scanlines) Box(Modifier.fillMaxSize().scanlines(true))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun NpTitle(ui: PlayerUi) {
    val c = Mpx.colors
    val t = ui.current
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        // Título grande VT323 com glow=true: o Shadow(blurRadius=18f) é o FOCO do A/B no Skia.
        Txt(
            (t?.title?.uppercase()?.replace(' ', '_') ?: "NO_SIGNAL") + blinkingCursor(),
            Modifier.fillMaxWidth().basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 2_000),
            size = 40.sp, display = true, glow = true, lineHeight = 40.sp,
        )
        Txt(
            t?.dosPath ?: "C:\\MUSIC\\> _",
            Modifier.fillMaxWidth().basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 3_000),
            size = 12.sp, color = c.dim,
        )
    }
}

@Composable
internal fun NpProgress(ui: PlayerUi, actions: NowPlayingActions) {
    val c = Mpx.colors
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val frac = if (ui.durationMs > 0) (ui.positionMs.toFloat() / ui.durationMs).coerceIn(0f, 1f) else 0f
        SegmentBar(24, (frac * 24).roundToInt(), onSeek = actions.seek)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Txt(formatTime(ui.positionMs), size = 20.sp, display = true)
            Txt("[${(frac * 100).roundToInt()}%]", size = 20.sp, display = true, color = c.dim)
            Txt(formatTime(ui.durationMs), size = 20.sp, display = true)
        }
    }
}

/**
 * Transporte (shuffle/prev/play-pause/next/repeat). O slot [trailing] opcional recebe o Modifier
 * de peso (`weight(1f).fillMaxHeight()`) do botão: o PAINEL não passa nada (5 botões, igual ao gate
 * A/B); a TELA passa o Bookmark como 6º botão (mesmo weight), igual a hoje.
 */
@Composable
internal fun NpTransport(
    ui: PlayerUi,
    actions: NowPlayingActions,
    trailing: (@Composable RowScope.(Modifier) -> Unit)? = null,
) {
    val c = Mpx.colors
    Row(Modifier.fillMaxWidth().height(50.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        val m = Modifier.weight(1f).fillMaxHeight()
        IconButton(
            MpxIcons.Shuffle, m, border = if (ui.shuffle) c.accent else c.line,
            tint = if (ui.shuffle) c.accent else c.dim, iconSize = 22.dp, onClick = actions.shuffle,
        )
        IconButton(MpxIcons.Prev, m, border = c.accent, onClick = actions.previous)
        IconButton(if (ui.isPlaying) MpxIcons.Pause else MpxIcons.Play, m, filled = true, onClick = actions.toggle)
        IconButton(MpxIcons.Next, m, border = c.accent, onClick = actions.next)
        IconButton(
            if (ui.repeat == RepeatMode.ONE) MpxIcons.RepeatOne else MpxIcons.Repeat, m,
            border = if (ui.repeat != RepeatMode.OFF) c.accent else c.line,
            tint = if (ui.repeat != RepeatMode.OFF) c.accent else c.dim, iconSize = 22.dp, onClick = actions.repeat,
        )
        trailing?.invoke(this, m)
    }
}

private val SPECTRUM_BASE = intArrayOf(38, 62, 80, 55, 90, 72, 48, 66, 85, 58, 40, 70, 92, 64, 50, 76, 60, 44, 68, 82, 56, 36, 52, 46, 30, 42, 26, 18)

@Composable
internal fun Spectrum(playing: Boolean) {
    val c = Mpx.colors
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(playing) {
        while (playing) {
            delay(110); tick++
        }
    }
    Column(Modifier.fillMaxWidth().frame().padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Txt(":: SPECTRUM", size = 11.sp, color = c.dim)
            val l = if (playing) 4 + (tick % 3 == 0).compareTo(false) else 0
            val r = if (playing) 3 + ((tick / 2) % 2) else 0
            Txt("L ${"▮".repeat(l)}${"▯".repeat(6 - l)} R ${"▮".repeat(r)}${"▯".repeat(6 - r)}", size = 11.sp, color = c.dim)
        }
        Canvas(Modifier.fillMaxWidth().height(36.dp)) {
            val n = SPECTRUM_BASE.size
            val gap = 3.dp.toPx()
            val bw = (size.width - gap * (n - 1)) / n
            val seg = 3.dp.toPx()
            val segGap = 2.dp.toPx()
            for (i in 0 until n) {
                val v = if (playing) (SPECTRUM_BASE[i] + ((tick * 7 + i * 13) % 23) - 11).coerceIn(8, 100) else 6
                val h = size.height * v / 100f
                var y = size.height - seg
                while (y >= size.height - h) {
                    drawRect(c.accent, Offset(i * (bw + gap), y), Size(bw, seg))
                    y -= seg + segGap
                }
            }
        }
    }
}
