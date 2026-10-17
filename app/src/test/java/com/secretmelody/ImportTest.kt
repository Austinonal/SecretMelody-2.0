package com.secretmelody

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportTest {
    @Test
    fun driveShareLinksBecomeDirectDownloads() {
        val direct = "https://drive.google.com/uc?export=download&id=AbC-123_x"
        assertEquals(direct, normalizeAudioUrl("https://drive.google.com/file/d/AbC-123_x/view?usp=sharing"))
        assertEquals(direct, normalizeAudioUrl("  https://drive.google.com/open?id=AbC-123_x "))
    }

    @Test
    fun otherLinksAreKept() = assertEquals("https://example.com/a.mp3", normalizeAudioUrl(" https://example.com/a.mp3 "))

    @Test
    fun fileNameFromHeaderBeatsUrl() =
        assertEquals("song one.mp3", fileNameFor("https://x.com/dl?id=1", "attachment; filename=\"song one.mp3\"", "audio/mpeg"))

    @Test
    fun fileNameFromUrlIsDecodedAndCleaned() {
        assertEquals("เพลง.mp3", fileNameFor("https://x.com/a/%E0%B9%80%E0%B8%9E%E0%B8%A5%E0%B8%87.mp3?dl=1", null, null))
        assertEquals("a_b.m4a", fileNameFor("https://x.com/a%3Fb.m4a", null, null))
    }

    @Test
    fun audioExtensions() {
        assertTrue(isAudioName("a.MP3"))
        assertTrue(isAudioName("a.flac"))
        assertFalse(isAudioName("a.mp4"))
        assertFalse(isAudioName("a"))
        assertEquals("a.b", withoutExtension("a.b.mp3"))
    }

    @Test
    fun sizes() {
        assertEquals("512 KB", formatSize(512 * 1024))
        assertEquals("5.0 MB", formatSize(5L shl 20))
        assertEquals("1.5 GB", formatSize(3L shl 29))
    }

    @Test
    fun editsOverrideOnlyWhatWasChanged() {
        val a = Track(1, "Old", "Artist", "Al", 9, 1000, key = "old.mp3")
        val b = Track(2, "Keep", "Other", "Al", 9, 1000, key = "keep.mp3")
        val out = listOf(a, b).withEdits(mapOf("old.mp3" to TrackEdit(title = "New", cover = "/c.jpg")))
        assertEquals(a.copy(title = "New", coverPath = "/c.jpg"), out[0])
        assertEquals(b, out[1])
    }
}
