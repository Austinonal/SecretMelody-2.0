package com.secretmelody

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Id used for the Liked Songs list wherever a playlist id is expected. */
const val LIKED_ID = -1L

data class Playlist(val id: Long, val name: String, val trackIds: List<String>) // trackIds are Track.key values

/** Older versions saved MediaStore ids; they are kept as "#<id>" until [Library.migrate] can turn them into keys. */
private fun JSONArray?.toKeys(): List<String> =
    if (this == null) emptyList() else (0 until length()).map { i -> opt(i).let { v -> if (v is Number) "#${v.toLong()}" else v.toString() } }

/** Turns old "#<id>" placeholders into file keys; anything unknown is left as is. */
fun List<String>.migrated(keyFor: (Long) -> String?): List<String> =
    map { k -> if (k.startsWith("#")) k.drop(1).toLongOrNull()?.let(keyFor) ?: k else k }.distinct()

/**
 * Likes and playlists, kept in one small JSON file in the app's private storage.
 * Songs are remembered by file name (Track.key), so moving a file to another folder keeps it in its playlists.
 */
class Library(private val file: File) {
    var liked by mutableStateOf<List<String>>(emptyList()) // newest first
        private set
    var playlists by mutableStateOf<List<Playlist>>(emptyList())
        private set

    init {
        runCatching { load(JSONObject(file.readText())) }
    }

    fun isLiked(key: String) = key in liked

    fun toggleLike(key: String) {
        liked = if (key in liked) liked - key else listOf(key) + liked
        save()
    }

    fun create(name: String): Playlist {
        val p = Playlist(System.currentTimeMillis(), name.trim(), emptyList())
        playlists = playlists + p
        save()
        return p
    }

    fun rename(id: Long, name: String) = update(id) { it.copy(name = name.trim()) }

    fun delete(id: Long) {
        playlists = playlists.filter { it.id != id }
        save()
    }

    /** False when the song is already in the playlist. */
    fun add(id: Long, key: String): Boolean {
        val p = playlists.find { it.id == id } ?: return false
        if (key in p.trackIds) return false
        update(id) { it.copy(trackIds = it.trackIds + key) }
        return true
    }

    /** Works for Liked Songs too (unlikes the song). */
    fun removeTrack(id: Long, key: String) {
        if (id == LIKED_ID) {
            if (isLiked(key)) toggleLike(key)
        } else {
            update(id) { it.copy(trackIds = it.trackIds - key) }
        }
    }

    /** Moves a song up (by = -1) or down (by = 1) in a playlist. */
    fun moveTrack(id: Long, key: String, by: Int) = update(id) {
        val i = it.trackIds.indexOf(key)
        it.copy(trackIds = it.trackIds.moved(i, i + by))
    }

    /** One-time upgrade of data saved by versions that used MediaStore ids. */
    fun migrate(keyFor: (Long) -> String?) {
        val needs = liked.any { it.startsWith("#") } || playlists.any { p -> p.trackIds.any { it.startsWith("#") } }
        if (!needs) return
        liked = liked.migrated(keyFor)
        playlists = playlists.map { it.copy(trackIds = it.trackIds.migrated(keyFor)) }
        save()
    }

    /** Replaces everything with a backup's contents. */
    fun restore(json: JSONObject) {
        load(json)
        save()
    }

    fun toJson(): JSONObject = JSONObject()
        .put("liked", JSONArray(liked))
        .put("playlists", JSONArray(playlists.map {
            JSONObject().put("id", it.id).put("name", it.name).put("tracks", JSONArray(it.trackIds))
        }))

    private fun update(id: Long, change: (Playlist) -> Playlist) {
        playlists = playlists.map { if (it.id == id) change(it) else it }
        save()
    }

    private fun load(json: JSONObject) {
        liked = json.optJSONArray("liked").toKeys()
        val arr = json.optJSONArray("playlists") ?: JSONArray()
        playlists = (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Playlist(o.getLong("id"), o.getString("name"), o.optJSONArray("tracks").toKeys())
        }
    }

    private fun save() {
        // Write then rename, so a crash mid-write can't wipe the saved playlists.
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(toJson().toString())
        tmp.renameTo(file)
    }
}

fun <T> List<T>.moved(from: Int, to: Int): List<T> {
    if (from !in indices || to !in indices || from == to) return this
    return toMutableList().apply { add(to, removeAt(from)) }
}

/** Recently played track ids, newest first, written by the playback process and read by the UI. */
fun recentFile(dir: File) = File(dir, "recent.txt")

fun recentAfter(old: List<String>, played: String, max: Int = 50): List<String> =
    (listOf(played) + old.filter { it != played }).take(max)

fun readRecent(dir: File): List<String> =
    runCatching { recentFile(dir).readLines().filter { it.isNotBlank() } }.getOrDefault(emptyList())

fun recordPlay(dir: File, mediaId: String) {
    val f = recentFile(dir)
    val tmp = File(dir, "recent.txt.tmp")
    tmp.writeText(recentAfter(readRecent(dir), mediaId).joinToString("\n"))
    tmp.renameTo(f)
}
