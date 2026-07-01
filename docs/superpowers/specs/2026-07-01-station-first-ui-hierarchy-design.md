# Station-First UI Hierarchy Design

## Goal

Refactor Yar Android's phone UI hierarchy so small-screen use is simpler and more tap-friendly. The current Spotify-like visual refresh looks good, but Home still exposes too many levels at once: region selection, station selection, and Timefree program selection all compete for space. The new hierarchy should be station-first:

- Home is for choosing a station and starting Live playback.
- Region selection is available on demand through a bottom sheet.
- Timefree is scoped to the currently selected station and lives inside the player detail experience.
- Playback/data/domain behavior stays unchanged; UI continues to call existing playback callbacks.

## Confirmed Direction

Use the station-first variant chosen during visual brainstorming:

1. Home shows a current region entry point, recent stations, and the station list for that region.
2. Tapping a station starts Live playback immediately.
3. The mini player confirms playback and opens the full player.
4. The full player is the station context, with Live controls primary and Timefree available for the same station.
5. Home does not show an outer Live/Timefree category and does not show a persistent region chip rail.

This keeps the quick radio behavior while moving deeper choices into the context where they matter.

## Home

Home should be reduced to a focused station launcher.

Required Home elements:

- App/title area with the selected region shown as a compact pill, for example `Tokyo`.
- Recent stations rail, if recent stations are available.
- Station list for the selected region.
- Loading/error states for station data.
- Existing mini player area when playback is active.

Home should not include:

- Persistent horizontal region chip rail.
- Inline Timefree program list.
- Top-level Live/Timefree segmented control.
- Broad nested card shells around the entire page.

The selected region pill opens the region bottom sheet. The station rows remain the primary tap targets and should keep clear play affordances. Long station names and subtitles must truncate cleanly.

## Region Bottom Sheet

Region selection should move out of the Home content flow.

Behavior:

- Tapping the current region pill opens a bottom sheet.
- The sheet lists all regions as large tap targets.
- Selecting a region updates the selected region and dismisses the sheet.
- The Home station list then reflects the selected region.
- The selected station may remain as playback context if it is currently playing, but Home should visually prioritize the new region list.

Design:

- Use a sheet-style overlay with a drag handle and title such as `Choose region`.
- Use rows or a compact grid with minimum touch target sizes.
- Highlight the current region.
- Avoid full-screen navigation unless the region list later needs search or grouping.

## Station Tap Behavior

Station selection should be direct:

- Tapping a station row starts Live playback for that station using the existing `onPlayLive(station)` path.
- The selected station is updated as today.
- The mini player appears or updates.
- The full player can be opened from the mini player by tap or upward drag.

Do not introduce a required station detail page before playback. The user explicitly chose direct Live playback, with Timefree inside the listening/player context.

## Mini Player

The mini player stays compact and playback-focused.

Responsibilities:

- Show current station/program/title metadata.
- Provide play/pause and Timefree skip controls when applicable.
- Open the full player on tap/drag.

Non-goals:

- Do not add the Timefree program list to the mini player.
- Do not add region selection to the mini player.

## Full Player

The full player becomes the station-scoped detail surface.

Required structure:

- Existing full-player hero and playback controls remain primary.
- Live state remains the default when a station is started from Home.
- Timefree programs for the current station are exposed inside the full player, using a tab, segmented control, or clear section.
- Timefree selection calls the existing `onPlayTimefree(station, program)` callback.
- Returning to Live calls the existing `onPlayLive(station)` callback.

The full player should not require users to go back to Home to find Timefree for the station they are currently listening to.

## State And Data Boundaries

Keep the implementation in the UI layer.

Allowed UI state:

- Whether the region bottom sheet is open.
- Which full-player subview/section is active, if a Live/Timefree tab is used.
- Presentation-only sorting/filtering already present in UI.

Not allowed:

- Moving playback decision logic into composables.
- Changing playback service behavior.
- Changing Android Auto media browsing behavior.
- Changing radiko API/data/domain models for this UI-only refactor.

## Files Expected To Change

Likely files:

- `app/src/main/java/dev/yar/android/ui/BrowserScreen.kt`
- `app/src/main/java/dev/yar/android/ui/YarApp.kt`
- `app/src/main/java/dev/yar/android/ui/PlayerSurfaces.kt`

Only add helper files if the current UI files become too large or if a local pattern clearly supports extraction. Do not change `data`, `domain`, or `playback` packages unless compilation proves a small type/interface adjustment is unavoidable.

## Accessibility And Small-Screen Requirements

- Region and station tap targets should be comfortable on small phones.
- Long Japanese names must not push icons or buttons off-screen.
- The region bottom sheet should dismiss predictably after selection.
- Full-player Timefree controls must remain reachable with vertical scrolling.
- Existing mini player tap and drag-to-open interactions must continue working.

## Verification

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
```

Manual review, if an emulator or device is available:

- Home no longer shows persistent region chips or inline Timefree list.
- Region pill opens the bottom sheet and selecting a region changes stations.
- Tapping a station starts Live playback directly.
- Mini player still opens the full player by tap and upward drag.
- Full player exposes Timefree for the current station.
- Selecting a Timefree program starts Timefree playback.
- Returning to Live works from the full player.
- Long station/program titles truncate cleanly on a small-screen viewport.

Document any untested playback areas. Do not claim notification, lock screen, background playback, or Android Auto behavior was manually tested unless those paths were actually exercised.
