package com.secretmelody

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamTest {
    @Test
    fun clockTimes() {
        assertEquals(260_000L, parseClock("4:20"))
        assertEquals(3_723_000L, parseClock("1:02:03"))
        assertNull(parseClock("LIVE"))
        assertNull(parseClock("1:2:3:4"))
    }

    @Test
    fun searchResultsBecomeStreamSongs() {
        // The shape of a real youtubei/v1/search response, trimmed; the live stream has no length and is left out.
        val json = """{"contents":{"twoColumnSearchResultsRenderer":{"primaryContents":{"sectionListRenderer":{"contents":[
            {"itemSectionRenderer":{"contents":[
              {"videoRenderer":{"videoId":"KdaRtd_xEGk","title":{"runs":[{"text":"ฝนตกไหม - Three Man Down |Official MV|"}]},
                "ownerText":{"runs":[{"text":"GeneLab"}]},"lengthText":{"simpleText":"4:21"}}},
              {"videoRenderer":{"videoId":"jfKfPfyJRdk","title":{"runs":[{"text":"lofi hip hop radio"}]},"ownerText":{"runs":[{"text":"Lofi Girl"}]}}},
              {"shelfRenderer":{}}
            ]}},
            {"continuationItemRenderer":{}}
        ]}}}}}"""
        val songs = parseYtSearch(json)
        assertEquals(1, songs.size)
        val s = songs[0]
        assertEquals("ฝนตกไหม" to "Three Man Down", s.title to s.artist)
        assertEquals(261_000L, s.durationMs)
        assertTrue(s.isStream)
        assertEquals("KdaRtd_xEGk", s.videoId)
        assertTrue(s.id < 0)
        assertEquals("https://i.ytimg.com/vi/KdaRtd_xEGk/mqdefault.jpg", s.remoteArt)
        assertEquals(emptyList<Track>(), parseYtSearch("{}"))
    }

    @Test
    fun accountPlaylistsAndLibraryFile() {
        // yt-dlp --flat-playlist -J of a liked list: channel is null there, so the title split decides the artist.
        val liked = JSONObject("""{"entries":[
            {"id":"ekr2nIex040","title":"ROSÉ & Bruno Mars - APT. (Official Music Video)","duration":174.0,"channel":null},
            {"id":"aaaaaaaaaaa","title":"[Private video]","duration":null},
            {"id":"bad","title":"x"}
        ]}""")
        val songs = parseYtEntries(liked)
        assertEquals(listOf(Triple("APT.", "ROSÉ & Bruno Mars", 174_000L)), songs.map { Triple(it.title, it.artist, it.durationMs) })

        val feed = JSONObject("""{"entries":[{"id":"PLabc","title":"ขับรถ"},{"id":"WL","title":"Watch later"},{"id":"OLAKxyz","title":""}]}""")
        assertEquals(listOf("PLabc" to "ขับรถ", "OLAKxyz" to "เพลย์ลิสต์"), playlistRefs(feed))

        val lib = YtLibrary(1_700_000_000_000, listOf(YtList("LM", "เพลงที่ชอบบน YouTube", songs)))
        assertEquals(lib, decodeYtLibrary(encodeYtLibrary(lib)))
    }

    @Test
    fun cookiesFileForYtDlp() {
        val lines = netscapeCookies("SID=a1; SAPISID=b/2=; junk").lines()
        assertEquals("# Netscape HTTP Cookie File", lines[0])
        assertEquals(listOf(".youtube.com", "TRUE", "/", "TRUE"), lines[1].split('\t').take(4))
        assertEquals(listOf("SID", "a1"), lines[1].split('\t').drop(5))
        assertEquals(listOf("SAPISID", "b/2="), lines[2].split('\t').drop(5))
        assertEquals(4, lines.size) // header, two cookies, trailing newline
    }
}
