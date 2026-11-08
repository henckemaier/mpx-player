package com.mpx.player.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mpx.player.data.CoverArt
import com.mpx.player.ui.components.InvertedBar
import com.mpx.player.ui.components.NpCoverMeta
import com.mpx.player.ui.components.NpProgress
import com.mpx.player.ui.components.NpTitle
import com.mpx.player.ui.components.NpTransport
import com.mpx.player.ui.components.Spectrum
import com.mpx.player.ui.components.trkLabel

/**
 * Bloco do topo da Now Playing (RETRATO). É a UNIDADE do gate visual A/B (render idêntico
 * Android × iOS/Skia, com foco no blur do text-shadow do título). Os sub-composables
 * (NpCoverMeta/NpTitle/NpProgress/NpTransport/Spectrum + trkLabel) agora vivem COMPARTILHADOS em
 * [com.mpx.player.ui.components] (NpParts) — UMA definição, reusada por este painel E pela
 * [com.mpx.player.ui.screens.NowPlayingScreen] cheia (zero duplicata). O render deste painel é
 * BYTE-A-BYTE o de antes (os mesmos composables, os mesmos args; o painel passa `trailing=null`
 * ao transporte → 5 botões, sem Bookmark).
 *
 * INCLUI: InvertedBar("> NOW PLAYING", "TRK NN/NN") + NpCoverMeta (capa 128dp + ART/ALB/FMT) +
 * título VT323 grande com glow + dosPath dim + progresso (SegmentBar + tempos) + transporte
 * (shuffle/prev/play-pause/next/repeat) + Spectrum. NÃO inclui (ficam na tela cheia, fora do
 * painel): Console/LYRICS, VolumeRow, Bookmark, e o layout de paisagem.
 *
 * CAPA (P7 fatia cover): no host LIVE a capa resolve assíncrono pelo [Cover] (iOS no-op → capa
 * gerada). No gate A/B, [sampleCover] já vem RESOLVIDA (ver [sampleCoverArt]) e é desenhada no 1º
 * frame — determinística nos dois lados. [tech] alimenta FMT/RATE/extra (default vazio no host).
 */
@Composable
fun NowPlayingPanel(
    ui: PlayerUi,
    actions: NowPlayingActions,
    tech: TechInfo = TechInfo(),
    sampleCover: CoverArt.Image? = null,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        InvertedBar("> NOW PLAYING", trkLabel(ui))
        NpCoverMeta(ui, tech, sampleCover)
        NpTitle(ui)
        NpProgress(ui, actions)
        NpTransport(ui, actions)
        Spectrum(ui.isPlaying)
    }
}
