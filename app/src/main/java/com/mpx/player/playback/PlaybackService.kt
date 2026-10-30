package com.mpx.player.playback

import android.app.PendingIntent
import android.content.Intent
import android.media.AudioManager
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.mpx.player.MainActivity
import com.mpx.player.MpxApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null
    private var effects: AudioEffects? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var crossfade = false
    private var fadeInUntil = 0L

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        val settings = (application as MpxApp).container.settings
        val sessionId = getSystemService(AudioManager::class.java).generateAudioSessionId()

        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        player.audioSessionId = sessionId

        val fx = AudioEffects(sessionId)
        effects = fx
        scope.launch { settings.settings.map { it.eq }.distinctUntilChanged().collect { fx.apply(it) } }
        scope.launch { settings.settings.map { it.crossfade }.distinctUntilChanged().collect { crossfade = it } }

        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (crossfade && reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                    fadeInUntil = System.currentTimeMillis() + FADE_IN_MS
                } else {
                    fadeInUntil = 0L
                    player.volume = 1f
                }
            }
        })
        scope.launch { fadeLoop(player) }

        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, player)
            .setSessionActivity(open)
            .setCallback(object : MediaSession.Callback {
                // O MediaController remove a URI local ao enviar os itens; ela volta pelo requestMetadata.
                override fun onAddMediaItems(
                    mediaSession: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    mediaItems: MutableList<MediaItem>,
                ): ListenableFuture<MutableList<MediaItem>> = Futures.immediateFuture(
                    mediaItems.map { item ->
                        item.requestMetadata.mediaUri?.let { item.buildUpon().setUri(it).build() } ?: item
                    }.toMutableList(),
                )
            })
            .build()
    }

    /** Crossfade simplificado: fade-out nos últimos 4 s e fade-in no início da próxima faixa. */
    private suspend fun fadeLoop(player: ExoPlayer) {
        while (scope.isActive) {
            delay(100)
            if (!crossfade || !player.isPlaying) continue
            val now = System.currentTimeMillis()
            val remaining = player.duration - player.currentPosition
            val target = when {
                fadeInUntil > now -> 1f - (fadeInUntil - now).toFloat() / FADE_IN_MS
                player.duration > 0 && remaining in 0..FADE_OUT_MS && player.hasNextMediaItem() ->
                    (remaining.toFloat() / FADE_OUT_MS).coerceAtLeast(0.05f)
                else -> 1f
            }
            if (player.volume != target) player.volume = target.coerceIn(0f, 1f)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        session?.run {
            player.release()
            release()
        }
        session = null
        effects?.release()
        effects = null
        super.onDestroy()
    }

    private companion object {
        const val FADE_OUT_MS = 4_000L
        const val FADE_IN_MS = 1_500L
    }
}
