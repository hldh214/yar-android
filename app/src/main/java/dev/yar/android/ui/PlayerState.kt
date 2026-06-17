package dev.yar.android.ui

import android.content.ComponentName
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import dev.yar.android.playback.YarMediaLibraryService
import kotlinx.coroutines.delay

internal data class YarPlayerState(
    val controller: MediaController?,
    val isPlaying: Boolean,
    val isLive: Boolean,
    val title: String?,
    val stationId: String?,
    val stationName: String?,
    val artworkUrl: String?,
    val playbackStartTime: String?,
    val playbackEndTime: String?,
    val mediaId: String?,
    val positionMs: Long,
    val durationMs: Long,
    val isBuffering: Boolean,
)

@Composable
internal fun rememberYarPlayerState(): YarPlayerState {
    val context = LocalContext.current
    var controller by remember { mutableStateOf<MediaController?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var isLive by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf<String?>(null) }
    var stationId by remember { mutableStateOf<String?>(null) }
    var stationName by remember { mutableStateOf<String?>(null) }
    var artworkUrl by remember { mutableStateOf<String?>(null) }
    var playbackStartTime by remember { mutableStateOf<String?>(null) }
    var playbackEndTime by remember { mutableStateOf<String?>(null) }
    var positionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }
    var playbackState by remember { mutableStateOf(Player.STATE_IDLE) }
    var activeMediaId by remember { mutableStateOf<String?>(null) }
    var basePositionMs by remember { mutableStateOf(0L) }
    var baseRealtimeMs by remember { mutableStateOf(0L) }

    fun rebaseDisplayPosition(mediaController: MediaController) {
        activeMediaId = mediaController.currentMediaItem?.mediaId
        positionMs = mediaController.displayPositionMs()
        durationMs = mediaController.displayDurationMs()
        basePositionMs = positionMs
        baseRealtimeMs = SystemClock.elapsedRealtime()
    }

    DisposableEffect(context) {
        val token = SessionToken(context, ComponentName(context, YarMediaLibraryService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        var mediaController: MediaController? = null
        future.addListener(
            {
                mediaController = future.get()
                val attachedController = mediaController ?: return@addListener
                controller = attachedController
                isPlaying = attachedController.isPlaying
                playbackState = attachedController.playbackState
                isLive = attachedController.isCurrentMediaLive()
                title = attachedController.mediaMetadata.title?.toString()
                stationId = attachedController.currentMediaItem?.stationId()
                stationName = attachedController.mediaMetadata.albumTitle?.toString()
                artworkUrl = attachedController.mediaMetadata.artworkUri?.toString()
                playbackStartTime = attachedController.currentMediaItem?.playbackStartTime()
                playbackEndTime = attachedController.currentMediaItem?.playbackEndTime()
                rebaseDisplayPosition(attachedController)
                attachedController.addListener(
                    object : Player.Listener {
                        override fun onIsPlayingChanged(nextIsPlaying: Boolean) {
                            isPlaying = nextIsPlaying
                            rebaseDisplayPosition(attachedController)
                        }

                        override fun onPlaybackStateChanged(playbackStateValue: Int) {
                            playbackState = playbackStateValue
                            rebaseDisplayPosition(attachedController)
                        }

                        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                            isLive = attachedController.isCurrentMediaLive()
                            stationId = mediaItem?.stationId()
                            stationName = attachedController.mediaMetadata.albumTitle?.toString()
                            artworkUrl = attachedController.mediaMetadata.artworkUri?.toString()
                            playbackStartTime = mediaItem?.playbackStartTime()
                            playbackEndTime = mediaItem?.playbackEndTime()
                            rebaseDisplayPosition(attachedController)
                        }

                        override fun onMediaMetadataChanged(mediaMetadata: androidx.media3.common.MediaMetadata) {
                            title = mediaMetadata.title?.toString()
                            stationName = mediaMetadata.albumTitle?.toString()
                            artworkUrl = mediaMetadata.artworkUri?.toString()
                        }

                        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                            durationMs = attachedController.displayDurationMs()
                        }
                    },
                )
            },
            MoreExecutors.directExecutor(),
        )

        onDispose {
            mediaController?.release()
            controller = null
        }
    }

    LaunchedEffect(controller) {
        while (controller != null) {
            val mediaController = controller
            if (mediaController != null) {
                isPlaying = mediaController.isPlaying
                playbackState = mediaController.playbackState
                durationMs = mediaController.displayDurationMs()
                isLive = mediaController.isCurrentMediaLive()
                val mediaId = mediaController.currentMediaItem?.mediaId
                if (mediaId != activeMediaId) {
                    rebaseDisplayPosition(mediaController)
                } else if (mediaController.isPlaying && !isLive && durationMs > 0) {
                    positionMs = (basePositionMs + SystemClock.elapsedRealtime() - baseRealtimeMs).coerceIn(0L, durationMs)
                } else {
                    rebaseDisplayPosition(mediaController)
                }
            }
            delay(1000)
        }
    }

    return YarPlayerState(
        controller = controller,
        isPlaying = isPlaying,
        isLive = isLive,
        title = title,
        stationId = stationId,
        stationName = stationName,
        artworkUrl = artworkUrl,
        playbackStartTime = playbackStartTime,
        playbackEndTime = playbackEndTime,
        mediaId = activeMediaId,
        positionMs = positionMs,
        durationMs = durationMs,
        isBuffering = playbackState == Player.STATE_BUFFERING,
    )
}

