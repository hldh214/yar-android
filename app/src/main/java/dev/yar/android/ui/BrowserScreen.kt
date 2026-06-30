package dev.yar.android.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.yar.android.domain.Program
import dev.yar.android.domain.Region
import dev.yar.android.domain.Station

internal data class BrowserUiState(
    val regions: List<Region>,
    val recentStations: List<Station>,
    val selectedRegion: Region?,
    val selectedStation: Station?,
    val selectedDate: BroadcastDate,
    val programs: List<Program>,
    val programsLoading: Boolean,
    val switchingTarget: PlaybackSwitchTarget?,
    val error: String?,
)

@Composable
internal fun BrowserScreen(
    state: BrowserUiState,
    modifier: Modifier = Modifier,
    onRegionSelected: (Region) -> Unit,
    onStationSelected: (Station) -> Unit,
    onDateSelected: (BroadcastDate) -> Unit,
    onProgramSelected: (Station, Program) -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        HomeHeaderRow(state)
        when {
            state.error != null -> EmptyState(
                title = "Stations did not load",
                message = state.error,
            )
            state.regions.isEmpty() -> EmptyState(
                title = "Loading stations",
                message = "Fetching radiko regions and stations.",
            )
            else -> StationHome(
                state = state,
                modifier = Modifier.weight(1f),
                onRegionSelected = onRegionSelected,
                onStationSelected = onStationSelected,
                onDateSelected = onDateSelected,
                onProgramSelected = onProgramSelected,
            )
        }
    }
}

@Composable
private fun HomeHeaderRow(state: BrowserUiState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "Yar",
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Black,
                style = MaterialTheme.typography.headlineLarge,
            )
            Text(
                text = state.selectedRegion?.name ?: "Choose a region",
                color = MutedText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        state.selectedStation?.let {
            StatusPill(text = it.id, color = ActiveGreen)
        }
    }
}

@Composable
private fun StationHome(
    state: BrowserUiState,
    modifier: Modifier = Modifier,
    onRegionSelected: (Region) -> Unit,
    onStationSelected: (Station) -> Unit,
    onDateSelected: (BroadcastDate) -> Unit,
    onProgramSelected: (Station, Program) -> Unit,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        if (maxWidth < 680.dp) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                RecentStationsRail(
                    stations = state.recentStations,
                    switchingTarget = state.switchingTarget,
                    onStationSelected = onStationSelected,
                )
                RegionSelector(
                    regions = state.regions,
                    selectedRegion = state.selectedRegion,
                    onRegionSelected = onRegionSelected,
                )
                StationList(
                    region = state.selectedRegion,
                    switchingTarget = state.switchingTarget,
                    modifier = Modifier.weight(1f),
                    onStationSelected = onStationSelected,
                )
                if (state.selectedStation != null) {
                    TimefreePrograms(
                        station = state.selectedStation,
                        selectedDate = state.selectedDate,
                        programs = state.programs,
                        programsLoading = state.programsLoading,
                        switchingTarget = state.switchingTarget,
                        modifier = Modifier.weight(0.45f),
                        onDateSelected = onDateSelected,
                        onProgramSelected = onProgramSelected,
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(
                    modifier = Modifier.weight(0.85f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    RecentStationsRail(
                        stations = state.recentStations,
                        switchingTarget = state.switchingTarget,
                        onStationSelected = onStationSelected,
                    )
                    RegionSelector(
                        regions = state.regions,
                        selectedRegion = state.selectedRegion,
                        onRegionSelected = onRegionSelected,
                    )
                }
                StationList(
                    region = state.selectedRegion,
                    switchingTarget = state.switchingTarget,
                    modifier = Modifier.weight(1.25f),
                    onStationSelected = onStationSelected,
                )
                TimefreePrograms(
                    station = state.selectedStation,
                    selectedDate = state.selectedDate,
                    programs = state.programs,
                    programsLoading = state.programsLoading,
                    switchingTarget = state.switchingTarget,
                    modifier = Modifier.weight(1f),
                    onDateSelected = onDateSelected,
                    onProgramSelected = onProgramSelected,
                )
            }
        }
    }
}

@Composable
private fun RecentStationsRail(
    stations: List<Station>,
    switchingTarget: PlaybackSwitchTarget?,
    onStationSelected: (Station) -> Unit,
) {
    if (stations.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Recent", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(stations.take(10), key = { it.id }) { station ->
                RecentStationCard(
                    station = station,
                    loading = switchingTarget == PlaybackSwitchTarget.Live(station.id),
                    enabled = switchingTarget == null,
                    onClick = { onStationSelected(station) },
                )
            }
        }
    }
}

