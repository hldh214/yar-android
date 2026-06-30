package dev.yar.android.ui

internal fun playbackStatusLabel(isLive: Boolean, busy: Boolean): String = when {
    busy -> "LOADING"
    isLive -> "LIVE"
    else -> "TIMEFREE"
}

internal fun playbackProgressRatio(positionMs: Long, durationMs: Long): Float {
    if (durationMs <= 0L) return 0f
    return (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
}

internal fun stationSubtitle(asciiName: String, stationId: String): String =
    asciiName.ifBlank { stationId }

internal fun controlsLocked(isSwitching: Boolean, isSeeking: Boolean, isBuffering: Boolean): Boolean =
    isSwitching || isSeeking || isBuffering
