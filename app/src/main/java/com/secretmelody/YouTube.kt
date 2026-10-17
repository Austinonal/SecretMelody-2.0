package com.secretmelody

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.provider.MediaStore
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import com.yausername.youtubedl_android.YoutubeDLRequest
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.util.UUID

enum class YtFormat { MP3, MP4 }

/** [key] is the saved file's name as Track.key spells it, so edits can be attached right away. */
data class YtResult(val id: Long, val isVideo: Boolean, val key: String)

/** What the link import card shows before converting. [thumb] is the full 16:9 picture the cover is cropped from. */
class YtPreview(val title: String, val artist: String, val thumb: Bitmap?)

/** Title and channel from YouTube's oEmbed JSON; auto-generated "Artist - Topic" channels lose the suffix. */
fun parseOembed(json: String): Pair<String, String> {
    val o = JSONObject(json)
    return o.optString("title") to o.optString("author_name").removeSuffix(" - Topic")
}

/** Fast (no yt-dlp): title, channel and thumbnail for the card. Blocking; call on Dispatchers.IO. */
fun youtubePreview(videoId: String): YtPreview {
    val watch = URLEncoder.encode("https://www.youtube.com/watch?v=$videoId", "UTF-8")
    val (title, artist) = parseOembed(String(httpGet("https://www.youtube.com/oembed?format=json&url=$watch"), Charsets.UTF_8))
    // maxresdefault is missing on some videos; mqdefault always exists. Both are 16:9 without black bars.
    val thumb = listOf("maxresdefault", "mqdefault").firstNotNullOfOrNull {
        runCatching { downloadBitmap("https://i.ytimg.com/vi/$videoId/$it.jpg") }.getOrNull()
    }
    return YtPreview(title, artist, thumb)
}

/** The middle square of a picture, as the cover is before the user moves it. */
fun centerSquare(b: Bitmap): Bitmap = cropBitmap(b, cropRect(b.width, b.height, 1000f, 1f, 0f, 0f))

private const val MP3_MAX_MINUTES = 240
private const val MP4_MAX_MINUTES = 30

/** null = not a YouTube video link. */
fun youtubeId(raw: String): String? =
    Regex("""(?:youtube\.com/(?:watch\?(?:.*&)?v=|shorts/)|youtu\.be/)([A-Za-z0-9_-]{11})(?![A-Za-z0-9_-])""")
        .find(raw.trim())?.groupValues?.get(1)

/** The link inside shared text (apps often add a title around it): a YouTube video in its plain form, else the first https link. */
fun linkFromShare(text: String): String? =
    youtubeId(text)?.let { "https://www.youtube.com/watch?v=$it" }
        ?: Regex("""https://\S+""").find(text)?.value

/** Refuses videos too long for the format, and ones with no length (live now, premiere not started). */
fun checkDuration(seconds: Long?, format: YtFormat) {
    if (seconds == null || seconds <= 0) throw ImportException("วิดีโอนี้แปลงไม่ได้")
    val max = if (format == YtFormat.MP3) MP3_MAX_MINUTES else MP4_MAX_MINUTES
    if (seconds > max * 60L) throw ImportException("วิดีโอยาวเกินไป MP3 ได้ไม่เกิน $MP3_MAX_MINUTES นาที / MP4 ไม่เกิน $MP4_MAX_MINUTES นาที")
}

private val initLock = Any()
private var ready = false

/** MediaStore renames on a clash ("Song (1).mp3"), so read back the name it really used. */
private fun savedName(context: Context, id: Long, video: Boolean): String? {
    val base = if (video) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
    context.contentResolver.query(ContentUris.withAppendedId(base, id), arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)
        ?.use { return if (it.moveToFirst()) it.getString(0) else null }
    return null
}

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
fun convertYoutube(context: Context, videoId: String, format: YtFormat, title: String?, onProgress: (Float) -> Unit): YtResult {
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
        val name = safeFileName(title?.takeIf { it.isNotBlank() } ?: info.title.orEmpty()) + ".$ext"
        val isVideo = format == YtFormat.MP4
        val id = saveToMusic(context, name, video = isVideo) { o -> out.inputStream().use { it.copyTo(o) } }
        return YtResult(id, isVideo, savedName(context, id, isVideo)?.lowercase() ?: name.lowercase())
    } finally {
        dir.deleteRecursively()
    }
}
