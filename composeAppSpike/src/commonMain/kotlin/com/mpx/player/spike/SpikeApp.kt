package com.mpx.player.spike

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mpx.player.spike.resources.Res
import com.mpx.player.spike.resources.jetbrains_mono_regular
import com.mpx.player.spike.resources.vt323
import org.jetbrains.compose.resources.Font

/**
 * SPIKE CMP — Fase 2. Bloco REPRESENTATIVO do topo da NowPlaying, concentrando os efeitos CRT
 * de maior risco visual no iOS/Skia: InvertedBar com glow(), Txt display (VT323) com
 * shadow blurRadius=18f, Modifier.scanlines() no painel inteiro, SegmentBar, DashedDivider e
 * uma linha com cursor piscante. Fonte, cor e lógica COPIADAS do :app (não compartilhadas).
 *
 * glow + cursor LIGADOS, displayScale=1f (VT323), phosphor GREEN, bg OLED preto.
 */
@Composable
fun CrtSpikeRoot() {
    // Fontes via composeResources (VT323 display; JetBrains Mono p/ texto mono pequeno).
    val display = FontFamily(Font(Res.font.vt323))
    val mono = FontFamily(Font(Res.font.jetbrains_mono_regular))
    val style = remember(display, mono) {
        SpikeStyle(
            c = spikeColorsGreenOled(),
            display = display,
            displayScale = 1f,
            mono = mono,
            glow = true,
            cursor = true,
            scanlines = true,
        )
    }
    val c = style.c
    val cursor = blinkingCursor(style.cursor)

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .scanlines(style.scanlines),
    ) {
        Column(
            Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            InvertedBar("> NOW PLAYING", "TRK 04/12", style)

            // Título + artista (display VT323 com glow — o blur é o foco do teste).
            Txt("NEON HORIZON", style, size = 34.sp, display = true, glow = true, color = c.bright)
            Txt("THE CATHODE RAYS", style, size = 22.sp, display = true, glow = true, color = c.accent)

            SegmentBar(count = 32, lit = 11, style = style, onSeek = {})

            DashedDivider(style)

            // Linha mono pequena com cursor piscante.
            Txt("MPX//OS (TM) v0.1 > READY$cursor", style, size = 12.sp, color = c.dim, letterSpacing = 1.sp)
        }
    }
}
