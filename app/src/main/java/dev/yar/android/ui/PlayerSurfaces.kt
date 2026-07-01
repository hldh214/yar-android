package dev.yar.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.Replay30
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import dev.yar.android.domain.NoaItem
import dev.yar.android.domain.Program
import dev.yar.android.domain.Station
import kotlin.math.roundToInt

internal data class PlaybackUiState(
    val title: String?,
    val station: Station?,
    val program: Program?,
    val artworkUrl: String?,
    val stationLogoUrl: String?,
    val currentSong: NoaItem?,
    val songs: List<NoaItem>,
    val songsLoading: Boolean,
    val programs: List<Program>,
    val timefreeDate: BroadcastDate,
    val timefreePrograms: List<Program>,
    val timefreeProgramsLoading: Boolean,
    val timefreeLoadingDates: Set<String>,
    val isPlaying: Boolean,
    val isLive: Boolean,
    val positionMs: Long,
    val durationMs: Long,
    val isSeeking: Boolean,
    val isBuffering: Boolean,
    val switchingTarget: PlaybackSwitchTarget?,
    val playbackError: String?,
)

internal sealed interface PlaybackSwitchTarget {
    data class Live(val stationId: String) : PlaybackSwitchTarget
    data class Timefree(val stationId: String, val startTime: String) : PlaybackSwitchTarget
}

