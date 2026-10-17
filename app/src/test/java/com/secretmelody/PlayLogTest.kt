package com.secretmelody

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayLogTest {
    private val t0 = 1_000_000L

    @Test
    fun noSwipeOrSwipeWhilePausedTellsNothing() {
        assertEquals(SwipeResult.NONE, swipeResult(PlayState(), t0))
        assertEquals(SwipeResult.NONE, swipeResult(PlayState(swipeAt = t0, swipePlaying = false), t0 + 60_000))
    }

    @Test
    fun beatingLongAfterTheSwipeMeansItSurvived() =
        assertEquals(SwipeResult.SURVIVED, swipeResult(PlayState(t0, true, beatAt = t0 + 20_000), t0 + 25_000))

    @Test
    fun playerStoppingItselfIsNotAKill() =
        assertEquals(SwipeResult.STOPPED_NORMALLY, swipeResult(PlayState(t0, true, beatAt = t0 + 5_000, stopAt = t0 + 6_000), t0 + 90_000))

    @Test
    fun silenceWithoutAStopEventIsAKillButOnlyOnceEnoughTimePassed() {
        val s = PlayState(t0, true, beatAt = t0)
        assertEquals(SwipeResult.NONE, swipeResult(s, t0 + 10_000))
        assertEquals(SwipeResult.KILLED, swipeResult(s, t0 + 40_000))
    }

    @Test
    fun stateSurvivesEncodingAndGarbage() {
        val s = PlayState(1, true, 3, 4)
        assertEquals(s, decodePlayState(s.encode()))
        assertEquals(PlayState(), decodePlayState("nonsense\n=\nbeatAt=abc"))
    }
}