internal fun MediaController.isCurrentMediaLive(): Boolean {
    val mediaId = currentMediaItem?.mediaId.orEmpty()
    return mediaId.startsWith(YarMediaLibraryService.STATION_PREFIX) || mediaId.startsWith(YarMediaLibraryService.LIVE_PREFIX)
}

internal fun MediaController.displayPositionMs(): Long {
    val timefree = currentMediaItem?.timefreeInfo()
    val position = currentPosition.coerceAtLeast(0L)
    return if (timefree != null) {
        (timefree.seekOffsetMs + position).coerceIn(0L, timefree.durationMs)
    } else {
        position
    }
}

internal fun MediaController.displayDurationMs(): Long {
    val timefree = currentMediaItem?.timefreeInfo()
    return timefree?.durationMs ?: duration.takeIf { it > 0 } ?: 0L
}

internal fun MediaItem.stationId(): String? {
    val mediaId = mediaId
    return when {
        mediaId.startsWith(YarMediaLibraryService.STATION_PREFIX) -> mediaId.removePrefix(YarMediaLibraryService.STATION_PREFIX)
        mediaId.startsWith(YarMediaLibraryService.LIVE_PREFIX) -> mediaId.removePrefix(YarMediaLibraryService.LIVE_PREFIX)
        mediaId.startsWith(YarMediaLibraryService.TIMEFREE_PREFIX) -> mediaId.removePrefix(YarMediaLibraryService.TIMEFREE_PREFIX).substringBefore(":")
        else -> null
    }
}

internal fun MediaItem.playbackStartTime(): String? {
    val mediaId = mediaId
    if (!mediaId.startsWith(YarMediaLibraryService.TIMEFREE_PREFIX)) return null
    return mediaId.removePrefix(YarMediaLibraryService.TIMEFREE_PREFIX).split(":").getOrNull(1)
}

internal fun MediaItem.playbackEndTime(): String? {
    val mediaId = mediaId
    if (!mediaId.startsWith(YarMediaLibraryService.TIMEFREE_PREFIX)) return null
    return mediaId.removePrefix(YarMediaLibraryService.TIMEFREE_PREFIX).split(":").getOrNull(2)
}

private data class TimefreeInfo(
    val durationMs: Long,
    val seekOffsetMs: Long,
)

private fun MediaItem.timefreeInfo(): TimefreeInfo? {
    val mediaId = mediaId
    if (!mediaId.startsWith(YarMediaLibraryService.TIMEFREE_PREFIX)) return null
    val parts = mediaId.removePrefix(YarMediaLibraryService.TIMEFREE_PREFIX).split(":")
    if (parts.size < 3) return null
    val durationMs = radikoDurationMs(parts[1], parts[2]) ?: return null
    val seekOffsetMs = parts.getOrNull(3)?.toLongOrNull()?.coerceAtLeast(0L)?.times(1000L) ?: 0L
    return TimefreeInfo(durationMs = durationMs, seekOffsetMs = seekOffsetMs)
}
