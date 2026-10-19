package com.secretmelody

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Size
import android.view.KeyEvent
import android.widget.RemoteViews
import androidx.media3.common.Player
import java.util.concurrent.Executors

/** Home screen player. The playback process redraws it as songs change; the system asks for it when it is added. */
class PlayerWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        // Nothing playing yet: show the song the player would resume, so play picks it up.
        val saved = ResumeStore.read(context.filesDir)
        val item = saved?.items?.getOrNull(saved.index)
        manager.updateAppWidget(ids, views(context, item?.title, item?.artist, playing = false, art = null, canResume = saved != null))
    }

    companion object {
        private val worker = Executors.newSingleThreadExecutor() // cover decoding off the player's thread

        /** Called by the playback service on song and play/pause changes. */
        fun update(context: Context, player: Player) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, PlayerWidget::class.java))
            if (ids.isEmpty()) return
            val item = player.currentMediaItem
            val md = player.mediaMetadata
            val title = md.title?.toString()
            val artist = md.artist?.toString()
            val playing = player.playWhenReady
            val audio = item?.localConfiguration?.uri
            val artwork = md.artworkUri
            worker.execute {
                val art = runCatching { cover(context, audio, artwork) }.getOrNull()
                manager.updateAppWidget(ids, views(context, title, artist, playing, art, canResume = item != null))
            }
        }

        /** A cover the user picked (a file) first, else the one inside the song. */
        private fun cover(context: Context, audio: Uri?, artwork: Uri?): Bitmap? =
            if (artwork?.scheme == "file") {
                BitmapFactory.decodeFile(artwork.path, BitmapFactory.Options().apply { inSampleSize = 4 })
            } else {
                audio?.let { context.contentResolver.loadThumbnail(it, Size(192, 192), null) }
            }

        private fun views(context: Context, title: String?, artist: String?, playing: Boolean, art: Bitmap?, canResume: Boolean) =
            RemoteViews(context.packageName, R.layout.widget_player).apply {
                setTextViewText(R.id.widget_title, title ?: "Ditto")
                setTextViewText(R.id.widget_artist, artist ?: "แตะเพื่อเปิดแอป")
                if (art != null) setImageViewBitmap(R.id.widget_art, art) else setImageViewResource(R.id.widget_art, R.drawable.ic_widget_note)
                setImageViewResource(R.id.widget_play, if (playing) R.drawable.ic_widget_pause else R.drawable.ic_widget_play)
                setContentDescription(R.id.widget_play, if (playing) "หยุดชั่วคราว" else "เล่น")
                val open = PendingIntent.getActivity(
                    context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
                setOnClickPendingIntent(R.id.widget_root, open)
                // With nothing to resume, play opens the app: a player started for nothing would have no song to show.
                setOnClickPendingIntent(R.id.widget_play, if (canResume) mediaKey(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, start = !playing) else open)
                setOnClickPendingIntent(R.id.widget_prev, mediaKey(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS, start = false))
                setOnClickPendingIntent(R.id.widget_next, mediaKey(context, KeyEvent.KEYCODE_MEDIA_NEXT, start = false))
            }

        /**
         * A media button press delivered to the playback service, the way Media3's own notification buttons do it:
         * as a foreground start when it is about to start playing, a plain start otherwise.
         */
        private fun mediaKey(context: Context, keyCode: Int, start: Boolean): PendingIntent {
            val intent = Intent(Intent.ACTION_MEDIA_BUTTON)
                .setComponent(ComponentName(context, PlaybackService::class.java))
                .putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            return if (start) PendingIntent.getForegroundService(context, keyCode, intent, flags)
            else PendingIntent.getService(context, keyCode, intent, flags)
        }
    }
}
