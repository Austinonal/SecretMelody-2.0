package com.secretmelody

import android.content.Context
import android.net.Uri
import android.view.TextureView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import java.io.File

// A looping, silent video behind Now Playing, like Spotify's Canvas. The user picks it per song; Spotify's own
// Canvas clips need a signed-in Spotify session, so they are not fetched.

private const val MAX_VIDEO_BYTES = 30L * 1024 * 1024

fun backgroundVideo(context: Context, t: Track) = File(context.filesDir, "canvas/${t.key.hashCode().toUInt()}.mp4")

/** Copies the picked video into the app. False when it is too big. Blocking; call on Dispatchers.IO. */
fun saveBackgroundVideo(context: Context, t: Track, uri: Uri): Boolean {
    val out = backgroundVideo(context, t).also { it.parentFile?.mkdirs() }
    val tmp = File(out.path + ".part")
    context.contentResolver.openInputStream(uri)!!.use { input ->
        tmp.outputStream().use { o ->
            val buf = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                total += n
                if (total > MAX_VIDEO_BYTES) { o.close(); tmp.delete(); return false }
                o.write(buf, 0, n)
            }
        }
    }
    return tmp.renameTo(out)
}

/** Fills the screen (cropped, never stretched), muted and looping, with a shade so text stays readable. */
@Composable
fun BackgroundVideo(file: File) {
    val context = LocalContext.current
    var ratio by remember(file) { mutableFloatStateOf(9f / 16f) }
    // Its own player: silent and without audio focus, so the song keeps playing.
    val player = remember(file) {
        ExoPlayer.Builder(context).build().apply {
            volume = 0f
            repeatMode = Player.REPEAT_MODE_ONE
            setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
            addListener(object : Player.Listener {
                override fun onVideoSizeChanged(v: VideoSize) {
                    if (v.width > 0 && v.height > 0) ratio = v.width * v.pixelWidthHeightRatio / v.height
                }
            })
            prepare()
            play()
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    Box(Modifier.fillMaxSize().clipToBounds(), contentAlignment = Alignment.Center) {
        AndroidView(
            factory = { TextureView(it).also(player::setVideoTextureView) },
            modifier = Modifier.fillMaxHeight().aspectRatio(ratio, matchHeightConstraintsFirst = true),
        )
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)))
    }
}
