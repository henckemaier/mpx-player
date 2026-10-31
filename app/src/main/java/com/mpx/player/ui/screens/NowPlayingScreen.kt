package com.mpx.player.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.combinedClickable
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mpx.player.model.LyricLine
import com.mpx.player.model.Playlist
import com.mpx.player.playback.RepeatMode
import com.mpx.player.playback.VolumeState
import com.mpx.player.ui.PlayerUi
import com.mpx.player.ui.TechInfo
import com.mpx.player.ui.components.Cover
import com.mpx.player.ui.components.IconButton
import com.mpx.player.ui.components.InvertedBar
import com.mpx.player.ui.components.MpxIcons
import com.mpx.player.ui.components.SegmentBar
import com.mpx.player.ui.components.TextButton
import com.mpx.player.ui.components.Txt
import com.mpx.player.ui.components.blinkingCursor
import com.mpx.player.ui.components.formatTime
import com.mpx.player.ui.components.frame
import com.mpx.player.ui.components.tap
import com.mpx.player.ui.theme.Mpx
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class NowPlayingActions(
    val toggle: () -> Unit,
    val next: () -> Unit,
    val previous: () -> Unit,
    val seek: (Float) -> Unit,
    val shuffle: () -> Unit,
    val repeat: () -> Unit,
    val setVolume: (Float) -> Unit,
    val mute: () -> Unit,
    val openLibrary: () -> Unit,
    val toggleFavorite: () -> Unit,
    val addToPlaylist: (String) -> Unit,
)

@Composable
fun NowPlayingScreen(
    ui: PlayerUi,
    tech: TechInfo,
    lyrics: List<LyricLine>,
    volume: VolumeState,
    favorite: Boolean,
    playlists: List<Playlist>,
    actions: NowPlayingActions,
) {
    var showPicker by remember { mutableStateOf(false) }
    if (Mpx.landscape) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(0.44f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                InvertedBar("> NOW PLAYING", trkLabel(ui))
                NpCoverMeta(ui, tech)
                NpTitle(ui)
                Spectrum(ui.isPlaying)
            }
            Column(Modifier.weight(0.56f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                NpProgress(ui, actions)
                NpTransport(ui, actions, favorite) { showPicker = true }
                VolumeRow(volume, actions.setVolume, actions.mute)
                Console(Modifier.weight(1f), ui, tech, lyrics, onEmpty = actions.openLibrary)
            }
        }
    } else {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            InvertedBar("> NOW PLAYING", trkLabel(ui))
            NpCoverMeta(ui, tech)
            NpTitle(ui)
            NpProgress(ui, actions)
            NpTransport(ui, actions, favorite) { showPicker = true }
            NpConsoleRegionPortrait(Modifier.weight(1f), ui, tech, lyrics, volume, actions)
        }
    }
    if (showPicker) {
        PlaylistPickerDialog(
            playlists,
            onPick = { actions.addToPlaylist(it); showPicker = false },
            onDismiss = { showPicker = false },
        )
    }
}

private fun trkLabel(ui: PlayerUi) =
    if (ui.queue.isEmpty()) "TRK --/--" else "TRK %02d/%02d".format(ui.index + 1, ui.queue.size)

