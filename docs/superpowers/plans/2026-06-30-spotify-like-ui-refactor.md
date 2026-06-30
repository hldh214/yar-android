# Spotify-Like UI Refactor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebuild Yar Android's phone UI into a mature Spotify-like Home, mini player, and full player experience while preserving native playback behavior.

**Architecture:** Keep all playback state, service commands, and Android Auto behavior unchanged. Add small presentation helpers for testable UI decisions, then refactor Compose surfaces in `ui` to consume those helpers and new design primitives.

**Tech Stack:** Kotlin, Jetpack Compose Material3, Coil Compose, Gradle Android plugin, Kotlin test/JUnit for pure presentation helper tests.

---

## File Structure

- Modify `gradle/libs.versions.toml`: add JUnit and Material icon dependency aliases.
- Modify `app/build.gradle.kts`: add `testImplementation` for unit tests and `implementation` for Material icons.
- Create `app/src/main/java/dev/yar/android/ui/PlaybackDisplay.kt`: pure presentation helpers for labels, subtitles, progress ratios, and control lock state.
- Create `app/src/test/java/dev/yar/android/ui/PlaybackDisplayTest.kt`: unit tests for helper behavior.
- Modify `app/src/main/java/dev/yar/android/ui/YarTheme.kt`: replace purple/pink glass palette with mature dark neutral and player accents.
- Modify `app/src/main/java/dev/yar/android/ui/UiKit.kt`: flatten surfaces, add icon-first player buttons, compact section headings, and stable artwork primitives.
- Modify `app/src/main/java/dev/yar/android/ui/BrowserScreen.kt`: reshape browse into Home-style recent rail, compact region chips, station list, and secondary timefree section.
- Modify `app/src/main/java/dev/yar/android/ui/PlayerSurfaces.kt`: redesign mini player and full player around artwork-first playback hierarchy.
- Modify `app/src/main/java/dev/yar/android/ui/YarApp.kt`: adjust top-level padding/composition only if the new Home surface needs it.

## Task 1: Add Test Harness And Presentation Helpers

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Create: `app/src/main/java/dev/yar/android/ui/PlaybackDisplay.kt`
- Create: `app/src/test/java/dev/yar/android/ui/PlaybackDisplayTest.kt`

- [ ] **Step 1: Add JUnit aliases**

Edit `gradle/libs.versions.toml` and add this library alias under `[libraries]`:

```toml
junit = { group = "junit", name = "junit", version = "4.13.2" }
```

- [ ] **Step 2: Add local test dependency**

Edit `app/build.gradle.kts` and add this line in `dependencies`:

```kotlin
testImplementation(libs.junit)
```

- [ ] **Step 3: Write failing tests for display helpers**

Create `app/src/test/java/dev/yar/android/ui/PlaybackDisplayTest.kt`:

```kotlin
package dev.yar.android.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackDisplayTest {
    @Test
    fun `playback status label prefers loading before live and timefree`() {
        assertEquals("LOADING", playbackStatusLabel(isLive = true, busy = true))
        assertEquals("LIVE", playbackStatusLabel(isLive = true, busy = false))
        assertEquals("TIMEFREE", playbackStatusLabel(isLive = false, busy = false))
    }

    @Test
    fun `playback progress ratio clamps invalid and out of range values`() {
        assertEquals(0f, playbackProgressRatio(positionMs = 10_000L, durationMs = 0L))
        assertEquals(0f, playbackProgressRatio(positionMs = -100L, durationMs = 10_000L))
        assertEquals(0.25f, playbackProgressRatio(positionMs = 2_500L, durationMs = 10_000L))
        assertEquals(1f, playbackProgressRatio(positionMs = 12_000L, durationMs = 10_000L))
    }

    @Test
    fun `station subtitle prefers ascii name then station id`() {
        assertEquals("TBS Radio", stationSubtitle(asciiName = "TBS Radio", stationId = "TBS"))
        assertEquals("TBS", stationSubtitle(asciiName = "", stationId = "TBS"))
        assertEquals("TBS", stationSubtitle(asciiName = "   ", stationId = "TBS"))
    }

    @Test
    fun `player controls lock for switching seeking or buffering`() {
        assertTrue(controlsLocked(isSwitching = true, isSeeking = false, isBuffering = false))
        assertTrue(controlsLocked(isSwitching = false, isSeeking = true, isBuffering = false))
        assertTrue(controlsLocked(isSwitching = false, isSeeking = false, isBuffering = true))
        assertFalse(controlsLocked(isSwitching = false, isSeeking = false, isBuffering = false))
    }
}
```

