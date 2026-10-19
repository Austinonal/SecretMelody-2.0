package com.secretmelody

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import kotlin.math.abs

data class LyricLine(val ms: Long, val text: String)

/** [synced] when the source has timings, else [plain] text; both null = none found. */
data class Lyrics(val synced: List<LyricLine>?, val plain: String?)

private val TIME_TAG = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]""")

/** Parses LRC ("[01:02.50]line"); a line may carry several time tags; tags like [ar:…] are skipped. */
fun parseLrc(lrc: String): List<LyricLine> = lrc.lines().flatMap { line ->
    val tags = TIME_TAG.findAll(line).toList()
    if (tags.isEmpty()) return@flatMap emptyList()
    val text = line.substring(tags.last().range.last + 1).trim()
    tags.map { m ->
        val (min, sec, frac) = m.destructured
        val fracMs = when (frac.length) { 0 -> 0; 1 -> frac.toInt() * 100; 2 -> frac.toInt() * 10; else -> frac.take(3).toInt() }
        LyricLine(min.toLong() * 60_000 + sec.toLong() * 1000 + fracMs, text)
    }
}.sortedBy { it.ms }

/** Index of the line being sung at [posMs]; -1 before the first. */
fun currentLine(lines: List<LyricLine>, posMs: Long): Int = lines.indexOfLast { it.ms <= posMs }

private fun fromLrclib(o: JSONObject): Lyrics? {
    val synced = o.optString("syncedLyrics").takeIf { it.isNotBlank() && it != "null" }?.let(::parseLrc)?.takeIf { it.isNotEmpty() }
    val plain = o.optString("plainLyrics").takeIf { it.isNotBlank() && it != "null" }
    return if (synced == null && plain == null) null else Lyrics(synced, plain)
}

/** From LRCLIB search results: synced first, then the closest length within 5 s. */
fun pickLyrics(results: JSONArray, durationSec: Double): Lyrics? =
    (0 until results.length()).map { results.getJSONObject(it) }
        .filter { abs(it.optDouble("duration", 0.0) - durationSec) <= 5 }
        .sortedWith(compareBy<JSONObject> { it.optString("syncedLyrics").isBlank() || it.optString("syncedLyrics") == "null" }
            .thenBy { abs(it.optDouble("duration", 0.0) - durationSec) })
        .firstNotNullOfOrNull(::fromLrclib)

private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

/** Free, keyless lyrics from lrclib.net; null when there are none. Blocking; call on Dispatchers.IO. */
fun fetchLyrics(t: Track): Lyrics? {
    val sec = t.durationMs / 1000.0
    val artist = t.artist.takeUnless { it == "ไม่ทราบศิลปิน" }.orEmpty()
    if (artist.isNotEmpty()) {
        runCatching {
            val url = "https://lrclib.net/api/get?track_name=${enc(t.title)}&artist_name=${enc(artist)}&duration=${sec.toLong()}"
            fromLrclib(JSONObject(String(httpGet(url), Charsets.UTF_8)))
        }.getOrNull()?.let { return it }
    }
    // Exact lookup missed (often a slightly different title): search and match by length.
    val q = listOf(t.title, artist).filter { it.isNotBlank() }.joinToString(" ")
    return pickLyrics(JSONArray(String(httpGet("https://lrclib.net/api/search?q=${enc(q)}"), Charsets.UTF_8)), sec)
}

/** Found lyrics are kept in app storage, so a song is looked up once. Misses are not kept, so they are tried again later. */
object LyricsCache {
    private fun file(dir: File, t: Track) = File(dir, "lyrics/${t.key.hashCode().toUInt()}-${t.title.hashCode().toUInt()}.json")

    fun read(dir: File, t: Track): Lyrics? = runCatching { fromLrclib(JSONObject(file(dir, t).readText())) }.getOrNull()

    fun save(dir: File, t: Track, l: Lyrics) {
        runCatching {
            val f = file(dir, t)
            f.parentFile?.mkdirs()
            val lrc = l.synced?.joinToString("\n") { "[%02d:%02d.%02d]%s".format(it.ms / 60_000, it.ms / 1000 % 60, it.ms % 1000 / 10, it.text) }
            f.writeText(JSONObject().put("syncedLyrics", lrc ?: "").put("plainLyrics", l.plain ?: "").toString())
        }
    }
}
