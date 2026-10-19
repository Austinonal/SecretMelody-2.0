package com.secretmelody

import android.content.Context
import android.webkit.CookieManager
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

// Signing in to YouTube: the user signs in on Google's own page inside the app; the session cookies it leaves are
// handed to yt-dlp, which reads the account's liked songs and playlists. Nothing is sent anywhere but YouTube.

/** One synced list: liked songs or one of the account's playlists. */
data class YtList(val id: String, val title: String, val tracks: List<Track>)

data class YtLibrary(val syncedAt: Long, val lists: List<YtList>)

/**
 * The session cookies are as good as the Google sign-in, so they live in noBackupFilesDir: private to the app and
 * never copied into Android's cloud backup.
 */
private fun cookiesFile(context: Context) = File(context.noBackupFilesDir, "yt-cookies.txt")
private fun libraryFile(context: Context) = File(context.filesDir, "yt-library.json")

/** "a=1; b=2" from the browser → the Netscape cookies file yt-dlp reads. */
fun netscapeCookies(cookieHeader: String, domain: String = ".youtube.com"): String = buildString {
    append("# Netscape HTTP Cookie File\n")
    cookieHeader.split(';').map { it.trim() }.filter { '=' in it }.forEach { c ->
        val (name, value) = c.split('=', limit = 2)
        // The browser does not say when each cookie expires; a year ahead is fine, YouTube refreshes them anyway.
        append("$domain\tTRUE\t/\tTRUE\t${System.currentTimeMillis() / 1000 + 365L * 24 * 3600}\t$name\t$value\n")
    }
}

object YtAccount {
    fun isSignedIn(context: Context) = cookiesFile(context).let { it.exists() && it.readText().contains("SAPISID") }

    /** Called by the sign-in page once Google has sent the browser back to YouTube Music. True = signed in. */
    fun takeCookies(context: Context): Boolean {
        val header = CookieManager.getInstance().getCookie("https://music.youtube.com") ?: return false
        if ("SAPISID=" !in header) return false
        val f = cookiesFile(context)
        f.writeText(netscapeCookies(header))
        f.setReadable(false, false)
        f.setReadable(true, true)
        return true
    }

    fun signOut(context: Context) {
        cookiesFile(context).delete()
        libraryFile(context).delete()
        CookieManager.getInstance().removeAllCookies(null)
    }

    fun read(context: Context): YtLibrary? = runCatching { decodeYtLibrary(libraryFile(context).readText()) }.getOrNull()

    /** Reads liked songs and playlists from the account with yt-dlp. Blocking; call on Dispatchers.IO. */
    fun sync(context: Context): YtLibrary {
        if (!isSignedIn(context)) throw ImportException("ยังไม่ได้เข้าสู่ระบบ YouTube")
        initOnce(context)
        val cookies = cookiesFile(context).absolutePath
        fun flat(url: String, limit: Int): JSONObject {
            val request = YoutubeDLRequest(url).addOption("--flat-playlist").addOption("-J")
                .addOption("--cookies", cookies).addOption("-I", "1:$limit")
            return JSONObject(YoutubeDL.getInstance().execute(request).out)
        }
        val lists = mutableListOf<YtList>()
        // YouTube Music's liked songs; plain YouTube's liked videos if that fails.
        val liked = runCatching { flat("https://music.youtube.com/playlist?list=LM", 500) }
            .recoverCatching { flat("https://www.youtube.com/playlist?list=LL", 500) }
            .getOrElse { throw ImportException("อ่านบัญชี YouTube ไม่ได้ ลองเข้าสู่ระบบใหม่") }
        lists += YtList("LM", "เพลงที่ชอบบน YouTube", parseYtEntries(liked))
        runCatching { flat("https://www.youtube.com/feed/playlists", 30) }.getOrNull()?.let { feed ->
            playlistRefs(feed).forEach { (id, title) ->
                runCatching { flat("https://www.youtube.com/playlist?list=$id", 300) }.getOrNull()?.let {
                    lists += YtList(id, title, parseYtEntries(it))
                }
            }
        }
        val lib = YtLibrary(System.currentTimeMillis(), lists.filter { it.tracks.isNotEmpty() })
        libraryFile(context).writeText(encodeYtLibrary(lib))
        return lib
    }
}

/** Songs from yt-dlp's flat playlist JSON; the title is split into song and artist when it can be. */
fun parseYtEntries(playlist: JSONObject): List<Track> {
    val entries = playlist.optJSONArray("entries") ?: return emptyList()
    return (0 until entries.length()).mapNotNull { i ->
        val e = entries.optJSONObject(i) ?: return@mapNotNull null
        val id = e.optString("id").takeIf { youtubeId("youtu.be/$it") == it } ?: return@mapNotNull null
        val clip = e.optString("title").takeIf { it.isNotBlank() && it != "null" } ?: return@mapNotNull null
        if (clip == "[Private video]" || clip == "[Deleted video]") return@mapNotNull null
        val channel = listOf("channel", "uploader").map { e.optString(it) }.firstOrNull { it.isNotBlank() && it != "null" }.orEmpty()
        val (title, artist) = splitVideoTitle(clip, channel)
        ytTrack(id, title, artist, (e.optDouble("duration", 0.0) * 1000).toLong())
    }
}

/** (playlist id, title) pairs from the account's playlists page. */
fun playlistRefs(feed: JSONObject): List<Pair<String, String>> {
    val entries = feed.optJSONArray("entries") ?: return emptyList()
    return (0 until entries.length()).mapNotNull { i ->
        val e = entries.optJSONObject(i) ?: return@mapNotNull null
        val id = e.optString("id").takeIf { it.startsWith("PL") || it.startsWith("OLAK") } ?: return@mapNotNull null
        id to e.optString("title").ifBlank { "เพลย์ลิสต์" }
    }
}

fun encodeYtLibrary(lib: YtLibrary): String = JSONObject().put("syncedAt", lib.syncedAt).put(
    "lists",
    JSONArray(lib.lists.map { l ->
        JSONObject().put("id", l.id).put("title", l.title).put(
            "items",
            JSONArray(l.tracks.map { JSONObject().put("id", it.videoId).put("t", it.title).put("a", it.artist).put("ms", it.durationMs) }),
        )
    }),
).toString()

fun decodeYtLibrary(json: String): YtLibrary {
    val o = JSONObject(json)
    val lists = o.getJSONArray("lists")
    return YtLibrary(
        o.optLong("syncedAt"),
        (0 until lists.length()).map { i ->
            val l = lists.getJSONObject(i)
            val items = l.getJSONArray("items")
            YtList(l.getString("id"), l.getString("title"), (0 until items.length()).map { j ->
                val t = items.getJSONObject(j)
                ytTrack(t.getString("id"), t.getString("t"), t.getString("a"), t.optLong("ms"))
            })
        },
    )
}
