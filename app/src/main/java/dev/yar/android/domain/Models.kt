package dev.yar.android.domain

data class Station(
    val id: String,
    val name: String,
    val asciiName: String,
    val areaId: String,
    val logoUrl: String,
)

data class Region(
    val id: String,
    val name: String,
    val stations: List<Station>,
)

data class Program(
    val id: String,
    val stationId: String,
    val title: String,
    val subtitle: String,
    val performer: String,
    val description: String,
    val info: String,
    val url: String,
    val startTime: String,
    val endTime: String,
    val imageUrl: String?,
    val isOnAir: Boolean,
    val isTimefree: Boolean,
    val durationSeconds: Long,
)

data class NoaItem(
    val id: String,
    val title: String,
    val artist: String,
    val stamp: String,
    val imageUrl: String,
    val largeImageUrl: String,
    val amazonUrl: String,
    val itunesUrl: String,
    val recochokuUrl: String,
)

sealed interface PlaybackRequest {
    data class Live(val station: Station, val program: Program?) : PlaybackRequest

    data class Timefree(
        val station: Station,
        val program: Program,
        val seekSeconds: Long = 0,
    ) : PlaybackRequest
}