@Composable
internal fun MiniPlayer(
    state: PlaybackUiState,
    modifier: Modifier = Modifier,
    onPauseResume: () -> Unit,
    onSkipBack: () -> Unit,
    onSkipForward: () -> Unit,
    onOpenDetails: () -> Unit,
    onOpenDragProgress: (Float) -> Unit,
) {
    var dragY by remember { mutableStateOf(0f) }
    val openDragDistancePx = with(LocalDensity.current) { 260.dp.toPx() }
    val busy = state.switchingTarget != null || state.isBuffering || state.isSeeking
    val statusLabel = playbackStatusLabel(isLive = state.isLive, busy = busy)
    val secondaryLine = when {
        state.currentSong?.artist?.isNotBlank() == true -> state.currentSong.artist
        state.program?.performer?.isNotBlank() == true -> state.program.performer
        state.station?.name?.isNotBlank() == true -> state.station.name
        else -> "Yar"
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenDetails)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = {
                        dragY = 0f
                        onOpenDragProgress(0f)
                    },
                    onVerticalDrag = { _, amount ->
                        dragY = (dragY + amount).coerceAtMost(0f)
                        onOpenDragProgress((-dragY / openDragDistancePx).coerceIn(0f, 1f))
                    },
                    onDragEnd = {
                        if (-dragY > openDragDistancePx * 0.18f) onOpenDetails()
                        onOpenDragProgress(0f)
                        dragY = 0f
                    },
                    onDragCancel = {
                        onOpenDragProgress(0f)
                        dragY = 0f
                    },
                )
            },
        shape = MaterialTheme.shapes.large,
        color = if (state.isLive) MaterialTheme.colorScheme.primaryContainer else ElevatedPanelAlt,
        tonalElevation = 3.dp,
    ) {
        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlaybackImage(
                    url = state.artworkUrl,
                    label = state.title ?: state.station?.name ?: "Yar",
                    modifier = Modifier.size(52.dp),
                    contentScale = ContentScale.Fit,
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    InlineMetaRow {
                        StatusPill(
                            text = statusLabel,
                            color = if (state.isLive) LiveAccent else MaterialTheme.colorScheme.secondary,
                        )
                        state.station?.name?.let {
                            Text(
                                text = it,
                                color = MutedText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                    Text(
                        text = state.title ?: "Tap a station to start live playback.",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = secondaryLine,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MutedText,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    MiniPlayerProgressStrip(state = state, busy = busy)
                }
                MiniPlayerControls(
                    isPlaying = state.isPlaying,
                    isLive = state.isLive,
                    busy = busy,
                    onPauseResume = onPauseResume,
                    onSkipBack = onSkipBack,
                    onSkipForward = onSkipForward,
                )
            }
            state.playbackError?.let {
                Text(
                    text = it,
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
private fun MiniPlayerProgressStrip(
    state: PlaybackUiState,
    busy: Boolean,
) {
    val hasTimefreeProgress = !state.isLive && state.durationMs > 0
    val statusText = when {
        hasTimefreeProgress && state.isSeeking -> "Seeking ${formatDuration(state.positionMs)} / ${formatDuration(state.durationMs)}"
        hasTimefreeProgress -> "${formatDuration(state.positionMs)} / ${formatDuration(state.durationMs)}"
        busy -> playbackStatusLabel(isLive = state.isLive, busy = true)
        state.isLive -> "Live stream"
        else -> "Timefree"
    }

    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        if (busy) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        } else {
            LinearProgressIndicator(
                progress = { if (hasTimefreeProgress) playbackProgressRatio(state.positionMs, state.durationMs) else 0f },
                modifier = Modifier.fillMaxWidth(),
                color = if (state.isLive) LiveAccent else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        }
        Text(
            text = statusText,
            color = MutedText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun MiniPlayerControls(
    isPlaying: Boolean,
    isLive: Boolean,
    busy: Boolean,
    onPauseResume: () -> Unit,
    onSkipBack: () -> Unit,
    onSkipForward: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (!isLive) {
            PlayerSecondaryButton(
                imageVector = Icons.Filled.Replay30,
                contentDescription = "Skip back 30 seconds",
                onClick = onSkipBack,
                enabled = !busy,
            )
        }
        PlayerPrimaryButton(
            isPlaying = isPlaying,
            onClick = onPauseResume,
            compact = true,
            enabled = !busy,
            loading = busy,
        )
        if (!isLive) {
            PlayerSecondaryButton(
                imageVector = Icons.Filled.Forward30,
                contentDescription = "Skip forward 30 seconds",
                onClick = onSkipForward,
                enabled = !busy,
            )
        }
    }
}

@Composable
internal fun PlayerDetailsOverlay(
    visible: Boolean,
    opened: Boolean,
    openingDragProgress: Float,
    scrollToTopSignal: Int,
    state: PlaybackUiState,
    timetableExpanded: Boolean,
    onToggleTimetable: () -> Unit,
    onDismiss: () -> Unit,
    onPauseResume: () -> Unit,
    onSeek: (Long) -> Unit,
    onSkipBack: () -> Unit,
    onSkipForward: () -> Unit,
    onPlayLive: (Station) -> Unit,
    onTimefreeDateSelected: (Station, BroadcastDate) -> Unit,
    onPlayTimefree: (Station, Program) -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    BackHandler(enabled = visible, onBack = onDismiss)
    if (visible) {
        val density = LocalDensity.current
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val fullHeightPx = with(density) { maxHeight.toPx() }.coerceAtLeast(1f)
            var dismissOffsetPx by remember { mutableStateOf(0f) }
            var closing by remember { mutableStateOf(false) }
            val scrollState = rememberScrollState()
            val targetSheetOffset = when {
                closing -> fullHeightPx
                opened -> dismissOffsetPx.coerceAtLeast(0f)
                else -> fullHeightPx * (1f - openingDragProgress.coerceIn(0f, 1f))
            }
            val sheetOffset = remember { Animatable(fullHeightPx) }
            LaunchedEffect(targetSheetOffset, opened, openingDragProgress, dismissOffsetPx, closing) {
                val isDraggingOpen = !opened && openingDragProgress > 0f
                val isDraggingDismiss = opened && dismissOffsetPx > 0f
                if (closing) {
                    sheetOffset.animateTo(targetSheetOffset, tween(180))
                    onDismiss()
                    closing = false
                } else if (isDraggingOpen || isDraggingDismiss) {
                    sheetOffset.snapTo(targetSheetOffset)
                } else {
                    sheetOffset.animateTo(targetSheetOffset, tween(220))
                }
            }
            val sheetAlpha = if (opened) 1f else openingDragProgress.coerceIn(0.15f, 1f)
            val dismissThresholdPx = fullHeightPx * 0.16f
            val nestedScrollConnection = remember(fullHeightPx, scrollState.value, opened) {
                object : NestedScrollConnection {
                    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                        if (!opened || source != NestedScrollSource.UserInput) return Offset.Zero
                        val dragDelta = available.y
                        if (dragDelta > 0f && scrollState.value == 0) {
                            dismissOffsetPx = (dismissOffsetPx + dragDelta).coerceIn(0f, fullHeightPx)
                            return Offset(0f, dragDelta)
                        }
                        if (dragDelta < 0f && dismissOffsetPx > 0f) {
                            val consumed = dragDelta.coerceAtLeast(-dismissOffsetPx)
                            dismissOffsetPx += consumed
                            return Offset(0f, consumed)
                        }
                        return Offset.Zero
                    }

                    override suspend fun onPreFling(available: Velocity): Velocity {
                        if (!opened || dismissOffsetPx <= 0f) return Velocity.Zero
                        val shouldDismiss = dismissOffsetPx > dismissThresholdPx || available.y > 900f
                        if (shouldDismiss) {
                            closing = true
                        }
                        dismissOffsetPx = 0f
                        return Velocity(0f, available.y)
                    }
                }
            }
            LaunchedEffect(scrollToTopSignal) {
                if (scrollToTopSignal > 0) {
                    scrollState.animateScrollTo(0)
                }
            }

            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .offset { IntOffset(0, sheetOffset.value.roundToInt()) }
                    .alpha(sheetAlpha)
                    .nestedScroll(nestedScrollConnection),
                color = MaterialTheme.colorScheme.background,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(start = 18.dp, top = 14.dp, end = 18.dp, bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    PlayerSheetHandle(
                        opened = opened,
                        fullHeightPx = fullHeightPx,
                        onDismiss = { closing = true },
                        onDrag = { dismissOffsetPx = it },
                    )
                    PlayerTopBar(state = state, onDismiss = { closing = true })
                    PlaybackHero(state = state)
                    PlaybackControls(
                        isPlaying = state.isPlaying,
                        isLive = state.isLive,
                        positionMs = state.positionMs,
                        durationMs = state.durationMs,
                        isSeeking = state.isSeeking,
                        isBuffering = state.isBuffering,
                        isSwitching = state.switchingTarget != null,
                        onPauseResume = onPauseResume,
                        onSeek = onSeek,
                        onSkipBack = onSkipBack,
                        onSkipForward = onSkipForward,
                    )
                    state.playbackError?.let {
                        NoticeCard(title = "Playback issue", message = it)
                    }
                    state.currentSong?.let { CurrentSongCard(song = it, isLive = state.isLive) }
                    SongsSection(
                        currentSong = state.currentSong,
                        songs = state.songs,
                        loading = state.songsLoading,
                        isLive = state.isLive,
                    )
                    StationTimefreeSection(
                        state = state,
                        onPlayLive = onPlayLive,
                        onDateSelected = onTimefreeDateSelected,
                        onPlayTimefree = onPlayTimefree,
                    )
                    state.program?.let { program ->
                        ProgramDescription(program = program, onOpenUrl = { uriHandler.openUri(it) })
                    }
                    if (!state.isLive) {
                        TimetableSection(
                            expanded = timetableExpanded,
                            programs = state.programs,
                            onToggle = onToggleTimetable,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StationTimefreeSection(
    state: PlaybackUiState,
    onPlayLive: (Station) -> Unit,
    onDateSelected: (Station, BroadcastDate) -> Unit,
    onPlayTimefree: (Station, Program) -> Unit,
) {
    val station = state.station ?: return
    val switchingTarget = state.switchingTarget
    val timefreePrograms = remember(state.timefreePrograms, station.id) {
        state.timefreePrograms
            .filter { it.stationId == station.id && !it.isOnAir && it.endTime < currentRadikoTimestamp() }
            .asReversed()
    }

    PlayerSection {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle(
                title = "Timefree from ${station.name}",
                subtitle = if (state.isLive) {
                    "Pick a recent program from this station."
                } else {
                    "You are listening to Timefree from this station."
                },
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (state.isLive) {
                    StatusPill(text = "Live now", color = LiveAccent)
                } else {
                    val switchingToLive = switchingTarget == PlaybackSwitchTarget.Live(station.id)
                    SwitchActionPill(
                        text = if (switchingToLive) "Switching..." else "Back to Live",
                        loading = switchingToLive,
                        modifier = Modifier.clickable(enabled = switchingTarget == null) { onPlayLive(station) },
                    )
                }
            }
            switchingTarget?.let {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            TimefreeDateSelector(
                selectedDate = state.timefreeDate,
                loadingDates = state.timefreeLoadingDates,
                enabled = switchingTarget == null,
                onDateSelected = { onDateSelected(station, it) },
            )
            if (state.timefreeProgramsLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(
                    "Loading Timefree programs for ${state.timefreeDate.label}.",
                    color = MutedText,
                    style = MaterialTheme.typography.bodySmall,
                )
            } else if (timefreePrograms.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    timefreePrograms.forEach { program ->
                        QuickTimefreeRow(
                            program = program,
                            selected = !state.isLive && state.program?.startTime == program.startTime,
                            loading = switchingTarget == PlaybackSwitchTarget.Timefree(station.id, program.startTime),
                            enabled = switchingTarget == null,
                            onClick = { onPlayTimefree(station, program) },
                        )
                    }
                }
            } else {
                Text(
                    "No ended Timefree programs for ${state.timefreeDate.label}.",
                    color = MutedText,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun TimefreeDateSelector(
    selectedDate: BroadcastDate,
    loadingDates: Set<String>,
    enabled: Boolean,
    onDateSelected: (BroadcastDate) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(broadcastDates(), key = { it.value }) { date ->
            val selected = date.value == selectedDate.value
            TimefreeDateChip(
                date = date,
                selected = selected,
                loading = date.value in loadingDates,
                enabled = enabled && !selected,
                onClick = { onDateSelected(date) },
            )
        }
    }
}

@Composable
private fun TimefreeDateChip(
    date: BroadcastDate,
    selected: Boolean,
    loading: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .defaultMinSize(minWidth = 54.dp, minHeight = 44.dp)
            .clickable(enabled = enabled, onClick = onClick),
        shape = MaterialTheme.shapes.small,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else ElevatedPanel,
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 2.dp,
                        color = if (selected) MaterialTheme.colorScheme.primary else MutedText,
                    )
                }
                Text(
                    text = date.label,
                    color = if (selected) MaterialTheme.colorScheme.primary else MutedText,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
private fun SwitchActionPill(text: String, loading: Boolean, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.defaultMinSize(minHeight = 48.dp),
        shape = MaterialTheme.shapes.small,
        color = LiveAccent.copy(alpha = 0.16f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = LiveAccent,
                )
            }
            Text(text, color = LiveAccent, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun QuickTimefreeRow(
    program: Program,
    selected: Boolean,
    loading: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    ListSurface(
        selected = selected || loading,
        enabled = enabled || loading || selected,
        onClick = if (enabled && !selected && !loading) onClick else null,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlaybackImage(
                url = program.imageUrl,
                label = program.title.ifBlank { "Program" },
                modifier = Modifier.size(44.dp),
                contentScale = ContentScale.Fit,
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${formatRadikoTime(program.startTime)}-${formatRadikoTime(program.endTime)}",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelMedium,
                    )
                    if (selected) StatusPill(text = "Now")
                    if (loading) StatusPill(text = "Loading")
                }
                Text(
                    text = program.title.ifBlank { "Untitled program" },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (program.performer.isNotBlank()) {
                    Text(program.performer, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MutedText, style = MaterialTheme.typography.bodySmall)
                }
            }
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun PlayerSheetHandle(
    opened: Boolean,
    fullHeightPx: Float,
    onDismiss: () -> Unit,
    onDrag: (Float) -> Unit,
) {
    var localDragY by remember { mutableStateOf(0f) }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        Surface(
            modifier = Modifier
                .width(56.dp)
                .height(5.dp)
                .pointerInput(opened) {
                    detectVerticalDragGestures(
                        onDragStart = { localDragY = 0f },
                        onVerticalDrag = { _, amount ->
                            if (opened) {
                                localDragY = (localDragY + amount).coerceAtLeast(0f)
                                onDrag(localDragY)
                            }
                        },
                        onDragEnd = {
                            if (opened && localDragY > fullHeightPx * 0.1f) onDismiss()
                            localDragY = 0f
                            onDrag(0f)
                        },
                        onDragCancel = {
                            localDragY = 0f
                            onDrag(0f)
                        },
                    )
                },
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.outline,
        ) {}
    }
}

@Composable
private fun PlayerTopBar(state: PlaybackUiState, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlaybackImage(
                url = state.stationLogoUrl,
                label = state.station?.name ?: "Yar",
                modifier = Modifier.size(38.dp),
                contentScale = ContentScale.Fit,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text("Playing from", color = MutedText, style = MaterialTheme.typography.labelSmall)
                Text(
                    text = state.station?.name ?: "Yar",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
        IconButton(onClick = onDismiss) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Close player",
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}

@Composable
private fun PlaybackHero(state: PlaybackUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PlaybackImage(
                url = state.artworkUrl,
                label = state.program?.title ?: state.title ?: "Now Playing",
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .aspectRatio(1f),
                contentScale = ContentScale.Fit,
            )
        }
        InlineMetaRow {
            StatusPill(
                text = if (state.isLive) "LIVE" else "TIMEFREE",
                color = if (state.isLive) LiveAccent else MaterialTheme.colorScheme.secondary,
            )
            state.program?.let { ProgramMetaChips(program = it) }
        }
        Text(
            text = state.title ?: state.program?.title ?: "Now Playing",
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.Black,
            style = MaterialTheme.typography.headlineSmall,
        )
        state.program?.performer
            ?.takeIf { it.isNotBlank() }
            ?.let { Text(it, color = MutedText, style = MaterialTheme.typography.bodyMedium) }
    }
}

@Composable
private fun ProgramMetaChips(program: Program) {
    StatusPill(text = "${formatRadikoTime(program.startTime)}-${formatRadikoTime(program.endTime)}")
    StatusPill(text = formatDuration(program.durationSeconds * 1000L), color = MaterialTheme.colorScheme.secondary)
}

@Composable
private fun PlaybackControls(
    isPlaying: Boolean,
    isLive: Boolean,
    positionMs: Long,
    durationMs: Long,
    isSeeking: Boolean,
    isBuffering: Boolean,
    isSwitching: Boolean,
    onPauseResume: () -> Unit,
    onSeek: (Long) -> Unit,
    onSkipBack: () -> Unit,
    onSkipForward: () -> Unit,
) {
    var draggingPositionMs by remember { mutableStateOf<Long?>(null) }
    val displayPositionMs = draggingPositionMs ?: positionMs
    val locked = controlsLocked(isSwitching = isSwitching, isSeeking = isSeeking, isBuffering = isBuffering)

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (isLive) Arrangement.Center else Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!isLive) {
                PlayerSecondaryButton(
                    imageVector = Icons.Filled.Replay30,
                    contentDescription = "Skip back 30 seconds",
                    onClick = onSkipBack,
                    enabled = !locked,
                )
            }
            PlayerPrimaryButton(
                isPlaying = isPlaying,
                onClick = onPauseResume,
                enabled = !isSwitching,
                loading = isSwitching,
            )
            if (!isLive) {
                PlayerSecondaryButton(
                    imageVector = Icons.Filled.Forward30,
                    contentDescription = "Skip forward 30 seconds",
                    onClick = onSkipForward,
                    enabled = !locked,
                )
            }
        }

        if (locked) {
            Text(
                text = when {
                    isSwitching -> "Switching playback..."
                    isSeeking -> "Seeking..."
                    else -> "Buffering audio..."
                },
                color = MutedText,
                style = MaterialTheme.typography.labelMedium,
            )
        }

        if (!isLive && durationMs > 0) {
            Slider(
                value = displayPositionMs.toFloat().coerceIn(0f, durationMs.toFloat()),
                onValueChange = { if (!locked) draggingPositionMs = it.toLong() },
                onValueChangeFinished = {
                    if (!locked) draggingPositionMs?.let(onSeek)
                    draggingPositionMs = null
                },
                enabled = !locked,
                valueRange = 0f..durationMs.toFloat(),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = if (isSeeking) "Seeking ${formatDuration(displayPositionMs)}" else formatDuration(displayPositionMs),
                    color = MutedText,
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(formatDuration(durationMs), color = MutedText, style = MaterialTheme.typography.labelMedium)
            }
        } else {
            Text("Live stream controls", color = MutedText, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun CurrentSongCard(song: NoaItem, isLive: Boolean) {
    PlayerSection {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle(title = if (isLive) "Now On-Air Song" else "Current Song")
            SongRow(song = song, highlighted = true)
        }
    }
}

@Composable
private fun SongsSection(
    currentSong: NoaItem?,
    songs: List<NoaItem>,
    loading: Boolean,
    isLive: Boolean,
) {
    val displaySongs = remember(songs) { songs.sortedByDescending { it.stamp } }
    if (!loading && displaySongs.isEmpty()) return

    PlayerSection {
        when {
            loading && songs.isEmpty() -> {
                SectionTitle(
                    title = "Loading songs",
                    subtitle = if (isLive) "Fetching current on-air songs." else "Fetching song history for this program.",
                )
            }
            displaySongs.isNotEmpty() -> {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Song History", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        StatusPill(text = displaySongs.size.toString(), color = MaterialTheme.colorScheme.secondary)
                    }
                    LazyColumn(
                        modifier = Modifier.height(170.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(displaySongs, key = { it.id.ifBlank { it.stamp + it.title } }) { song ->
                            SongRow(song = song, highlighted = song.id.isNotBlank() && song.id == currentSong?.id)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SongRow(song: NoaItem, highlighted: Boolean) {
    val uriHandler = LocalUriHandler.current
    ListSurface(selected = highlighted) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlaybackImage(
                url = song.imageUrl.takeIf { it.isNotBlank() },
                label = song.title.ifBlank { "Song" },
                modifier = Modifier.size(44.dp),
                contentScale = ContentScale.Fit,
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = song.title.ifBlank { "Unknown song" },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (song.artist.isNotBlank()) {
                    Text(song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MutedText, style = MaterialTheme.typography.bodySmall)
                }
                SongLinks(song = song, onOpenUrl = { uriHandler.openUri(it) })
            }
            Text(formatNoaStamp(song.stamp), color = MutedText, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun SongLinks(song: NoaItem, onOpenUrl: (String) -> Unit) {
    val links = listOf(
        "Apple" to song.itunesUrl,
        "Amazon" to song.amazonUrl,
        "RecoChoku" to song.recochokuUrl,
    ).filter { (_, url) -> url.isNotBlank() }
    if (links.isEmpty()) return

    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        links.forEach { (label, url) ->
            Text(
                text = label,
                modifier = Modifier.clickable { onOpenUrl(url) },
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun ProgramDescription(program: Program, onOpenUrl: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val detail = listOf(program.description, program.info)
        .filter { it.isNotBlank() }
        .distinct()
        .joinToString("\n\n")
    if (program.subtitle.isBlank() && detail.isBlank() && program.url.isBlank()) return

    PlayerSection {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Program Details", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = if (expanded) "Hide" else "Show",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            if (!expanded) {
                val preview = program.subtitle.ifBlank { detail }.lineSequence().firstOrNull().orEmpty()
                if (preview.isNotBlank()) Text(preview, maxLines = 2, color = MutedText, style = MaterialTheme.typography.bodyMedium)
            } else {
                if (program.subtitle.isNotBlank()) Text(program.subtitle, style = MaterialTheme.typography.bodyLarge)
                if (detail.isNotBlank()) Text(detail, color = MutedText, style = MaterialTheme.typography.bodyMedium)
                if (program.url.isNotBlank()) {
                    Text(
                        text = "Program Website",
                        modifier = Modifier.clickable { onOpenUrl(program.url) },
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
}

@Composable
private fun TimetableSection(
    expanded: Boolean,
    programs: List<Program>,
    onToggle: () -> Unit,
) {
    PlayerSection {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Same-day timetable", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = if (expanded) "Hide" else "Show",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            if (expanded) {
                LazyColumn(
                    modifier = Modifier.height(250.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(programs, key = { it.id }) { item ->
                        ProgramRow(program = item, onClick = {})
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerSection(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.24f))
        content()
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        if (!subtitle.isNullOrBlank()) {
            Text(subtitle, color = MutedText, style = MaterialTheme.typography.bodySmall)
        }
    }
}
