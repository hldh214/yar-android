package dev.yar.android.data

import dev.yar.android.domain.Program
import dev.yar.android.domain.NoaItem
import dev.yar.android.domain.Region

interface RadikoClient {
    suspend fun getRegions(): List<Region>

    suspend fun getPrograms(stationId: String, date: String? = null): List<Program>

    suspend fun getLatestNoaItems(stationId: String): List<NoaItem>

    suspend fun getNoaItems(stationId: String, startTime: String, endTime: String): List<NoaItem>

    suspend fun getLivePlaylistUrl(stationId: String): String

    suspend fun getLiveStream(stationId: String): ResolvedStream

    suspend fun getTimefreeStream(
        stationId: String,
        startTime: String,
        endTime: String,
        seekTime: String = startTime,
    ): ResolvedStream

    suspend fun getTimefreePlaylistUrl(
        stationId: String,
        startTime: String,
        endTime: String,
        seekSeconds: Long = 0,
    ): String
}

data class ResolvedStream(
    val playlistUrl: String,
    val authToken: String,
    val areaId: String,
)
