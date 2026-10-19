package com.secretmelody

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FeaturesTest {
    private fun t(id: Long, artist: String = "A") = Track(id, "song $id", artist, "", 0, 200_000, key = "k$id")

    @Test
    fun statsRoundTripAndIgnoreJunk() {
        val stats = mapOf("1" to SongStat(3, 600), "22" to SongStat(0, 5))
        assertEquals(stats, decodeStats(encodeStats(stats)))
        assertEquals(mapOf("1" to SongStat(2, 9)), decodeStats("1\t2\t9\nbroken line\n3\tx\t1\n"))
    }

    @Test
    fun mostPlayedNeedsTwoPlaysAndOrdersByPlaysThenTime() {
        val tracks = listOf(t(1), t(2), t(3), t(4))
        val stats = mapOf("1" to SongStat(2, 100), "2" to SongStat(5, 50), "3" to SongStat(1, 9999), "4" to SongStat(2, 300))
        assertEquals(listOf(2L, 4L, 1L), mostPlayed(tracks, stats).map { it.id })
    }

    @Test
    fun summaryTotalsTopSongsAndArtists() {
        val tracks = listOf(t(1, "X"), t(2, "Y"), t(3, "X"))
        val s = summarize(tracks, mapOf("1" to SongStat(4, 100), "2" to SongStat(1, 500), "3" to SongStat(2, 50), "99" to SongStat(9, 9)))
        assertEquals(650L, s.seconds) // a deleted song (99) is left out
        assertEquals(7, s.plays)
        assertEquals(listOf(1L, 3L, 2L), s.songs.map { it.first.id })
        assertEquals(listOf("Y" to 500L, "X" to 150L), s.artists)
        assertEquals("1 ชม. 2 นาที", formatListening(3725))
        assertEquals("5 นาที", formatListening(300))
        assertEquals("40 วินาที", formatListening(40))
    }

    @Test
    fun audioSettingsSurviveTheFileAndBadInput() {
        val a = AudioSettings(skipSilence = true, evenVolume = false, eqOn = true, eqLevels = listOf(300, -150, 0, 600, -1500))
        assertEquals(a, audioSettingsFrom(a.toJson()))
        assertEquals(AudioSettings(), audioSettingsFrom("not json"))
        assertEquals(AudioSettings(skipSilence = true), audioSettingsFrom("""{"skipSilence":true}"""))
        assertEquals("60 Hz", formatHz(60))
        assertEquals("14 kHz", formatHz(14000))
    }

    @Test
    fun lrcParsing() {
        val lines = parseLrc(
            """
            [ar:Three Man Down]
            [00:09.35] ฝนตกอีกแล้ว
            [00:15.05]คืนนี้คงหนาวกว่าคืนไหนไหน
            [01:02.5][02:10.123]ซ้ำสองที่
            [00:20]
            ไม่มีเวลา
            """.trimIndent(),
        )
        assertEquals(
            listOf(
                LyricLine(9_350, "ฝนตกอีกแล้ว"), LyricLine(15_050, "คืนนี้คงหนาวกว่าคืนไหนไหน"), LyricLine(20_000, ""),
                LyricLine(62_500, "ซ้ำสองที่"), LyricLine(130_123, "ซ้ำสองที่"),
            ),
            lines,
        )
        assertEquals(-1, currentLine(lines, 5_000))
        assertEquals(0, currentLine(lines, 9_350))
        assertEquals(2, currentLine(lines, 30_000))
        assertEquals(4, currentLine(lines, 999_999))
    }

    @Test
    fun searchResultsPreferSyncedAndCloseLength() {
        // Real LRCLIB search results for ฝนตกไหม (trimmed), song length 261 s.
        val results = JSONArray(
            """[
            {"trackName":"ฝนตกไหม","duration":263.0,"syncedLyrics":null,"plainLyrics":"plain only"},
            {"trackName":"ฝนตกไหม","duration":261.0,"syncedLyrics":"[00:09.35] ฝนตกอีกแล้ว","plainLyrics":"ฝนตกอีกแล้ว"},
            {"trackName":"ฝนตกไหม","duration":184.0,"syncedLyrics":"[00:01.00] other cut","plainLyrics":"x"}
            ]""",
        )
        assertEquals(listOf(LyricLine(9_350, "ฝนตกอีกแล้ว")), pickLyrics(results, 261.25)?.synced)
        assertEquals("plain only", pickLyrics(JSONArray().put(results.get(0)), 261.0)?.plain)
        assertNull(pickLyrics(JSONArray().put(results.get(2)), 261.0)) // 77 s off: another version
    }

    @Test
    fun lyricsLookupUsesThePlainTitleAndMainArtist() {
        assertEquals("Blinding Lights", cleanTitle("Blinding Lights (Official Video) [4K]"))
        assertEquals("Yesterday", cleanTitle("Yesterday - Remastered 2009"))
        assertEquals("(Untitled)", cleanTitle("(Untitled)")) // never empties the title
        assertEquals("Lady Gaga", mainArtist("Lady Gaga feat. Bruno Mars"))
        assertEquals("ROSÉ", mainArtist("ROSÉ & Bruno Mars"))
        assertEquals("Three Man Down", mainArtist("Three Man Down"))
    }

    @Test
    fun translationAnswerAndCache() {
        // Shape of Google Translate's web answer: segments (translated, original, …), then the detected language.
        val answer = """[[["ฉันเดินอยู่ท่ามกลางสายฝน\n","I've been walking in the rain\n",null,null,10],["คุณทำให้ฉันมีชีวิตชีวา","You make me feel alive",null,null,10]],null,"en"]"""
        assertEquals("en" to "ฉันเดินอยู่ท่ามกลางสายฝน\nคุณทำให้ฉันมีชีวิตชีวา", parseTranslate(answer))
        val tr = Translation("en", listOf("ก", "", "ข"))
        assertEquals(tr, decodeTranslation(encodeTranslation(tr), 3))
        assertNull(decodeTranslation(encodeTranslation(tr), 4)) // lyrics changed: translate again
        assertNull(decodeTranslation("junk", 3))
        assertEquals(Translation("th", emptyList()), decodeTranslation(encodeTranslation(Translation("th", emptyList())), 9))
    }

    @Test
    fun lyricsOfOtherSongsAreRejected() {
        val results = JSONArray(
            """[
            {"trackName":"Smile","artistName":"Lily Allen","duration":252.0,"syncedLyrics":"[00:01.00] wrong song"},
            {"trackName":"Die With A Smile","artistName":"Someone Else","duration":252.0,"syncedLyrics":"[00:01.00] wrong artist"},
            {"trackName":"Die With A Smile - Instrumental","artistName":"Lady Gaga & Bruno Mars","duration":252.0,"syncedLyrics":"[00:01.00] instrumental"},
            {"trackName":"Lady Gaga, Bruno Mars - Die With A Smile","artistName":"Lady Gaga","duration":251.0,"syncedLyrics":"[00:01.00] right"}
            ]""",
        )
        val got = pickLyrics(results, 252.0, "Die With A Smile (Official Video)", "Lady Gaga feat. Bruno Mars")
        assertEquals(listOf(LyricLine(1_000, "right")), got?.synced)
        assertNull(pickLyrics(results, 252.0, "Garden", "Lady Gaga"))
        assertNull(pickLyrics(results, 252.0, "Die With A Smile", "Taylor Swift"))
    }
}
