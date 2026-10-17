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
) {
    val uri: Uri get() = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
    val artUri: Uri get() = ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId)

    fun toMediaItem(): MediaItem = MediaItem.Builder()
        .setMediaId(id.toString())
        .setUri(uri)
        .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(uri).build())
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .setArtworkUri(coverPath?.let { Uri.fromFile(File(it)) } ?: artUri)
                .build()
        )
        .build()
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

/** Albums in disc/track order, the most recently added album first. */
fun List<Track>.albums(): List<TrackGroup> = groupBy { it.albumId }.values
    .map { ts ->
        val sorted = ts.sortedWith(compareBy<Track>({ it.trackNo }, { it.title.lowercase() }))
        TrackGroup(sorted[0].album.ifBlank { "ไม่ทราบอัลบั้ม" }, sorted[0].artist, sorted, isArtist = false)
    }
    .sortedByDescending { g -> g.tracks.maxOf { it.dateAdded } }

/** Artists A to Z, each with their songs A to Z. */
fun List<Track>.artists(): List<TrackGroup> = groupBy { it.artist }
    .map { (name, ts) -> TrackGroup(name, "ศิลปิน", ts.sortedBy { it.title.lowercase() }, isArtist = true) }
    .sortedBy { it.title.lowercase() }

fun List<Track>.search(query: String): List<Track> {
    val q = query.trim()
    if (q.isEmpty()) return this
    return filter { t -> listOf(t.title, t.artist, t.album).any { it.contains(q, ignoreCase = true) } }
}

fun formatTime(ms: Long): String {
    val s = ms.coerceAtLeast(0) / 1000
    return "%d:%02d".format(s / 60, s % 60)
}
