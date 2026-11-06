package com.mpx.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import com.mpx.player.resources.Res
import com.mpx.player.resources.vt323
import org.jetbrains.compose.resources.Font

/**
 * Tela-fumaça do porte iOS (P7 F7.1): prova que o Compose Multiplatform roda no host
 * iOS a partir do :shared — fundo preto + texto verde-fósforo, na fonte VT323 carregada
 * como Compose Resource (exercita a receita de fonte-como-resource ponta-a-ponta).
 *
 * NÃO é o CRT final (CrtSpikeRoot/SpikeCrt ficaram no spike e foram descartados). É o
 * mínimo para o gate visual do CI: se renderizar, o pipeline host→framework→resources
 * está de pé e o Passo 7 pode trazer a UI real do app. Usa só compose.foundation
 * (BasicText) — sem material3 nas deps do :shared.
 */
@Composable
fun SmokeScreen() {
    val phosphor = Color(0xFF3DFF8F)
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = "MPX//OS iOS SMOKE OK",
            style = TextStyle(
                color = phosphor,
                fontFamily = FontFamily(Font(Res.font.vt323)),
                fontSize = 32.sp,
            ),
        )
    }
}
