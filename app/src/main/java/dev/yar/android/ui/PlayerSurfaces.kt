package dev.yar.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
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
    val isPlaying: Boolean,
    val isLive: Boolean,
    val positionMs: Long,
    val durationMs: Long,
    val isSeeking: Boolean,
)

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
    val openDragDistancePx = with(LocalDensity.current) { 340.dp.toPx() }

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
                        if (-dragY > openDragDistancePx * 0.25f) onOpenDetails()
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
                            text = if (state.isLive) "LIVE" else "TIMEFREE",
                            color = if (state.isLive) LiveRed else MaterialTheme.colorScheme.secondary,
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
                    onPauseResume = onPauseResume,
                    onSkipBack = onSkipBack,
                    onSkipForward = onSkipForward,
                )
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
    onPauseResume: () -> Unit,
    onSkipBack: () -> Unit,
    onSkipForward: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (!isLive) {
            Text(
                text = "-30",
                modifier = Modifier
                    .clickable(onClick = onSkipBack)
                    .padding(8.dp),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelMedium,
            )
        }
        PlayerPrimaryButton(label = if (isPlaying) "II" else "▶", onClick = onPauseResume, compact = true)
        if (!isLive) {
            Text(
                text = "+30",
                modifier = Modifier
                    .clickable(onClick = onSkipForward)
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
) {
    val uriHandler = LocalUriHandler.current
    BackHandler(enabled = visible, onBack = onDismiss)
    var dragY by remember { mutableStateOf(0f) }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(animationSpec = tween(260), initialOffsetY = { it }),
        exit = slideOutVertically(animationSpec = tween(220), targetOffsetY = { it }),
    ) {
        val density = LocalDensity.current
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val fullHeightPx = with(density) { maxHeight.toPx() }.coerceAtLeast(1f)
            val openingOffsetPx = if (opened) 0f else fullHeightPx * (1f - openingDragProgress.coerceIn(0f, 1f))
            val dismissOffsetPx = if (opened) dragY.coerceAtLeast(0f) else 0f
            val offsetPx = openingOffsetPx + dismissOffsetPx

            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .offset { IntOffset(0, offsetPx.roundToInt()) }
                    .alpha(if (opened) 1f else openingDragProgress.coerceIn(0.15f, 1f))
                    .pointerInput(opened) {
                        detectVerticalDragGestures(
                            onDragStart = { dragY = 0f },
                            onVerticalDrag = { _, amount -> if (opened) dragY = (dragY + amount).coerceAtLeast(0f) },
                            onDragEnd = {
                                if (opened && dragY > fullHeightPx * 0.18f) onDismiss()
                                dragY = 0f
                            },
                            onDragCancel = { dragY = 0f },
                        )
                    },
                color = MaterialTheme.colorScheme.background,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    PlayerSheetHandle(opened = opened, fullHeightPx = fullHeightPx, onDismiss = onDismiss, onDrag = { dragY = it })
                    PlayerTopBar(state = state, onDismiss = onDismiss)
                    PlaybackHero(state = state)
                    PlaybackControls(
                        isPlaying = state.isPlaying,
                        isLive = state.isLive,
                        positionMs = state.positionMs,
                        durationMs = state.durationMs,
                        isSeeking = state.isSeeking,
                        onPauseResume = onPauseResume,
                        onSeek = onSeek,
                        onSkipBack = onSkipBack,
                        onSkipForward = onSkipForward,
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
                    color = if (state.isLive) LiveRed else MaterialTheme.colorScheme.secondary,
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
    onPauseResume: () -> Unit,
    onSeek: (Long) -> Unit,
    onSkipBack: () -> Unit,
    onSkipForward: () -> Unit,
) {
    var draggingPositionMs by remember { mutableStateOf<Long?>(null) }
    val displayPositionMs = draggingPositionMs ?: positionMs

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
                if (!isLive) PlayerSecondaryButton(label = "-30", onClick = onSkipBack)
                PlayerPrimaryButton(label = if (isPlaying) "II" else "▶", onClick = onPauseResume)
                if (!isLive) PlayerSecondaryButton(label = "+30", onClick = onSkipForward)
            }

            if (!isLive && durationMs > 0) {
                Slider(
                    value = displayPositionMs.toFloat().coerceIn(0f, durationMs.toFloat()),
                    onValueChange = { draggingPositionMs = it.toLong() },
                    onValueChangeFinished = {
                        draggingPositionMs?.let(onSeek)
                        draggingPositionMs = null
                    },
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
