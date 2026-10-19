package com.secretmelody

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubeTest {
    private val id = "dQw4w9WgXcQ"

    @Test
    fun youtubeLinksGiveTheVideoId() {
        listOf(
            "https://www.youtube.com/watch?v=$id",
            "https://youtube.com/watch?v=$id&t=30s",
            "https://m.youtube.com/watch?v=$id",
            "https://music.youtube.com/watch?v=$id&list=RDAMVM$id",
            "https://www.youtube.com/watch?feature=share&v=$id",
            "https://youtu.be/$id?si=AbCdEf",
            "  https://www.youtube.com/shorts/$id  ",
        ).forEach { assertEquals(it, id, youtubeId(it)) }
    }

    @Test
    fun otherLinksAreNotYoutube() {
        listOf(
            "https://www.youtube.com/playlist?list=PL1234567890",
            "https://vimeo.com/123",
            "",
            "; rm -rf /",
            "https://youtu.be/${id}X", // 12 characters
            "https://example.com/a.mp3",
        ).forEach { assertNull(it, youtubeId(it)) }
    }

    @Test
    fun safeFileNames() {
        assertEquals("a_b_c_d_e_f_g_h_i_", safeFileName("""a\b/c:d*e?f"g<h>i|"""))
        assertEquals("a_b", safeFileName("a\u0000b"))
        assertEquals("a_b", safeFileName("a\nb"))
        assertEquals(150, safeFileName("ก".repeat(400)).length)
        assertEquals("เพลงที่นำเข้า", safeFileName("   "))
        assertEquals("Song (Live)", safeFileName("  Song (Live) "))
    }

    @Test
    fun durationLimits() {
        checkDuration(240 * 60L, YtFormat.MP3)
        checkDuration(30 * 60L, YtFormat.MP4)
        tooLong { checkDuration(241 * 60L, YtFormat.MP3) }
        tooLong { checkDuration(31 * 60L, YtFormat.MP4) }
        for (s in listOf(null, 0L)) {
            assertEquals("วิดีโอนี้แปลงไม่ได้", assertThrows(ImportException::class.java) { checkDuration(s, YtFormat.MP3) }.message)
        }
    }

    @Test
    fun oembedGivesTitleAndArtist() {
        assertEquals("Song" to "Rick Astley", parseOembed("""{"title":"Song","author_name":"Rick Astley","type":"video"}"""))
        assertEquals("เพลง" to "ศิลปิน", parseOembed("""{"title":"เพลง","author_name":"ศิลปิน - Topic"}"""))
        assertEquals("" to "", parseOembed("{}"))
    }

    @Test
    fun linksFromSharedText() {
        val watch = "https://www.youtube.com/watch?v=$id"
        assertEquals(watch, linkFromShare("https://youtu.be/$id?si=AbCdEf"))
        assertEquals(watch, linkFromShare("Never Gonna Give You Up\nhttps://youtube.com/shorts/$id?feature=share"))
        assertEquals("https://example.com/a.mp3", linkFromShare("ฟังนี่ https://example.com/a.mp3 นะ"))
        assertNull(linkFromShare("แค่ข้อความ ไม่มีลิงก์"))
        assertNull(linkFromShare("http://example.com/a.mp3"))
    }

    @Test
    fun clipTitlesSplitIntoSongAndArtist() {
        // Real clip titles and channels from YouTube searches.
        assertEquals("Never Gonna Give You Up" to "Rick Astley", splitVideoTitle("Rick Astley - Never Gonna Give You Up (Official Video) (4K Remaster)", "Rick Astley"))
        assertEquals("Blinding Lights" to "The Weeknd", splitVideoTitle("The Weeknd - Blinding Lights (Official Video)", "TheWeekndVEVO"))
        assertEquals("ฝนตกไหม" to "Three Man Down", splitVideoTitle("ฝนตกไหม - Three Man Down |Official MV|", "GeneLab"))
        assertEquals("ฝนตกไหม" to "Three Man Down", splitVideoTitle("ฝนตกไหม - Three Man Down |Lyric Video|", "GeneLab"))
        assertEquals("ทางผ่าน" to "Polycat", splitVideoTitle("Polycat - ทางผ่าน [Official MV]", "Polycat"))
        assertEquals("ฝนตกไหม" to "กิต Three Man Down", splitVideoTitle("ฝนตกไหม - กิต Three Man Down | The Wall Song ร้องข้ามกำแพง", "WorkpointOfficial"))
        assertEquals("Mr. Brightside" to "The Killers", splitVideoTitle("The Killers - Mr. Brightside (Official Music Video)", "TheKillersMusic"))
        // No separator: the cleaned title, the channel as artist.
        assertEquals("Shape of You" to "Ed Sheeran", splitVideoTitle("Shape of You [Official Video]", "Ed Sheeran"))
        assertEquals("ลมหายใจ" to "Bodyslam", splitVideoTitle("ลมหายใจ", "Bodyslam - Topic"))
    }

    private fun tooLong(block: () -> Unit) =
        assertTrue(assertThrows(ImportException::class.java) { block() }.message!!.startsWith("วิดีโอยาวเกินไป"))
}
