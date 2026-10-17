package com.secretmelody

import org.junit.Assert.assertEquals
import org.junit.Test

class TracksTest {
    private val a = Track(1, "Patience", "Take That", "Beautiful World", 10, 1000)
    private val b = Track(2, "นิทาน", "Musketeers", "Musketeers", 11, 2000)

    @Test
    fun blankQueryReturnsEverything() = assertEquals(listOf(a, b), listOf(a, b).search("  "))

    @Test
    fun matchesArtistIgnoringCase() = assertEquals(listOf(a), listOf(a, b).search("take"))

    @Test
    fun matchesThaiTitle() = assertEquals(listOf(b), listOf(a, b).search("นิทา"))

    @Test
    fun albumsAreInTrackOrderAndNewestAlbumFirst() {
        val old1 = Track(3, "B side", "X", "Old", albumId = 1, durationMs = 1, dateAdded = 10, trackNo = 2)
        val old2 = Track(4, "A side", "X", "Old", albumId = 1, durationMs = 1, dateAdded = 10, trackNo = 1)
        val new = Track(5, "Hit", "Y", "New", albumId = 2, durationMs = 1, dateAdded = 99, trackNo = 1)
        val albums = listOf(old1, old2, new).albums()
        assertEquals(listOf("New", "Old"), albums.map { it.title })
        assertEquals(listOf(old2, old1), albums[1].tracks)
    }

    @Test
    fun artistsGroupSongsAtoZ() {
        val artists = listOf(b, a, a.copy(id = 9, title = "Back for Good")).artists()
        assertEquals(listOf("Musketeers", "Take That"), artists.map { it.title })
        assertEquals(listOf("Back for Good", "Patience"), artists[1].tracks.map { it.title })
    }

    @Test
    fun formatsTime() {
        assertEquals("0:00", formatTime(-5))
        assertEquals("3:05", formatTime(185_000))
    }
}
