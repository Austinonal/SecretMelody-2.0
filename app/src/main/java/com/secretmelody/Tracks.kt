package com.secretmelody

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import java.io.File

data class Track(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val dateAdded: Long = 0, // seconds since epoch
    val trackNo: Int = 0, // MediaStore encodes disc * 1000 + track
    val sizeBytes: Long = 0,
    val coverPath: String? = null, // a cover the user picked, overrides the one inside the file
    val key: String = "id:$id", // file name, lowercase: how playlists, likes and edits remember a song across moves
    val remoteArt: String? = null, // cover on the web, for songs played from YouTube
) {
    val uri: Uri get() = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
    val artUri: Uri get() = ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId)

    fun toMediaItem(): MediaItem {
        val stream = key.startsWith(YT_KEY)
        val playUri = if (stream) ytStreamUri(key.removePrefix(YT_KEY)) else uri
        return MediaItem.Builder()
            .setMediaId(if (stream) key else id.toString())
            .setUri(playUri)
            .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(playUri).build())
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setAlbumTitle(album)
                    .setDurationMs(durationMs.takeIf { it > 0 })
                    .setArtworkUri(coverPath?.let { Uri.fromFile(File(it)) } ?: remoteArt?.let(Uri::parse) ?: artUri)
                    .build()
            )
            .build()
    }
}

/** All music files on the phone, A to Z. Call off the main thread. */
fun loadTracks(context: Context): List<Track> {
    val projection = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.ALBUM,
        MediaStore.Audio.Media.ALBUM_ID,
        MediaStore.Audio.Media.DURATION,
        MediaStore.Audio.Media.DATE_ADDED,
        MediaStore.Audio.Media.TRACK,
        MediaStore.Audio.Media.SIZE,
        MediaStore.Audio.Media.DISPLAY_NAME,
    )
    val tracks = mutableListOf<Track>()
    context.contentResolver.query(
        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection, "${MediaStore.Audio.Media.IS_MUSIC} != 0", null,
        "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC",
    )?.use { c ->
        while (c.moveToNext()) {
            tracks += Track(
                id = c.getLong(0),
                title = c.getString(1) ?: "ไม่มีชื่อเพลง",
                artist = c.getString(2)?.takeUnless { it == MediaStore.UNKNOWN_STRING } ?: "ไม่ทราบศิลปิน",
                album = c.getString(3) ?: "",
                albumId = c.getLong(4),
                durationMs = c.getLong(5),
                dateAdded = c.getLong(6),
                trackNo = c.getInt(7),
                sizeBytes = c.getLong(8),
                key = c.getString(9)?.lowercase() ?: "id:${c.getLong(0)}",
            )
        }
    }
    return tracks
}

/** An album or an artist: what a cover card and the collection page show. */
data class TrackGroup(
    val title: String,
    val subtitle: String,
    val tracks: List<Track>,
    val isArtist: Boolean,
    val playlistId: Long? = null, // set for playlists and Liked Songs (LIKED_ID)
)

/** MediaStore keeps disc and track together as disc * 1000 + track. 0 = unknown. */
val Track.disc: Int get() = trackNo / 1000

/** Spotify's rule: up to 3 songs under 30 minutes is a single, up to 6 under 30 minutes an EP. */
fun albumType(tracks: List<Track>): String {
    val minutes = tracks.sumOf { it.durationMs } / 60_000
    return when {
        tracks.size <= 3 && minutes < 30 -> "ซิงเกิล"
        tracks.size <= 6 && minutes < 30 -> "EP"
        else -> "อัลบั้ม"
    }
}

/** Albums in disc/track order, the most recently added album first. Subtitle: "อัลบั้ม • Artist". */
fun List<Track>.albums(): List<TrackGroup> = groupBy { it.albumId }.values
    .map { ts ->
        val sorted = ts.sortedWith(compareBy<Track>({ it.trackNo }, { it.title.lowercase() }))
        val artist = sorted.map { it.artist }.distinct().singleOrNull() ?: "ศิลปินหลายคน"
        TrackGroup(sorted[0].album.ifBlank { "ไม่ทราบอัลบั้ม" }, "${albumType(sorted)} • $artist", sorted, isArtist = false)
    }
    .sortedByDescending { g -> g.tracks.maxOf { it.dateAdded } }

private val ALWAYS_SPLIT = Regex("""\s+(?:feat\.?|ft\.?|featuring)\s+|\s*;\s*|\s*/\s*""", RegexOption.IGNORE_CASE)
private val MAYBE_SPLIT = Regex("""\s*,\s*|\s+&\s+|\s+x\s+|\s+และ\s+""")

/**
 * The artists on one song. "feat.", ";" and "/" always separate; ",", "&", " x " only when a piece is an artist
 * the library already has on its own, so names like "Simon & Garfunkel" or "Earth, Wind & Fire" stay whole.
 */
fun splitArtists(raw: String, known: Set<String>): List<String> =
    raw.split(ALWAYS_SPLIT).map { it.trim() }.filter { it.isNotEmpty() }.flatMap { part ->
        val pieces = part.split(MAYBE_SPLIT).map { it.trim() }.filter { it.isNotEmpty() }
        if (pieces.size > 1 && pieces.any { it in known && it != part }) pieces else listOf(part)
    }.distinct().ifEmpty { listOf(raw) }

/** Artists A to Z, each with their songs A to Z; a song with several artists is on each of their pages. */
fun List<Track>.artists(): List<TrackGroup> {
    val known = mapTo(HashSet()) { it.artist }
    return flatMap { t -> splitArtists(t.artist, known).map { it to t } }
        .groupBy({ it.first }, { it.second })
        .map { (name, ts) -> TrackGroup(name, "ศิลปิน", ts.distinct().sortedBy { it.title.lowercase() }, isArtist = true) }
        .sortedBy { it.title.lowercase() }
}

fun List<Track>.search(query: String): List<Track> {
    val q = query.trim()
    if (q.isEmpty()) return this
    return filter { t -> listOf(t.title, t.artist, t.album).any { it.contains(q, ignoreCase = true) } }
}

fun formatTime(ms: Long): String {
    val s = ms.coerceAtLeast(0) / 1000
    return "%d:%02d".format(s / 60, s % 60)
}
