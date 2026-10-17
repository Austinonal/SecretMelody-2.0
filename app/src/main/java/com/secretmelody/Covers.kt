package com.secretmelody

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.min
import kotlin.math.roundToInt

/** One cover found online, with the song it belongs to so same-name songs can be told apart. */
data class CoverResult(val title: String, val artist: String, val album: String, val thumbUrl: String, val bigUrl: String)

/**
 * Covers come from Apple's public iTunes Search API (no key needed). Google has no free image-search API for apps,
 * so it can't be used here. The Thai store is searched so Thai songs are found too.
 */
fun coverSearchUrl(query: String) =
    "https://itunes.apple.com/search?term=${URLEncoder.encode(query.trim(), "UTF-8")}&media=music&entity=song&country=TH&limit=40"

fun parseCoverResults(json: String): List<CoverResult> {
    val arr = JSONObject(json).optJSONArray("results") ?: return emptyList()
    return (0 until arr.length()).mapNotNull { i ->
        val o = arr.optJSONObject(i) ?: return@mapNotNull null
        val thumb = o.optString("artworkUrl100").ifBlank { return@mapNotNull null }
        CoverResult(
            o.optString("trackName"), o.optString("artistName"), o.optString("collectionName"),
            thumb, thumb.replace("100x100bb", "600x600bb"),
        )
    }.distinctBy { it.bigUrl } // the same album cover appears once per song on it
}

/** https GET with a size cap. Blocking; call on Dispatchers.IO. */
fun httpGet(url: String, maxBytes: Int = 8 * 1024 * 1024): ByteArray {
    val u = URL(url)
    require(u.protocol == "https") { "https only" }
    val conn = u.openConnection() as HttpURLConnection
    try {
        conn.connectTimeout = 15_000
        conn.readTimeout = 20_000
        conn.setRequestProperty("User-Agent", "SecretMelody")
        if (conn.responseCode != 200) throw ImportException("เซิร์ฟเวอร์ตอบ ${conn.responseCode}")
        val out = ByteArrayOutputStream()
        conn.inputStream.use { input ->
            val buf = ByteArray(32 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                out.write(buf, 0, n)
                if (out.size() > maxBytes) throw ImportException("ไฟล์ใหญ่เกินไป")
            }
        }
        return out.toByteArray()
    } finally {
        conn.disconnect()
    }
}

fun searchCovers(query: String): List<CoverResult> = parseCoverResults(String(httpGet(coverSearchUrl(query)), Charsets.UTF_8))

private val remoteCache = object : LruCache<String, Bitmap>(8 * 1024) { // KB
    override fun sizeOf(key: String, value: Bitmap) = value.byteCount / 1024
}

/** Downloads and decodes an image. Blocking; call on Dispatchers.IO. Null if it isn't an image. */
fun downloadBitmap(url: String): Bitmap? {
    remoteCache.get(url)?.let { return it }
    val bytes = httpGet(url)
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.also { remoteCache.put(url, it) }
}

/** The square part of an image (in image pixels) that the crop box shows. */
data class CropRect(val left: Int, val top: Int, val size: Int)

/**
 * The image is first scaled so its shorter side fills the [view]-pixel box, then zoomed by [scale] and moved by
 * [offX]/[offY] screen pixels from centre. Returns the part of the image inside the box.
 */
fun cropRect(w: Int, h: Int, view: Float, scale: Float, offX: Float, offY: Float): CropRect {
    val k = view / min(w, h) * scale // screen pixels per image pixel
    val size = (view / k).roundToInt().coerceIn(1, min(w, h))
    val left = (w / 2f - (view / 2 + offX) / k).roundToInt().coerceIn(0, w - size)
    val top = (h / 2f - (view / 2 + offY) / k).roundToInt().coerceIn(0, h - size)
    return CropRect(left, top, size)
}

/** How far (screen pixels) the image may move from centre on each axis before the box would show past its edge. */
fun maxOffset(w: Int, h: Int, view: Float, scale: Float): Pair<Float, Float> {
    val k = view / min(w, h) * scale
    return ((w * k - view) / 2).coerceAtLeast(0f) to ((h * k - view) / 2).coerceAtLeast(0f)
}

/** Cuts the chosen square out and scales it to at most 800px. */
fun cropBitmap(src: Bitmap, r: CropRect): Bitmap {
    val square = Bitmap.createBitmap(src, r.left, r.top, r.size, r.size)
    return if (r.size <= 800) square else Bitmap.createScaledBitmap(square, 800, 800, true)
}
