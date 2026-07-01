package dev.yar.android.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackDisplayTest {
    @Test
    fun `playback status label prefers loading before live and timefree`() {
        assertEquals("LOADING", playbackStatusLabel(isLive = true, busy = true))
        assertEquals("LIVE", playbackStatusLabel(isLive = true, busy = false))
        assertEquals("TIMEFREE", playbackStatusLabel(isLive = false, busy = false))
    }

    @Test
    fun `playback progress ratio clamps invalid and out of range values`() {
        assertEquals(0f, playbackProgressRatio(positionMs = 10_000L, durationMs = 0L))
        assertEquals(0f, playbackProgressRatio(positionMs = -100L, durationMs = 10_000L))
        assertEquals(0.25f, playbackProgressRatio(positionMs = 2_500L, durationMs = 10_000L))
        assertEquals(1f, playbackProgressRatio(positionMs = 12_000L, durationMs = 10_000L))
    }

    @Test
    fun `station subtitle prefers ascii name then station id`() {
        assertEquals("TBS Radio", stationSubtitle(asciiName = "TBS Radio", stationId = "TBS"))
        assertEquals("TBS", stationSubtitle(asciiName = "", stationId = "TBS"))
        assertEquals("TBS", stationSubtitle(asciiName = "   ", stationId = "TBS"))
    }

    @Test
    fun `region picker label falls back when region is absent`() {
        assertEquals("Choose region", regionPickerLabel(null))
        assertEquals("Tokyo", regionPickerLabel("Tokyo"))
        assertEquals("Choose region", regionPickerLabel(""))
        assertEquals("Choose region", regionPickerLabel("   "))
    }

    @Test
    fun `recent station limit keeps at most ten items`() {
        assertEquals(listOf("0", "1", "2"), visibleRecentItems(listOf("0", "1", "2")))
        assertEquals(
            (0 until 10).map { it.toString() },
            visibleRecentItems((0 until 12).map { it.toString() }),
        )
    }

    @Test
    fun `player controls lock for switching seeking or buffering`() {
        assertTrue(controlsLocked(isSwitching = true, isSeeking = false, isBuffering = false))
        assertTrue(controlsLocked(isSwitching = false, isSeeking = true, isBuffering = false))
        assertTrue(controlsLocked(isSwitching = false, isSeeking = false, isBuffering = true))
        assertFalse(controlsLocked(isSwitching = false, isSeeking = false, isBuffering = false))
    }
}
