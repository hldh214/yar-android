package dev.yar.android.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    GlassCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionHeader(
                eyebrow = "Radio library",
                title = "Browse radio",
                subtitle = state.selectedStation?.let { "Selected ${it.name}" } ?: "Regions, live stations, and timefree programs.",
            )
            when {
                state.error != null -> EmptyState(
                    title = "Stations did not load",
                    message = state.error,
                )
                state.regions.isEmpty() -> EmptyState(
                    title = "Loading stations",
                    message = "Fetching radiko regions and stations.",
                )
                else -> StationBrowser(
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
}

@Composable
private fun StationBrowser(
    state: BrowserUiState,
    modifier: Modifier = Modifier,
    onRegionSelected: (Region) -> Unit,
    onStationSelected: (Station) -> Unit,
    onDateSelected: (BroadcastDate) -> Unit,
    onProgramSelected: (Station, Program) -> Unit,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        if (maxWidth < 680.dp) {
            MobileBrowser(
                state = state,
                onRegionSelected = onRegionSelected,
                onStationSelected = onStationSelected,
                onDateSelected = onDateSelected,
                onProgramSelected = onProgramSelected,
            )
        } else {
            WideBrowser(
                state = state,
                onRegionSelected = onRegionSelected,
                onStationSelected = onStationSelected,
                onDateSelected = onDateSelected,
                onProgramSelected = onProgramSelected,
            )
        }
    }
}

@Composable
private fun MobileBrowser(
    state: BrowserUiState,
    onRegionSelected: (Region) -> Unit,
    onStationSelected: (Station) -> Unit,
    onDateSelected: (BroadcastDate) -> Unit,
    onProgramSelected: (Station, Program) -> Unit,
) {
    var section by remember { mutableStateOf(MobileBrowseSection.Regions) }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        RecentStationsRail(
            stations = state.recentStations,
            switchingTarget = state.switchingTarget,
            onStationSelected = onStationSelected,
        )
        MobileBrowseTabs(
            selected = section,
            hasStation = state.selectedStation != null,
            onSelected = { section = it },
        )
        when (section) {
            MobileBrowseSection.Regions -> RegionList(
                regions = state.regions,
                selectedRegion = state.selectedRegion,
                modifier = Modifier.weight(1f),
                onRegionSelected = {
                    onRegionSelected(it)
                    section = MobileBrowseSection.Stations
                },
            )
            MobileBrowseSection.Stations -> StationList(
                region = state.selectedRegion,
                switchingTarget = state.switchingTarget,
                modifier = Modifier.weight(1f),
                onStationSelected = onStationSelected,
            )
            MobileBrowseSection.Timefree -> TimefreePrograms(
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

@Composable
private fun WideBrowser(
    state: BrowserUiState,
    onRegionSelected: (Region) -> Unit,
    onStationSelected: (Station) -> Unit,
    onDateSelected: (BroadcastDate) -> Unit,
    onProgramSelected: (Station, Program) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(
            modifier = Modifier.weight(0.9f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            RecentStationsRail(
                stations = state.recentStations,
                switchingTarget = state.switchingTarget,
                onStationSelected = onStationSelected,
            )
            RegionList(
                regions = state.regions,
                selectedRegion = state.selectedRegion,
                modifier = Modifier.weight(1f),
                onRegionSelected = onRegionSelected,
            )
        }
        StationList(
            region = state.selectedRegion,
            switchingTarget = state.switchingTarget,
            modifier = Modifier.weight(1f),
            onStationSelected = onStationSelected,
        )
        TimefreePrograms(
            station = state.selectedStation,
            selectedDate = state.selectedDate,
            programs = state.programs,
            programsLoading = state.programsLoading,
            switchingTarget = state.switchingTarget,
            modifier = Modifier.weight(1.1f),
            onDateSelected = onDateSelected,
            onProgramSelected = onProgramSelected,
        )
    }
}

private enum class MobileBrowseSection(val label: String) {
    Regions("Regions"),
    Stations("Stations"),
    Timefree("Timefree"),
}

@Composable
private fun MobileBrowseTabs(
    selected: MobileBrowseSection,
    hasStation: Boolean,
    onSelected: (MobileBrowseSection) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MobileBrowseSection.entries.forEach { section ->
            val enabled = section != MobileBrowseSection.Timefree || hasStation
            StatusPill(
                text = section.label,
                selected = enabled && section == selected,
                modifier = Modifier.clickable(enabled = enabled) { onSelected(section) },
                color = if (enabled) MaterialTheme.colorScheme.primary else MutedText,
            )
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
            .width(116.dp)
            .clickable(enabled = enabled && !loading, onClick = onClick),
        shape = MaterialTheme.shapes.large,
        color = ElevatedPanelAlt,
        tonalElevation = 3.dp,
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            PlaybackImage(
                url = station.logoUrl,
                label = station.name,
                modifier = Modifier.size(48.dp),
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
                text = if (loading) "Starting live..." else station.areaId,
                color = MutedText,
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
private fun RegionList(
    regions: List<Region>,
    selectedRegion: Region?,
    modifier: Modifier = Modifier,
    onRegionSelected: (Region) -> Unit,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text("Regions", color = MutedText, style = MaterialTheme.typography.labelLarge)
        }
        items(regions, key = { it.id }) { region ->
            RegionRow(
                region = region,
                selected = region.id == selectedRegion?.id,
                onClick = { onRegionSelected(region) },
            )
        }
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
            Text(
                text = region?.name ?: "Stations",
                color = MutedText,
                style = MaterialTheme.typography.labelLarge,
            )
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
                    text = station?.let { "Timefree for ${it.name}" } ?: "Timefree",
                    color = MutedText,
                    style = MaterialTheme.typography.labelLarge,
                )
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
private fun RegionRow(region: Region, selected: Boolean, onClick: () -> Unit) {
    ListSurface(selected = selected, onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(36.dp),
                shape = MaterialTheme.shapes.medium,
                color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else ElevatedPanelAlt,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = region.name.take(1),
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Black,
                    )
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = region.name,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall,
                )
                Text("${region.stations.size} stations", color = MutedText, style = MaterialTheme.typography.bodySmall)
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
                    text = station.asciiName.ifBlank { station.id },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MutedText,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (loading) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = LiveAccent)
                    StatusPill(text = "Starting", color = LiveAccent)
                }
            } else {
                StatusPill(text = "Live", color = LiveAccent)
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
