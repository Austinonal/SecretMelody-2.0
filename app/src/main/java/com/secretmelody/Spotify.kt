package com.secretmelody

import android.content.Context
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import com.yausername.youtubedl_android.YoutubeDLRequest
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.exp

// Spotify audio is DRM-protected, so a Spotify link only names the song: we read its title, artists, length and
// cover from Spotify's public embed page, find the same recording on YouTube and convert that (as spotDL does).

/** null = not a Spotify track link. */
fun spotifyTrackId(raw: String): String? =
    Regex("""open\.spotify\.com/(?:intl-[a-z-]+/)?track/([A-Za-z0-9]{22})(?![A-Za-z0-9])""").find(raw.trim())?.groupValues?.get(1)

class SpotifyTrack(val title: String, val artists: List<String>, val durationSec: Double, val coverUrl: String?)

/** Reads the track from the JSON Spotify's embed page carries (no API key needed). */
fun parseSpotifyEmbed(html: String): SpotifyTrack {
    val json = Regex("""<script id="__NEXT_DATA__" type="application/json">(.*?)</script>""", RegexOption.DOT_MATCHES_ALL)
        .find(html)?.groupValues?.get(1) ?: throw ImportException("อ่านข้อมูลเพลงจาก Spotify ไม่ได้")
    val e = JSONObject(json).getJSONObject("props").getJSONObject("pageProps").getJSONObject("state")
        .getJSONObject("data").getJSONObject("entity")
    val artists = e.optJSONArray("artists")?.let { a -> (0 until a.length()).map { a.getJSONObject(it).optString("name") } }.orEmpty()
    val images = e.optJSONObject("visualIdentity")?.optJSONArray("image")
    val cover = images?.let { a -> (0 until a.length()).map { a.getJSONObject(it) }.maxByOrNull { it.optInt("maxWidth") }?.optString("url") }
    return SpotifyTrack(e.optString("name"), artists.filter { it.isNotBlank() }, e.optDouble("duration", 0.0) / 1000, cover)
}

/** Blocking; call on Dispatchers.IO. */
fun spotifyTrack(id: String): SpotifyTrack =
    parseSpotifyEmbed(String(httpGet("https://open.spotify.com/embed/track/$id"), Charsets.UTF_8))

class YtCandidate(val id: String, val title: String, val channel: String, val durationSec: Double?)

// Matching rules ported from spotDL (MIT, https://github.com/spotDL/spotify-downloader, spotdl/utils/matching.py).
private val FORBIDDEN_WORDS = listOf(
    "bassboost", "remix", "remaster", "reverb", "live", "acoustic", "8d", "concert", "acapella", "slowed",
    "instrumental", "cover", "karaoke", "sped", "nightcore",
)

/** Lowercase words, punctuation dropped; letters of any script (Thai too) are kept. */
private fun words(s: String) = Regex("""[\p{L}\p{N}\p{M}]+""").findAll(s.lowercase()).map { it.value }.toList()

/** How sure we are that [c] is the same recording as [t], 0..100; null = not the song (or a different version). */
fun matchScore(t: SpotifyTrack, c: YtCandidate): Double? {
    val song = words(t.title)
    if (song.isEmpty()) return null
    val result = words(c.title)
    val resultFlat = result.joinToString("")
    if (song.none { it in resultFlat }) return null
    val resultText = result.joinToString(" ")
    var name = 100.0 * song.count { it in resultText } / song.size // substring, as Thai titles often have no spaces
    val songFlat = song.joinToString("")
    FORBIDDEN_WORDS.forEach { if (it in resultFlat && it !in songFlat) name -= 15 }
    val channelFlat = words(c.channel).joinToString("")
    val artist = if (t.artists.any { a -> words(a).joinToString("").let { it.isNotEmpty() && (it in resultFlat || it in channelFlat) } }) 100.0 else 0.0
    val duration = c.durationSec ?: return null
    if (name <= 60 || artist < 70) return null
    val time = 100 * exp(-0.1 * abs(t.durationSec - duration))
    if (time < 25) return null // more than ~14 s off: a video with an intro, a live take…
    val avg = (artist + name) / 2
    if (time < 50 && avg < 75) return null
    // A sure name/artist match keeps its score; only weaker ones lean on the length (spotDL does the same).
    return minOf(if (avg <= 85) (avg + time) / 2 else avg, 100.0)
}

/** Best first (closest length breaks ties); only results that look like the same song. */
fun rankCandidates(t: SpotifyTrack, results: List<YtCandidate>): List<YtCandidate> =
    results.mapNotNull { c -> matchScore(t, c)?.let { c to it } }
        .sortedWith(compareByDescending<Pair<YtCandidate, Double>> { it.second }.thenBy { abs(t.durationSec - it.first.durationSec!!) })
        .map { it.first }

fun parseSearch(json: String): List<YtCandidate> {
    val entries = JSONObject(json).optJSONArray("entries") ?: return emptyList()
    return (0 until entries.length()).map { entries.getJSONObject(it) }.mapNotNull { e ->
        val id = e.optString("id").takeIf { youtubeId("youtu.be/$it") == it } ?: return@mapNotNull null
        YtCandidate(id, e.optString("title"), e.optString("channel"), e.optDouble("duration").takeIf { !it.isNaN() })
    }
}

/** Searches YouTube with yt-dlp for the Spotify song. Blocking; call inside runInterruptible(Dispatchers.IO). */
fun findOnYoutube(context: Context, t: SpotifyTrack): List<YtCandidate> {
    initOnce(context)
    // "ytsearch10:" keeps the whole query one argument that yt-dlp reads as a search, never as an option.
    val request = YoutubeDLRequest("ytsearch10:${t.artists.joinToString(" ")} - ${t.title}")
        .addOption("--flat-playlist").addOption("-J")
    val out = try {
        YoutubeDL.getInstance().execute(request).out
    } catch (e: YoutubeDLException) {
        throw ImportException("ค้นหาเพลงบน YouTube ไม่ได้ ลองใหม่ภายหลัง")
    }
    return rankCandidates(t, parseSearch(out))
}
