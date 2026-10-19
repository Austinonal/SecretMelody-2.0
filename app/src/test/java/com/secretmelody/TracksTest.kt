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
    fun severalArtistsOnOneSong() {
        val known = setOf("Olivia Rodrigo", "Chappell Roan", "Simon & Garfunkel", "Earth, Wind & Fire", "The Weeknd")
        assertEquals(listOf("Olivia Rodrigo", "Chappell Roan"), splitArtists("Olivia Rodrigo, Chappell Roan", known))
        assertEquals(listOf("The Weeknd", "Daft Punk"), splitArtists("The Weeknd feat. Daft Punk", known))
        assertEquals(listOf("A", "B", "C"), splitArtists("A; B / C", known))
        assertEquals(listOf("Simon & Garfunkel"), splitArtists("Simon & Garfunkel", known)) // a band, not two artists
        assertEquals(listOf("Earth, Wind & Fire"), splitArtists("Earth, Wind & Fire", known))
        assertEquals(listOf("Three Man Down"), splitArtists("Three Man Down", known))
        val duet = Track(20, "expectations", "Olivia Rodrigo, Chappell Roan", "X", 30, 1000)
        val solo = Track(21, "drivers license", "Olivia Rodrigo", "Y", 31, 1000)
        val pages = listOf(duet, solo).artists().associate { it.title to it.tracks.map { t -> t.id } }
        assertEquals(mapOf("Chappell Roan" to listOf(20L), "Olivia Rodrigo" to listOf(21L, 20L)), pages)
    }

    @Test
    fun albumTypesAndDiscs() {
        fun songs(n: Int, min: Int) = (1..n).map { Track(100L + it, "s$it", "A", "Al", 7, min * 60_000L, trackNo = it) }
        assertEquals("ซิงเกิล", albumType(songs(1, 4)))
        assertEquals("EP", albumType(songs(5, 4)))
        assertEquals("อัลบั้ม", albumType(songs(10, 4)))
        assertEquals("อัลบั้ม", albumType(songs(3, 12))) // three long songs are not a single
        assertEquals("ซิงเกิล • A", songs(2, 3).albums().single().subtitle)
        val twoDiscs = listOf(Track(1, "a", "A", "B", 1, 1, trackNo = 2001), Track(2, "b", "A", "B", 1, 1, trackNo = 1003))
        assertEquals(listOf(1, 2), twoDiscs.albums().single().tracks.map { it.disc })
    }

    @Test
    fun formatsTime() {
        assertEquals("0:00", formatTime(-5))
        assertEquals("3:05", formatTime(185_000))
    }
}
