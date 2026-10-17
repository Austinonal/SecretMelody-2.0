package com.secretmelody

import java.io.File

/**
 * What the playback process last did, kept in a tiny file so the app can tell, after the user swipes it away,
 * whether the phone killed the player or it kept going. The playback process is the only writer.
 */
data class PlayState(
    val swipeAt: Long = 0, // when the app was removed from Recents
    val swipePlaying: Boolean = false, // was music playing at that moment
    val beatAt: Long = 0, // last "still alive" tick, written every few seconds while playing
    val stopAt: Long = 0, // when the player itself stopped (paused, finished): a kill never writes this
)

enum class SwipeResult { NONE, SURVIVED, STOPPED_NORMALLY, KILLED }

fun swipeResult(s: PlayState, now: Long): SwipeResult = when {
    s.swipeAt == 0L || !s.swipePlaying -> SwipeResult.NONE
    s.stopAt > s.swipeAt -> SwipeResult.STOPPED_NORMALLY
    s.beatAt > s.swipeAt + 15_000 -> SwipeResult.SURVIVED
    now - s.swipeAt < 30_000 -> SwipeResult.NONE // too soon to tell
    else -> SwipeResult.KILLED
}

fun PlayState.encode() = "swipeAt=$swipeAt\nswipePlaying=$swipePlaying\nbeatAt=$beatAt\nstopAt=$stopAt\n"

fun decodePlayState(text: String): PlayState {
    val m = text.lines().mapNotNull { l -> l.split('=', limit = 2).takeIf { it.size == 2 } }.associate { it[0] to it[1] }
    return PlayState(
        m["swipeAt"]?.toLongOrNull() ?: 0, m["swipePlaying"] == "true",
        m["beatAt"]?.toLongOrNull() ?: 0, m["stopAt"]?.toLongOrNull() ?: 0,
    )
}

object PlayLog {
    private fun file(dir: File) = File(dir, "play-state.txt")

    fun read(dir: File): PlayState = runCatching { decodePlayState(file(dir).readText()) }.getOrDefault(PlayState())

    private fun update(dir: File, change: (PlayState) -> PlayState) {
        runCatching {
            val tmp = File(dir, "play-state.txt.tmp")
            tmp.writeText(change(read(dir)).encode())
            tmp.renameTo(file(dir))
        }
    }

    fun swiped(dir: File, playing: Boolean, now: Long = System.currentTimeMillis()) =
        update(dir) { it.copy(swipeAt = now, swipePlaying = playing, beatAt = now) }

    fun beat(dir: File, now: Long = System.currentTimeMillis()) = update(dir) { it.copy(beatAt = now) }

    fun stopped(dir: File, now: Long = System.currentTimeMillis()) = update(dir) { it.copy(stopAt = now) }
}
