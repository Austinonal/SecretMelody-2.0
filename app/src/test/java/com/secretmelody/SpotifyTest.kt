package com.secretmelody

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpotifyTest {
    @Test
    fun spotifyTrackLinks() {
        val id = "4cOdK2wGLETKBW3PvgPWqT"
        assertEquals(id, spotifyTrackId("https://open.spotify.com/track/$id"))
        assertEquals(id, spotifyTrackId(" https://open.spotify.com/track/$id?si=abc123 "))
        assertEquals(id, spotifyTrackId("https://open.spotify.com/intl-th/track/$id"))
        assertNull(spotifyTrackId("https://open.spotify.com/album/$id"))
        assertNull(spotifyTrackId("https://open.spotify.com/playlist/$id"))
        assertNull(spotifyTrackId("https://open.spotify.com/track/${id}X")) // 23 characters
        assertNull(spotifyTrackId("https://youtu.be/dQw4w9WgXcQ"))
        assertEquals("sp:$id", previewKey("https://open.spotify.com/track/$id"))
        assertEquals("dQw4w9WgXcQ", previewKey("https://youtu.be/dQw4w9WgXcQ"))
        assertNull(previewKey("https://example.com/a.mp3"))
    }

    @Test
    fun spotifyListLinks() {
        val id = "4aawyAB9vmqN3uQ7FjRGTy"
        assertEquals("album/$id", spotifyListPath("https://open.spotify.com/album/$id?si=x"))
        assertEquals("playlist/$id", spotifyListPath("https://open.spotify.com/intl-th/playlist/$id"))
        assertEquals("artist/$id", spotifyListPath("ฟังนี่ https://open.spotify.com/artist/$id"))
        assertNull(spotifyListPath("https://open.spotify.com/track/$id")) // a single song goes the other way
        assertNull(spotifyListPath("https://open.spotify.com/show/$id"))
        assertNull(spotifyTrackId("https://open.spotify.com/album/$id"))
    }

    @Test
    fun embedPageListsTheSongs() {
        // Same shape as open.spotify.com/embed/playlist/37i9dQZF1DXcBWIGoYBM5M, trimmed.
        val html = """<script id="__NEXT_DATA__" type="application/json">{"props":{"pageProps":{"state":{"data":{"entity":
            {"name":"Today’s Top Hits","trackList":[
              {"uri":"spotify:track:3XmwAZpIMNpTJy8faw3ajD","title":"expectations (feat. Chappell Roan)","subtitle":"Olivia Rodrigo,\u00a0Chappell Roan","duration":223935},
              {"uri":"spotify:episode:abc","title":"a podcast"},
              {"uri":"spotify:track:0VjIjW4GlUZAMYd2vXMi3b","title":"Blinding Lights","subtitle":"The Weeknd"}]}}}}}}</script>"""
        val l = parseSpotifyList(html)
        assertEquals("Today’s Top Hits", l.name)
        assertEquals(listOf("3XmwAZpIMNpTJy8faw3ajD", "0VjIjW4GlUZAMYd2vXMi3b"), l.items.map { it.id })
        assertEquals("Olivia Rodrigo, Chappell Roan", l.items[0].artists)
    }

    @Test
    fun embedPageGivesTheSong() {
        // Same shape as open.spotify.com/embed/track/28LO3kx9x3fPRbXfAHprp2, trimmed.
        val html = """<html><script id="__NEXT_DATA__" type="application/json">{"props":{"pageProps":{"state":{"data":{"entity":
            {"name":"ฝนตกไหม","artists":[{"name":"Three Man Down"},{"name":"Guest"}],"duration":261250,
            "visualIdentity":{"image":[{"url":"https://i/300","maxWidth":300},{"url":"https://i/640","maxWidth":640},{"url":"https://i/64","maxWidth":64}]}}}}}}}
            </script></html>"""
        val t = parseSpotifyEmbed(html)
        assertEquals("ฝนตกไหม", t.title)
        assertEquals(listOf("Three Man Down", "Guest"), t.artists)
        assertEquals(261.25, t.durationSec, 0.001)
        assertEquals("https://i/640", t.coverUrl)
    }

    @Test(expected = ImportException::class)
    fun pageWithoutDataIsRefused() {
        parseSpotifyEmbed("<html>blocked</html>")
    }

    private fun c(id: String, title: String, channel: String, duration: Double?) = YtCandidate(id, title, channel, duration)

    // Real `ytsearch10:` results captured on 2026-10-18.
    @Test
    fun thaiSongSkipsTheMvIntroAndLiveVersions() {
        val song = SpotifyTrack("ฝนตกไหม", listOf("Three Man Down"), 261.25, null)
        val results = listOf(
            c("ENnGPkhVu2g", "ฝนตกไหม - Three Man Down |Official MV|", "GeneLab", 317.0),
            c("m9h12tuUqGo", "ฝนตกไหม - Three Man Down |Lyric Video|", "GeneLab", 268.0),
            c("2sxydkgSIDg", "ฝนตกไหม   Three Man Down  (เนื้อเพลง)", "manday music", 260.0),
            c("XtyOtCNBxk8", "ฝนตกไหม - กิต Three Man Down | The Wall Song ร้องข้ามกำแพง", "WorkpointOfficial", 296.0),
            c("dXOEYME8Sx8", "ฝนตกไหม - Three Man Down Live At Impact Arena 2023", "GeneLab", 529.0),
            c("I8o4cmVCaHY", "Is it raining? | Kit THREE MAN DOWN x TorSaksit (Piano & i Live)", "TorBright Channel", 229.0),
            c("Uq5jlgfJhoI", "ฝนตกไหม -​ Three​ Man​ Down​ [เนื้อเพลง​]​", "Song ​L​yrics​ X3", 268.0),
            c("eAi4bhgaGZQ", "ROCK MOUNTAIN 2026 : ฝนตกไหม - THREE MAN DOWN", "GMM Show", 290.0),
            c("KS-SPFb_rlI", "GeneLab LIVE in the Rain | Three Man Down - ฝนตกไหม", "GeneLab", 300.0),
            c("awLLFMcj_m8", "[THAI/ROM/ENG/繁中] ฝนตกไหม fon tok mai 下雨了嗎 Is it raining - Three Man Down lyrics video", "Thai Music", 261.0),
        )
        val ranked = rankCandidates(song, results).map { it.id }
        assertEquals(listOf("awLLFMcj_m8", "2sxydkgSIDg"), ranked.take(2))
        assertEquals(setOf("awLLFMcj_m8", "2sxydkgSIDg", "m9h12tuUqGo", "Uq5jlgfJhoI"), ranked.toSet())
    }

    @Test
    fun englishSongSkipsVideoIntroExtendedAndLoops() {
        val song = SpotifyTrack("Blinding Lights", listOf("The Weeknd"), 200.04, null)
        val results = listOf(
            c("BwX8OS4cegU", "The Weeknd - Blinding Lights (Lyrics)", "7clouds Indie", 201.0),
            c("4NRXx6U8ABQ", "The Weeknd - Blinding Lights (Official Video)", "The Weeknd", 263.0),
            c("XwxLwG2_Sxk", "The Weeknd - Blinding Lights (Lyrics)", "7clouds", 200.0),
            c("fHI8X4OXluQ", "The Weeknd - Blinding Lights (Official Audio)", "The Weeknd", 204.0),
            c("Uj3uJp6DK5k", "The Weeknd - Blinding Lights (Extended Version)", "Extended Version", 642.0),
            c("Xu3CY_2jkZI", "The Weeknd(위켄드) - Blinding Lights [가사/Lyrics]", "웅키", 202.0),
            c("j0HX8HcwPGQ", "The Weeknd - Blinding Lights but its 2 hours long", "no_1", 7200.0),
            c("s4uOQVfw5Vs", "The Weeknd - Blinding Lights (Lyrics)", "Aura Serenity", 220.0),
            c("j283yQlXyVs", "The Weeknd - Blinding Lights (Lyrics)", "Rina Love Notes", 1275.0),
        )
        assertEquals(listOf("XwxLwG2_Sxk", "BwX8OS4cegU", "Xu3CY_2jkZI", "fHI8X4OXluQ"), rankCandidates(song, results).map { it.id })
    }

    @Test
    fun otherVersionsAndOtherArtistsAreRejected() {
        val song = SpotifyTrack("Shape of You", listOf("Ed Sheeran"), 233.7, null)
        val cover = c("aaaaaaaaaaa", "Ed Sheeran - Shape of You (Acoustic Cover)", "x", 234.0)
        val plain = c("fffffffffff", "Ed Sheeran - Shape of You", "x", 236.0)
        assertEquals(listOf(plain, cover), rankCandidates(song, listOf(cover, plain))) // -15 per word not in the song's title
        assertNull(matchScore(song, c("bbbbbbbbbbb", "Shape of You - Someone Else", "Someone", 234.0))) // artist missing
        assertNull(matchScore(song, c("ccccccccccc", "Ed Sheeran - Perfect", "Ed Sheeran", 234.0))) // other song
        assertNull(matchScore(song, c("ddddddddddd", "Ed Sheeran - Shape of You", "Ed Sheeran", null))) // no length
        val remix = SpotifyTrack("Shape of You - Remix", listOf("Ed Sheeran"), 233.7, null)
        assertEquals(true, matchScore(remix, c("eeeeeeeeeee", "Ed Sheeran - Shape of You (Remix)", "x", 234.0)) != null) // asked for
    }

    @Test
    fun searchJsonKeepsOnlyRealVideoIds() {
        val json = """{"entries":[{"id":"dQw4w9WgXcQ","title":"A","channel":"B","duration":213.0},
            {"id":"UC-a-channel-id","title":"a channel"},{"id":"abcdefghijk","title":"C","channel":"D"}]}"""
        val out = parseSearch(json)
        assertEquals(listOf("dQw4w9WgXcQ", "abcdefghijk"), out.map { it.id })
        assertEquals(213.0, out[0].durationSec!!, 0.0)
        assertNull(out[1].durationSec)
    }
}
