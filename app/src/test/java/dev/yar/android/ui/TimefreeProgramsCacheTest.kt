package dev.yar.android.ui

import dev.yar.android.domain.Program
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimefreeProgramsCacheTest {
    @Test
    fun `cache keeps programs by station and date`() {
        val cache = TimefreeProgramsCache()
            .startLoading(stationId = "YFM", date = "20260701")
            .finishLoading(stationId = "YFM", date = "20260701", programs = listOf(sampleProgram("p1")))

        assertEquals(listOf(sampleProgram("p1")), cache.programsFor(stationId = "YFM", date = "20260701"))
        assertEquals(emptyList<Any>(), cache.programsFor(stationId = "FMT", date = "20260701"))
        assertTrue(cache.isLoaded(stationId = "YFM", date = "20260701"))
        assertFalse(cache.isLoading(stationId = "YFM", date = "20260701"))
    }

    @Test
    fun `dates needing load skip cached and loading dates`() {
        val dates = listOf(
            BroadcastDate(value = "20260701", label = "7/1(水)"),
            BroadcastDate(value = "20260630", label = "6/30(火)"),
            BroadcastDate(value = "20260629", label = "6/29(月)"),
        )
        val cache = TimefreeProgramsCache()
            .startLoading(stationId = "YFM", date = "20260630")
            .finishLoading(stationId = "YFM", date = "20260701", programs = emptyList())

        assertEquals(listOf(dates[2]), cache.datesNeedingLoad(stationId = "YFM", dates = dates))
        assertEquals(dates, cache.datesNeedingLoad(stationId = "FMT", dates = dates))
    }

    @Test
    fun `late loads from another station do not overwrite current cache`() {
        val cache = TimefreeProgramsCache(stationId = "FMT")
            .finishLoading(stationId = "YFM", date = "20260701", programs = listOf(sampleProgram("late")))

        assertEquals("FMT", cache.stationId)
        assertEquals(emptyList<Any>(), cache.programsFor(stationId = "YFM", date = "20260701"))
    }

    @Test
    fun `remember program appends without replacing a loaded date`() {
        val cache = TimefreeProgramsCache(stationId = "YFM")
            .finishLoading(stationId = "YFM", date = "20260701", programs = listOf(sampleProgram("p1")))
            .rememberProgram(stationId = "YFM", program = sampleProgram("p2", startTime = "20260701100000"))

        assertEquals(
            listOf(sampleProgram("p1"), sampleProgram("p2", startTime = "20260701100000")),
            cache.programsFor(stationId = "YFM", date = "20260701"),
        )
    }
}

private fun sampleProgram(id: String, startTime: String = "20260701090000"): Program = Program(
    id = id,
    stationId = "YFM",
    title = "Program $id",
    subtitle = "",
    performer = "",
    description = "",
    info = "",
    url = "",
    startTime = startTime,
    endTime = "20260701100000",
    imageUrl = null,
    isOnAir = false,
    isTimefree = true,
    durationSeconds = 3600,
)
