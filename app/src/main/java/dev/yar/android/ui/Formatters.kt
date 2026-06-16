package dev.yar.android.ui

import dev.yar.android.domain.NoaItem

internal data class BroadcastDate(val value: String, val label: String)

internal fun broadcastDates(): List<BroadcastDate> {
    val nowJst = java.time.Instant.now().atOffset(java.time.ZoneOffset.UTC).plusHours(9)
    val today = if (nowJst.hour < 5) nowJst.minusDays(1) else nowJst
    return (0L..6L).map { offset ->
        val date = today.minusDays(offset)
        BroadcastDate(
            value = "%04d%02d%02d".format(date.year, date.monthValue, date.dayOfMonth),
            label = if (offset == 0L) "Today" else "-${offset}d",
        )
    }
}

internal fun currentRadikoTimestamp(): String {
    val jst = java.time.Instant.now().atOffset(java.time.ZoneOffset.UTC).plusHours(9)
    return "%04d%02d%02d%02d%02d%02d".format(
        jst.year,
        jst.monthValue,
        jst.dayOfMonth,
        jst.hour,
        jst.minute,
        jst.second,
    )
}

internal fun formatRadikoTime(value: String): String = if (value.length >= 12) {
    "${value.substring(8, 10)}:${value.substring(10, 12)}"
} else {
    "--:--"
}

internal fun formatDuration(milliseconds: Long): String {
    val totalSeconds = (milliseconds / 1000).coerceAtLeast(0L)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

internal fun currentNoaItem(
    songs: List<NoaItem>,
    isLive: Boolean,
    programStartTime: String?,
    positionMs: Long,
): NoaItem? {
    if (songs.isEmpty()) return null
    val filtered = if (!programStartTime.isNullOrBlank()) {
        songs.filter { song ->
            val songTime = songRadikoTimestamp(song)
            songTime == null || songTime >= programStartTime
        }
    } else {
        songs
    }
    if (filtered.isEmpty()) return null
    if (isLive) return filtered.maxByOrNull { it.stamp }

    val startInstant = programStartTime?.let { runCatching { parseRadikoInstant(it) }.getOrNull() } ?: return null
    val playbackInstant = startInstant.plusMillis(positionMs)
    return filtered
        .mapNotNull { song -> parseNoaInstant(song.stamp)?.let { it to song } }
        .filter { (stamp, _) -> !stamp.isAfter(playbackInstant) }
        .maxByOrNull { (stamp, _) -> stamp }
        ?.second
}

internal fun formatNoaStamp(stamp: String): String {
    val instant = parseNoaInstant(stamp) ?: return ""
    val jst = instant.atOffset(java.time.ZoneOffset.ofHours(9))
    return "%02d:%02d".format(jst.hour, jst.minute)
}

internal fun radikoDurationMs(startTime: String, endTime: String): Long? = runCatching {
    java.time.Duration.between(parseRadikoInstant(startTime), parseRadikoInstant(endTime)).toMillis().coerceAtLeast(0L)
}.getOrNull()

private fun songRadikoTimestamp(song: NoaItem): String? {
    val match = Regex("^(\\d{4})-(\\d{2})-(\\d{2})T(\\d{2}):(\\d{2}):(\\d{2})").find(song.id)
        ?: Regex("^(\\d{4})-(\\d{2})-(\\d{2})T(\\d{2}):(\\d{2}):(\\d{2})").find(song.stamp)
        ?: return null
    return match.groupValues.drop(1).joinToString("")
}

private fun parseNoaInstant(stamp: String): java.time.Instant? = runCatching {
    java.time.OffsetDateTime.parse(stamp).toInstant()
}.getOrNull()

internal fun parseRadikoInstant(timestamp: String): java.time.Instant = java.time.LocalDateTime.of(
    timestamp.substring(0, 4).toInt(),
    timestamp.substring(4, 6).toInt(),
    timestamp.substring(6, 8).toInt(),
    timestamp.substring(8, 10).toInt(),
    timestamp.substring(10, 12).toInt(),
    timestamp.substring(12, 14).toInt(),
).atOffset(java.time.ZoneOffset.ofHours(9)).toInstant()