- [ ] **Step 4: Run tests to verify RED**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests dev.yar.android.ui.PlaybackDisplayTest
```

Expected: compilation fails because `playbackStatusLabel`, `playbackProgressRatio`, `stationSubtitle`, and `controlsLocked` are undefined.

- [ ] **Step 5: Implement minimal helpers**

Create `app/src/main/java/dev/yar/android/ui/PlaybackDisplay.kt`:

```kotlin
package dev.yar.android.ui

internal fun playbackStatusLabel(isLive: Boolean, busy: Boolean): String = when {
    busy -> "LOADING"
    isLive -> "LIVE"
    else -> "TIMEFREE"
}

internal fun playbackProgressRatio(positionMs: Long, durationMs: Long): Float {
    if (durationMs <= 0L) return 0f
    return (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
}

internal fun stationSubtitle(asciiName: String, stationId: String): String =
    asciiName.ifBlank { stationId }

internal fun controlsLocked(isSwitching: Boolean, isSeeking: Boolean, isBuffering: Boolean): Boolean =
    isSwitching || isSeeking || isBuffering
```

- [ ] **Step 6: Run tests to verify GREEN**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests dev.yar.android.ui.PlaybackDisplayTest
```

Expected: `PlaybackDisplayTest` passes.

- [ ] **Step 7: Commit**

Run:

```powershell
git -c safe.directory=C:/git/yar-android add gradle/libs.versions.toml app/build.gradle.kts app/src/main/java/dev/yar/android/ui/PlaybackDisplay.kt app/src/test/java/dev/yar/android/ui/PlaybackDisplayTest.kt
git -c safe.directory=C:/git/yar-android commit -m "Add UI display helper tests"
```

## Task 2: Refresh Theme And Shared UI Primitives

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/java/dev/yar/android/ui/YarTheme.kt`
- Modify: `app/src/main/java/dev/yar/android/ui/UiKit.kt`
- Modify: `app/src/main/java/dev/yar/android/ui/PlayerSurfaces.kt`

- [ ] **Step 1: Run existing helper tests before editing**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests dev.yar.android.ui.PlaybackDisplayTest
```

Expected: tests pass before visual primitive changes.

- [ ] **Step 2: Add Material icon dependency**

Edit `gradle/libs.versions.toml` and add this library alias under `[libraries]`:

```toml
androidx-compose-material-icons-extended = { module = "androidx.compose.material:material-icons-extended" }
```

Edit `app/build.gradle.kts` and add this line in `dependencies`:

```kotlin
implementation(libs.androidx.compose.material.icons.extended)
```

- [ ] **Step 3: Replace theme colors and shape scale**

Update `YarTheme.kt` so the color tokens are:

```kotlin
internal val YarColors = darkColorScheme(
    primary = Color(0xFF1DB954),
    onPrimary = Color(0xFF04130A),
    primaryContainer = Color(0xFF12351F),
    onPrimaryContainer = Color(0xFFD7FBE0),
    secondary = Color(0xFF38BDF8),
    onSecondary = Color(0xFF03131C),
    tertiary = Color(0xFFFF5C7A),
    onTertiary = Color(0xFF24040B),
    background = Color(0xFF050608),
    onBackground = Color(0xFFF7F7F8),
    surface = Color(0xFF101114),
    onSurface = Color(0xFFF7F7F8),
    surfaceVariant = Color(0xFF191B20),
    onSurfaceVariant = Color(0xFFC5CAD3),
    outline = Color(0xFF3A3D45),
    error = Color(0xFFFF5C7A),
    errorContainer = Color(0xFF3B0C16),
    onErrorContainer = Color(0xFFFFD9DF),
)

internal val AppBackgroundTop = Color(0xFF0C0D10)
internal val AppBackgroundMid = Color(0xFF08090B)
internal val AppBackgroundBottom = Color(0xFF050608)
internal val ElevatedPanel = Color(0xFF15171C)
internal val ElevatedPanelAlt = Color(0xFF1B1E24)
internal val GlassPanel = Color(0xF214161A)
internal val MutedText = Color(0xFFA7ADB8)
internal val LiveAccent = Color(0xFF38BDF8)
internal val TimefreeAccent = Color(0xFFFF5C7A)
internal val ActiveGreen = Color(0xFF1DB954)
```

Update `YarShapes` to:

```kotlin
private val YarShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
)
```

- [ ] **Step 4: Keep background restrained**

Update `YarBackground` to keep a subtle neutral vertical gradient using the new background colors. Do not add decorative orbs or large colorful gradients.

- [ ] **Step 5: Add icon-first control imports**

In `UiKit.kt`, add imports for Material icons used by player controls:

```kotlin
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.vector.ImageVector
```

In `PlayerSurfaces.kt`, add imports for icons used at call sites:

```kotlin
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.Replay30
```

- [ ] **Step 6: Flatten shared surface primitives**

Update `GlassCard` to use `MaterialTheme.shapes.large` with `ElevatedPanel`/`ElevatedPanelAlt`, not a glass look:

```kotlin
Card(
    modifier = modifier,
    shape = MaterialTheme.shapes.large,
    colors = CardDefaults.cardColors(containerColor = if (highlight) ElevatedPanelAlt else ElevatedPanel),
    elevation = CardDefaults.cardElevation(defaultElevation = if (highlight) 3.dp else 1.dp),
) {
    content()
}
```

Update `ListSurface` to use `MaterialTheme.shapes.medium`, `ElevatedPanel`, and `primaryContainer` only for selected rows.

- [ ] **Step 7: Update artwork primitive**

Change `PlaybackImage` surface shape to `MaterialTheme.shapes.medium`, keep stable caller-provided dimensions, and use a restrained fallback gradient based on `primaryContainer` and `surfaceVariant`.

- [ ] **Step 8: Replace player button functions**

Change `PlayerPrimaryButton` to accept icon content descriptions:

```kotlin
internal fun PlayerPrimaryButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    compact: Boolean = false,
    enabled: Boolean = true,
    loading: Boolean = false,
)
```

Inside, render `CircularProgressIndicator` when loading, otherwise render:

```kotlin
Icon(
    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
    contentDescription = if (isPlaying) "Pause" else "Play",
)
```

Change `PlayerSecondaryButton` to:

```kotlin
internal fun PlayerSecondaryButton(
    imageVector: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
)
```

and render an `OutlinedIconButton` with `Icon(imageVector, contentDescription)`.

- [ ] **Step 9: Update compilation call sites**

Update all existing `PlayerPrimaryButton(label = ...)` and `PlayerSecondaryButton(label = ...)` call sites in `PlayerSurfaces.kt` to use the new APIs. Use `Icons.Filled.Replay30`, `Icons.Filled.Forward30`, `isPlaying`, and descriptive content descriptions.

- [ ] **Step 10: Run tests and compile**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests dev.yar.android.ui.PlaybackDisplayTest
.\gradlew.bat :app:compileDebugKotlin
```

Expected: tests pass and Kotlin compilation succeeds.

- [ ] **Step 11: Commit**

Run:

```powershell
git -c safe.directory=C:/git/yar-android add app/src/main/java/dev/yar/android/ui/YarTheme.kt app/src/main/java/dev/yar/android/ui/UiKit.kt app/src/main/java/dev/yar/android/ui/PlayerSurfaces.kt gradle/libs.versions.toml app/build.gradle.kts
git -c safe.directory=C:/git/yar-android commit -m "Refresh player UI theme primitives"
```

## Task 3: Rebuild Browser As Spotify-Like Home

**Files:**
- Modify: `app/src/main/java/dev/yar/android/ui/BrowserScreen.kt`
- Modify: `app/src/main/java/dev/yar/android/ui/YarApp.kt` only if top-level spacing needs adjustment.

- [ ] **Step 1: Run tests before editing**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests dev.yar.android.ui.PlaybackDisplayTest
```

Expected: tests pass.

- [ ] **Step 2: Remove mobile tab state**

In `BrowserScreen.kt`, remove `MobileBrowseSection`, `MobileBrowseTabs`, and the mobile `section` state. The mobile flow should no longer start on a Regions tab.

- [ ] **Step 3: Replace `BrowserScreen` wrapper**

Change `BrowserScreen` to render an unframed `Column` with:

- `HomeHeaderRow(state = state)`
- loading/error handling
- `StationHome(...)`

Do not wrap the whole browser in `GlassCard`.

- [ ] **Step 4: Add `HomeHeaderRow`**

Add a composable near the top of `BrowserScreen.kt`:

```kotlin
@Composable
private fun HomeHeaderRow(state: BrowserUiState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
```

- [ ] **Step 5: Add `StationHome` responsive layout**

Replace `StationBrowser`, `MobileBrowser`, and `WideBrowser` with a `StationHome` that uses `BoxWithConstraints`.

Mobile:

- `RecentStationsRail`
- `RegionSelector`
- `StationList`
- `TimefreePrograms`

Wide:

- Left column: `RecentStationsRail`, `RegionSelector`
- Middle column: `StationList`
- Right column: `TimefreePrograms`

Use weights so station list remains the primary area.

- [ ] **Step 6: Add compact `RegionSelector`**

Add:

```kotlin
@Composable
private fun RegionSelector(
    regions: List<Region>,
    selectedRegion: Region?,
    onRegionSelected: (Region) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Regions", color = MutedText, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(regions, key = { it.id }) { region ->
                StatusPill(
                    text = region.name,
                    selected = region.id == selectedRegion?.id,
                    modifier = Modifier.clickable { onRegionSelected(region) },
                    color = ActiveGreen,
                )
            }
        }
    }
}
```

- [ ] **Step 7: Restyle recent station cards**

Update `RecentStationCard`:

- Width around `104.dp`.
- Shape `MaterialTheme.shapes.large`.
- Background `ElevatedPanel`.
- Logo `56.dp`.
- Show station name and `stationSubtitle(station.asciiName, station.id)`.
- Use a small `CircularProgressIndicator` only when loading.

- [ ] **Step 8: Restyle station rows**

Update `StationRow`:

- Use `stationSubtitle(station.asciiName, station.id)`.
- Show an icon-style circular play/loading affordance on the right.
- Use `LiveAccent` for live/start indicators.
- Avoid `StatusPill("Live")` as the only action; the row itself starts live playback.

- [ ] **Step 9: Keep timefree secondary**

Keep `TimefreePrograms` below station list on mobile and in the right column on wide. Update section title to `Timefree` and subtitle to the selected station name. Keep date chips and existing program filtering.

- [ ] **Step 10: Adjust `YarApp` top-level header**

If `HomeHeader()` in `YarApp.kt` duplicates the new `HomeHeaderRow`, remove the top-level `HomeHeader()` call and let `BrowserScreen` own the Home header. Keep `NotificationPermissionCard` above or below the Home header based on visual fit.

- [ ] **Step 11: Run tests and compile**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests dev.yar.android.ui.PlaybackDisplayTest
.\gradlew.bat :app:compileDebugKotlin
```

Expected: tests pass and Kotlin compilation succeeds.

- [ ] **Step 12: Commit**

Run:

```powershell
git -c safe.directory=C:/git/yar-android add app/src/main/java/dev/yar/android/ui/BrowserScreen.kt app/src/main/java/dev/yar/android/ui/YarApp.kt
git -c safe.directory=C:/git/yar-android commit -m "Rework browse screen into player home"
```

## Task 4: Redesign Mini Player

**Files:**
- Modify: `app/src/main/java/dev/yar/android/ui/PlayerSurfaces.kt`

- [ ] **Step 1: Run tests before editing**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests dev.yar.android.ui.PlaybackDisplayTest
```

Expected: tests pass.

- [ ] **Step 2: Update mini player surface**

In `MiniPlayer`, change the `Surface` to:

- Shape `MaterialTheme.shapes.large`.
- Color `MaterialTheme.colorScheme.primaryContainer` when live, `ElevatedPanelAlt` when timefree.
- Tonal elevation no more than `4.dp`.
- Padding `8.dp`.

- [ ] **Step 3: Make mini player layout stable**

Use a single `Row`:

- Artwork `52.dp`.
- Center `Column(weight = 1f)` with status/station row, title, secondary line, and progress strip.
- Right controls with icon primary button and optional compact timefree skip icons.

Use `playbackStatusLabel(isLive = state.isLive, busy = busy)` for the status text.

- [ ] **Step 4: Replace text controls**

Update `MiniPlayerControls` to use:

```kotlin
PlayerSecondaryButton(
    imageVector = Icons.Filled.Replay30,
    contentDescription = "Skip back 30 seconds",
    onClick = onSkipBack,
    enabled = !busy,
)
PlayerPrimaryButton(
    isPlaying = isPlaying,
    onClick = onPauseResume,
    compact = true,
    enabled = !busy,
    loading = busy,
)
PlayerSecondaryButton(
    imageVector = Icons.Filled.Forward30,
    contentDescription = "Skip forward 30 seconds",
    onClick = onSkipForward,
    enabled = !busy,
)
```

For live, render only `PlayerPrimaryButton`.

- [ ] **Step 5: Use helper progress ratio**

Replace inline progress math with:

```kotlin
progress = { playbackProgressRatio(state.positionMs, state.durationMs) }
```

- [ ] **Step 6: Keep error text compact**

Render `state.playbackError` as a single-line label under the main row:

```kotlin
Text(
    text = it,
    maxLines = 1,
    overflow = TextOverflow.Ellipsis,
    color = MaterialTheme.colorScheme.error,
    style = MaterialTheme.typography.labelSmall,
)
```

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
git -c safe.directory=C:/git/yar-android add app/src/main/java/dev/yar/android/ui/PlayerSurfaces.kt
git -c safe.directory=C:/git/yar-android commit -m "Polish mini player surface"
```

## Task 5: Redesign Full Player Overlay

**Files:**
- Modify: `app/src/main/java/dev/yar/android/ui/PlayerSurfaces.kt`

- [ ] **Step 1: Run tests before editing**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests dev.yar.android.ui.PlaybackDisplayTest
```

Expected: tests pass.

- [ ] **Step 2: Reorder full player content**

Inside `PlayerDetailsOverlay`, keep the existing sheet animation and nested scroll logic. Reorder the inner `Column` to:

1. `PlayerSheetHandle`
2. `PlayerTopBar`
3. `PlaybackHero`
4. `PlaybackControls`
5. playback error notice
6. `CurrentSongCard`
7. `SongsSection`
8. `StationPlaybackSwitch`
9. `ProgramDescription`
10. `TimetableSection`

- [ ] **Step 3: Make hero artwork square**

Update `PlaybackHero` artwork modifier to:

```kotlin
Modifier
    .fillMaxWidth()
    .aspectRatio(1f)
```

Keep `ContentScale.Fit` to avoid cropping station/program art.

- [ ] **Step 4: Improve top bar close control**

In `PlayerTopBar`, replace the text `Close` with an icon button using `Icons.Filled.Close` or `Icons.AutoMirrored.Filled.KeyboardArrowDown`, content description `Close player`, and a minimum touch target.

- [ ] **Step 5: Flatten playback controls**

Remove the `GlassCard` wrapper around `PlaybackControls`. Use a plain `Column` with centered icon buttons, slider, and labels. Use:

```kotlin
val controlsAreLocked = controlsLocked(
    isSwitching = isSwitching,
    isSeeking = isSeeking,
    isBuffering = isBuffering,
)
```

Use icon buttons for skip and play/pause.

- [ ] **Step 6: Restyle supporting sections**

Update `StationPlaybackSwitch`, `ProgramDescription`, and `EmptyState` usage in this file to avoid nested card-heavy layout. Use `GlassCard` only for distinct repeated/supporting items, not for a broad page section.

- [ ] **Step 7: Keep existing behavior callbacks**

Confirm these callbacks are still passed unchanged:

- `onPauseResume`
- `onSeek`
- `onSkipBack`
- `onSkipForward`
- `onPlayLive`
- `onPlayTimefree`
- `onToggleTimetable`
- `onDismiss`

- [ ] **Step 8: Run tests and compile**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests dev.yar.android.ui.PlaybackDisplayTest
.\gradlew.bat :app:compileDebugKotlin
```

Expected: tests pass and Kotlin compilation succeeds.

- [ ] **Step 9: Commit**

Run:

```powershell
git -c safe.directory=C:/git/yar-android add app/src/main/java/dev/yar/android/ui/PlayerSurfaces.kt
git -c safe.directory=C:/git/yar-android commit -m "Redesign full player overlay"
```

## Task 6: Final Build And Manual Review

**Files:**
- Modify only files needed for compile fixes discovered during verification.

- [ ] **Step 1: Run unit tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

Expected: all available unit tests pass.

- [ ] **Step 2: Build debug APK**

Run:

```powershell
.\gradlew.bat :app:assembleDebug
```

Expected: build succeeds and produces `app/build/outputs/apk/debug/app-debug.apk`.

- [ ] **Step 3: Inspect Git diff for scope**

Run:

```powershell
git -c safe.directory=C:/git/yar-android status --short
git -c safe.directory=C:/git/yar-android diff --stat HEAD
git -c safe.directory=C:/git/yar-android diff -- app/src/main/java/dev/yar/android/playback app/src/main/java/dev/yar/android/data app/src/main/java/dev/yar/android/domain
```

Expected: playback, data, and domain diffs are empty. UI and test/build files are the only changed areas.

- [ ] **Step 4: Manual UI review**

Install/run the debug build on an emulator or device if available. Check:

- Home shows recent stations above the selected region station list.
- No large nested card shell around the entire browser.
- Region switching still changes station list.
- Tapping a station still starts live playback.
- Selecting a timefree program still starts timefree playback.
- Mini player tap and upward drag still open full player.
- Full player close/back dismiss still works.
- Timefree seek and 30-second skip controls still call the existing callbacks.
- Long Japanese station/program titles truncate cleanly.

- [ ] **Step 5: Document untested manual areas**

If emulator/device playback cannot be tested, record that in the final implementation summary with exact commands that did run. Do not claim notification, lock screen, background playback, or Android Auto behavior was manually tested unless it was.

- [ ] **Step 6: Commit final verification fixes**

If compile or review required small fixes, commit them:

```powershell
git -c safe.directory=C:/git/yar-android add app/src/main/java/dev/yar/android/ui app/src/test/java/dev/yar/android/ui gradle/libs.versions.toml app/build.gradle.kts
git -c safe.directory=C:/git/yar-android commit -m "Finalize Spotify-like UI refactor"
```

If no fixes are needed, do not create an empty commit.
