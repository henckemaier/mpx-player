package com.mpx.player.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.mpx.player.R
import com.mpx.player.model.UserSettings

@Immutable
data class Phosphor(val name: String, val accent: Color, val dim: Color, val line: Color)

val Phosphors = listOf(
    Phosphor("PHOSPHOR", Color(0xFF3DFF8F), Color(0xFF27A35C), Color(0xFF1F5C36)),
    Phosphor("AMBER", Color(0xFFFFB000), Color(0xFFB37B00), Color(0xFF5C4000)),
    Phosphor("ICE", Color(0xFF4DE8FF), Color(0xFF2A9DB0), Color(0xFF1A5560)),
    Phosphor("MONO", Color(0xFFE6E6E6), Color(0xFF9A9A9A), Color(0xFF444444)),
)

/** Índice sentinela de tema para "cor personalizada" (a cor vem de UserSettings.customColor). */
const val CUSTOM_THEME = -1

/** Monta um fósforo a partir de uma cor de destaque qualquer, derivando dim/line por escurecimento. */
fun customPhosphor(accent: Color) = Phosphor(
    name = "CUSTOM",
    accent = accent,
    dim = lerp(accent, Color.Black, 0.38f),
    line = lerp(accent, Color.Black, 0.64f),
)

@Immutable
data class MpxColors(
    val accent: Color,
    val dim: Color,
    val line: Color,
    val bg: Color,
    /** Fundo das capas (#061209 no tema verde). */
    val panel: Color,
    /** Segmento apagado (#0C2416). */
    val segOff: Color,
    /** Divisória tracejada (#12361F). */
    val dash: Color,
    /** Texto sobre o destaque (#031006). */
    val onAccent: Color,
    /** Destaque mais claro (#9DFFC4 / #C8FFE0). */
    val bright: Color,
    val themeName: String,
)

@Immutable
data class MpxStyle(
    val c: MpxColors,
    val display: FontFamily,
    /** VT323 é bem menor que as outras fontes no mesmo tamanho; compensa ao trocar a fonte. */
    val displayScale: Float,
    val mono: FontFamily,
    val glow: Boolean,
    val cursor: Boolean,
    val scanlines: Boolean,
    val coverStyle: Int,
    val monoCovers: Boolean,
)

val Vt323 = FontFamily(Font(R.font.vt323))
val JetBrainsMono = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_semibold, FontWeight.SemiBold),
)

fun colorsFor(theme: Int, oled: Boolean, customArgb: Int? = null): MpxColors {
    val p = if (theme == CUSTOM_THEME && customArgb != null) customPhosphor(Color(customArgb))
    else Phosphors.getOrElse(theme) { Phosphors[0] }
    val bg = if (oled) Color.Black else Color(0xFF040704)
    return MpxColors(
        accent = p.accent,
        dim = p.dim,
        line = p.line,
        bg = bg,
        panel = lerp(bg, p.accent, 0.03f),
        segOff = lerp(bg, p.accent, 0.12f),
        dash = lerp(bg, p.accent, 0.18f),
        onAccent = lerp(Color.Black, p.accent, 0.06f),
        bright = lerp(p.accent, Color.White, 0.55f),
        themeName = p.name,
    )
}

val LocalMpx = staticCompositionLocalOf {
    MpxStyle(colorsFor(0, false), Vt323, 1f, JetBrainsMono, glow = true, cursor = true, scanlines = true, coverStyle = 0, monoCovers = true)
}

/** True quando a tela está deitada; definido no shell e lido pelas telas para o layout de paisagem. */
val LocalLandscape = staticCompositionLocalOf { false }

@Composable
fun MpxTheme(settings: UserSettings, content: @Composable () -> Unit) {
    val style = remember(settings) {
        MpxStyle(
            c = colorsFor(settings.theme, settings.oled, settings.customColor),
            display = when (settings.font) {
                1 -> JetBrainsMono
                2 -> FontFamily.Monospace
                else -> Vt323
            },
            displayScale = if (settings.font == 0) 1f else 0.66f,
            mono = JetBrainsMono,
            glow = settings.glow,
            cursor = settings.cursor,
            scanlines = settings.scanlines,
            coverStyle = settings.cover,
            monoCovers = settings.monoCovers,
        )
    }
    CompositionLocalProvider(LocalMpx provides style, content = content)
}

object Mpx {
    val style: MpxStyle @Composable get() = LocalMpx.current
    val colors: MpxColors @Composable get() = LocalMpx.current.c
    val landscape: Boolean @Composable get() = LocalLandscape.current
}
