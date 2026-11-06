package com.mpx.player.spike

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import com.mpx.player.spike.resources.Res
import com.mpx.player.spike.resources.vt323
import org.jetbrains.compose.resources.Font

/**
 * Conteúdo mínimo do spike (só toolchain): fundo preto + texto verde CRT na fonte VT323
 * carregada via composeResources. Prova o compose-compiler + o acessor Res.font gerado.
 * O componente CRT real (scanlines/glow) é a Fase 2.
 */
@Composable
fun CrtSpikeRoot() {
    val vt = FontFamily(Font(Res.font.vt323))
    Box(Modifier.fillMaxSize().background(Color(0xFF000000))) {
        BasicText(
            text = "MPX//OS SPIKE v0",
            modifier = Modifier.align(Alignment.Center),
            style = TextStyle(fontFamily = vt, color = Color(0xFF3DFF8F), fontSize = 28.sp),
        )
    }
}
