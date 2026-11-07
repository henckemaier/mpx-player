package com.mpx.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mpx.player.ui.nav.Destination
import com.mpx.player.ui.theme.Mpx

// Chrome de navegação (cabeçalho + barra/trilho de abas). Migrou do Crt.kt (:app) para commonMain na
// fatia NAV, no MESMO pacote `com.mpx.player.ui.components`. O `BuildConfig.VERSION_NAME` (Android)
// virou o parâmetro puro `version` (o host passa). As abas aceitam `isEnabled` p/ o iOS apagar/
// travar destinos indisponíveis (Eq/Queue/Boot) sem aba morta — no Android é sempre `true` e nada
// muda. O slot `right` do cabeçalho segue preenchido pelo host (Battery/FILES são Android-only).

@Composable
fun MpxHeader(version: String, right: @Composable RowScope.() -> Unit) {
    val c = Mpx.colors
    Row(
        Modifier
            .fillMaxWidth()
            .frame()
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Txt("MPX//OS (TM) v$version", size = 11.sp, color = c.dim, letterSpacing = 1.sp)
        right()
    }
}

@Composable
fun HeaderLabel(text: String) = Txt(text, size = 11.sp, color = Mpx.colors.dim, letterSpacing = 1.sp)

/** Barra de navegação horizontal (modo retrato). Aba desabilitada renderiza apagada e não-tapável. */
@Composable
fun MpxNav(
    selected: Destination,
    onSelect: (Destination) -> Unit,
    isEnabled: (Destination) -> Boolean = { true },
) {
    val c = Mpx.colors
    Row(Modifier.fillMaxWidth().height(44.dp).frame()) {
        Destination.entries.forEachIndexed { i, dest ->
            val on = dest == selected
            val enabled = isEnabled(dest)
            if (i > 0) Box(Modifier.width(1.dp).fillMaxHeight().background(c.line))
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(if (on) c.accent else Color.Transparent)
                    .then(if (enabled) Modifier.tap { onSelect(dest) } else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                Txt(
                    dest.label,
                    size = 20.sp,
                    display = true,
                    color = if (!enabled) c.line else if (on) c.onAccent else c.accent,
                )
            }
        }
    }
}

/** Navegação vertical (barra lateral) para o modo paisagem. Mesmas abas/realce do [MpxNav]. */
@Composable
fun MpxNavRail(
    selected: Destination,
    onSelect: (Destination) -> Unit,
    isEnabled: (Destination) -> Boolean = { true },
) {
    val c = Mpx.colors
    Column(Modifier.width(60.dp).fillMaxHeight().frame()) {
        Destination.entries.forEachIndexed { i, dest ->
            val on = dest == selected
            val enabled = isEnabled(dest)
            if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(if (on) c.accent else Color.Transparent)
                    .then(if (enabled) Modifier.tap { onSelect(dest) } else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                Txt(
                    dest.label,
                    size = 16.sp,
                    display = true,
                    color = if (!enabled) c.line else if (on) c.onAccent else c.accent,
                    align = TextAlign.Center,
                )
            }
        }
    }
}
