package com.secretmelody

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test

class ShotTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5)

    private fun shot(name: String, s: LinkImportState) = paparazzi.snapshot(name) {
        MaterialTheme(colorScheme = darkColorScheme(primary = Accent, background = Bg, surface = Surface2)) {
            Box(Modifier.fillMaxSize().background(Bg)) { LinkImportScreen(s, null, {}, {}, {}, {}, {}) }
        }
    }

    @Test fun empty() = shot("1_empty", LinkImportState())
    @Test fun mp3() = shot("2_youtube_mp3", LinkImportState().apply { url = "https://youtu.be/dQw4w9WgXcQ" })
    @Test fun mp4() = shot("3_youtube_mp4", LinkImportState().apply { url = "https://youtu.be/dQw4w9WgXcQ"; format = YtFormat.MP4 })
    @Test fun running() = shot("4_converting", LinkImportState().apply { url = "https://youtu.be/dQw4w9WgXcQ"; running = true; percent = 0.42f })
    @Test fun error() = shot("5_error", LinkImportState().apply { url = "https://youtu.be/dQw4w9WgXcQ"; error = "วิดีโอยาวเกินไป MP3 ได้ไม่เกิน 240 นาที / MP4 ไม่เกิน 30 นาที" })
}
