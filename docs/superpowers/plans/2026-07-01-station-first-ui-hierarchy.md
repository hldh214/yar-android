# Station-First UI Hierarchy Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebuild the phone UI hierarchy so Home is station-first, region selection moves into a bottom sheet, and Timefree is only exposed inside the current station/player context.

**Architecture:** Keep playback, data, domain, and Android Auto behavior unchanged. Add small presentation helpers where testable, then refactor Compose UI state and surfaces in `ui` to separate Home, region picking, and player-scoped Timefree. Preserve existing playback callbacks: station rows still call Live playback, and Timefree rows still call the existing Timefree callback.

**Tech Stack:** Kotlin, Jetpack Compose Material3, existing Yar UI primitives, Gradle Android plugin, JUnit unit tests for pure presentation helpers.

---

## File Structure

- Modify `app/src/main/java/dev/yar/android/ui/PlaybackDisplay.kt`: add small pure helpers for region labels and recent station limiting.
- Modify `app/src/test/java/dev/yar/android/ui/PlaybackDisplayTest.kt`: add tests for those helpers.
- Modify `app/src/main/java/dev/yar/android/ui/BrowserScreen.kt`: remove inline region rail and Home Timefree list, add region picker entry point and bottom sheet UI.
- Modify `app/src/main/java/dev/yar/android/ui/YarApp.kt`: own the region sheet open state and wire `BrowserScreen` callbacks to select/dismiss region sheet.
- Modify `app/src/main/java/dev/yar/android/ui/PlayerSurfaces.kt`: make station-scoped Timefree in the full player clearer and remove duplicate/outer Home assumptions if needed.

Do not modify `data`, `domain`, or `playback` packages.

## Task 1: Add Presentation Helper Coverage

**Files:**
- Modify: `app/src/main/java/dev/yar/android/ui/PlaybackDisplay.kt`
- Modify: `app/src/test/java/dev/yar/android/ui/PlaybackDisplayTest.kt`

- [ ] **Step 1: Add failing tests for region label and recent station limit**

Append these tests to `PlaybackDisplayTest`:

```kotlin
@Test
fun `region picker label falls back when region is absent`() {
    assertEquals("Choose region", regionPickerLabel(null))
    assertEquals("Tokyo", regionPickerLabel("Tokyo"))
    assertEquals("Choose region", regionPickerLabel(""))
    assertEquals("Choose region", regionPickerLabel("   "))
}

@Test
fun `recent station limit keeps at most ten items`() {
    assertEquals(listOf("0", "1", "2"), visibleRecentItems(listOf("0", "1", "2")))
    assertEquals(
        (0 until 10).map { it.toString() },
        visibleRecentItems((0 until 12).map { it.toString() }),
    )
}
```

- [ ] **Step 2: Run tests to verify RED**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests dev.yar.android.ui.PlaybackDisplayTest
```

Expected: compilation fails because `regionPickerLabel` and `visibleRecentItems` are undefined.

- [ ] **Step 3: Implement helpers**

Append these functions to `PlaybackDisplay.kt`:

```kotlin
internal fun regionPickerLabel(regionName: String?): String =
    regionName?.takeIf { it.isNotBlank() } ?: "Choose region"

internal fun <T> visibleRecentItems(items: List<T>): List<T> =
    items.take(10)
```

- [ ] **Step 4: Run tests to verify GREEN**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests dev.yar.android.ui.PlaybackDisplayTest
```

Expected: `PlaybackDisplayTest` passes.

- [ ] **Step 5: Commit**

Run:

```powershell
git -c safe.directory=C:/git/yar-android add app/src/main/java/dev/yar/android/ui/PlaybackDisplay.kt app/src/test/java/dev/yar/android/ui/PlaybackDisplayTest.kt
git -c safe.directory=C:/git/yar-android commit -m "Add station-first UI display helpers"
```

## Task 2: Make Home Station-Only

**Files:**
- Modify: `app/src/main/java/dev/yar/android/ui/BrowserScreen.kt`