@Composable
private fun NpCoverMeta(ui: PlayerUi, tech: TechInfo) {
    val c = Mpx.colors
    val t = ui.current
    Row(Modifier.fillMaxWidth().frame().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Cover(t, Modifier.size(128.dp).frame(c.accent), disc = true, scanlines = true)
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

@Composable
private fun NpTitle(ui: PlayerUi) {
    val c = Mpx.colors
    val t = ui.current
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
private fun NpProgress(ui: PlayerUi, actions: NowPlayingActions) {
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

@Composable
private fun NpTransport(ui: PlayerUi, actions: NowPlayingActions, favorite: Boolean, onLongFavorite: () -> Unit) {
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
        BookmarkButton(favorite, m, onTap = actions.toggleFavorite, onLong = onLongFavorite)
    }
}

/** Botão de marcador: toque = favoritar; toque longo = escolher playlist. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookmarkButton(favorite: Boolean, modifier: Modifier, onTap: () -> Unit, onLong: () -> Unit) {
    val c = Mpx.colors
    Box(
        modifier
            .background(if (favorite) c.accent else c.bg)
            .frame(if (favorite) c.accent else c.line)
            .combinedClickable(onClick = onTap, onLongClick = onLong),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            if (favorite) MpxIcons.BookmarkOn else MpxIcons.Bookmark, null,
            Modifier.size(22.dp),
            colorFilter = ColorFilter.tint(if (favorite) c.onAccent else c.accent),
        )
    }
}

@Composable
private fun PlaylistPickerDialog(playlists: List<Playlist>, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val c = Mpx.colors
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties()) {
        Column(
            Modifier.fillMaxWidth().background(c.bg).frame(c.accent).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Txt("> ADICIONAR A PLAYLIST", size = 12.sp, color = c.dim)
            Column(Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState())) {
                playlists.forEach { p ->
                    Txt(
                        "  " + (if (p.locked) "♥ " else "") + p.name.uppercase(),
                        Modifier.fillMaxWidth().height(40.dp).tap { onPick(p.id) }.padding(vertical = 11.dp),
                        size = 13.sp,
                    )
                }
            }
            TextButton("CANCELAR", Modifier.fillMaxWidth().height(40.dp), onClick = onDismiss)
        }
    }
}

@Composable
private fun VolumeRow(volume: VolumeState, onSet: (Float) -> Unit, onMute: () -> Unit) {
    val c = Mpx.colors
    val lit = if (volume.muted || volume.max == 0) 0 else (volume.level * 20f / volume.max).roundToInt()
    Row(Modifier.fillMaxWidth().height(44.dp).frame(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            if (volume.muted) MpxIcons.Muted else MpxIcons.Volume,
            Modifier.width(44.dp).fillMaxHeight(), border = c.bg, iconSize = 22.dp, onClick = onMute,
        )
        Box(Modifier.width(1.dp).fillMaxHeight().background(c.line))
        Txt("VOL", Modifier.padding(horizontal = 6.dp), size = 11.sp, color = c.dim)
        Row(
            Modifier
                .weight(1f)
                .height(28.dp)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val d = awaitFirstDown()
                        onSet(((d.position.x / size.width) * 20f).roundToInt().coerceIn(0, 20) / 20f)
                        while (true) {
                            val p = awaitPointerEvent().changes.firstOrNull() ?: break
                            if (!p.pressed) break
                            onSet(((p.position.x / size.width) * 20f).roundToInt().coerceIn(0, 20) / 20f)
                            p.consume()
                        }
                    }
                },
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            repeat(20) { i ->
                Box(Modifier.weight(1f).height((8 + i).dp).background(if (i < lit) c.accent else c.segOff))
            }
        }
        Txt(
            if (volume.muted) "MUTE" else "%03d".format(lit * 5),
            Modifier.width(46.dp).padding(end = 6.dp), size = 20.sp, display = true, align = TextAlign.End,
        )
    }
}

/** Altura aproximada do bloco SPECTRUM + VOLUME (horizontal) + espaçamento — o quanto o console
 *  cresce para cima ao ser puxado, cobrindo esses dois painéis. */
private val COVERED_H = 134.dp

/**
 * Região inferior do retrato: SPECTRUM + VOLUME ao fundo e o CONSOLE (CMD.LOG/LYRICS) por cima.
 * O console pode ser puxado para cima pela alça, crescendo sobre esses dois painéis. Quando
 * expandido, o VOLUME reaparece "em pé" fixado na direita, ainda operável.
 */
@Composable
private fun NpConsoleRegionPortrait(
    modifier: Modifier,
    ui: PlayerUi,
    tech: TechInfo,
    lyrics: List<LyricLine>,
    volume: VolumeState,
    actions: NowPlayingActions,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val expand = remember { Animatable(0f) }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val totalPx = constraints.maxHeight.toFloat()
        val coveredPx = with(density) { COVERED_H.toPx() }.coerceAtMost(totalPx)
        val collapsedPx = (totalPx - coveredPx).coerceAtLeast(0f)
        val frac = expand.value
        val consoleDp = with(density) { (collapsedPx + frac * coveredPx).toDp() }
        val standingVolume = frac > 0.4f

        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Spectrum(ui.isPlaying)
            VolumeRow(volume, actions.setVolume, actions.mute)
        }
        Console(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(consoleDp),
            ui, tech, lyrics, onEmpty = actions.openLibrary,
            contentEndPadding = if (standingVolume) 52.dp else 0.dp,
            topBar = {
                ConsoleGrip(
                    expanded = frac > 0.5f,
                    onDrag = { dy ->
                        if (coveredPx > 0f) scope.launch { expand.snapTo((expand.value - dy / coveredPx).coerceIn(0f, 1f)) }
                    },
                    onStop = { scope.launch { expand.animateTo(if (expand.value > 0.5f) 1f else 0f) } },
                    onToggle = { scope.launch { expand.animateTo(if (expand.value > 0.5f) 0f else 1f) } },
                )
            },
        )
        if (standingVolume) {
            VerticalVolume(
                volume, actions.setVolume, actions.mute,
                Modifier.align(Alignment.TopEnd).padding(top = 50.dp, end = 4.dp, bottom = 4.dp).width(46.dp).fillMaxHeight(),
            )
        }
    }
}

/** Alça para puxar o console para cima/baixo (arrastar) ou alternar (toque). */
@Composable
private fun ConsoleGrip(expanded: Boolean, onDrag: (Float) -> Unit, onStop: () -> Unit, onToggle: () -> Unit) {
    val c = Mpx.colors
    val a = if (expanded) "▼" else "▲"
    Box(
        Modifier
            .fillMaxWidth()
            .height(20.dp)
            .draggable(
                orientation = Orientation.Vertical,
                state = rememberDraggableState { onDrag(it) },
                onDragStopped = { onStop() },
            )
            .tap(onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        Txt("$a ════════════ $a", size = 10.sp, color = c.dim, letterSpacing = 2.sp)
    }
}

/** VOLUME "em pé": barra vertical fixada na direita, usada quando o console está expandido. */
@Composable
private fun VerticalVolume(volume: VolumeState, onSet: (Float) -> Unit, onMute: () -> Unit, modifier: Modifier) {
    val c = Mpx.colors
    val lit = if (volume.muted || volume.max == 0) 0 else (volume.level * 20f / volume.max).roundToInt()
    Column(modifier.background(c.bg).frame(), horizontalAlignment = Alignment.CenterHorizontally) {
        Txt(
            if (volume.muted) "MUT" else "%03d".format(lit * 5),
            Modifier.padding(vertical = 4.dp), size = 13.sp, display = true,
        )
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 7.dp, vertical = 6.dp)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val d = awaitFirstDown()
                        onSet(((1f - d.position.y / size.height) * 20f).roundToInt().coerceIn(0, 20) / 20f)
                        while (true) {
                            val p = awaitPointerEvent().changes.firstOrNull() ?: break
                            if (!p.pressed) break
                            onSet(((1f - p.position.y / size.height) * 20f).roundToInt().coerceIn(0, 20) / 20f)
                            p.consume()
                        }
                    }
                },
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            repeat(20) { i ->
                Box(Modifier.fillMaxWidth().weight(1f).background(if ((19 - i) < lit) c.accent else c.segOff))
            }
        }
        IconButton(
            if (volume.muted) MpxIcons.Muted else MpxIcons.Volume,
            Modifier.fillMaxWidth().height(40.dp), border = c.bg, iconSize = 20.dp, onClick = onMute,
        )
    }
}

