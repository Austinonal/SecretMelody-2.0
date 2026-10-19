package com.secretmelody

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import com.yausername.youtubedl_android.YoutubeDLRequest
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap

// YouTube songs are played straight from YouTube without being saved: a Track whose key is "yt:<video id>" and
// whose address is ytstream://<video id>, turned into a real stream address by the player only when it plays.

const val YT_KEY = "yt:"
private const val YT_SCHEME = "ytstream"

val Track.isStream: Boolean get() = key.startsWith(YT_KEY)
val Track.videoId: String get() = key.removePrefix(YT_KEY)

fun ytStreamUri(videoId: String): Uri = Uri.parse("$YT_SCHEME://$videoId")

/** A YouTube video as a song. Negative ids keep it apart from MediaStore's. */
fun ytTrack(videoId: String, title: String, artist: String, durationMs: Long, thumb: String? = null) = Track(
    id = -1L - (videoId.hashCode().toLong() and 0x7fffffff), title = title, artist = artist, album = "YouTube", albumId = -1,
    durationMs = durationMs, key = YT_KEY + videoId, remoteArt = thumb ?: "https://i.ytimg.com/vi/$videoId/mqdefault.jpg",
)

/** The song playing from YouTube, rebuilt from what the player holds (it is not in the phone's library). */
fun streamTrack(item: MediaItem): Track? {
    if (!item.mediaId.startsWith(YT_KEY)) return null
    val md = item.mediaMetadata
    return ytTrack(
        item.mediaId.removePrefix(YT_KEY), md.title?.toString().orEmpty(), md.artist?.toString().orEmpty(),
        md.durationMs ?: 0L, md.artworkUri?.toString(),
    )
}

/** "4:20", "1:02:03" → milliseconds; null if unreadable. */
fun parseClock(s: String): Long? {
    val parts = s.trim().split(':').map { it.toLongOrNull() ?: return null }
    if (parts.isEmpty() || parts.size > 3) return null
    return parts.fold(0L) { acc, p -> acc * 60 + p } * 1000
}

/** Videos from a YouTube search response (the web client's JSON), as songs. */
fun parseYtSearch(json: String): List<Track> {
    val sections = JSONObject(json).optJSONObject("contents")?.optJSONObject("twoColumnSearchResultsRenderer")
        ?.optJSONObject("primaryContents")?.optJSONObject("sectionListRenderer")?.optJSONArray("contents") ?: return emptyList()
    return (0 until sections.length()).flatMap { i ->
        val items = sections.getJSONObject(i).optJSONObject("itemSectionRenderer")?.optJSONArray("contents") ?: return@flatMap emptyList()
        (0 until items.length()).mapNotNull { j ->
            val v = items.getJSONObject(j).optJSONObject("videoRenderer") ?: return@mapNotNull null
            val id = v.optString("videoId").takeIf { youtubeId("youtu.be/$it") == it } ?: return@mapNotNull null
            val clip = v.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") ?: return@mapNotNull null
            val channel = v.optJSONObject("ownerText")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text").orEmpty()
            val length = v.optJSONObject("lengthText")?.optString("simpleText")?.let(::parseClock) ?: return@mapNotNull null // live streams have none
            val (title, artist) = splitVideoTitle(clip, channel)
            ytTrack(id, title, artist, length)
        }
    }
}

/** Searches YouTube videos, like the YouTube website does (no key). Blocking; call on Dispatchers.IO. */
fun searchYoutube(query: String): List<Track> {
    val version = "2." + SimpleDateFormat("yyyyMMdd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date()) + ".01.00"
    val body = JSONObject()
        .put("context", JSONObject().put("client", JSONObject().put("clientName", "WEB").put("clientVersion", version).put("hl", "th").put("gl", "TH")))
        .put("query", query)
        .put("params", "EgIQAQ==") // videos only
    val conn = URL("https://www.youtube.com/youtubei/v1/search?prettyPrint=false").openConnection() as HttpURLConnection
    try {
        conn.connectTimeout = 15_000
        conn.readTimeout = 20_000
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")
        conn.outputStream.use { it.write(body.toString().toByteArray()) }
        if (conn.responseCode != 200) throw ImportException("ค้นหาบน YouTube ไม่ได้ (${conn.responseCode})")
        return parseYtSearch(conn.inputStream.use { String(it.readBytes(), Charsets.UTF_8) })
    } finally {
        conn.disconnect()
    }
}

/**
 * Turns ytstream://<id> into YouTube's real audio address when the player opens it. The address lasts 6 hours,
 * so it is kept for 5 and asked again after that. Runs on the player's loading thread, in the playback process.
 */
class StreamResolver(private val context: Context) : ResolvingDataSource.Resolver {
    private class Found(val url: String, val headers: Map<String, String>, val until: Long)
    private val found = ConcurrentHashMap<String, Found>()

    override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
        if (dataSpec.uri.scheme != YT_SCHEME) return dataSpec
        val id = dataSpec.uri.host ?: throw IOException("no video id")
        val f = found[id]?.takeIf { it.until > System.currentTimeMillis() } ?: lookUp(id).also { found[id] = it }
        return dataSpec.buildUpon().setUri(Uri.parse(f.url)).setHttpRequestHeaders(f.headers).build()
    }

    private fun lookUp(id: String): Found {
        initOnce(context)
        val request = YoutubeDLRequest("https://www.youtube.com/watch?v=$id")
            .addOption("--no-playlist").addOption("-f", "bestaudio[ext=m4a]/bestaudio").addOption("-j")
        val out = try {
            YoutubeDL.getInstance().execute(request).out
        } catch (e: YoutubeDLException) {
            // An old yt-dlp is the usual cause; update once and try again.
            runCatching { YoutubeDL.getInstance().updateYoutubeDL(context.applicationContext, YoutubeDL.UpdateChannel.STABLE) }
            try { YoutubeDL.getInstance().execute(request).out } catch (e: YoutubeDLException) { throw IOException("เล่นจาก YouTube ไม่ได้", e) }
        }
        val json = JSONObject(out)
        val headers = json.optJSONObject("http_headers")?.let { h -> h.keys().asSequence().associateWith { h.getString(it) } }.orEmpty()
        return Found(json.getString("url"), headers, System.currentTimeMillis() + 5 * 60 * 60 * 1000L)
    }
}
