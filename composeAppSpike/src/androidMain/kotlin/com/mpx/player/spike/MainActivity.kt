package com.mpx.player.spike

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

/** Host Android do spike: só monta o CrtSpikeRoot. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { CrtSpikeRoot() }
    }
}
