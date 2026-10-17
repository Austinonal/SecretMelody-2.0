package com.secretmelody

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import java.io.File

/** One queued song, with enough metadata to show it in the notification before the UI has loaded anything. */
data class ResumeItem(val mediaId: String, val uri: String, val title: String, val artist: String, val album: String, val art: String)

/** The queue and where playback was, so a killed player can come back exactly where it stopped. */
data class ResumeState(val index: Int, val positionMs: Long, val items: List<ResumeItem>)

private fun clean(s: String) = s.replace('\t', ' ').replace('\n', ' ')

fun ResumeState.encode(): String = buildString {
    append("index=$index\npos=$positionMs\n")
    items.forEach { append(listOf(it.mediaId, it.uri, it.title, it.artist, it.album, it.art).joinToString("\t", transform = ::clean)).append('\n') }
}

fun decodeResume(text: String): ResumeState? {
    val lines = text.lines()
    val index = lines.firstOrNull()?.removePrefix("index=")?.toIntOrNull() ?: return null
    val pos = lines.getOrNull(1)?.removePrefix("pos=")?.toLongOrNull() ?: return null
    val items = lines.drop(2).mapNotNull { l ->
        val f = l.split('\t')
        if (f.size == 6) ResumeItem(f[0], f[1], f[2], f[3], f[4], f[5]) else null
    }
    if (items.isEmpty()) return null
    return ResumeState(index.coerceIn(0, items.lastIndex), pos.coerceAtLeast(0), items)
}

object ResumeStore {
    private fun file(dir: File) = File(dir, "resume.txt")

    fun read(dir: File): ResumeState? = runCatching { decodeResume(file(dir).readText()) }.getOrNull()

    /** Called on the player's thread: snapshot the queue and write it out. */
    fun save(dir: File, player: Player) {
        if (player.mediaItemCount == 0) return
        val items = (0 until player.mediaItemCount).map { i ->
            val m = player.getMediaItemAt(i)
            val md = m.mediaMetadata
            ResumeItem(
                m.mediaId, (m.localConfiguration?.uri ?: m.requestMetadata.mediaUri)?.toString().orEmpty(),
                md.title?.toString().orEmpty(), md.artist?.toString().orEmpty(), md.albumTitle?.toString().orEmpty(),
                md.artworkUri?.toString().orEmpty(),
            )
        }.filter { it.uri.isNotEmpty() }
        val state = ResumeState(player.currentMediaItemIndex, player.currentPosition, items)
        runCatching {
            val tmp = File(dir, "resume.txt.tmp")
            tmp.writeText(state.encode())
            tmp.renameTo(file(dir))
        }
    }
}

fun ResumeItem.toMediaItem(): MediaItem {
    val u = Uri.parse(uri)
    return MediaItem.Builder()
        .setMediaId(mediaId)
        .setUri(u)
        .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(u).build())
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title).setArtist(artist).setAlbumTitle(album)
                .setArtworkUri(art.takeIf { it.isNotEmpty() }?.let(Uri::parse))
                .build()
        )
        .build()
}
