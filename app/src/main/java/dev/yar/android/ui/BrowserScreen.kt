package dev.yar.android.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
    onOpenRegionPicker: () -> Unit,
    onStationSelected: (Station) -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        HomeHeaderRow(state = state, onOpenRegionPicker = onOpenRegionPicker)
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
                onStationSelected = onStationSelected,
            )
        }
    }
}

@Composable
private fun HomeHeaderRow(state: BrowserUiState, onOpenRegionPicker: () -> Unit) {
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
        }
        StatusPill(
            text = regionPickerLabel(state.selectedRegion?.name),
            selected = state.selectedRegion != null,
            modifier = Modifier.clickable(onClick = onOpenRegionPicker),
            color = ActiveGreen,
        )
    }
}

@Composable
private fun StationHome(
    state: BrowserUiState,
    modifier: Modifier = Modifier,
    onStationSelected: (Station) -> Unit,
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
                StationList(
                    region = state.selectedRegion,
                    switchingTarget = state.switchingTarget,
                    modifier = Modifier.weight(1f),
                    onStationSelected = onStationSelected,
                )
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
                }
                StationList(
                    region = state.selectedRegion,
                    switchingTarget = state.switchingTarget,
                    modifier = Modifier.weight(1.25f),
                    onStationSelected = onStationSelected,
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
            items(visibleRecentItems(stations), key = { it.id }) { station ->
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RegionPickerSheet(
    visible: Boolean,
    regions: List<Region>,
    selectedRegion: Region?,
    onDismiss: () -> Unit,
    onRegionSelected: (Region) -> Unit,
) {
    if (!visible) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Choose region", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            LazyColumn(
                modifier = Modifier.heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(regions, key = { it.id }) { region ->
                    RegionPickerRow(
                        region = region,
                        selected = region.id == selectedRegion?.id,
                        onClick = { onRegionSelected(region) },
                    )
                }
            }
        }
    }
}

@Composable
private fun RegionPickerRow(region: Region, selected: Boolean, onClick: () -> Unit) {
    ListSurface(selected = selected, onClick = onClick) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = region.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyLarge,
            )
            if (selected) {
                StatusPill(text = "Selected", color = ActiveGreen)
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
