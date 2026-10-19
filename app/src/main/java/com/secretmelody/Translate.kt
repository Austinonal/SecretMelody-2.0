package com.secretmelody

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** [lines] line up with the synced lyrics ("" for an empty line); empty when the lyrics are already Thai. */
data class Translation(val lang: String, val lines: List<String>)

/** Google Translate's web endpoint answer → (language it detected, translated text). */
fun parseTranslate(json: String): Pair<String, String> {
    val a = JSONArray(json)
    val segs = a.getJSONArray(0)
    val text = (0 until segs.length()).joinToString("") { segs.getJSONArray(it).optString(0).takeIf { s -> s != "null" }.orEmpty() }
    return a.optString(2) to text
}

private fun translateText(text: String): Pair<String, String> {
    val conn = URL("https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=th&dt=t").openConnection() as HttpURLConnection
    try {
        conn.connectTimeout = 15_000
        conn.readTimeout = 20_000
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
        conn.outputStream.use { it.write("q=${URLEncoder.encode(text, "UTF-8")}".toByteArray()) }
        if (conn.responseCode != 200) throw ImportException("แปลไม่ได้ (${conn.responseCode})")
        return parseTranslate(conn.inputStream.use { String(it.readBytes(), Charsets.UTF_8) })
    } finally {
        conn.disconnect()
    }
}

/** Thai translation of every lyric line. Needs no key (Google Translate's web endpoint); blocking, call on Dispatchers.IO. */
fun translateLyrics(lines: List<String>): Translation {
    val at = lines.indices.filter { lines[it].isNotBlank() }
    if (at.isEmpty()) return Translation("th", emptyList())
    val (lang, whole) = translateText(at.joinToString("\n") { lines[it].replace('\n', ' ') })
    if (lang.startsWith("th")) return Translation("th", emptyList())
    var parts = whole.trimEnd().split("\n")
    // shortcut: if Google merged or split lines the batch no longer lines up, so ask line by line (slow, rare).
    if (parts.size != at.size) parts = at.map { translateText(lines[it]).second }
    val out = MutableList(lines.size) { "" }
    at.forEachIndexed { k, i -> out[i] = parts[k].trim() }
    return Translation(lang, out)
}

fun encodeTranslation(t: Translation): String = JSONObject().put("lang", t.lang).put("lines", JSONArray(t.lines)).toString()

/** null when [json] is unreadable or does not fit lyrics of [n] lines. */
fun decodeTranslation(json: String, n: Int): Translation? = runCatching {
    val o = JSONObject(json)
    val a = o.getJSONArray("lines")
    Translation(o.getString("lang"), (0 until a.length()).map { a.getString(it) }).takeIf { it.lines.size == n || it.lang.startsWith("th") }
}.getOrNull()
