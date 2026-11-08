package com.mpx.player.ui

import androidx.compose.ui.graphics.toArgb
import com.mpx.player.data.CoverArt
import com.mpx.player.data.CoverProcessor
import com.mpx.player.data.decodeImageBytes
import com.mpx.player.model.UserSettings
import com.mpx.player.resources.Res
import com.mpx.player.ui.theme.colorsFor
import org.jetbrains.compose.resources.ExperimentalResourceApi

/**
 * FEED DETERMINÍSTICO da capa para o gate visual A/B (P7 fatia cover). SÓ o caminho do A/B usa
 * isto: lê o sample de `composeResources/files/`, decodifica e processa pelo MESMO pipeline comum
 * do app ([decodeImageBytes] expect/actual + [CoverProcessor] dither 1-bit) → [CoverArt.Image]. O
 * host LIVE NÃO passa por aqui (usa o [com.mpx.player.data.AlbumArtSource] real; no iOS é no-op →
 * capa gerada).
 *
 * POR QUE RESOLVER SÍNCRONO (o harness chama via `runBlocking` ANTES de compor): o render do A/B é
 * de UM frame (`ImageComposeScene.render()` no iOS / 1º `doOnPreDraw` no Android). O caminho normal
 * do [com.mpx.player.ui.components.Cover] resolve a capa em `produceState` (assíncrono) e NÃO
 * assentaria nesse único frame → cairia na capa gerada. Pré-resolvendo aqui e injetando a capa no
 * [NowPlayingPanel], ela já aparece no 1º frame — determinística e idêntica nos dois lados.
 *
 * POR QUE É À PROVA DE JITTER: o sample é 96×96 PURO preto/branco = o DITHER size do
 * [CoverProcessor], então `CoverTransforms.scale()` curto-circuita (sem bilinear) e o
 * Floyd–Steinberg não difunde erro (luminância exatamente 0/1, longe do limiar 0.5). O 1-bit sai
 * BIT-IDÊNTICO em Android (BitmapFactory) e iOS (skiko), imune ao micro-jitter de decode (medido:
 * ±8/canal → 0 de 9216 bits mudam). As cores on/off vêm do tema via [colorsFor] (função PURA) com
 * o MESMO [UserSettings] do harness — sem precisar de @Composable para ler `Mpx.colors` fora de
 * composição.
 */
const val SAMPLE_COVER_PATH: String = "files/sample_cover.png"

@OptIn(ExperimentalResourceApi::class)
suspend fun sampleCoverArt(settings: UserSettings): CoverArt.Image {
    val colors = colorsFor(settings.theme, settings.oled, settings.customColor)
    val bytes = Res.readBytes(SAMPLE_COVER_PATH)
    val src = decodeImageBytes(bytes, maxSide = 256)
        ?: error("A/B: $SAMPLE_COVER_PATH não decodificou")
    val art = CoverProcessor().process(
        src = src,
        key = "ab-sample",
        style = 1,          // DITHER
        mono = true,        // 1-bit
        accent = colors.accent.toArgb(),
        bg = colors.panel.toArgb(),
    )
    return art as CoverArt.Image
}
