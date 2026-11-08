package com.mpx.player.shared.playback

import com.mpx.player.shared.PlatformContext

/** iOS: o [context] é ignorado (ver [PlatformContext]). */
actual fun createAudioPlayer(context: PlatformContext): AudioPlayer = IosAudioPlayer()
