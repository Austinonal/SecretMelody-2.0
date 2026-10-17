package com.secretmelody

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder

private val AUDIO_EXTENSIONS = setOf("mp3", "m4a", "aac", "flac", "ogg", "opus", "wav", "wma")
private const val MAX_DOWNLOAD_BYTES = 300L * 1024 * 1024
private const val MUSIC_FOLDER = "Music/SecretMelody"

/** Shown to the user as is, so keep these readable. */
class ImportException(message: String) : Exception(message)

fun extensionOf(name: String): String = name.substringAfterLast('.', "").lowercase()

fun isAudioName(name: String) = extensionOf(name) in AUDIO_EXTENSIONS

fun withoutExtension(name: String) = if (isAudioName(name)) name.substringBeforeLast('.') else name

/** A Google Drive "share" link becomes its direct-download form; anything else is returned unchanged. */
fun normalizeAudioUrl(raw: String): String {
    val url = raw.trim()
    val id = Regex("""drive\.google\.com/file/d/([\w-]+)""").find(url)?.groupValues?.get(1)
        ?: Regex("""drive\.google\.com/open\?(?:.*&)?id=([\w-]+)""").find(url)?.groupValues?.get(1)
    return if (id != null) "https://drive.google.com/uc?export=download&id=$id" else url
}

/** Best guess at the file name: Content-Disposition first, then the last part of the URL path. */
fun fileNameFor(url: String, contentDisposition: String?, contentType: String?): String {
    val fromHeader = contentDisposition?.let { Regex("""filename\*?=(?:UTF-8'')?"?([^";]+)"?""", RegexOption.IGNORE_CASE).find(it)?.groupValues?.get(1) }
    var name = fromHeader ?: url.substringBefore('?').substringAfterLast('/')
    name = runCatching { URLDecoder.decode(name, "UTF-8") }.getOrDefault(name)
    name = name.replace(Regex("""[\\/:*?"<>|]"""), "_").trim().ifBlank { "เพลงที่นำเข้า" }
    if (!isAudioName(name)) {
        val ext = contentType?.substringBefore(';')?.trim()?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
        name += "." + (ext?.takeIf { it in AUDIO_EXTENSIONS } ?: "mp3")
    }
    return name
}

fun mimeFor(name: String): String =
    MimeTypeMap.getSingleton().getMimeTypeFromExtension(extensionOf(name)) ?: "audio/mpeg"

fun formatSize(bytes: Long): String = when {
    bytes >= 1L shl 30 -> "%.1f GB".format(bytes / (1L shl 30).toDouble())
    bytes >= 1L shl 20 -> "%.1f MB".format(bytes / (1L shl 20).toDouble())
    else -> "${bytes / 1024} KB"
}

/** Writes a new song into Music/SecretMelody through MediaStore (no storage permission needed). Returns its MediaStore id. */
private fun saveToMusic(context: Context, name: String, write: (OutputStream) -> Unit): Long {
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.Audio.Media.DISPLAY_NAME, name)
        put(MediaStore.Audio.Media.MIME_TYPE, mimeFor(name))
        put(MediaStore.Audio.Media.RELATIVE_PATH, MUSIC_FOLDER)
        put(MediaStore.Audio.Media.IS_MUSIC, 1)
        put(MediaStore.Audio.Media.IS_PENDING, 1)
    }
    val uri = resolver.insert(MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)
        ?: throw ImportException("สร้างไฟล์ในเครื่องไม่ได้ พื้นที่อาจเต็ม")
    try {
        (resolver.openOutputStream(uri) ?: throw ImportException("เขียนไฟล์ไม่ได้")).use(write)
        resolver.update(uri, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
    } catch (e: Throwable) {
        runCatching { resolver.delete(uri, null, null) } // never leave a half-written song behind
        throw e
    }
    return ContentUris.parseId(uri)
}

private fun alreadyImported(context: Context, name: String, size: Long): Boolean {
    if (size <= 0) return false
    context.contentResolver.query(
        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.Audio.Media._ID),
        "${MediaStore.Audio.Media.DISPLAY_NAME} = ? AND ${MediaStore.Audio.Media.SIZE} = ?", arrayOf(name, size.toString()), null,
    )?.use { return it.moveToFirst() }
    return false
}

data class FileImport(val added: List<Long>, val skipped: Int, val notAudio: Int, val failed: Int)

