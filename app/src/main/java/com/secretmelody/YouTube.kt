package com.secretmelody

import android.content.Context
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.io.File
import java.util.UUID

enum class YtFormat { MP3, MP4 }

data class YtResult(val id: Long, val isVideo: Boolean)

private const val MP3_MAX_MINUTES = 240
private const val MP4_MAX_MINUTES = 30

/** null = not a YouTube video link. */
fun youtubeId(raw: String): String? =
    Regex("""(?:youtube\.com/(?:watch\?(?:.*&)?v=|shorts/)|youtu\.be/)([A-Za-z0-9_-]{11})(?![A-Za-z0-9_-])""")
        .find(raw.trim())?.groupValues?.get(1)

/** Refuses videos too long for the format, and ones with no length (live now, premiere not started). */
fun checkDuration(seconds: Long?, format: YtFormat) {
    if (seconds == null || seconds <= 0) throw ImportException("วิดีโอนี้แปลงไม่ได้")
    val max = if (format == YtFormat.MP3) MP3_MAX_MINUTES else MP4_MAX_MINUTES
    if (seconds > max * 60L) throw ImportException("วิดีโอยาวเกินไป MP3 ได้ไม่เกิน $MP3_MAX_MINUTES นาที / MP4 ไม่เกิน $MP4_MAX_MINUTES นาที")
}

private val initLock = Any()
private var ready = false

/** First run unpacks Python and ffmpeg, which takes a while: never call on the main thread. */
private fun initOnce(context: Context) = synchronized(initLock) {
    if (!ready) {
        YoutubeDL.getInstance().init(context.applicationContext)
        FFmpeg.getInstance().init(context.applicationContext)
        ready = true
    }
}

/**
 * Converts one YouTube video on the phone (yt-dlp + ffmpeg) and saves it through MediaStore:
 * MP3 into Music/SecretMelody (title and cover embedded), MP4 360p into Movies/SecretMelody.
 * Blocking: call it inside runInterruptible(Dispatchers.IO) so cancelling stops the conversion.
 */
fun convertYoutube(context: Context, videoId: String, format: YtFormat, onProgress: (Float) -> Unit): YtResult {
    initOnce(context)
    val ytdl = YoutubeDL.getInstance()
    // Only a URL rebuilt from the id reaches yt-dlp, so nothing else the user typed becomes an option.
    val url = "https://www.youtube.com/watch?v=$videoId"
    // YouTube changes often; an old bundled yt-dlp is the usual cause of failures, so update it once and retry.
    var updated = false
    fun <T> withUpdate(message: String, block: () -> T): T {
        try {
            return block()
        } catch (e: YoutubeDLException) {
            if (updated) throw ImportException(message)
            updated = true
            runCatching { ytdl.updateYoutubeDL(context.applicationContext, YoutubeDL.UpdateChannel.STABLE) }
        }
        return try { block() } catch (e: YoutubeDLException) { throw ImportException(message) }
    }

    val info = withUpdate("วิดีโอนี้แปลงไม่ได้") { ytdl.getInfo(YoutubeDLRequest(url).addOption("--no-playlist")) }
    checkDuration(info.duration.toLong(), format)
    val ext = if (format == YtFormat.MP3) "mp3" else "mp4"
    val dir = File(context.cacheDir, "yt/$videoId").apply { deleteRecursively(); mkdirs() }
    try {
        val request = YoutubeDLRequest(url).apply {
            addOption("--no-playlist")
            addOption("-o", "${dir.absolutePath}/$videoId.%(ext)s")
            if (format == YtFormat.MP3) {
                addOption("-f", "bestaudio")
                addOption("-x")
                addOption("--audio-format", "mp3")
                addOption("--audio-quality", "192K")
                addOption("--embed-metadata")
                addOption("--embed-thumbnail") // the cover the library shows
                addOption("--convert-thumbnails", "jpg")
            } else {
                addOption("-f", "bv*[height<=360][ext=mp4]+ba[ext=m4a]/b[height<=360][ext=mp4]/b[height<=360]")
                addOption("--merge-output-format", "mp4")
            }
        }
        val processId = UUID.randomUUID().toString()
        withUpdate("แปลงไม่สำเร็จ ลองใหม่ภายหลัง") {
            try {
                // yt-dlp reports 100% before ffmpeg finishes converting, so hold at 99% until it is done.
                ytdl.execute(request, processId) { progress, _, _ -> onProgress(progress.coerceIn(0f, 99f) / 100f) }
            } catch (e: InterruptedException) {
                ytdl.destroyProcessById(processId)
                throw e
            }
        }
        val out = dir.listFiles()?.firstOrNull { it.extension == ext } ?: throw ImportException("แปลงไม่สำเร็จ ลองใหม่ภายหลัง")
        val name = safeFileName(info.title.orEmpty()) + ".$ext"
        val isVideo = format == YtFormat.MP4
        val id = saveToMusic(context, name, video = isVideo) { o -> out.inputStream().use { it.copyTo(o) } }
        return YtResult(id, isVideo)
    } finally {
        dir.deleteRecursively()
    }
}
