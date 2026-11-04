package com.mpx.player.shared.playback

import com.mpx.player.shared.PlatformContext

/** Android: adaptador Media3 sobre o `Context` embrulhado no [PlatformContext]. */
actual fun createAudioPlayer(context: PlatformContext): AudioPlayer = AndroidAudioPlayer(context.context)
