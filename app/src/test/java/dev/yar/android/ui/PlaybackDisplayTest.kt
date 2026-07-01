package dev.yar.android.ui

import androidx.media3.common.MediaItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

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
    fun `timefree display position uses service content position without adding seek offset again`() {
        val mediaItem = MediaItem.Builder()
            .setMediaId("yar_timefree:FMT:20260701000000:20260701000300:120")
            .build()

        assertEquals(45_000L, displayTimefreePositionMs(contentPositionMs = 45_000L, mediaItem = mediaItem))
        assertEquals(180_000L, displayTimefreePositionMs(contentPositionMs = 220_000L, mediaItem = mediaItem))
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
    fun `broadcast date label uses absolute date with japanese weekday`() {
        val date = LocalDateTime.of(2026, 7, 1, 12, 0).atOffset(ZoneOffset.ofHours(9))

        assertEquals("7/1(水)", formatBroadcastDateLabel(date))
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
