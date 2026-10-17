package com.secretmelody

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CoversTest {
    @Test
    fun centredCropOfAWideImageTakesTheMiddleSquare() =
        assertEquals(CropRect(left = 50, top = 0, size = 100), cropRect(200, 100, view = 300f, scale = 1f, offX = 0f, offY = 0f))

    @Test
    fun zoomingInShrinksTheSquareAroundTheCentre() =
        assertEquals(CropRect(left = 75, top = 25, size = 50), cropRect(200, 100, view = 300f, scale = 2f, offX = 0f, offY = 0f))

    @Test
    fun draggingRightShowsMoreOfTheLeftSideAndStopsAtTheEdge() {
        // 3 screen px per image px at scale 1: dragging 90px right moves the square 30 image px left.
        assertEquals(CropRect(left = 20, top = 0, size = 100), cropRect(200, 100, 300f, 1f, offX = 90f, offY = 0f))
        assertEquals(0, cropRect(200, 100, 300f, 1f, offX = 10_000f, offY = 0f).left)
        assertEquals(150f to 0f, maxOffset(200, 100, 300f, 1f))
    }

    @Test
    fun itunesResultsUseTheBigCoverAndDropRepeats() {
        val json = """{"results":[
            {"trackName":"A","artistName":"X","collectionName":"Al","artworkUrl100":"https://i/100x100bb.jpg"},
            {"trackName":"B","artistName":"X","collectionName":"Al","artworkUrl100":"https://i/100x100bb.jpg"},
            {"trackName":"C","artistName":"Y","collectionName":"Other"}
        ]}"""
        val r = parseCoverResults(json)
        assertEquals(1, r.size)
        assertEquals("https://i/600x600bb.jpg", r[0].bigUrl)
    }

    @Test
    fun backupCoverNamesCannotEscapeTheCoversFolder() {
        assertEquals("12-34.jpg", safeCoverName("covers/12-34.jpg"))
        assertNull(safeCoverName("covers/../../shared_prefs/x.jpg"))
        assertNull(safeCoverName("covers/a/b.jpg"))
        assertNull(safeCoverName("covers/evil.sh"))
    }

    @Test
    fun oldIdEntriesBecomeFileKeys() =
        assertEquals(listOf("a.mp3", "#9", "b.mp3"), listOf("#1", "#9", "b.mp3", "#1").migrated { if (it == 1L) "a.mp3" else null })
}