- [ ] **Step 1: Run existing tests before editing**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests dev.yar.android.ui.PlaybackDisplayTest
```

Expected: tests pass.

- [ ] **Step 2: Change `BrowserScreen` callback API**

Update the `BrowserScreen` signature so Home opens a region picker instead of selecting inline regions:

```kotlin
internal fun BrowserScreen(
    state: BrowserUiState,
    modifier: Modifier = Modifier,
    onOpenRegionPicker: () -> Unit,
    onStationSelected: (Station) -> Unit,
)
```

Remove `onRegionSelected`, `onDateSelected`, and `onProgramSelected` from `BrowserScreen`.

- [ ] **Step 3: Update `HomeHeaderRow` to render a clickable region pill**

Change `HomeHeaderRow` to accept `onOpenRegionPicker`:

```kotlin
private fun HomeHeaderRow(state: BrowserUiState, onOpenRegionPicker: () -> Unit)
```

Keep the `Yar` heading. Replace the secondary selected-region text and selected-station status pill with a right-aligned region pill:

```kotlin
StatusPill(
    text = regionPickerLabel(state.selectedRegion?.name),
    selected = state.selectedRegion != null,
    modifier = Modifier.clickable(onClick = onOpenRegionPicker),
    color = ActiveGreen,
)
```

Ensure the title column uses `Modifier.weight(1f)` so the region pill cannot push the title off-screen.

- [ ] **Step 4: Simplify `StationHome` parameters**

Update `StationHome` to remove region and Timefree callbacks:

```kotlin
private fun StationHome(
    state: BrowserUiState,
    modifier: Modifier = Modifier,
    onStationSelected: (Station) -> Unit,
)
```

Remove calls to `RegionSelector` and `TimefreePrograms`.

- [ ] **Step 5: Update mobile layout**

For `maxWidth < 680.dp`, render only:

```kotlin
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
```

- [ ] **Step 6: Update wide layout**

For wide screens, render recent stations in the left column and station list as the primary panel:

```kotlin
Row(
    modifier = Modifier.fillMaxSize(),
    horizontalArrangement = Arrangement.spacedBy(16.dp),
) {
    Column(
        modifier = Modifier.weight(0.8f),
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
        modifier = Modifier.weight(1.4f),
        onStationSelected = onStationSelected,
    )
}
```

- [ ] **Step 7: Use helper in `RecentStationsRail`**

Replace `stations.take(10)` with:

```kotlin
visibleRecentItems(stations)
```

Keep the key as `station.id`.

- [ ] **Step 8: Delete unused Home-only Timefree and region composables**

Remove these private composables from `BrowserScreen.kt`:

```kotlin
RegionSelector
RegionChip
TimefreePrograms
```

Keep `ProgramRow` because `PlayerSurfaces.kt` uses it for the full player timetable.

- [ ] **Step 9: Compile to catch call-site errors**

Run:

```powershell
.\gradlew.bat :app:compileDebugKotlin
```

Expected: compilation fails in `YarApp.kt` because `BrowserScreen` still passes removed callbacks. This is expected before Task 3.

- [ ] **Step 10: Do not commit yet**

Leave Task 2 changes uncommitted until Task 3 wires the new API in `YarApp.kt`, so the repository is not left in a known uncompilable commit.

## Task 3: Add Region Bottom Sheet Wiring

**Files:**
- Modify: `app/src/main/java/dev/yar/android/ui/YarApp.kt`
- Modify: `app/src/main/java/dev/yar/android/ui/BrowserScreen.kt`

- [ ] **Step 1: Add region sheet state in `YarApp`**

Near the other UI state variables, add:

```kotlin
var showRegionPicker by remember { mutableStateOf(false) }
```

- [ ] **Step 2: Update `BrowserScreen` call site**

Replace the old `BrowserScreen` callbacks in `YarApp.kt` with:

```kotlin
onOpenRegionPicker = { showRegionPicker = true },
onStationSelected = { playLive(it) },
```

Remove the old `onRegionSelected`, `onDateSelected`, and `onProgramSelected` arguments.

- [ ] **Step 3: Create `RegionPickerSheet` in `BrowserScreen.kt`**

Add a new internal composable near `BrowserScreen`:

```kotlin
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
```

Add required imports:

```kotlin
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
```

- [ ] **Step 4: Add `RegionPickerRow`**

Add this private composable under `RegionPickerSheet`:

```kotlin
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
```

Add required import:

```kotlin
import androidx.compose.foundation.layout.defaultMinSize
```

- [ ] **Step 5: Render `RegionPickerSheet` from `YarApp`**

Inside `BoxWithConstraints`, after the main `Column` and before `PlayerDetailsOverlay`, render:

```kotlin
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
```

- [ ] **Step 6: Ensure Home state no longer owns Timefree inputs**

Keep `BrowserUiState` fields unchanged for now if removing them causes broad churn, but `BrowserScreen` must no longer read these fields:

```kotlin
selectedDate
programs
programsLoading
```

They remain useful for player/full-player state through `YarApp`.

- [ ] **Step 7: Run tests and compile**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests dev.yar.android.ui.PlaybackDisplayTest
.\gradlew.bat :app:compileDebugKotlin
```

Expected: tests pass and Kotlin compilation succeeds.

- [ ] **Step 8: Commit**

