package com.secretmelody

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.media3.common.C
import androidx.media3.common.Player
import kotlinx.coroutines.delay

/** Compose-observable snapshot of the player. */
class PlayerUi(private val player: Player) {
    var mediaId by mutableStateOf<String?>(null)
        private set
    var showPlay by mutableStateOf(true)
        private set
    var shuffle by mutableStateOf(false)
        private set
    var repeat by mutableIntStateOf(Player.REPEAT_MODE_OFF)
        private set
    var version by mutableIntStateOf(0) // bumps on every player event, so the queue screen re-reads the timeline
        private set
    var speed by mutableFloatStateOf(1f)
        private set

    fun sync() {
        speed = player.playbackParameters.speed
        mediaId = player.currentMediaItem?.mediaId
        showPlay = !player.playWhenReady ||
            player.playbackState == Player.STATE_IDLE ||
            player.playbackState == Player.STATE_ENDED
        shuffle = player.shuffleModeEnabled
        repeat = player.repeatMode
        version++
    }

    init {
        sync()
    }
}

@Composable
fun rememberPlayerUi(player: Player): PlayerUi {
    val ui = remember(player) { PlayerUi(player) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) = ui.sync()
        }
        player.addListener(listener)
        ui.sync()
        onDispose { player.removeListener(listener) }
    }
    return ui
}

/** Position and duration in ms, polled twice a second. Duration is 0 while unknown. */
@Composable
fun rememberProgress(player: Player): Pair<Long, Long> {
    var pos by remember { mutableLongStateOf(0L) }
    var dur by remember { mutableLongStateOf(0L) }
    LaunchedEffect(player) {
        while (true) {
            pos = player.currentPosition
            dur = player.duration.coerceAtLeast(0L)
            delay(500)
        }
    }
    return pos to dur
}

fun Player.playList(tracks: List<Track>, index: Int) {
    setMediaItems(tracks.map { it.toMediaItem() }, index, 0L)
    prepare()
    play()
}

fun Player.playNext(track: Track) {
    if (mediaItemCount == 0) playList(listOf(track), 0)
    else addMediaItem(currentMediaItemIndex + 1, track.toMediaItem())
}

fun Player.togglePlay() {
    when {
        playWhenReady && playbackState != Player.STATE_ENDED && playbackState != Player.STATE_IDLE -> pause()
        else -> {
            if (playbackState == Player.STATE_IDLE) prepare()
            if (playbackState == Player.STATE_ENDED) seekToDefaultPosition()
            play()
        }
    }
}

/** Indices of the songs that will play after the current one, in play order (shuffle-aware). */
fun Player.upNextIndices(max: Int = 200): List<Int> {
    val timeline = currentTimeline
    if (timeline.isEmpty) return emptyList()
    val out = mutableListOf<Int>()
    var i = timeline.getNextWindowIndex(currentMediaItemIndex, Player.REPEAT_MODE_OFF, shuffleModeEnabled)
    while (i != C.INDEX_UNSET && out.size < max) {
        out += i
        i = timeline.getNextWindowIndex(i, Player.REPEAT_MODE_OFF, shuffleModeEnabled)
    }
    return out
}
