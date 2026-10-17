package com.secretmelody

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject
import java.io.File

/** What the user changed about one song. A null field means "keep what the file says". */
data class TrackEdit(val title: String? = null, val artist: String? = null, val cover: String? = null)

/** Applies [edits] on top of what MediaStore reports. The audio files themselves are never touched. */
fun List<Track>.withEdits(edits: Map<String, TrackEdit>): List<Track> =
    if (edits.isEmpty()) this else map { t ->
        val e = edits[t.key] ?: return@map t
        t.copy(title = e.title ?: t.title, artist = e.artist ?: t.artist, coverPath = e.cover)
    }

/** Edited titles, artists and covers, keyed by Track.key, in a small JSON file plus a covers folder in app storage. */
class Edits(private val file: File, val coversDir: File) {
    var map by mutableStateOf<Map<String, TrackEdit>>(emptyMap())
        private set

    init {
        runCatching { load(JSONObject(file.readText())) }
    }

    fun setText(key: String, title: String, artist: String) = update(key) {
        it.copy(title = title.trim().ifBlank { null }, artist = artist.trim().ifBlank { null })
    }

    /** Keeps an already-cropped cover (at most 800px). Call off the main thread. */
    fun setCover(key: String, bitmap: Bitmap) {
        coversDir.mkdirs()
        val out = File(coversDir, "${key.hashCode().toUInt()}-${System.currentTimeMillis()}.jpg") // new name each time, so cached images refresh
        out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        map[key]?.cover?.let { File(it).delete() }
        update(key) { it.copy(cover = out.path) }
    }

    fun resetCover(key: String) {
        map[key]?.cover?.let { File(it).delete() }
        update(key) { it.copy(cover = null) }
    }

    /** One-time upgrade of edits saved by versions that keyed them by MediaStore id. */
    fun migrate(keyFor: (Long) -> String?) {
        val old = map.keys.filter { it.startsWith("#") }
        if (old.isEmpty()) return
        map = map.entries.associate { (k, v) -> (if (k.startsWith("#")) k.drop(1).toLongOrNull()?.let(keyFor) ?: k else k) to v }
        save()
    }

    /** Replaces everything with a backup's contents; cover paths must already point into [coversDir]. */
    fun restore(json: JSONObject) {
        load(json)
        save()
    }

    fun toJson(): JSONObject = JSONObject().also { json ->
        map.forEach { (k, e) ->
            json.put(k, JSONObject().put("t", e.title ?: "").put("a", e.artist ?: "").put("c", e.cover?.let { File(it).name } ?: ""))
        }
    }

    private fun load(json: JSONObject) {
        map = json.keys().asSequence().associate { key ->
            val o = json.getJSONObject(key)
            val cover = o.optString("c").ifBlank { null }?.let { if (it.contains('/')) File(it) else File(coversDir, it) }
            // Keys that are plain numbers come from older versions (MediaStore ids).
            (if (key.all(Char::isDigit)) "#$key" else key) to TrackEdit(
                o.optString("t").ifBlank { null },
                o.optString("a").ifBlank { null },
                cover?.takeIf { it.exists() }?.path,
            )
        }
    }

    private fun update(key: String, change: (TrackEdit) -> TrackEdit) {
        val next = change(map[key] ?: TrackEdit())
        map = if (next == TrackEdit()) map - key else map + (key to next)
        save()
    }

    private fun save() {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(toJson().toString())
        tmp.renameTo(file)
    }
}

/** Reads an image the user picked, scaled so its longer side is at most [maxSide]. Call off the main thread. */
fun decodeImage(context: Context, uri: Uri, maxSide: Int = 1600): Bitmap? = decodeScaled(context, uri, maxSide)

private fun decodeScaled(context: Context, uri: Uri, maxSide: Int): Bitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (bounds.outWidth / sample > maxSide * 2 || bounds.outHeight / sample > maxSide * 2) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    val bmp = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return@runCatching null
    val scale = maxSide.toFloat() / maxOf(bmp.width, bmp.height)
    if (scale >= 1f) bmp else Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true)
}.getOrNull()