/** Copies the picked files into Music/SecretMelody. Skips files already imported (same name and size). */
suspend fun importFiles(context: Context, uris: List<Uri>, onProgress: (done: Int, total: Int) -> Unit): FileImport {
    val added = mutableListOf<Long>()
    var skipped = 0
    var notAudio = 0
    var failed = 0
    uris.forEachIndexed { i, uri ->
        currentCoroutineContext().ensureActive()
        onProgress(i, uris.size)
        var name = "เพลง"
        var size = -1L
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                c.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let { name = c.getString(it) ?: name }
                c.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 }?.let { size = c.getLong(it) }
            }
        }
        val type = context.contentResolver.getType(uri).orEmpty()
        if (!type.startsWith("audio/") && !isAudioName(name)) {
            notAudio++
            return@forEachIndexed
        }
        if (!isAudioName(name)) name += "." + (MimeTypeMap.getSingleton().getExtensionFromMimeType(type) ?: "mp3")
        if (alreadyImported(context, name, size)) {
            skipped++
            return@forEachIndexed
        }
        try {
            val input = context.contentResolver.openInputStream(uri) ?: throw ImportException("เปิดไฟล์ไม่ได้")
            added += saveToMusic(context, name) { out -> input.use { it.copyTo(out) } }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failed++
        }
    }
    onProgress(uris.size, uris.size)
    return FileImport(added, skipped, notAudio, failed)
}

/**
 * Downloads one direct audio file (https only) into Music/SecretMelody and returns its MediaStore id.
 * Pages that are not audio files (a video site's page, an HTML page) are refused with a readable reason.
 * Blocking: call it inside runInterruptible(Dispatchers.IO) so cancelling stops the download.
 */
fun downloadAudio(context: Context, rawUrl: String, onProgress: (done: Long, total: Long) -> Unit): Long {
    val url = normalizeAudioUrl(rawUrl)
    val parsed = runCatching { URL(url) }.getOrNull() ?: throw ImportException("ลิงก์ไม่ถูกต้อง")
    if (parsed.protocol != "https") throw ImportException("รองรับเฉพาะลิงก์ที่ขึ้นต้นด้วย https://")
    val conn = parsed.openConnection() as HttpURLConnection
    try {
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        conn.setRequestProperty("User-Agent", "SecretMelody/0.4")
        val code = try {
            conn.responseCode
        } catch (e: java.io.IOException) {
            throw ImportException("เชื่อมต่อไม่ได้ ตรวจสอบอินเทอร์เน็ตหรือลิงก์อีกครั้ง")
        }
        if (code != 200) throw ImportException("โหลดไม่ได้ (เซิร์ฟเวอร์ตอบ $code) ลิงก์อาจหมดอายุหรือต้องล็อกอิน")
        val type = conn.contentType?.substringBefore(';')?.trim()?.lowercase().orEmpty()
        val name = fileNameFor(url, conn.getHeaderField("Content-Disposition"), type)
        val looksAudio = type.startsWith("audio/") || (type == "application/octet-stream" && isAudioName(name)) || (type.isEmpty() && isAudioName(name))
        if (!looksAudio) {
            throw ImportException(
                if (type.startsWith("text/") || type.startsWith("video/")) "ลิงก์นี้เป็นหน้าเว็บหรือวิดีโอ ไม่ใช่ไฟล์เสียง ใช้ลิงก์ที่ชี้ไปที่ไฟล์ .mp3 หรือ .m4a โดยตรง"
                else "ลิงก์นี้ไม่ใช่ไฟล์เสียง ($type)"
            )
        }
        val total = conn.contentLengthLong
        if (total > MAX_DOWNLOAD_BYTES) throw ImportException("ไฟล์ใหญ่เกิน ${formatSize(MAX_DOWNLOAD_BYTES)}")
        return saveToMusic(context, name) { out -> copyLimited(conn.inputStream, out, total, onProgress) }
    } finally {
        conn.disconnect()
    }
}

private fun copyLimited(input: InputStream, out: OutputStream, total: Long, onProgress: (Long, Long) -> Unit) {
    val buffer = ByteArray(64 * 1024)
    var done = 0L
    var lastReport = 0L
    input.use {
        while (true) {
            val n = it.read(buffer)
            if (n < 0) break
            done += n
            if (done > MAX_DOWNLOAD_BYTES) throw ImportException("ไฟล์ใหญ่เกิน ${formatSize(MAX_DOWNLOAD_BYTES)}")
            out.write(buffer, 0, n)
            if (done - lastReport >= 256 * 1024) {
                lastReport = done
                onProgress(done, total)
            }
            if (Thread.currentThread().isInterrupted) throw CancellationException("ยกเลิก")
        }
    }
    onProgress(done, total)
}
