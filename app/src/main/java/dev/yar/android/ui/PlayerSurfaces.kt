package dev.yar.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.Replay30
import androidx.compose.material3.CircularProgressIndicator
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
    val stationPrograms: List<Program>,
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
        shape = MaterialTheme.shapes.extraLarge,
        color = GlassPanel,
        tonalElevation = 8.dp,
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlaybackImage(
                    url = state.artworkUrl,
                    label = state.title ?: state.station?.name ?: "Yar",
                    modifier = Modifier.size(54.dp),
                    contentScale = ContentScale.Fit,
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    InlineMetaRow {
                        StatusPill(
                            text = when {
                                busy -> "LOADING"
                                state.isLive -> "LIVE"
                                else -> "TIMEFREE"
                            },
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
                    if (!state.isLive && state.durationMs > 0) {
                        LinearProgressIndicator(
                            progress = { (state.positionMs.toFloat() / state.durationMs.toFloat()).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = ElevatedPanelAlt,
                        )
                    }
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
            if (busy) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            state.playbackError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
            }
            if (!state.isLive && state.durationMs > 0) {
                Text(
                    text = if (state.isSeeking) {
                        "Seeking ${formatDuration(state.positionMs)} / ${formatDuration(state.durationMs)}"
                    } else {
                        "${formatDuration(state.positionMs)} / ${formatDuration(state.durationMs)}"
                    },
                    color = MutedText,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
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
            Text(
                text = "-30",
                modifier = Modifier
                    .clickable(enabled = !busy, onClick = onSkipBack)
                    .padding(8.dp),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelMedium,
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
            Text(
                text = "+30",
                modifier = Modifier
                    .clickable(enabled = !busy, onClick = onSkipForward)
                    .padding(8.dp),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
internal fun PlayerDetailsOverlay(
    visible: Boolean,
    opened: Boolean,
    openingDragProgress: Float,
    state: PlaybackUiState,
    timetableExpanded: Boolean,
    onToggleTimetable: () -> Unit,
    onDismiss: () -> Unit,
    onPauseResume: () -> Unit,
    onSeek: (Long) -> Unit,
    onSkipBack: () -> Unit,
    onSkipForward: () -> Unit,
    onPlayLive: (Station) -> Unit,
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
                    StationPlaybackSwitch(
                        state = state,
                        onPlayLive = onPlayLive,
                        onPlayTimefree = onPlayTimefree,
                    )
                    state.currentSong?.let { CurrentSongCard(song = it, isLive = state.isLive) }
                    SongsSection(
                        currentSong = state.currentSong,
                        songs = state.songs,
                        loading = state.songsLoading,
                        isLive = state.isLive,
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
private fun StationPlaybackSwitch(
    state: PlaybackUiState,
    onPlayLive: (Station) -> Unit,
    onPlayTimefree: (Station, Program) -> Unit,
) {
    val station = state.station ?: return
    val switchingTarget = state.switchingTarget
    val timefreePrograms = remember(state.stationPrograms, state.program?.startTime, station.id) {
        val candidates = state.stationPrograms
            .filter { it.stationId == station.id && !it.isOnAir && it.endTime < currentRadikoTimestamp() }
        val currentIndex = candidates.indexOfFirst { it.startTime == state.program?.startTime }
        if (currentIndex >= 0) {
            val fromIndex = (currentIndex - 2).coerceAtLeast(0)
            candidates.drop(fromIndex).take(6)
        } else {
            candidates.asReversed().take(6)
        }
    }

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("${station.name} programs", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = if (state.isLive) "You are listening live. Choose a program below for timefree." else "You are listening to timefree. Return to live anytime.",
                    color = MutedText,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
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
            if (timefreePrograms.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier.height(178.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(timefreePrograms, key = { it.id }) { program ->
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
                Text("Recent timefree programs are loading or unavailable.", color = MutedText, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun SwitchActionPill(text: String, loading: Boolean, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
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
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            PlaybackImage(
                url = state.stationLogoUrl,
                label = state.station?.name ?: "Yar",
                modifier = Modifier.size(38.dp),
                contentScale = ContentScale.Fit,
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
        Text(
            text = "Close",
            modifier = Modifier.clickable(onClick = onDismiss),
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleSmall,
        )
    }
}

@Composable
private fun PlaybackHero(state: PlaybackUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PlaybackImage(
            url = state.artworkUrl,
            label = state.program?.title ?: state.title ?: "Now Playing",
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.9f),
            contentScale = ContentScale.Fit,
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
            if (state.program?.performer?.isNotBlank() == true) {
                Text(state.program.performer, color = MutedText, style = MaterialTheme.typography.bodyMedium)
            }
        }
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
    val controlsLocked = isSwitching || isSeeking || isBuffering

    GlassCard(modifier = Modifier.fillMaxWidth(), highlight = true) {
        Column(
            modifier = Modifier.padding(14.dp),
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
                        enabled = !controlsLocked,
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
                        enabled = !controlsLocked,
                    )
                }
            }

            if (controlsLocked) {
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
                    onValueChange = { if (!controlsLocked) draggingPositionMs = it.toLong() },
                    onValueChangeFinished = {
                        if (!controlsLocked) draggingPositionMs?.let(onSeek)
                        draggingPositionMs = null
                    },
                    enabled = !controlsLocked,
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
}

@Composable
private fun CurrentSongCard(song: NoaItem, isLive: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = if (isLive) "Now On-Air Song" else "Current Song",
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelLarge,
        )
        SongRow(song = song, highlighted = true)
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
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        when {
            loading && songs.isEmpty() -> EmptyState(
                title = "Loading songs",
                message = if (isLive) "Fetching current on-air songs." else "Fetching song history for this program.",
            )
            displaySongs.isNotEmpty() -> {
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

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
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
                return@Column
            }
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

@Composable
private fun TimetableSection(
    expanded: Boolean,
    programs: List<Program>,
    onToggle: () -> Unit,
) {
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
