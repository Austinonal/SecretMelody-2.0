package com.secretmelody

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.FileProvider
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val CARD_W = 1080
private const val PAD = 88f
private const val MAX_LINES = 8 // a card for stories, not the whole song; the full text goes along as text

/**
 * Lyrics as a picture for stories and chats: the cover, the lines and the song, on a colour from the cover (the same
 * darkened colour the player uses, so white text stays readable). The full lyrics go along as text too.
 */
suspend fun shareLyrics(context: Context, lyrics: String, t: Track) {
    val text = "${lyrics.trim()}\n\n— ${t.title} · ${t.artist}"
    val file = runCatching { withContext(Dispatchers.IO) { drawLyricsCard(context, lyrics, t) } }.getOrNull()
    val send = Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_TEXT, text)
    if (file == null) {
        send.setType("text/plain")
    } else {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.share", file)
        send.setType("image/png").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newRawUri(null, uri) // lets the chosen app read the picture
    }
    context.startActivity(Intent.createChooser(send, "แชร์เนื้อเพลง"))
}

private suspend fun drawLyricsCard(context: Context, lyrics: String, t: Track): File {
    val art = loadArt(context, t, 320)
    val swatch = art?.let { Palette.from(it).generate() }?.let { it.darkVibrantSwatch ?: it.vibrantSwatch ?: it.dominantSwatch }
    val bg = androidx.core.graphics.ColorUtils.blendARGB(swatch?.rgb ?: colorFor(t.album + t.artist).toArgb(), Color.BLACK, 0.3f)

    val width = (CARD_W - 2 * PAD).toInt()
    val lyricPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 62f; typeface = Typeface.DEFAULT_BOLD }
    val shown = lyrics.trim().lines().filter { it.isNotBlank() }.take(MAX_LINES).joinToString("\n")
    val body = StaticLayout.Builder.obtain(shown, 0, shown.length, lyricPaint, width)
        .setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(14f, 1f).build()

    val cover = 200f
    val bodyTop = PAD + cover + 72f
    val height = (bodyTop + body.height + 72f + 40f + PAD).toInt().coerceAtLeast(CARD_W)
    val bitmap = Bitmap.createBitmap(CARD_W, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(bg)

    val coverBox = RectF(PAD, PAD, PAD + cover, PAD + cover)
    if (art != null) {
        canvas.save()
        canvas.clipPath(Path().apply { addRoundRect(coverBox, 24f, 24f, Path.Direction.CW) })
        canvas.drawBitmap(art, null, coverBox, Paint(Paint.FILTER_BITMAP_FLAG))
        canvas.restore()
    } else {
        canvas.drawRoundRect(coverBox, 24f, 24f, Paint().apply { color = Color.argb(60, 255, 255, 255) })
    }

    val textLeft = PAD + cover + 40f
    val textWidth = CARD_W - PAD - textLeft
    val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 50f; typeface = Typeface.DEFAULT_BOLD }
    val artistPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(204, 255, 255, 255); textSize = 40f }
    canvas.drawText(TextUtils.ellipsize(t.title, titlePaint, textWidth, TextUtils.TruncateAt.END).toString(), textLeft, PAD + 88f, titlePaint)
    canvas.drawText(TextUtils.ellipsize(t.artist, artistPaint, textWidth, TextUtils.TruncateAt.END).toString(), textLeft, PAD + 148f, artistPaint)

    canvas.save()
    canvas.translate(PAD, bodyTop)
    body.draw(canvas)
    canvas.restore()

    val brand = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(170, 255, 255, 255); textSize = 34f; typeface = Typeface.DEFAULT_BOLD }
    canvas.drawText("Ditto", PAD, height - PAD, brand)

    val dir = File(context.cacheDir, "share").apply { mkdirs() }
    return File(dir, "lyrics.png").also { f -> f.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
}
