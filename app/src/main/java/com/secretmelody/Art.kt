package com.secretmelody

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import android.util.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A few MB of decoded covers so scrolling a long list doesn't decode the same thumbnails again and again. */
private val cache = object : LruCache<String, Bitmap>(16 * 1024) { // size counted in KB
    override fun sizeOf(key: String, value: Bitmap) = value.byteCount / 1024
}

/** The cover for a song: the picture the user chose, else the one inside the file. Null if there is none. */
suspend fun loadArt(context: Context, t: Track, px: Int): Bitmap? {
    val key = "${t.id}:${t.coverPath}:$px"
    cache.get(key)?.let { return it }
    val bitmap = withContext(Dispatchers.IO) {
        runCatching {
            val picked = t.coverPath
            if (picked != null) {
                val opts = BitmapFactory.Options().apply { inSampleSize = (800 / px).coerceAtLeast(1) }
                BitmapFactory.decodeFile(picked, opts)
            } else {
                context.contentResolver.loadThumbnail(t.uri, Size(px, px), null)
            }
        }.getOrNull()
    }
    if (bitmap != null) cache.put(key, bitmap)
    return bitmap
}

suspend fun loadArtImage(context: Context, t: Track, px: Int): ImageBitmap? = loadArt(context, t, px)?.asImageBitmap()
