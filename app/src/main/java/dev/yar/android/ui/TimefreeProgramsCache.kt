package dev.yar.android.ui

import dev.yar.android.domain.Program

internal data class TimefreeProgramsCache(
    val stationId: String? = null,
    val programsByDate: Map<String, List<Program>> = emptyMap(),
    val loadingDates: Set<String> = emptySet(),
) {
    fun startLoading(stationId: String, date: String): TimefreeProgramsCache {
        val base = if (this.stationId == stationId) this else TimefreeProgramsCache(stationId = stationId)
        return base.copy(loadingDates = base.loadingDates + date)
    }

    fun finishLoading(stationId: String, date: String, programs: List<Program>): TimefreeProgramsCache {
        if (this.stationId != stationId) return this
        return copy(
            programsByDate = programsByDate + (date to programs),
            loadingDates = loadingDates - date,
        )
    }

    fun rememberProgram(stationId: String, program: Program): TimefreeProgramsCache {
        val date = program.startTime.take(8)
        val base = if (this.stationId == stationId) this else TimefreeProgramsCache(stationId = stationId)
        val existing = base.programsByDate[date].orEmpty()
        val nextPrograms = if (existing.any { it.startTime == program.startTime }) existing else existing + program
        return base.copy(programsByDate = base.programsByDate + (date to nextPrograms))
    }

    fun programsFor(stationId: String, date: String): List<Program> =
        if (this.stationId == stationId) programsByDate[date].orEmpty() else emptyList()

    fun isLoading(stationId: String, date: String): Boolean =
        this.stationId == stationId && date in loadingDates

    fun isLoaded(stationId: String, date: String): Boolean =
        this.stationId == stationId && date in programsByDate

    fun datesNeedingLoad(stationId: String, dates: List<BroadcastDate>): List<BroadcastDate> =
        dates.filterNot { date -> isLoaded(stationId, date.value) || isLoading(stationId, date.value) }
}
