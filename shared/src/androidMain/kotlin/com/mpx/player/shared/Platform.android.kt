package com.mpx.player.shared

import android.os.Build

actual object Platform {
    actual val name: String = "Android ${Build.VERSION.SDK_INT}"
}