Run:

```powershell
git -c safe.directory=C:/git/yar-android add app/src/main/java/dev/yar/android/ui/BrowserScreen.kt app/src/main/java/dev/yar/android/ui/YarApp.kt
git -c safe.directory=C:/git/yar-android commit -m "Make home station-first"
```

## Task 4: Tighten Full Player Timefree Context

**Files:**
- Modify: `app/src/main/java/dev/yar/android/ui/PlayerSurfaces.kt`

- [ ] **Step 1: Run tests before editing**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests dev.yar.android.ui.PlaybackDisplayTest
```

Expected: tests pass.

- [ ] **Step 2: Rename station-scoped section for clarity**

Rename `StationPlaybackSwitch` to `StationTimefreeSection` and update the call site:

```kotlin
StationTimefreeSection(
    state = state,
    onPlayLive = onPlayLive,
    onPlayTimefree = onPlayTimefree,
)
```

The function signature stays:

```kotlin
private fun StationTimefreeSection(
    state: PlaybackUiState,
    onPlayLive: (Station) -> Unit,
    onPlayTimefree: (Station, Program) -> Unit,
)
```

- [ ] **Step 3: Make section title explicitly station-scoped**

Inside `StationTimefreeSection`, change the title/subtitle to:

```kotlin
SectionTitle(
    title = "Timefree from ${station.name}",
    subtitle = if (state.isLive) {
        "Pick a recent program from this station."
    } else {
        "You are listening to Timefree from this station."
    },
)
```

- [ ] **Step 4: Keep Live return visible only for Timefree**

Keep the current behavior:

```kotlin
if (state.isLive) {
    StatusPill(text = "Live now", color = LiveAccent)
} else {
    // Back to Live action
}
```

Do not add another Home-level Live/Timefree selector.

- [ ] **Step 5: Improve empty state wording**

Change the empty Timefree message to:

```kotlin
Text(
    "Recent Timefree programs for this station are loading or unavailable.",
    color = MutedText,
    style = MaterialTheme.typography.bodySmall,
)
```

- [ ] **Step 6: Run tests and compile**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests dev.yar.android.ui.PlaybackDisplayTest
.\gradlew.bat :app:compileDebugKotlin
```

Expected: tests pass and Kotlin compilation succeeds.

- [ ] **Step 7: Commit**

Run:

```powershell
git -c safe.directory=C:/git/yar-android add app/src/main/java/dev/yar/android/ui/PlayerSurfaces.kt
git -c safe.directory=C:/git/yar-android commit -m "Clarify player-scoped timefree"
```

## Task 5: Final Verification And Scope Check

**Files:**
- Modify only files needed for compile fixes discovered during verification.

- [ ] **Step 1: Run full unit tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

Expected: build succeeds.

- [ ] **Step 2: Build debug APK**

Run:

```powershell
.\gradlew.bat :app:assembleDebug
```

Expected: build succeeds and `app/build/outputs/apk/debug/app-debug.apk` exists.

- [ ] **Step 3: Confirm scope**

Run:

```powershell
git -c safe.directory=C:/git/yar-android status --short
git -c safe.directory=C:/git/yar-android diff --stat HEAD
git -c safe.directory=C:/git/yar-android diff -- app/src/main/java/dev/yar/android/playback app/src/main/java/dev/yar/android/data app/src/main/java/dev/yar/android/domain
```

Expected:

- No uncommitted tracked UI changes after commits.
- `playback`, `data`, and `domain` diffs are empty.

- [ ] **Step 4: Manual UI review if possible**

On an emulator or device, check:

- Home has no persistent region chip rail.
- Home has no inline Timefree program list.
- Region pill opens the region bottom sheet.
- Selecting a region dismisses the sheet and updates the station list.
- Tapping a station starts Live playback directly.
- Mini player still opens full player by tap and upward drag.
- Full player shows Timefree for the current station.
- Selecting a Timefree program starts Timefree playback.
- Returning to Live works from the full player.
- Long Japanese station/program titles truncate without pushing buttons off-screen.

- [ ] **Step 5: Document untested areas in final summary**

If no emulator/device manual playback test was performed, state that notification controls, lock screen controls, background playback, and Android Auto Desktop Head Unit were not manually verified.

- [ ] **Step 6: Commit any final fixes**

If Task 5 required compile or review fixes, commit them:

```powershell
git -c safe.directory=C:/git/yar-android add app/src/main/java/dev/yar/android/ui app/src/test/java/dev/yar/android/ui
git -c safe.directory=C:/git/yar-android commit -m "Finalize station-first UI hierarchy"
```

If there are no fixes, do not create an empty commit.
