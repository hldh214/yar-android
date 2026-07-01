package dev.yar.android.ui

import android.content.Intent
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.yar.android.data.HttpRadikoClient
import dev.yar.android.data.RecentStationsStore
import dev.yar.android.domain.NoaItem
import dev.yar.android.domain.Program
import dev.yar.android.domain.Region
import dev.yar.android.domain.Station
import dev.yar.android.playback.YarMediaLibraryService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val PLAYBACK_SWITCH_TIMEOUT_MS = 20_000L
private const val PLAYBACK_SEEK_TIMEOUT_MS = 12_000L

internal fun nextScrollToTopSignal(current: Int, shouldScroll: Boolean): Int =
    if (shouldScroll) current + 1 else current

@Composable
fun YarApp(
    showNotificationPermissionPrompt: Boolean = false,
    onRequestNotificationPermission: () -> Unit = {},
) {
    val context = LocalContext.current
    val client = remember { HttpRadikoClient() }
    val recentStationsStore = remember { RecentStationsStore(context) }
    val scope = rememberCoroutineScope()
    val playerState = rememberYarPlayerState()

    var regions by remember { mutableStateOf<List<Region>>(emptyList()) }
    var recentStationIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedRegion by remember { mutableStateOf<Region?>(null) }
    var selectedStation by remember { mutableStateOf<Station?>(null) }
    var selectedDate by remember { mutableStateOf(broadcastDates().first()) }
    var timefreeProgramsCache by remember { mutableStateOf(TimefreeProgramsCache()) }
    var playingStation by remember { mutableStateOf<Station?>(null) }
    var playingProgram by remember { mutableStateOf<Program?>(null) }
    var playbackPrograms by remember { mutableStateOf<List<Program>>(emptyList()) }
    var playbackSongs by remember { mutableStateOf<List<NoaItem>>(emptyList()) }
    var songsLoading by remember { mutableStateOf(false) }
    var pendingSeekPositionMs by remember { mutableStateOf<Long?>(null) }
    var pendingSeekMediaId by remember { mutableStateOf<String?>(null) }
    var switchingTarget by remember { mutableStateOf<PlaybackSwitchTarget?>(null) }
    var showPlayerDetails by remember { mutableStateOf(false) }
    var showRegionPicker by remember { mutableStateOf(false) }
    var playerDetailsOpenDragProgress by remember { mutableStateOf(0f) }
    var playerDetailsScrollToTopSignal by remember { mutableStateOf(0) }
    var showDetailsTimetable by remember { mutableStateOf(false) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    val stationsById = regions.flatMap { it.stations }.associateBy { it.id }
    val recentStations = remember(regions, recentStationIds) {
        val byId = regions.flatMap { it.stations }.associateBy { it.id }
        recentStationIds.mapNotNull { byId[it] }
    }
    val currentStationId = playerState.stationId ?: playingStation?.id
    val currentPlayingStation = currentStationId?.let { stationsById[it] } ?: playingStation
    val matchedPlayingProgram = playingProgram?.takeIf { program ->
        (currentStationId == null || program.stationId == currentStationId) &&
            (playerState.playbackStartTime == null || program.startTime == playerState.playbackStartTime)
    }
    val currentPlayingProgram = matchedPlayingProgram
        ?: playerState.playbackStartTime?.let { startTime -> playbackPrograms.firstOrNull { it.startTime == startTime } }
        ?: playbackPrograms.firstOrNull { it.isOnAir }
    val selectedPrograms = currentPlayingStation
        ?.let { station -> timefreeProgramsCache.programsFor(station.id, selectedDate.value) }
        .orEmpty()
    val timefreeLoadingDates = currentPlayingStation
        ?.takeIf { station -> timefreeProgramsCache.stationId == station.id }
        ?.let { timefreeProgramsCache.loadingDates }
        .orEmpty()
    val songProgramStartTime = playerState.playbackStartTime ?: currentPlayingProgram?.startTime
    val songProgramEndTime = playerState.playbackEndTime ?: currentPlayingProgram?.endTime
    val currentSong = remember(playbackSongs, playerState.isLive, songProgramStartTime, playerState.positionMs) {
        currentNoaItem(
            songs = playbackSongs,
            isLive = playerState.isLive,
            programStartTime = songProgramStartTime,
            positionMs = playerState.positionMs,
        )
    }
    val playbackUiState = PlaybackUiState(
        title = playerState.title ?: currentPlayingProgram?.title ?: currentPlayingStation?.name,
        station = currentPlayingStation,
        program = currentPlayingProgram,
        artworkUrl = currentPlayingProgram?.imageUrl ?: currentSong?.imageUrl ?: playerState.artworkUrl ?: currentPlayingStation?.logoUrl,
        stationLogoUrl = currentPlayingStation?.logoUrl,
        currentSong = currentSong,
        songs = playbackSongs,
        songsLoading = songsLoading,
        programs = selectedPrograms,
        timefreeDate = selectedDate,
        timefreePrograms = selectedPrograms,
        timefreeProgramsLoading = currentPlayingStation
            ?.let { station -> timefreeProgramsCache.isLoading(station.id, selectedDate.value) }
            ?: false,
        timefreeLoadingDates = timefreeLoadingDates,
        isPlaying = playerState.isPlaying,
        isLive = playerState.isLive,
        positionMs = pendingSeekPositionMs ?: playerState.positionMs,
        durationMs = playerState.durationMs,
        isSeeking = pendingSeekPositionMs != null,
        isBuffering = playerState.isBuffering,
        switchingTarget = switchingTarget,
        playbackError = playbackError,
    )

    fun refreshRecentStations(stationId: String) {
        recentStationsStore.record(stationId)
        recentStationIds = recentStationsStore.getStationIds()
    }

    fun prefetchTimefreePrograms(station: Station, dates: List<BroadcastDate> = broadcastDates(), selectOnAir: Boolean = false) {
        selectedStation = station
        selectedRegion = regions.firstOrNull { region -> region.stations.any { it.id == station.id } } ?: selectedRegion
        val datesToLoad = timefreeProgramsCache.datesNeedingLoad(station.id, dates)
        if (datesToLoad.isEmpty()) {
            if (selectOnAir) {
                playingProgram = timefreeProgramsCache.programsFor(station.id, selectedDate.value).firstOrNull { it.isOnAir }
            }
            return
        }
        timefreeProgramsCache = datesToLoad.fold(timefreeProgramsCache) { cache, date ->
            cache.startLoading(station.id, date.value)
        }
        datesToLoad.forEach { date ->
            scope.launch {
                val loadedPrograms = runCatching { client.getPrograms(station.id, date.value) }.getOrDefault(emptyList())
                timefreeProgramsCache = timefreeProgramsCache.finishLoading(station.id, date.value, loadedPrograms)
                if (selectOnAir && date.value == selectedDate.value && selectedStation?.id == station.id) {
                    playingProgram = loadedPrograms.firstOrNull { it.isOnAir }
                }
            }
        }
    }

    fun selectTimefreeDate(station: Station, date: BroadcastDate) {
        selectedStation = station
        selectedRegion = regions.firstOrNull { region -> region.stations.any { it.id == station.id } } ?: selectedRegion
        selectedDate = date
        prefetchTimefreePrograms(station, listOf(date))
    }

    fun playLive(station: Station, scrollDetailsToTop: Boolean = false) {
        playbackError = null
        switchingTarget = PlaybackSwitchTarget.Live(station.id)
        refreshRecentStations(station.id)
        selectedStation = station
        selectedRegion = regions.firstOrNull { region -> region.stations.any { it.id == station.id } } ?: selectedRegion
        val today = broadcastDates().first()
        playingStation = station
        playingProgram = null
        selectedDate = today
        prefetchTimefreePrograms(station = station, dates = listOf(today), selectOnAir = true)
        if (scrollDetailsToTop) {
            showPlayerDetails = true
            playerDetailsScrollToTopSignal = nextScrollToTopSignal(playerDetailsScrollToTopSignal, shouldScroll = true)
        }
        context.startService(
            Intent(context, YarMediaLibraryService::class.java)
                .setAction(YarMediaLibraryService.ACTION_PLAY_LIVE)
                .putExtra(YarMediaLibraryService.EXTRA_STATION_ID, station.id),
        )
    }

    fun playTimefree(station: Station, program: Program) {
        playbackError = null
        switchingTarget = PlaybackSwitchTarget.Timefree(station.id, program.startTime)
        refreshRecentStations(station.id)
        playingStation = station
        playingProgram = program
        broadcastDates().firstOrNull { it.value == program.startTime.take(8) }?.let { selectedDate = it }
        timefreeProgramsCache = timefreeProgramsCache.rememberProgram(station.id, program)
        showDetailsTimetable = false
        showPlayerDetails = true
        playerDetailsScrollToTopSignal += 1
        context.startService(
            Intent(context, YarMediaLibraryService::class.java)
                .setAction(YarMediaLibraryService.ACTION_PLAY_TIMEFREE)
                .putExtra(YarMediaLibraryService.EXTRA_STATION_ID, station.id)
                .putExtra(YarMediaLibraryService.EXTRA_START_TIME, program.startTime)
                .putExtra(YarMediaLibraryService.EXTRA_END_TIME, program.endTime),
        )
    }

    fun pauseResume() {
        val nextPlaying = !playerState.isPlaying
        if (playerState.isLive) {
            val stationId = playerState.stationId ?: playingStation?.id
            if (nextPlaying && stationId != null) {
                context.startService(
                    Intent(context, YarMediaLibraryService::class.java)
                        .setAction(YarMediaLibraryService.ACTION_PLAY_LIVE)
                        .putExtra(YarMediaLibraryService.EXTRA_STATION_ID, stationId),
                )
            } else {
                playerState.controller?.stop() ?: context.startService(
                    Intent(context, YarMediaLibraryService::class.java).setAction(YarMediaLibraryService.ACTION_STOP),
                )
            }
        } else {
            val controller = playerState.controller
            if (controller != null) {
                if (nextPlaying) controller.play() else controller.pause()
            } else {
                context.startService(
                    Intent(context, YarMediaLibraryService::class.java).setAction(
                        if (nextPlaying) YarMediaLibraryService.ACTION_RESUME else YarMediaLibraryService.ACTION_PAUSE,
                    ),
                )
            }
        }
    }

    fun seekTimefree(seekPositionMs: Long) {
        playbackError = null
        val clampedSeekPositionMs = seekPositionMs.coerceIn(0L, playerState.durationMs.coerceAtLeast(0L))
        pendingSeekPositionMs = clampedSeekPositionMs
        pendingSeekMediaId = playerState.mediaId
        context.startService(
            Intent(context, YarMediaLibraryService::class.java)
                .setAction(YarMediaLibraryService.ACTION_SEEK_TIMEFREE)
                .putExtra(YarMediaLibraryService.EXTRA_SEEK_SECONDS, clampedSeekPositionMs / 1000L),
        )
    }

    fun sendPlaybackAction(action: String) {
        context.startService(Intent(context, YarMediaLibraryService::class.java).setAction(action))
    }

    fun skipTimefree(deltaMs: Long) {
        if (playerState.isLive || playerState.durationMs <= 0L) {
            sendPlaybackAction(if (deltaMs < 0) YarMediaLibraryService.ACTION_SKIP_BACK else YarMediaLibraryService.ACTION_SKIP_FORWARD)
            return
        }
        seekTimefree((playbackUiState.positionMs + deltaMs).coerceIn(0L, playerState.durationMs))
    }

    LaunchedEffect(playerState.mediaId, playerState.positionMs, pendingSeekPositionMs, pendingSeekMediaId) {
        val pending = pendingSeekPositionMs
        val pendingMediaId = pendingSeekMediaId
        if (pending != null && pendingMediaId != null && playerState.mediaId != pendingMediaId) {
            pendingSeekPositionMs = null
            pendingSeekMediaId = null
        }
    }

    LaunchedEffect(pendingSeekPositionMs, pendingSeekMediaId) {
        if (pendingSeekPositionMs == null || pendingSeekMediaId == null) return@LaunchedEffect
        delay(PLAYBACK_SEEK_TIMEOUT_MS)
        if (pendingSeekPositionMs != null && pendingSeekMediaId != null) {
            pendingSeekPositionMs = null
            pendingSeekMediaId = null
            playbackError = "Seek is taking too long. Check the network and try again."
        }
    }

    LaunchedEffect(playerState.stationId, playerState.playbackStartTime, playerState.isLive, switchingTarget) {
        when (val target = switchingTarget) {
            is PlaybackSwitchTarget.Live -> {
                if (playerState.isLive && playerState.stationId == target.stationId) {
                    switchingTarget = null
                    playbackError = null
                }
            }
            is PlaybackSwitchTarget.Timefree -> {
                if (!playerState.isLive && playerState.stationId == target.stationId && playerState.playbackStartTime == target.startTime) {
                    switchingTarget = null
                    playbackError = null
                }
            }
            null -> Unit
        }
    }

    LaunchedEffect(switchingTarget) {
        val target = switchingTarget ?: return@LaunchedEffect
        delay(PLAYBACK_SWITCH_TIMEOUT_MS)
        if (switchingTarget == target) {
            switchingTarget = null
            playbackError = "Playback is taking too long. Check the network and try again."
        }
    }

    LaunchedEffect(client) {
        runCatching { client.getRegions() }
            .onSuccess {
                regions = it
                selectedRegion = it.firstOrNull { region -> region.stations.any { station -> station.areaId == "JP13" } }
                    ?: it.firstOrNull()
            }
            .onFailure {
                Log.e("YarApp", "Failed to load stations", it)
                error = "${it::class.simpleName}: ${it.message ?: "Failed to load stations"}"
            }
    }

    LaunchedEffect(Unit) {
        recentStationIds = recentStationsStore.getStationIds()
    }

    LaunchedEffect(showPlayerDetails, currentPlayingStation?.id) {
        val station = currentPlayingStation ?: return@LaunchedEffect
        if (showPlayerDetails) {
            prefetchTimefreePrograms(station = station)
        }
    }

    LaunchedEffect(playerState.stationId, playerState.mediaId) {
        val stationId = playerState.stationId ?: return@LaunchedEffect
        if (playingProgram?.stationId != stationId) {
            playingProgram = null
        }
        val playbackDate = playerState.playbackStartTime?.takeIf { it.length >= 8 }?.substring(0, 8)
        val loadedPrograms = runCatching { client.getPrograms(stationId, playbackDate) }.getOrDefault(emptyList())
        playbackPrograms = loadedPrograms
        val playbackBroadcastDate = playbackDate
            ?.let { value -> broadcastDates().firstOrNull { it.value == value } }
            ?: broadcastDates().first()
        selectedDate = playbackBroadcastDate
        timefreeProgramsCache = if (timefreeProgramsCache.stationId == stationId) {
            timefreeProgramsCache.finishLoading(stationId, playbackBroadcastDate.value, loadedPrograms)
        } else {
            TimefreeProgramsCache(stationId = stationId).finishLoading(stationId, playbackBroadcastDate.value, loadedPrograms)
        }
        playingProgram = when {
            playerState.playbackStartTime != null -> loadedPrograms.firstOrNull { it.startTime == playerState.playbackStartTime }
            playerState.isLive -> loadedPrograms.firstOrNull { it.isOnAir }
            else -> playingProgram?.takeIf { it.stationId == stationId }
        }
    }

    LaunchedEffect(playerState.stationId, regions) {
        val stationId = playerState.stationId ?: return@LaunchedEffect
        val station = stationsById[stationId] ?: return@LaunchedEffect
        playingStation = station
        selectedStation = station
        selectedRegion = regions.firstOrNull { region -> region.stations.any { it.id == stationId } } ?: selectedRegion
    }

    LaunchedEffect(playerState.stationId, playerState.mediaId, playerState.isLive, songProgramStartTime, songProgramEndTime) {
        val stationId = playerState.stationId ?: return@LaunchedEffect
        if (playerState.isLive) {
            while (true) {
                songsLoading = true
                playbackSongs = runCatching { client.getLatestNoaItems(stationId) }.getOrDefault(emptyList())
                songsLoading = false
                delay(10_000)
            }
        } else if (!songProgramStartTime.isNullOrBlank() && !songProgramEndTime.isNullOrBlank()) {
            songsLoading = true
            playbackSongs = runCatching { client.getNoaItems(stationId, songProgramStartTime, songProgramEndTime) }.getOrDefault(emptyList())
            songsLoading = false
        } else {
            playbackSongs = emptyList()
            songsLoading = false
        }
    }

    YarTheme {
        YarBackground {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing),
            ) {
                val horizontalPadding = if (maxWidth < 600.dp) 16.dp else 24.dp
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = horizontalPadding, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (showNotificationPermissionPrompt) {
                        NotificationPermissionCard(onRequestPermission = onRequestNotificationPermission)
                    }
                    BrowserScreen(
                        state = BrowserUiState(
                            regions = regions,
                            recentStations = recentStations,
                            selectedRegion = selectedRegion,
                            switchingTarget = switchingTarget,
                            error = error,
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 360.dp),
                        onOpenRegionPicker = { showRegionPicker = true },
                        onStationSelected = { playLive(it) },
                    )
                    if (playingStation != null || playerState.title != null) {
                        MiniPlayer(
                            state = playbackUiState,
                            onPauseResume = { pauseResume() },
                            onSkipBack = { skipTimefree(-30_000L) },
                            onSkipForward = { skipTimefree(30_000L) },
                            onOpenDetails = { showPlayerDetails = true },
                            onOpenDragProgress = { playerDetailsOpenDragProgress = it },
                        )
                    }
                }

                RegionPickerSheet(
                    visible = showRegionPicker,
                    regions = regions,
                    selectedRegion = selectedRegion,
                    onDismiss = { showRegionPicker = false },
                    onRegionSelected = { region ->
                        selectedRegion = region
                        showRegionPicker = false
                    },
                )

                PlayerDetailsOverlay(
                    visible = showPlayerDetails || playerDetailsOpenDragProgress > 0f,
                    opened = showPlayerDetails,
                    openingDragProgress = playerDetailsOpenDragProgress,
                    scrollToTopSignal = playerDetailsScrollToTopSignal,
                    state = playbackUiState,
                    timetableExpanded = showDetailsTimetable,
                    onToggleTimetable = { showDetailsTimetable = !showDetailsTimetable },
                    onDismiss = {
                        showPlayerDetails = false
                        playerDetailsOpenDragProgress = 0f
                    },
                    onPauseResume = { pauseResume() },
                    onSeek = { seekTimefree(it) },
                    onSkipBack = { skipTimefree(-30_000L) },
                    onSkipForward = { skipTimefree(30_000L) },
                    onPlayLive = { playLive(it, scrollDetailsToTop = true) },
                    onTimefreeDateSelected = { station, date -> selectTimefreeDate(station, date) },
                    onPlayTimefree = { station, program -> playTimefree(station, program) },
                )
            }
        }
    }
}

@Composable
private fun NotificationPermissionCard(onRequestPermission: () -> Unit) {
    GlassCard(modifier = Modifier.fillMaxWidth(), highlight = true) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("Playback notifications", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = "Show media controls in background and on lock screen.",
                    color = MutedText,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Button(onClick = onRequestPermission) {
                Text("Allow")
            }
        }
    }
}
