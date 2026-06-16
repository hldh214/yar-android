package dev.yar.android.data

import android.content.Context

class RecentStationsStore(context: Context) {
    private val preferences = context.getSharedPreferences("recent_stations", Context.MODE_PRIVATE)

    fun record(stationId: String) {
        val nextIds = listOf(stationId)
            .plus(getStationIds().filterNot { it == stationId })
            .take(MAX_RECENT_STATIONS)
        preferences.edit().putString(KEY_STATION_IDS, nextIds.joinToString(",")).apply()
    }

    fun getStationIds(): List<String> = preferences.getString(KEY_STATION_IDS, null)
        ?.split(",")
        ?.map { it.trim() }
        ?.filter { it.isNotBlank() }
        .orEmpty()

    private companion object {
        const val KEY_STATION_IDS = "station_ids"
        const val MAX_RECENT_STATIONS = 12
    }
}
