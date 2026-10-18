package com.secretmelody

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Link imports live here, not in the screen, so a long conversion (a whole album) carries on when the screen is
 * closed: ConvertService keeps the process alive while [state] says it is running.
 */
object Convert {
    val state = LinkImportState()
    var reload by mutableIntStateOf(0) // bumps when a song lands, so the library rescans
        private set
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    private var edits: Edits? = null

    /** One Edits for the whole process: a second copy loaded by a reopened screen would overwrite what this one saved. */
    fun edits(context: Context): Edits = edits ?: Edits(File(context.filesDir, "edits.json"), File(context.filesDir, "covers")).also { edits = it }

    fun cancel() = job?.cancel()

    fun start(context: Context) {
        val app = context.applicationContext
        val edits = edits(app)
        state.running = true
        state.error = null
        state.resultId = null
        state.done = 0
        state.total = 0
        state.batchResult = null
        state.batchTotal = 0
        val batch = state.list?.takeIf { spotifyListPath(state.url) == state.listFor }
            ?.items?.filter { it.id in state.picked }
        val fromSpotify = spotifyTrackId(state.url) != null
        // A Spotify link converts the YouTube recording picked on the screen.
        val videoId = youtubeId(state.url) ?: state.chosen.takeIf { fromSpotify }
        state.percent = if (videoId != null || batch != null) 0f else null
        app.startForegroundService(Intent(app, ConvertService::class.java)) // notification + keeps going with the screen off
        job = scope.launch {
            try {
                if (batch != null) {
                    // Each song goes the single-Spotify-song way: its own page, the best YouTube match, MP3, then its edits.
                    val failed = mutableListOf<String>()
                    var skipped = 0
                    state.batchTotal = batch.size
                    batch.forEachIndexed { i, item ->
                        state.batchIndex = i
                        state.batchTitle = item.title
                        state.percent = 0f
                        // Checked before any download or search, so re-running an album costs next to nothing.
                        if (withContext(Dispatchers.IO) { alreadyHave(app, edits, item) }) {
                            skipped++
                            return@forEachIndexed
                        }
                        try {
                            val t = runInterruptible(Dispatchers.IO) { spotifyTrack(item.id) }
                            val best = runInterruptible(Dispatchers.IO) { findOnYoutube(app, t) }.firstOrNull()
                                ?: throw ImportException("ไม่เจอบน YouTube")
                            val cover = withContext(Dispatchers.IO) { t.coverUrl?.let { u -> runCatching { downloadBitmap(u) }.getOrNull() } }
                            val r = runInterruptible(Dispatchers.IO) {
                                convertYoutube(app, best.id, YtFormat.MP3, t.title, best.durationSec, embedCover = cover == null) { state.percent = it }
                            }
                            edits.setText(r.key, t.title, t.artists.joinToString(", "))
                            cover?.let { withContext(Dispatchers.IO) { edits.setCover(r.key, centerSquare(it)) } }
                            reload++
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Throwable) {
                            failed += item.title // one bad song shouldn't stop the rest
                        }
                    }
                    state.batchResult = batchSummary(batch.size, skipped, failed)
                    state.batchTotal = 0
                } else if (videoId != null) {
                    val preview = state.takeIf { it.previewId == previewKey(it.url) }
                    val format = if (fromSpotify) YtFormat.MP3 else state.format
                    val knownLength = state.candidates?.find { it.id == videoId }?.durationSec?.takeIf { fromSpotify }
                    val result = runInterruptible(Dispatchers.IO) {
                        convertYoutube(app, videoId, format, preview?.title, knownLength, embedCover = preview?.cover == null) { state.percent = it }
                    }
                    if (result.isVideo) toast(app, "บันทึกวิดีโอไว้ที่ Movies/SecretMelody แล้ว")
                    else {
                        // The name, artist and cover chosen on the card, stored like any later edit.
                        if (preview != null) {
                            edits.setText(result.key, preview.title, preview.artist)
                            preview.cover?.let { c -> withContext(Dispatchers.IO) { edits.setCover(result.key, c) } }
                        }
                        reload++
                        state.resultId = result.id
                    }
                } else {
                    val id = runInterruptible(Dispatchers.IO) {
                        downloadAudio(app, state.url) { done, total ->
                            state.done = done
                            state.total = total
                        }
                    }
                    reload++
                    state.resultId = id
                }
                state.url = ""
            } catch (e: CancellationException) {
                state.error = "ยกเลิกแล้ว"
            } catch (e: ImportException) {
                state.error = e.message
            } catch (e: Throwable) { // also Errors from the yt-dlp library, so a surprise shows here instead of closing the app
                state.error = "นำเข้าไม่สำเร็จ: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                state.running = false
            }
        }
    }

