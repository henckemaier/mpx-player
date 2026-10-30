package com.mpx.player.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Ícones do protótipo, com os mesmos paths SVG (viewBox 24x24). */
object MpxIcons {
    private fun filled(name: String, d: String) = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        .addPath(pathData = addPathNodes(d), fill = SolidColor(Color.White))
        .build()

    private fun stroked(name: String, d: String) = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        .addPath(
            pathData = addPathNodes(d),
            stroke = SolidColor(Color.White),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Square,
        )
        .build()

    val Play = filled("play", "M7 4v16l13-8z")
    val Pause = filled("pause", "M6 5h4v14H6zM14 5h4v14h-4z")
    val Prev = filled("prev", "M5 5h2v14H5zM20 5v14L9 12z")
    val Next = filled("next", "M17 5h2v14h-2zM4 5v14l11-7z")
    val Shuffle = stroked("shuffle", "M3 6h4l10 12h4M3 18h4l3-3.6M14 9.6 17 6h4M18 3l3 3-3 3M18 15l3 3-3 3")
    val Repeat = stroked("repeat", "M4 11V8h14M15 5l3 3-3 3M20 13v3H6M9 19l-3-3 3-3")
    val RepeatOne = stroked("repeat1", "M4 11V8h14M15 5l3 3-3 3M20 13v3H6M9 19l-3-3 3-3M12 10v5")
    val Volume = stroked("vol", "M4 9h4l5-4v14l-5-4H4zM17 9a4 4 0 0 1 0 6M19.5 6.5a8 8 0 0 1 0 11")
    val Muted = stroked("mute", "M4 9h4l5-4v14l-5-4H4zM17 9l5 6M22 9l-5 6")
}
