package com.secretmelody

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import android.content.ContentUris
import android.provider.MediaStore
import androidx.media3.common.MediaMetadata
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.MoreExecutors
import java.io.File
import java.util.concurrent.Executors
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/** Custom command from the app: Bundle int "minutes" (> 0 pause after that long, -1 at the end of this song, 0 cancel). */
const val CMD_SLEEP = "secretmelody.sleep"

/**
 * Owns the player so music keeps going with the app closed; Media3 adds the notification and lock screen controls.
 * A library service (not just a session) so Android Auto can browse the songs too.
 */
private const val ROOT = "root"

class PlaybackService : MediaLibraryService() {
    private var session: MediaLibrarySession? = null
    private val io = MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor())
    @Volatile private var browsed = emptyMap<String, List<MediaItem>>() // folder id -> its songs, from the last browse
    private var exo: ExoPlayer? = null
    private var effects: AudioEffects? = null
    private val sleep = Runnable { session?.player?.pause() }
    private val handler = Handler(Looper.getMainLooper())
    private var listenedThisSong = 0L // seconds, reset when the song changes
    private val heartbeat = object : Runnable {
        override fun run() {
            val player = session?.player
            if (player?.isPlaying == true) {
                PlayLog.beat(filesDir)
                ResumeStore.save(filesDir, player)
                player.currentMediaItem?.mediaId?.let { id ->
                    // A play counts after 30 s of real listening, not on every queue change.
                    listenedThisSong += 5
                    PlayStats.add(filesDir, id, 5, play = listenedThisSong == 30L)
                }
            }
            handler.postDelayed(this, 5_000)
        }
    }

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            // Files play as they are; ytstream:// (a YouTube song) becomes YouTube's audio address when it starts.
            .setMediaSourceFactory(DefaultMediaSourceFactory(ResolvingDataSource.Factory(DefaultDataSource.Factory(this), StreamResolver(this))))
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
        effects = AudioEffects(player).also { it.apply(AudioStore.read(filesDir)) }
        // Recorded here, not in the UI, so songs played with the app closed still count.
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                listenedThisSong = 0
                val id = mediaItem?.mediaId ?: return
                runCatching { recordPlay(filesDir, id) }
                ResumeStore.save(filesDir, player)
            }

            override fun onEvents(player: Player, events: Player.Events) {
                if (events.containsAny(Player.EVENT_MEDIA_ITEM_TRANSITION, Player.EVENT_PLAY_WHEN_READY_CHANGED, Player.EVENT_MEDIA_METADATA_CHANGED)) {
                    PlayerWidget.update(this@PlaybackService, player)
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!isPlaying) {
                    player.pauseAtEndOfMediaItems = false // an "end of this song" timer is used up (or cancelled by pausing)
                    PlayLog.stopped(filesDir)
                    ResumeStore.save(filesDir, player)
                }
            }
        })
        session = MediaLibrarySession.Builder(this, player, object : MediaLibrarySession.Callback {
                // Media3 drops the URI when a controller sends items, so put it back from requestMetadata; Android Auto
                // sends only the id, so take the whole item from the last browse.
                override fun onAddMediaItems(
                    mediaSession: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    mediaItems: MutableList<MediaItem>,
                ): ListenableFuture<MutableList<MediaItem>> = Futures.immediateFuture(mediaItems.map(::resolve).toMutableList())

                // Picking one song in the car plays the rest of its folder after it, as in the app.
                override fun onSetMediaItems(
                    mediaSession: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    mediaItems: MutableList<MediaItem>,
                    startIndex: Int,
                    startPositionMs: Long,
                ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
                    val one = mediaItems.singleOrNull()
                    val folder = one?.takeIf { controller.packageName != packageName }
                        ?.let { item -> browsed.values.firstOrNull { list -> list.any { it.mediaId == item.mediaId } } }
                    val items = folder ?: mediaItems.map(::resolve)
                    val index = if (folder != null) folder.indexOfFirst { it.mediaId == one!!.mediaId } else startIndex
                    return Futures.immediateFuture(MediaSession.MediaItemsWithStartPosition(items, index, startPositionMs))
                }

                override fun onGetLibraryRoot(
                    session: MediaLibrarySession,
                    browser: MediaSession.ControllerInfo,
                    params: LibraryParams?,
                ): ListenableFuture<LibraryResult<MediaItem>> = Futures.immediateFuture(LibraryResult.ofItem(folder(ROOT, "SecretMelody"), params))

                override fun onGetChildren(
                    session: MediaLibrarySession,
                    browser: MediaSession.ControllerInfo,
                    parentId: String,
                    page: Int,
                    pageSize: Int,
                    params: LibraryParams?,
                ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = io.submit<LibraryResult<ImmutableList<MediaItem>>> {
                    val all = children(parentId)
                    val from = (page * pageSize).coerceIn(0, all.size)
                    LibraryResult.ofItemList(all.subList(from, (from + pageSize).coerceAtMost(all.size)), params)
                }

                override fun onGetItem(
                    session: MediaLibrarySession,
                    browser: MediaSession.ControllerInfo,
                    mediaId: String,
                ): ListenableFuture<LibraryResult<MediaItem>> = Futures.immediateFuture(LibraryResult.ofItem(resolve(MediaItem.Builder().setMediaId(mediaId).build()), null))

                override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
                    val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                        .add(SessionCommand(CMD_SLEEP, Bundle.EMPTY))
                        .add(SessionCommand(CMD_AUDIO, Bundle.EMPTY))
                        .build()
                    return MediaSession.ConnectionResult.AcceptedResultBuilder(session).setAvailableSessionCommands(commands).build()
                }

                override fun onCustomCommand(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    customCommand: SessionCommand,
                    args: Bundle,
                ): ListenableFuture<SessionResult> {
                    when (customCommand.customAction) {
                        CMD_SLEEP -> setSleep(args.getInt("minutes"))
                        CMD_AUDIO -> effects?.apply(audioSettingsFrom(args))
                        else -> return super.onCustomCommand(session, controller, customCommand, args)
                    }
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

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = session

    /** A browsable folder for Android Auto. */
    private fun folder(id: String, title: String) = MediaItem.Builder()
        .setMediaId(id)
        .setMediaMetadata(
            MediaMetadata.Builder().setTitle(title).setIsBrowsable(true).setIsPlayable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED).build()
        )
        .build()

    /** The car's view of the library: the same songs, edits, likes and playlists as the app. Runs on [io]. */
    private fun children(parentId: String): List<MediaItem> {
        val library = Library(File(filesDir, "library.json")) // read only here: the app is its only writer
        if (parentId == ROOT) {
            return listOf(folder("all", "เพลงทั้งหมด"), folder("liked", "เพลงที่ถูกใจ"), folder("recent", "เล่นล่าสุด"), folder("most", "ฟังบ่อยที่สุด")) +
                library.playlists.map { folder("pl:${it.id}", it.name) }
        }
        val edits = Edits(File(filesDir, "edits.json"), File(filesDir, "covers"))
        val tracks = loadTracks(this).withEdits(edits.map)
        val byKey = tracks.associateBy { it.key }
        val byId = tracks.associateBy { it.id.toString() }
        val songs = when {
            parentId == "all" -> tracks
            parentId == "liked" -> library.liked.mapNotNull { byKey[it] }
            parentId == "recent" -> readRecent(filesDir).mapNotNull { byId[it] }
            parentId == "most" -> mostPlayed(tracks, PlayStats.read(filesDir))
            parentId.startsWith("pl:") -> library.playlists.find { "pl:${it.id}" == parentId }?.trackIds?.mapNotNull { byKey[it] }.orEmpty()
            else -> emptyList()
        }
        val items = songs.map { t ->
            t.toMediaItem().let { it.buildUpon().setMediaMetadata(it.mediaMetadata.buildUpon().setIsBrowsable(false).setIsPlayable(true).setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC).build()).build() }
        }
        browsed = browsed + (parentId to items)
        return items
    }

    /** Fills in an item that arrived with only its id (from Android Auto) or without its URI (any controller). */
    private fun resolve(item: MediaItem): MediaItem {
        item.requestMetadata.mediaUri?.let { return item.buildUpon().setUri(it).build() } // from the app: keep its metadata
        browsed.values.firstNotNullOfOrNull { list -> list.find { it.mediaId == item.mediaId } }?.let { return it }
        val uri = item.mediaId.toLongOrNull()?.let { ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, it) }
        return item.buildUpon().setUri(uri).build()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        val playing = player?.isPlaying == true
        PlayLog.swiped(filesDir, playing)
        // Keep going if music is playing (or about to); stop only when idle, so an idle service doesn't linger.
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        io.shutdown()
        handler.removeCallbacks(heartbeat)
        handler.removeCallbacks(sleep)
        session?.player?.let { ResumeStore.save(filesDir, it) }
        effects?.release()
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }
}
