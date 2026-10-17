package com.secretmelody

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ResumeTest {
    private val a = ResumeItem("1", "content://media/external/audio/media/1", "Title\twith tab", "Artist", "Album", "")
    private val b = ResumeItem("2", "content://media/external/audio/media/2", "Two", "B", "", "file:///c.jpg")

    @Test
    fun roundTripsQueueAndPosition() {
        val s = ResumeState(1, 93_500, listOf(a, b))
        assertEquals(s.copy(items = listOf(a.copy(title = "Title with tab"), b)), decodeResume(s.encode()))
    }

    @Test
    fun clampsAnOutOfRangeIndex() = assertEquals(0, decodeResume(ResumeState(9, 0, listOf(b)).encode())!!.index)

    @Test
    fun emptyOrBrokenFilesResumeNothing() {
        assertNull(decodeResume(""))
        assertNull(decodeResume("index=0\npos=0\n"))
        assertNull(decodeResume("garbage"))
    }
}
