package com.mpx.player.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mpx.player.ui.components.MpxHeader
import com.mpx.player.ui.components.MpxNav
import com.mpx.player.ui.components.MpxNavRail
import com.mpx.player.ui.nav.Destination
import com.mpx.player.ui.nav.NavState
import com.mpx.player.ui.theme.Mpx

/**
 * Shell comum da navegação (replica o antigo `MainShell` do :app, agora ZERO Android). Retrato:
 * cabeçalho + [MpxNav] + conteúdo; paisagem: [MpxNavRail] + coluna(cabeçalho + conteúdo). Usa
 * [Mpx.landscape] (LocalLandscape) para decidir barra vs. trilho.
 *
 * Renderiza SÓ o slot do destino [NavState.current] — os demais nem compõem, MESMA política de
 * hoje: trocar de aba recria o ViewModel daquele destino (preserva o comportamento Android, zero
 * regressão). O host monta o mapa [destinations] (telas/VMs/ações ficam no :app) e passa a
 * [version] e o slot [header] (indicadores Android-only).
 */
@Composable
fun MpxShell(
    nav: NavState,
    destinations: Map<Destination, @Composable () -> Unit>,
    onSelect: (Destination) -> Unit,
    header: @Composable RowScope.() -> Unit,
    version: String,
    enabled: (Destination) -> Boolean = { true },
) {
    val content: @Composable () -> Unit = { destinations[nav.current]?.invoke() }
    if (Mpx.landscape) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MpxNavRail(nav.current, onSelect, enabled)
            Column(
                Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MpxHeader(version, header)
                content()
            }
        }
    } else {
        MpxHeader(version, header)
        MpxNav(nav.current, onSelect, enabled)
        content()
    }
}
