package com.mpx.player.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mpx.player.ui.theme.Mpx

// Blocos CRT que ficam no :app. Os primitivos portáveis (Txt, frame, tap, glow, InvertedBar,
// SegmentBar, MpxButton, IconButton, TextButton, blinkingCursor, scanlines, formatTime,
// DashedDivider, ToggleRow) foram para :shared/commonMain (CrtKit.kt), e o chrome de navegação
// (Tab→Destination, MpxNav, MpxNavRail, MpxHeader, HeaderLabel) migrou na fatia NAV para
// :shared/commonMain (Chrome.kt), no MESMO pacote `com.mpx.player.ui.components` — por isso são
// usados aqui sem import. Só o Panel segue no :app.

@Composable
fun Panel(
    modifier: Modifier = Modifier,
    border: Color = Mpx.colors.line,
    padding: Dp = 10.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier.border(1.dp, border).padding(padding), content = content)
}