private val SPECTRUM_BASE = intArrayOf(38, 62, 80, 55, 90, 72, 48, 66, 85, 58, 40, 70, 92, 64, 50, 76, 60, 44, 68, 82, 56, 36, 52, 46, 30, 42, 26, 18)

@Composable
private fun Spectrum(playing: Boolean) {
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

@Composable
private fun Console(
    modifier: Modifier,
    ui: PlayerUi,
    tech: TechInfo,
    lyrics: List<LyricLine>,
    onEmpty: () -> Unit,
    contentEndPadding: Dp = 0.dp,
    topBar: (@Composable () -> Unit)? = null,
) {
    val c = Mpx.colors
    var lyricMode by rememberSaveable { mutableStateOf(false) }
    val curIdx = lyrics.indexOfLast { it.timeMs <= ui.positionMs }
    Column(modifier.fillMaxWidth().background(c.bg).frame()) {
        topBar?.invoke()
        Row(Modifier.fillMaxWidth().height(30.dp), verticalAlignment = Alignment.CenterVertically) {
            ConsoleTab(">_ CMD.LOG", !lyricMode) { lyricMode = false }
            ConsoleTab("♪ LYRICS.LRC", lyricMode) { lyricMode = true }
            Box(Modifier.weight(1f))
            Txt(
                if (lyricMode) (if (lyrics.isEmpty()) "NO .LRC" else "SYNC ${curIdx + 1}/${lyrics.size}") else "TTY0",
                Modifier.padding(end = 10.dp), size = 11.sp, color = c.dim,
            )
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
        Box(Modifier.fillMaxSize().clipToBounds().padding(start = 10.dp, end = 10.dp + contentEndPadding, top = 6.dp, bottom = 6.dp)) {
            if (!lyricMode) CmdLog(ui, tech, onEmpty) else LyricsView(lyrics, curIdx, ui.positionMs, ui.isPlaying)
        }
    }
}

@Composable
private fun ConsoleTab(label: String, selected: Boolean, onClick: () -> Unit) {
    val c = Mpx.colors
    Row(Modifier.fillMaxHeight()) {
        Box(
            Modifier.fillMaxHeight().background(if (selected) c.accent else c.bg).tap(onClick = onClick).padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center,
        ) { Txt(label, size = 11.sp, color = if (selected) c.onAccent else c.accent) }
        Box(Modifier.width(1.dp).fillMaxHeight().background(c.line))
    }
}

@Composable
private fun CmdLog(ui: PlayerUi, tech: TechInfo, onEmpty: () -> Unit) {
    val c = Mpx.colors
    val t = ui.current
    val cursor = blinkingCursor()
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Bottom) {
        val lines = if (t == null) listOf(
            "@ECHO OFF",
            "> MOUNT /music ...... OK",
            "> QUEUE EMPTY",
            "> TOQUE AQUI PARA ABRIR A BIBLIOTECA",
        ) else listOf(
            "@ECHO OFF",
            "> MOUNT /music ...... OK",
            "> DECODE ${tech.format.lowercase()} ${tech.bitrate.lowercase()}bps/${tech.sampleRate}",
            "> BUFFER [##########] 100%",
            "> SRC ${ui.sourceLabel.ifEmpty { t.album }.uppercase()}",
        )
        lines.forEach { Txt(it, size = 11.sp, color = c.dim, lineHeight = 17.sp) }
        val last = when {
            t == null -> "> WAITING"
            ui.isPlaying -> "> PLAY ${t.displayName.uppercase()}"
            else -> "> PAUSE ${t.displayName.uppercase()}"
        }
        Row(if (t == null) Modifier.tap(onClick = onEmpty) else Modifier) {
            Txt(last, Modifier.weight(1f, fill = false), size = 11.sp, lineHeight = 17.sp)
            Txt(cursor, Modifier.background(if (cursor == "_") c.accent else c.bg), size = 11.sp, color = c.onAccent, lineHeight = 17.sp)
        }
    }
}

@Composable
private fun LyricsView(lyrics: List<LyricLine>, cur: Int, positionMs: Long, playing: Boolean) {
    val c = Mpx.colors
    if (lyrics.isEmpty()) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Bottom) {
            Txt("> LRC NOT FOUND", size = 12.sp, color = c.dim, lineHeight = 19.sp)
            Txt("CFG > [3] IMPORTAR > LETRAS .LRC", size = 12.sp, color = c.dim, lineHeight = 19.sp)
            Txt("> _", size = 12.sp, lineHeight = 19.sp)
        }
        return
    }
    // digitação: caracteres revelados desde o início da linha atual, animado por frame
    var now by remember { mutableStateOf(positionMs) }
    LaunchedEffect(positionMs, playing) {
        val base = positionMs
        val start = withFrameMillis { it }
        while (playing) {
            withFrameMillis { f -> now = base + (f - start) }
        }
        now = positionMs
    }
    // Área rolável fixada no rodapé: quando a letra passa da altura do console, rola e mantém a
    // linha atual sempre inteira em vez de cortá-la pela metade.
    val scroll = rememberScrollState()
    LaunchedEffect(scroll.maxValue) { scroll.scrollTo(scroll.maxValue) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Quantas linhas passadas mostrar: o bastante para preencher a altura disponível (mantendo a
        // linha atual encostada no rodapé) em vez de deixar metade do painel vazio quando ele está
        // expandido. O que sobra rola para fora do topo, então superestimar é seguro.
        val history = ((maxHeight / 19.dp).toInt() + 2).coerceAtLeast(3)
        Column(
            Modifier.fillMaxWidth().heightIn(min = maxHeight).verticalScroll(scroll),
            verticalArrangement = Arrangement.Bottom,
        ) {
            for (k in history downTo 1) {
                val i = cur - k
                if (i >= 0) LyricRow(lyrics[i], dim = true)
            }
            if (cur >= 0) {
                val line = lyrics[cur]
                val chars = ((now - line.timeMs) / 45).toInt().coerceIn(0, line.text.length)
                // Linha atual: quebra em várias linhas; "█" é o cursor de digitação.
                Txt(
                    "[${formatTime(line.timeMs)}] ${line.text.uppercase().take(chars)}█",
                    Modifier.fillMaxWidth(),
                    size = 12.sp, glow = true, lineHeight = 19.sp, maxLines = 6,
                )
            } else {
                Txt("> AGUARDANDO ${formatTime(lyrics.first().timeMs)}", size = 12.sp, color = c.dim, lineHeight = 19.sp)
            }
        }
    }
}

@Composable
private fun LyricRow(l: LyricLine, dim: Boolean) {
    val c = Mpx.colors
    Txt("[${formatTime(l.timeMs)}] ${l.text.uppercase()}", size = 12.sp, color = if (dim) c.dim else c.accent, lineHeight = 19.sp, maxLines = 3)
}
