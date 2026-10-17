package com.secretmelody

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/** Custom command from the app: Bundle int "minutes" (> 0 pause after that long, -1 at the end of this song, 0 cancel). */
const val CMD_SLEEP = "secretmelody.sleep"

/** Owns the player so music keeps going with the app closed; Media3 adds the notification and lock screen controls. */
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private var exo: ExoPlayer? = null
    private val sleep = Runnable { session?.player?.pause() }
    private val handler = Handler(Looper.getMainLooper())
    private val heartbeat = object : Runnable {
        override fun run() {
            val player = session?.player
            if (player?.isPlaying == true) {
                PlayLog.beat(filesDir)
                ResumeStore.save(filesDir, player)
            }
            handler.postDelayed(this, 5_000)
        }
    }

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true, // pause for calls and other apps (audio focus)
            )
            .setHandleAudioBecomingNoisy(true) // pause when headphones are unplugged
            .setWakeMode(C.WAKE_MODE_LOCAL) // keep the CPU awake while playing with the screen off
            .build()
        exo = player
        // Recorded here, not in the UI, so songs played with the app closed still count.
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val id = mediaItem?.mediaId ?: return
                runCatching { recordPlay(filesDir, id) }
                ResumeStore.save(filesDir, player)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!isPlaying) {
                    player.pauseAtEndOfMediaItems = false // an "end of this song" timer is used up (or cancelled by pausing)
                    PlayLog.stopped(filesDir)
                    ResumeStore.save(filesDir, player)
                }
            }
        })
        session = MediaSession.Builder(this, player)
            .setCallback(object : MediaSession.Callback {
                // Media3 drops the URI when a controller sends items, so put it back from requestMetadata.
                override fun onAddMediaItems(
                    mediaSession: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    mediaItems: MutableList<MediaItem>,
                ): ListenableFuture<MutableList<MediaItem>> = Futures.immediateFuture(
                    mediaItems.map { it.buildUpon().setUri(it.requestMetadata.mediaUri).build() }.toMutableList()
                )

                override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
                    val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                        .add(SessionCommand(CMD_SLEEP, Bundle.EMPTY))
                        .build()
                    return MediaSession.ConnectionResult.AcceptedResultBuilder(session).setAvailableSessionCommands(commands).build()
                }

                override fun onCustomCommand(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    customCommand: SessionCommand,
                    args: Bundle,
                ): ListenableFuture<SessionResult> {
                    if (customCommand.customAction != CMD_SLEEP) return super.onCustomCommand(session, controller, customCommand, args)
                    setSleep(args.getInt("minutes"))
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }

                // Play pressed on headphones or the system media controls after the player was gone: pick up where it stopped.
                override fun onPlaybackResumption(
                    mediaSession: MediaSession,
                    controller: MediaSession.ControllerInfo,
                ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
                    val saved = ResumeStore.read(filesDir)
                        ?: return Futures.immediateFailedFuture(UnsupportedOperationException("nothing to resume"))
                    return Futures.immediateFuture(
                        MediaSession.MediaItemsWithStartPosition(saved.items.map { it.toMediaItem() }, saved.index, saved.positionMs)
                    )
                }
            })
            .build()
        // A fresh start after the phone killed us: load the last queue, paused, so the app shows it ready to continue.
        ResumeStore.read(filesDir)?.let { saved ->
            player.setMediaItems(saved.items.map { it.toMediaItem() }, saved.index, saved.positionMs)
            player.prepare()
        }
        handler.post(heartbeat)
    }

    private fun setSleep(minutes: Int) {
        handler.removeCallbacks(sleep)
        exo?.pauseAtEndOfMediaItems = minutes == -1
        if (minutes > 0) handler.postDelayed(sleep, minutes * 60_000L)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        val playing = player?.isPlaying == true
        PlayLog.swiped(filesDir, playing)
        // Keep going if music is playing (or about to); stop only when idle, so an idle service doesn't linger.
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacks(heartbeat)
        handler.removeCallbacks(sleep)
        session?.player?.let { ResumeStore.save(filesDir, it) }
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }
}
