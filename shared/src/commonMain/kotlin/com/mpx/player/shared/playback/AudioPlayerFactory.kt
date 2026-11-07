package com.mpx.player.shared.playback

import com.mpx.player.shared.PlatformContext

/**
 * Fábrica do player por plataforma (ponto único de DI para a UI/ViewModels).
 *
 * - Android: devolve o adaptador Media3 ([AndroidAudioPlayer]); o [context] é o
 *   `android.content.Context` do app (ver [PlatformContext]).
 * - iOS: devolve `IosAudioPlayer`; o [context] é ignorado.
 *
 * A instância só fica utilizável após [AudioPlayer.connect]; libere com
 * [AudioPlayer.release].
 */
expect fun createAudioPlayer(context: PlatformContext): AudioPlayer
