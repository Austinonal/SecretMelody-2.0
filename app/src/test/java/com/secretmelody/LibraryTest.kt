package com.secretmelody

import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryTest {
    @Test
    fun movesUpAndDown() {
        assertEquals(listOf("b", "a", "c"), listOf("a", "b", "c").moved(1, 0))
        assertEquals(listOf("a", "c", "b"), listOf("a", "b", "c").moved(1, 2))
    }

    @Test
    fun moveOutOfRangeChangesNothing() {
        assertEquals(listOf("a", "b"), listOf("a", "b").moved(0, -1))
        assertEquals(listOf("a", "b"), listOf("a", "b").moved(-1, 0))
        assertEquals(listOf("a", "b"), listOf("a", "b").moved(1, 2))
    }

    @Test
    fun recentPutsLatestFirstWithoutDuplicates() {
        assertEquals(listOf("3", "1", "2"), recentAfter(listOf("1", "2", "3"), "3"))
        assertEquals(listOf("9", "1"), recentAfter(listOf("1", "2"), "9", max = 2))
    }
}
