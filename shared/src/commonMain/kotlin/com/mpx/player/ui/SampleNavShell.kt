package com.mpx.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mpx.player.ui.components.HeaderLabel
import com.mpx.player.ui.components.Txt
import com.mpx.player.ui.components.frame
import com.mpx.player.ui.nav.Destination
import com.mpx.player.ui.nav.NavState
import com.mpx.player.ui.theme.Mpx

/**
 * Conteúdo DUMMY do 4º par A/B (perfil 'nav'): um frame CRT com "CONTENT" centralizado. NÃO é uma
 * tela real — o par A/B 'nav' gateia o CHROME de navegação (barra [com.mpx.player.ui.components.MpxNav]
 * + cabeçalho [com.mpx.player.ui.components.MpxHeader]), não uma tela de conteúdo (essas já têm pares
 * próprios: nowplaying/library/settings). Altura FIXA (80dp) p/ caber no canvas 393x200 sem clip e
 * sem `fillMaxSize` (que num Column sem weight transbordaria e ficaria não-determinístico).
 */
@Composable
fun NavShellDummyContent() {
    Box(
        Modifier.fillMaxWidth().height(80.dp).frame(),
        contentAlignment = Alignment.Center,
    ) {
        Txt("CONTENT", size = 20.sp, display = true)
    }
}

/**
 * Sub-árvore COMPARTILHADA do 4º par A/B (perfil 'nav'): renderiza o [MpxShell] REAL com conteúdo
 * [NavShellDummyContent] — IDÊNTICA nos dois lados (iOS [com.mpx.player.ui.NavShellRenderTest] ×
 * Android [com.mpx.player.debug.AbNavBaselineActivity]) p/ o pixel-diff gatear SÓ o chrome. Estática
 * e determinística: SEM animação de transição (render de 1 frame, t=0).
 *
 * `nav = NavState()` → current = PLAY (aba selecionada, accent-fill). `enabled` habilita PLAY/LIB/
 * CFG e deixa QUEUE/EQ APAGADOS (não-tapáveis) → o par cobre os 3 estados de aba: SELECIONADA
 * (accent-fill), NORMAL (texto accent) e DESABILITADA (dim). `header` = [HeaderLabel]("NAV") (sem
 * Battery/FILES, que são Android-only). `version` fixa "1.0" (NÃO BuildConfig → determinístico).
 * `onSelect` no-op (render de 1 frame).
 *
 * O caller injeta o ambiente: [com.mpx.player.ui.theme.LocalLandscape] (false=retrato→barra;
 * true=paisagem→trilho), `Density(1f)` e [com.mpx.player.ui.theme.MpxTheme]. O `Column(spacedBy 10dp)`
 * + `padding(10dp)` + bg espelham o wrapper do host/`:app` (o [MpxShell] retrato emite header+barra+
 * conteúdo em sequência e conta com o espaçamento do Column pai).
 */
@Composable
fun NavShellAbSample() {
    Column(
        Modifier.fillMaxSize().background(Mpx.colors.bg).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        MpxShell(
            nav = NavState(),
            destinations = mapOf(Destination.PLAY to { NavShellDummyContent() }),
            onSelect = {},
            header = { HeaderLabel("NAV") },
            version = "1.0",
            enabled = { it == Destination.PLAY || it == Destination.LIBRARY || it == Destination.CONFIG },
        )
    }
}
