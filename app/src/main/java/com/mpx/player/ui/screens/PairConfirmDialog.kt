package com.mpx.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mpx.player.data.PairInfo
import com.mpx.player.ui.components.TextButton
import com.mpx.player.ui.components.Txt
import com.mpx.player.ui.components.frame
import com.mpx.player.ui.theme.Mpx

/**
 * Confirmação do deep-link `mpx://pair` (BROWSABLE: qualquer QR ou página pode dispará-lo).
 * Nada é conectado nem salvo até o usuário confirmar o host exibido.
 */
@Composable
fun PairConfirmDialog(info: PairInfo, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val c = Mpx.colors
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = true)) {
        Column(
            Modifier.fillMaxWidth().background(c.bg).frame(c.accent).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Txt("PAREAR COM O PC?", size = 16.sp)
            Txt("AGENTE: ${info.host}", size = 13.sp)
            Txt(
                "SÓ CONFIRME SE VOCÊ ABRIU ESTE QR/LINK DO SEU PRÓPRIO COMPUTADOR, NA SUA REDE.",
                size = 10.sp, color = c.dim, maxLines = 3,
            )
            Row(Modifier.fillMaxWidth().height(44.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton("CANCELAR", Modifier.weight(1f).height(44.dp), border = c.accent, onClick = onDismiss)
                TextButton("> PAREAR", Modifier.weight(1f).height(44.dp), filled = true, onClick = onConfirm)
            }
        }
    }
}
