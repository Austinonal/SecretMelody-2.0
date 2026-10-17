package com.secretmelody

import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** What a backup holds once read: the two JSON files. Cover images are already unpacked into the covers folder. */
class BackupData(val library: JSONObject, val edits: JSONObject)

/** Only a plain cover file name is accepted from a backup, never a path, so a crafted zip can't write elsewhere. */
fun safeCoverName(entryName: String): String? {
    val name = entryName.removePrefix("covers/")
    if (name.isEmpty() || name.contains('/') || name.contains('\\') || name.contains("..") || name.startsWith(".")) return null
    return name.takeIf { it.endsWith(".jpg") }
}

/** Zips likes, playlists, song edits and custom covers. */
fun writeBackup(out: OutputStream, library: Library, edits: Edits) {
    ZipOutputStream(out).use { zip ->
        fun put(name: String, bytes: ByteArray) {
            zip.putNextEntry(ZipEntry(name))
            zip.write(bytes)
            zip.closeEntry()
        }
        put("secretmelody.txt", "SecretMelody backup v1".toByteArray())
        put("library.json", library.toJson().toString(2).toByteArray())
        put("edits.json", edits.toJson().toString(2).toByteArray())
        edits.map.values.mapNotNull { it.cover }.map(::File).filter { it.exists() }.forEach { put("covers/${it.name}", it.readBytes()) }
    }
}

/** Reads a backup made by [writeBackup]; unpacks its covers into [coversDir]. Throws ImportException if it isn't one. */
fun readBackup(input: InputStream, coversDir: File): BackupData {
    var library: JSONObject? = null
    var edits: JSONObject? = null
    var marker = false
    coversDir.mkdirs()
    ZipInputStream(input).use { zip ->
        var count = 0
        while (true) {
            val e = zip.nextEntry ?: break
            if (++count > 10_000) throw ImportException("ไฟล์สำรองมีรายการมากผิดปกติ")
            val bytes = zip.readBytes()
            if (bytes.size > 20 * 1024 * 1024) throw ImportException("ไฟล์สำรองมีไฟล์ใหญ่ผิดปกติ")
            when {
                e.isDirectory -> Unit
                e.name == "secretmelody.txt" -> marker = true
                e.name == "library.json" -> library = JSONObject(String(bytes))
                e.name == "edits.json" -> edits = JSONObject(String(bytes))
                e.name.startsWith("covers/") -> safeCoverName(e.name)?.let { File(coversDir, it).writeBytes(bytes) }
            }
        }
    }
    if (!marker || library == null) throw ImportException("ไฟล์นี้ไม่ใช่ไฟล์สำรองของ SecretMelody")
    return BackupData(library!!, edits ?: JSONObject())
}