    /**
     * Same file name the conversion would give it ("title.mp3") and, if the app saved an artist for that file, the same
     * artist: so a second "Intro" from another album is still converted.
     */
    private fun alreadyHave(context: Context, edits: Edits, item: SpotifyListItem): Boolean {
        val name = safeFileName(item.title) + ".mp3"
        val found = context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.Audio.Media._ID),
            "${MediaStore.Audio.Media.DISPLAY_NAME} = ? COLLATE NOCASE", arrayOf(name), null,
        )?.use { it.count > 0 } ?: false
        return found && sameArtist(edits.map[name.lowercase()]?.artist, item.artists)
    }

    /** One line for the notification. */
    fun progressText(): String {
        val s = state
        val p = s.percent?.let { " ${(it * 100).toInt()}%" }.orEmpty()
        return if (s.batchTotal > 0) "เพลงที่ ${s.batchIndex + 1}/${s.batchTotal}: ${s.batchTitle}$p" else "กำลังแปลง$p"
    }
}

/** null saved artist = the file came from elsewhere, so the name alone decides. */
fun sameArtist(saved: String?, artists: String) = saved == null || saved.trim().equals(artists.trim(), ignoreCase = true)

fun batchSummary(total: Int, skipped: Int, failed: List<String>): String =
    "แปลงเสร็จ ${total - skipped - failed.size} จาก $total เพลง" +
        (if (skipped > 0) "\nข้าม $skipped เพลงที่มีอยู่แล้ว" else "") +
        (if (failed.isEmpty()) "" else "\nไม่สำเร็จ: ${failed.joinToString(", ")}")

private fun toast(context: Context, text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

/** Foreground while a link import runs: a progress notification with a cancel button, and the CPU kept awake. */
class ConvertService : Service() {
    private val scope = MainScope()
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) Convert.cancel()
        startForeground(NOTIFICATION_ID, notification(Convert.progressText()), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        if (wakeLock == null) {
            val lock = getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SecretMelody:convert").apply { setReferenceCounted(false) }
            wakeLock = lock
            scope.launch {
                val nm = getSystemService(NotificationManager::class.java)
                var shown = ""
                while (Convert.state.running) {
                    val text = Convert.progressText()
                    if (text != shown) {
                        shown = text
                        nm.notify(NOTIFICATION_ID, notification(text))
                        lock.acquire(15 * 60 * 1000L) // renewed on progress: a job stuck for 15 min lets the phone sleep
                    }
                    delay(1000) // reading the state once a second is cheaper than a notification per progress line
                }
                stopSelf()
            }
        }
        return START_NOT_STICKY // the job lives in this process; after a kill there is nothing to resume
    }

    // Android 15 limits dataSync services to 6 h a day.
    override fun onTimeout(startId: Int, fgsType: Int) {
        Convert.cancel()
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        wakeLock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }

    private fun notification(text: String): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "แปลงเพลง", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val cancel = PendingIntent.getService(
            this, 1, Intent(this, ConvertService::class.java).setAction(ACTION_CANCEL), PendingIntent.FLAG_IMMUTABLE,
        )
        val percent = Convert.state.percent
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.media3_notification_small_icon)
            .setContentTitle("SecretMelody กำลังแปลงเพลง")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .setProgress(100, ((percent ?: 0f) * 100).toInt(), percent == null)
            .addAction(Notification.Action.Builder(null, "ยกเลิก", cancel).build())
            .build()
    }

    private companion object {
        const val CHANNEL = "convert"
        const val NOTIFICATION_ID = 2
        const val ACTION_CANCEL = "com.secretmelody.CANCEL_CONVERT"
    }
}
