package com.secretmelody

import java.io.File

/** How much one song has been listened to. Keyed by MediaStore id, like the recent list. */
data class SongStat(val plays: Int, val seconds: Long)

fun encodeStats(stats: Map<String, SongStat>): String =
    stats.entries.joinToString("\n") { (id, s) -> "$id\t${s.plays}\t${s.seconds}" }

fun decodeStats(text: String): Map<String, SongStat> = text.lines().mapNotNull { l ->
    val f = l.split('\t')
    val plays = f.getOrNull(1)?.toIntOrNull() ?: return@mapNotNull null
    val seconds = f.getOrNull(2)?.toLongOrNull() ?: return@mapNotNull null
    f[0] to SongStat(plays, seconds)
}.toMap()

/** Written only by the playback process, so songs played with the app closed count too. */
object PlayStats {
    private fun file(dir: File) = File(dir, "play-stats.txt")

    fun read(dir: File): Map<String, SongStat> = runCatching { decodeStats(file(dir).readText()) }.getOrDefault(emptyMap())

    private fun update(dir: File, id: String, change: (SongStat) -> SongStat) {
        runCatching {
            val all = read(dir)
            val tmp = File(dir, "play-stats.txt.tmp")
            tmp.writeText(encodeStats(all + (id to change(all[id] ?: SongStat(0, 0)))))
            tmp.renameTo(file(dir))
        }
    }

    /** [play]: this song just passed 30 s of listening, so it counts as played once. */
    fun add(dir: File, id: String, seconds: Long, play: Boolean) =
        update(dir, id) { SongStat(it.plays + if (play) 1 else 0, it.seconds + seconds) }
}

/** Songs played at least twice, most played first (listening time breaks ties). */
fun mostPlayed(tracks: List<Track>, stats: Map<String, SongStat>, limit: Int = 50): List<Track> =
    tracks.filter { (stats[it.id.toString()]?.plays ?: 0) >= 2 }
        .sortedWith(compareByDescending<Track> { stats[it.id.toString()]!!.plays }.thenByDescending { stats[it.id.toString()]!!.seconds })
        .take(limit)

data class ListeningSummary(
    val seconds: Long,
    val plays: Int,
    val songs: List<Pair<Track, SongStat>>, // most played first
    val artists: List<Pair<String, Long>>, // most listened (seconds) first
)

fun summarize(tracks: List<Track>, stats: Map<String, SongStat>): ListeningSummary {
    val known = tracks.mapNotNull { t -> stats[t.id.toString()]?.let { t to it } }
    return ListeningSummary(
        seconds = known.sumOf { it.second.seconds },
        plays = known.sumOf { it.second.plays },
        songs = known.sortedWith(compareByDescending<Pair<Track, SongStat>> { it.second.plays }.thenByDescending { it.second.seconds }).take(10),
        artists = known.groupBy { it.first.artist }.mapValues { (_, v) -> v.sumOf { it.second.seconds } }
            .entries.sortedByDescending { it.value }.take(5).map { it.key to it.value },
    )
}

/** "12 ชม. 5 นาที", "35 นาที", "40 วินาที". */
fun formatListening(seconds: Long): String = when {
    seconds >= 3600 -> "${seconds / 3600} ชม. ${seconds % 3600 / 60} นาที"
    seconds >= 60 -> "${seconds / 60} นาที"
    else -> "$seconds วินาที"
}
