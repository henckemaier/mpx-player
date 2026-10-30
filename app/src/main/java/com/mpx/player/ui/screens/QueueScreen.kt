package com.mpx.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mpx.player.ui.PlayerUi
import com.mpx.player.ui.components.Cover
import com.mpx.player.ui.components.DashedDivider
import com.mpx.player.ui.components.InvertedBar
import com.mpx.player.ui.components.TextButton
import com.mpx.player.ui.components.Txt
import com.mpx.player.ui.components.formatTime
import com.mpx.player.ui.components.frame
import com.mpx.player.ui.components.tap
import com.mpx.player.ui.theme.Mpx

data class QueueActions(
    val pick: (Int) -> Unit,
    val shuffle: () -> Unit,
    val clear: () -> Unit,
    val play: () -> Unit,
)

@Composable
fun QueueScreen(ui: PlayerUi, actions: QueueActions) {
    val c = Mpx.colors
    val t = ui.current
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth().frame().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Cover(t, Modifier.size(92.dp).frame(c.accent), disc = true)
            Column(Modifier.height(92.dp).weight(1f), verticalArrangement = Arrangement.SpaceBetween) {
                Txt(t?.album?.uppercase() ?: "FILA VAZIA", size = 26.sp, display = true, lineHeight = 26.sp)
                Column {
                    Txt(t?.artist?.uppercase() ?: "---", size = 11.sp, color = c.dim, lineHeight = 16.sp)
                    Txt(
                        "${ui.queue.size} TRK · ${formatTime(ui.totalMs)} · ${t?.format ?: "---"}",
                        size = 11.sp, color = c.dim, lineHeight = 16.sp,
                    )
                }
                Txt("PLAYLIST: ${ui.sourceLabel.ifEmpty { "/queue.m3u" }.lowercase()}", size = 11.sp, lineHeight = 16.sp)
            }
        }

        InvertedBar("> DIR /QUEUE", "${ui.queue.size} FILES", glow = false)

        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp)) {
            Txt("NR", Modifier.width(34.dp), size = 10.sp, color = c.dim)
            Txt("FILENAME", Modifier.weight(1f), size = 10.sp, color = c.dim)
            Txt("TIME", Modifier.width(52.dp), size = 10.sp, color = c.dim, align = TextAlign.End)
        }

        val listState = rememberLazyListState()
        LaunchedEffect(ui.index) {
            if (ui.index > 2) listState.animateScrollToItem(ui.index - 2)
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth().frame(), state = listState) {
            if (ui.queue.isEmpty()) item {
                Txt("> NENHUM ARQUIVO NA FILA. ESCOLHA UM ÁLBUM EM LIB.", Modifier.padding(10.dp), size = 12.sp, color = c.dim, maxLines = 3)
            }
            itemsIndexed(ui.queue) { i, tr ->
                val cur = i == ui.index
                val fg = when {
                    cur -> c.onAccent
                    i < ui.index -> c.dim
                    else -> c.accent
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .background(if (cur) c.accent else c.bg)
                        .tap { actions.pick(i) }
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Txt("%02d".format(i + 1), Modifier.width(34.dp), size = 12.sp, color = fg)
                    val mark = when {
                        cur -> "▶ "
                        i < ui.index -> "✓ "
                        else -> ""
                    }
                    Txt(mark + tr.displayName.uppercase(), Modifier.weight(1f), size = 12.sp, color = fg)
                    Txt(formatTime(tr.durationMs), Modifier.width(52.dp), size = 12.sp, color = fg, align = TextAlign.End)
                }
                DashedDivider()
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Txt("TOTAL ${formatTime(ui.totalMs)}", size = 11.sp, color = c.dim)
            Txt("RESTANTE ${formatTime(ui.remainingMs.coerceAtLeast(0))}", size = 11.sp, color = c.dim)
        }

        Row(Modifier.fillMaxWidth().height(44.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val m = Modifier.weight(1f).fillMaxHeight()
            TextButton("SHUFFLE", m, onClick = actions.shuffle)
            TextButton("CLEAR", m, onClick = actions.clear)
            TextButton(if (ui.isPlaying) "▶ PLAYING" else "▶ PLAY", m, filled = true, onClick = actions.play)
        }
    }
}