@Composable
private fun RecentStationCard(station: Station, loading: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .width(104.dp)
            .clickable(enabled = enabled && !loading, onClick = onClick),
        shape = MaterialTheme.shapes.large,
        color = ElevatedPanel,
        tonalElevation = 3.dp,
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            PlaybackImage(
                url = station.logoUrl,
                label = station.name,
                modifier = Modifier.size(56.dp),
                contentScale = ContentScale.Fit,
            )
            Text(
                text = station.name,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stationSubtitle(station.asciiName, station.id),
                color = MutedText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelSmall,
            )
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun RegionSelector(
    regions: List<Region>,
    selectedRegion: Region?,
    onRegionSelected: (Region) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Regions", color = MutedText, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(regions, key = { it.id }) { region ->
                RegionChip(
                    region = region,
                    selected = region.id == selectedRegion?.id,
                    onClick = { onRegionSelected(region) },
                )
            }
        }
    }
}

@Composable
private fun RegionChip(region: Region, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .widthIn(max = 156.dp)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.small,
        color = if (selected) ActiveGreen.copy(alpha = 0.16f) else ElevatedPanelAlt,
    ) {
        Text(
            text = region.name,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            color = if (selected) ActiveGreen else MutedText,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun StationList(
    region: Region?,
    switchingTarget: PlaybackSwitchTarget?,
    modifier: Modifier = Modifier,
    onStationSelected: (Station) -> Unit,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Stations",
                    color = MutedText,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelLarge,
                )
                region?.let {
                    Text(
                        text = it.name,
                        color = MutedText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        items(region?.stations.orEmpty(), key = { it.id }) { station ->
            StationRow(
                station = station,
                loading = switchingTarget == PlaybackSwitchTarget.Live(station.id),
                enabled = switchingTarget == null,
                onClick = { onStationSelected(station) },
            )
        }
    }
}

@Composable
private fun TimefreePrograms(
    station: Station?,
    selectedDate: BroadcastDate,
    programs: List<Program>,
    programsLoading: Boolean,
    switchingTarget: PlaybackSwitchTarget?,
    modifier: Modifier = Modifier,
    onDateSelected: (BroadcastDate) -> Unit,
    onProgramSelected: (Station, Program) -> Unit,
) {
    val endedPrograms = programs.filter { !it.isOnAir && it.endTime < currentRadikoTimestamp() }
        .takeLast(10)
        .asReversed()

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Timefree",
                    color = MutedText,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelLarge,
                )
                station?.let {
                    Text(
                        text = it.name,
                        color = MutedText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (station != null) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(broadcastDates(), key = { it.value }) { date ->
                            StatusPill(
                                text = date.label,
                                selected = date.value == selectedDate.value,
                                modifier = Modifier.clickable(enabled = switchingTarget == null) { onDateSelected(date) },
                            )
                        }
                    }
                }
            }
        }
        when {
            station == null -> item {
                EmptyState(
                    title = "Choose a station",
                    message = "Timefree episodes appear here after selecting a station.",
                )
            }
            programsLoading -> item {
                EmptyState(
                    title = "Loading timefree",
                    message = "Fetching recent programs for ${station.name}.",
                )
            }
            endedPrograms.isEmpty() -> item {
                EmptyState(
                    title = "No ended programs",
                    message = "Try a different broadcast date.",
                )
            }
            else -> items(endedPrograms, key = { it.id }) { program ->
                ProgramRow(
                    program = program,
                    loading = switchingTarget == PlaybackSwitchTarget.Timefree(station.id, program.startTime),
                    enabled = switchingTarget == null,
                    onClick = { onProgramSelected(station, program) },
                )
            }
        }
    }
}

@Composable
private fun StationRow(station: Station, loading: Boolean, enabled: Boolean, onClick: () -> Unit) {
    ListSurface(selected = loading, enabled = enabled || loading, onClick = if (loading) null else onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlaybackImage(
                url = station.logoUrl,
                label = station.name,
                modifier = Modifier.size(44.dp),
                contentScale = ContentScale.Fit,
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = station.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = stationSubtitle(station.asciiName, station.id),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MutedText,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Surface(
                modifier = Modifier.size(36.dp),
                shape = CircleShape,
                color = LiveAccent.copy(alpha = if (loading) 0.18f else 0.12f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (loading) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = LiveAccent)
                    } else {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = "Play ${station.name} live",
                            modifier = Modifier.size(22.dp),
                            tint = LiveAccent,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun ProgramRow(program: Program, loading: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    ListSurface(selected = loading, enabled = enabled || loading, onClick = if (loading) null else onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlaybackImage(
                url = program.imageUrl,
                label = program.title.ifBlank { "Program" },
                modifier = Modifier.size(54.dp),
                contentScale = ContentScale.Fit,
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                InlineMetaRow {
                    Text(
                        text = "${formatRadikoTime(program.startTime)}-${formatRadikoTime(program.endTime)}",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelMedium,
                    )
                    if (program.isTimefree) StatusPill(text = if (loading) "Loading" else "TF")
                }
                Text(
                    text = program.title.ifBlank { "Untitled program" },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (program.performer.isNotBlank()) {
                    Text(
                        text = program.performer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MutedText,
                        style = MaterialTheme.typography.bodySmall,
                    )
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
