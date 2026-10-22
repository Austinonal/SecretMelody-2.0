package com.secretmelody

import androidx.compose.ui.graphics.Color

// "Seoul Night" theme, not in use yet: the app still runs on the palette at the top of Ui.kt.
// To switch, point Accent/AccentText/Bg/Surface2/Muted/Tile/TileColors in Ui.kt at these, and put
// OnAccent where Ui.kt draws Color.White on Accent. Every screen's mockup is in design/seoul-night/.
// shortcut: the mockups' display font (Black Han Sans) is not bundled, add it to res/font when switching.
object SeoulTheme {
    // Hot pink is light enough for both roles: as text on Bg (6.5:1) and as a fill under black (6.5:1).
    val Accent = Color(0xFFFF4FA3)
    val AccentText = Accent
    val OnAccent = Color(0xFF0B0A10)
    val Bg = Color(0xFF0B0A10)
    val Surface2 = Color(0xFF1C1A24)
    val Muted = Color(0xFFA89FBA)
    val Tile = Color(0xFF1C1A24)
    val TileColors = listOf(
        Color(0xFF7B2CBF), Color(0xFFC9184A), Color(0xFF1D3557), Color(0xFF2A1B3D),
        Color(0xFF3A2654), Color(0xFF5A1F8F), Color(0xFF8E44D1), Color(0xFF4A3B60),
    )
}
