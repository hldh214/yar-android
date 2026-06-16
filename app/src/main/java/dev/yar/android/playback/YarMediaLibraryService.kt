package dev.yar.android.playback

import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.ForwardingSimpleBasePlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dev.yar.android.data.HttpRadikoClient
import dev.yar.android.data.RecentStationsStore
import dev.yar.android.domain.Region
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.launch

@UnstableApi
class YarMediaLibraryService : MediaLibraryService() {
    private var player: ExoPlayer? = null
    private var session: MediaLibrarySession? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var client: HttpRadikoClient
    private lateinit var recentStationsStore: RecentStationsStore
    private var currentPlayback: CurrentPlayback? = null

    override fun onCreate() {
        super.onCreate()
        client = HttpRadikoClient(this)
        recentStationsStore = RecentStationsStore(this)
        val exoPlayer = ExoPlayer.Builder(this).build()
        player = exoPlayer
        exoPlayer.addListener(
            object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    updateTimefreeProgress(isPlaying)
                }
            },
        )
        val sessionPlayer = object : ForwardingSimpleBasePlayer(exoPlayer) {
            override fun getState(): State {
                val state = super.getState()
                val playback = currentPlayback
                if (playback is CurrentPlayback.Timefree) {
                    val mediaItem = exoPlayer.currentMediaItem
                    if (mediaItem != null) {
                        val durationMs = timefreeDurationMs(playback)
                        val positionMs = currentTimefreePositionMs(playback)
                        val itemData = SimpleBasePlayer.MediaItemData.Builder(mediaItem.mediaId)
                            .setMediaItem(mediaItem)
                            .setMediaMetadata(mediaItem.mediaMetadata)
                            .setIsSeekable(true)
                            .setIsDynamic(false)
                            .setDurationUs(durationMs * 1000L)
                            .build()
                        return state.buildUpon()
                            .setPlaylist(ImmutableList.of(itemData))
                            .setCurrentMediaItemIndex(0)
                            .setContentPositionMs(positionMs)
                            .setContentBufferedPositionMs(SimpleBasePlayer.PositionSupplier.getConstant(positionMs))
                            .setSeekBackIncrementMs(SKIP_SECONDS * 1000L)
                            .setSeekForwardIncrementMs(SKIP_SECONDS * 1000L)
                            .build()
                    }
                }
                return state
            }

            override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
                val playback = currentPlayback
                if (playback is CurrentPlayback.Timefree) {
                    val currentSeconds = currentTimefreePositionMs(playback) / 1000L
                    val targetSeconds = when (seekCommand) {
                        Player.COMMAND_SEEK_BACK -> currentSeconds - SKIP_SECONDS
                        Player.COMMAND_SEEK_FORWARD -> currentSeconds + SKIP_SECONDS
                        Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
                        Player.COMMAND_SEEK_TO_MEDIA_ITEM -> positionMs.takeIf { it != C.TIME_UNSET }?.let { it / 1000L } ?: currentSeconds
                        Player.COMMAND_SEEK_TO_DEFAULT_POSITION -> 0L
                        else -> currentSeconds
                    }
                    serviceScope.launch { seekTimefreeTo(targetSeconds) }
                    return Futures.immediateVoidFuture()
                }
                return super.handleSeek(mediaItemIndex, positionMs, seekCommand)
            }
        }
        session = MediaLibrarySession.Builder(
            this,
            sessionPlayer,
            Callback(serviceScope, client, recentStationsStore, ::playStation, ::playTimefree),
        ).build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val result = super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_PLAY_LIVE) {
            intent.getStringExtra(EXTRA_STATION_ID)?.let { stationId ->
                serviceScope.launch { playStation(stationId) }
            }
        } else if (intent?.action == ACTION_PLAY_TIMEFREE) {
            val stationId = intent.getStringExtra(EXTRA_STATION_ID)
            val startTime = intent.getStringExtra(EXTRA_START_TIME)
            val endTime = intent.getStringExtra(EXTRA_END_TIME)
            if (!stationId.isNullOrBlank() && !startTime.isNullOrBlank() && !endTime.isNullOrBlank()) {
                serviceScope.launch { playTimefree(stationId, startTime, endTime) }
            }
        } else if (intent?.action == ACTION_PAUSE) {
            player?.pause()
        } else if (intent?.action == ACTION_RESUME) {
            player?.play()
        } else if (intent?.action == ACTION_STOP) {
            player?.stop()
        } else if (intent?.action == ACTION_SKIP_BACK) {
            serviceScope.launch { skipBy(-SKIP_SECONDS) }
        } else if (intent?.action == ACTION_SKIP_FORWARD) {
            serviceScope.launch { skipBy(SKIP_SECONDS) }
        } else if (intent?.action == ACTION_SEEK_TIMEFREE) {
            val seekSeconds = intent.getLongExtra(EXTRA_SEEK_SECONDS, 0L)
            serviceScope.launch { seekTimefreeTo(seekSeconds) }
        }
        return result
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = session

    override fun onDestroy() {
        session?.release()
        session = null
        player?.release()
        player = null
        serviceScope.cancel()
        super.onDestroy()
    }

    private suspend fun playStation(stationId: String) {
        val regions = client.getRegions()
        val station = regions.asSequence()
            .flatMap { it.stations.asSequence() }
            .firstOrNull { it.id == stationId }
            ?: return
        val program = runCatching { client.getPrograms(stationId).firstOrNull { it.isOnAir } }.getOrNull()
        val stream = client.getLiveStream(stationId)
        currentPlayback = CurrentPlayback.Live(stationId)
        recentStationsStore.record(stationId)
        val mediaItem = MediaItem.Builder()
            .setMediaId("$LIVE_PREFIX$stationId")
            .setUri(Uri.parse(stream.playlistUrl))
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(program?.title?.ifBlank { null } ?: station.name)
                    .setArtist(program?.performer?.ifBlank { null } ?: station.asciiName.ifBlank { station.areaId })
                    .setAlbumTitle(station.name)
                    .setSubtitle(station.name)
                    .setArtworkUri(Uri.parse(program?.imageUrl ?: station.logoUrl))
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .build(),
            )
            .build()
        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Linux; Android) AppleWebKit/537.36 YarAndroid/0.1")
            .setDefaultRequestProperties(mapOf("X-Radiko-AuthToken" to stream.authToken))
        val mediaSource = HlsMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)

        player?.apply {
            setMediaSource(mediaSource)
            prepare()
            playWhenReady = true
        }
    }

    private suspend fun playTimefree(stationId: String, startTime: String, endTime: String, seekSeconds: Long = 0L) {
        val regions = client.getRegions()
        val station = regions.asSequence()
            .flatMap { it.stations.asSequence() }
            .firstOrNull { it.id == stationId }
            ?: return
        val program = runCatching { client.getPrograms(stationId).firstOrNull { it.startTime == startTime } }.getOrNull()
        val seekTime = addSecondsToRadikoTimestamp(startTime, seekSeconds)
        val stream = client.getTimefreeStream(stationId, startTime, endTime, seekTime)
        currentPlayback = CurrentPlayback.Timefree(
            stationId = stationId,
            startTime = startTime,
            endTime = endTime,
            seekOffsetSeconds = seekSeconds,
            basePositionMs = seekSeconds * 1000L,
            baseRealtimeMs = SystemClock.elapsedRealtime(),
            playing = true,
        )
        recentStationsStore.record(stationId)
        val mediaItem = MediaItem.Builder()
            .setMediaId("$TIMEFREE_PREFIX$stationId:$startTime:$endTime:$seekSeconds")
            .setUri(Uri.parse(stream.playlistUrl))
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(program?.title?.ifBlank { null } ?: station.name)
                    .setArtist(program?.performer?.ifBlank { null } ?: station.asciiName.ifBlank { station.areaId })
                    .setAlbumTitle(station.name)
                    .setSubtitle(station.name)
                    .setArtworkUri(Uri.parse(program?.imageUrl ?: station.logoUrl))
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .build(),
            )
            .build()
        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Linux; Android) AppleWebKit/537.36 YarAndroid/0.1")
            .setDefaultRequestProperties(mapOf("X-Radiko-AuthToken" to stream.authToken))
        val mediaSource = HlsMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)

        player?.apply {
            setMediaSource(mediaSource)
            prepare()
            playWhenReady = true
        }
    }

    private suspend fun skipBy(deltaSeconds: Long) {
        val playback = currentPlayback
        if (playback is CurrentPlayback.Timefree) {
            val durationSeconds = radikoDurationSeconds(playback.startTime, playback.endTime)
            val nextOffset = (currentTimefreePositionMs(playback) / 1000L + deltaSeconds)
                .coerceIn(0L, durationSeconds.coerceAtLeast(0L))
            playTimefree(playback.stationId, playback.startTime, playback.endTime, nextOffset)
        } else {
            if (deltaSeconds < 0) player?.seekBack() else player?.seekForward()
        }
    }

    private suspend fun seekTimefreeTo(seekSeconds: Long) {
        val playback = currentPlayback
        if (playback is CurrentPlayback.Timefree) {
            val durationSeconds = radikoDurationSeconds(playback.startTime, playback.endTime).coerceAtLeast(0L)
            val nextOffset = seekSeconds.coerceIn(0L, durationSeconds)
            playTimefree(playback.stationId, playback.startTime, playback.endTime, nextOffset)
        }
    }

    private fun addSecondsToRadikoTimestamp(timestamp: String, seconds: Long): String {
        if (seconds <= 0) return timestamp
        val instant = java.time.LocalDateTime.of(
            timestamp.substring(0, 4).toInt(),
            timestamp.substring(4, 6).toInt(),
            timestamp.substring(6, 8).toInt(),
            timestamp.substring(8, 10).toInt(),
            timestamp.substring(10, 12).toInt(),
            timestamp.substring(12, 14).toInt(),
        ).atOffset(java.time.ZoneOffset.ofHours(9)).toInstant().plusSeconds(seconds)
        val jst = instant.atOffset(java.time.ZoneOffset.ofHours(9))
        return "%04d%02d%02d%02d%02d%02d".format(
            jst.year,
            jst.monthValue,
            jst.dayOfMonth,
            jst.hour,
            jst.minute,
            jst.second,
        )
    }

    private fun radikoDurationSeconds(startTime: String, endTime: String): Long {
        val start = parseRadikoInstant(startTime)
        val end = parseRadikoInstant(endTime)
        return java.time.Duration.between(start, end).seconds
    }

    private fun updateTimefreeProgress(isPlaying: Boolean) {
        val playback = currentPlayback
        if (playback is CurrentPlayback.Timefree) {
            currentPlayback = playback.copy(
                basePositionMs = currentTimefreePositionMs(playback),
                baseRealtimeMs = SystemClock.elapsedRealtime(),
                playing = isPlaying,
            )
        }
    }

    private fun currentTimefreePositionMs(playback: CurrentPlayback.Timefree): Long {
        val elapsedMs = if (playback.playing) SystemClock.elapsedRealtime() - playback.baseRealtimeMs else 0L
        return (playback.basePositionMs + elapsedMs).coerceIn(0L, timefreeDurationMs(playback))
    }

    private fun timefreeDurationMs(playback: CurrentPlayback.Timefree): Long =
        radikoDurationSeconds(playback.startTime, playback.endTime).coerceAtLeast(0L) * 1000L

    private fun parseRadikoInstant(timestamp: String): java.time.Instant = java.time.LocalDateTime.of(
        timestamp.substring(0, 4).toInt(),
        timestamp.substring(4, 6).toInt(),
        timestamp.substring(6, 8).toInt(),
        timestamp.substring(8, 10).toInt(),
        timestamp.substring(10, 12).toInt(),
        timestamp.substring(12, 14).toInt(),
    ).atOffset(java.time.ZoneOffset.ofHours(9)).toInstant()

    private class Callback(
        private val scope: CoroutineScope,
        private val client: HttpRadikoClient,
        private val recentStationsStore: RecentStationsStore,
        private val playStation: suspend (String) -> Unit,
        private val playTimefree: suspend (String, String, String) -> Unit,
    ) : MediaLibrarySession.Callback {
        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val root = MediaItem.Builder()
                .setMediaId(ROOT_ID)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle("Yar")
                        .setIsBrowsable(true)
                        .setIsPlayable(false)
                        .build(),
                )
                .build()

            return Futures.immediateFuture(LibraryResult.ofItem(root, params))
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            return scope.future {
                val children = when {
                    parentId == ROOT_ID -> ImmutableList.of(recentItem(), regionsItem())
                    parentId == RECENT_ID -> {
                        val stationsById = client.getRegions().flatMap { it.stations }.associateBy { it.id }
                        recentStationsStore.getStationIds()
                            .mapNotNull { stationsById[it] }
                            .map { station ->
                                MediaItem.Builder()
                                    .setMediaId("$STATION_PREFIX${station.id}")
                                    .setMediaMetadata(
                                        MediaMetadata.Builder()
                                            .setTitle(station.name)
                                            .setArtist(station.asciiName.ifBlank { station.areaId })
                                            .setArtworkUri(Uri.parse(station.logoUrl))
                                            .setIsBrowsable(true)
                                            .setIsPlayable(false)
                                            .build(),
                                    )
                                    .build()
                            }
                            .toImmutableList()
                    }
                    parentId == REGIONS_ID -> client.getRegions().map { it.toMediaItem() }.toImmutableList()
                    parentId.startsWith(REGION_PREFIX) -> {
                        val regionId = parentId.removePrefix(REGION_PREFIX)
                        client.getRegions()
                            .firstOrNull { it.id == regionId }
                            ?.stations
                            ?.map { station ->
                                MediaItem.Builder()
                                    .setMediaId("$STATION_PREFIX${station.id}")
                                    .setMediaMetadata(
                                        MediaMetadata.Builder()
                                            .setTitle(station.name)
                                            .setArtist(station.asciiName.ifBlank { station.areaId })
                                            .setArtworkUri(android.net.Uri.parse(station.logoUrl))
                                            .setIsBrowsable(true)
                                            .setIsPlayable(false)
                                            .build(),
                                    )
                                    .build()
                            }
                            ?.toImmutableList()
                            ?: ImmutableList.of()
                    }
                    parentId.startsWith(STATION_PREFIX) -> {
                        val stationId = parentId.removePrefix(STATION_PREFIX)
                        val station = client.getRegions().asSequence()
                            .flatMap { it.stations.asSequence() }
                            .firstOrNull { it.id == stationId }
                        val liveItem = MediaItem.Builder()
                            .setMediaId("$LIVE_PREFIX$stationId")
                            .setMediaMetadata(
                                MediaMetadata.Builder()
                                    .setTitle("Live")
                                    .setSubtitle(station?.name)
                                    .setIsBrowsable(false)
                                    .setIsPlayable(true)
                                    .build(),
                            )
                            .build()
                        val timefreeItems = runCatching { client.getPrograms(stationId) }
                            .getOrDefault(emptyList())
                            .filter { !it.isOnAir && it.endTime < currentRadikoTimestamp() }
                            .takeLast(8)
                            .asReversed()
                            .map { program ->
                                MediaItem.Builder()
                                    .setMediaId("$TIMEFREE_PREFIX$stationId:${program.startTime}:${program.endTime}")
                                    .setMediaMetadata(
                                        MediaMetadata.Builder()
                                            .setTitle(program.title.ifBlank { station?.name ?: stationId })
                                            .setArtist(program.performer.ifBlank { station?.name ?: stationId })
                                            .setAlbumTitle(station?.name)
                                            .setSubtitle("${formatRadikoTime(program.startTime)}-${formatRadikoTime(program.endTime)}")
                                            .setArtworkUri(Uri.parse(program.imageUrl ?: station?.logoUrl.orEmpty()))
                                            .setIsBrowsable(false)
                                            .setIsPlayable(true)
                                            .build(),
                                    )
                                    .build()
                            }
                        listOf(liveItem).plus(timefreeItems).toImmutableList()
                    }
                    else -> ImmutableList.of()
                }

                LibraryResult.ofItemList(children, params)
            }
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> {
            playMediaId(mediaItems.firstOrNull()?.mediaId, mediaSession)
            return Futures.immediateFuture(mediaItems)
        }

        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
            startIndex: Int,
            startPositionMs: Long,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            playMediaId(mediaItems.getOrNull(startIndex.coerceAtLeast(0))?.mediaId, mediaSession)
            return Futures.immediateFuture(MediaSession.MediaItemsWithStartPosition(mediaItems, startIndex, startPositionMs))
        }

        @Suppress("OVERRIDE_DEPRECATION")
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            return Futures.immediateFuture(MediaSession.MediaItemsWithStartPosition(emptyList(), C.INDEX_UNSET, C.TIME_UNSET))
        }

        private fun playMediaId(mediaId: String?, mediaSession: MediaSession): Boolean {
            if (mediaId.isNullOrBlank()) return false
            return when {
                mediaId.startsWith(LIVE_PREFIX) -> {
                    scope.launch { playStation(mediaId.removePrefix(LIVE_PREFIX)) }
                    mediaSession.player.playWhenReady = true
                    true
                }
                mediaId.startsWith(TIMEFREE_PREFIX) -> {
                    val parts = mediaId.removePrefix(TIMEFREE_PREFIX).split(":")
                    if (parts.size >= 3) {
                        scope.launch { playTimefree(parts[0], parts[1], parts[2]) }
                        mediaSession.player.playWhenReady = true
                        true
                    } else {
                        false
                    }
                }
                else -> false
            }
        }

        private fun recentItem(): MediaItem = MediaItem.Builder()
            .setMediaId(RECENT_ID)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle("Recent Stations")
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .build(),
            )
            .build()

        private fun regionsItem(): MediaItem = MediaItem.Builder()
            .setMediaId(REGIONS_ID)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle("Regions")
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .build(),
            )
            .build()

        private fun Region.toMediaItem(): MediaItem = MediaItem.Builder()
            .setMediaId("$REGION_PREFIX$id")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(name)
                    .setSubtitle("${stations.size} stations")
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .build(),
            )
            .build()

        private fun <T : Any> List<T>.toImmutableList(): ImmutableList<T> = ImmutableList.copyOf(this)
    }

    companion object {
        const val ROOT_ID = "yar_root"
        const val RECENT_ID = "yar_recent"
        const val REGIONS_ID = "yar_regions"
        const val REGION_PREFIX = "yar_region:"
        const val STATION_PREFIX = "yar_station:"
        const val LIVE_PREFIX = "yar_live:"
        const val TIMEFREE_PREFIX = "yar_timefree:"
        const val ACTION_PLAY_LIVE = "dev.yar.android.action.PLAY_LIVE"
        const val ACTION_PLAY_TIMEFREE = "dev.yar.android.action.PLAY_TIMEFREE"
        const val ACTION_PAUSE = "dev.yar.android.action.PAUSE"
        const val ACTION_RESUME = "dev.yar.android.action.RESUME"
        const val ACTION_STOP = "dev.yar.android.action.STOP"
        const val ACTION_SKIP_BACK = "dev.yar.android.action.SKIP_BACK"
        const val ACTION_SKIP_FORWARD = "dev.yar.android.action.SKIP_FORWARD"
        const val ACTION_SEEK_TIMEFREE = "dev.yar.android.action.SEEK_TIMEFREE"
        const val EXTRA_STATION_ID = "dev.yar.android.extra.STATION_ID"
        const val EXTRA_START_TIME = "dev.yar.android.extra.START_TIME"
        const val EXTRA_END_TIME = "dev.yar.android.extra.END_TIME"
        const val EXTRA_SEEK_SECONDS = "dev.yar.android.extra.SEEK_SECONDS"
        const val SKIP_SECONDS = 30L

        fun currentRadikoTimestamp(): String {
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

        fun formatRadikoTime(value: String): String = if (value.length >= 12) {
            "${value.substring(8, 10)}:${value.substring(10, 12)}"
        } else {
            "--:--"
        }
    }
}

private sealed interface CurrentPlayback {
    data class Live(val stationId: String) : CurrentPlayback

    data class Timefree(
        val stationId: String,
        val startTime: String,
        val endTime: String,
        val seekOffsetSeconds: Long,
        val basePositionMs: Long,
        val baseRealtimeMs: Long,
        val playing: Boolean,
    ) : CurrentPlayback
}
