# Spotify-Like UI Refactor Design

Date: 2026-06-30
Status: Ready for user review

## Goal

Refactor Yar Android's phone UI into a mature, native audio-player experience inspired by Spotify's information hierarchy: a useful Home surface, a persistent mini player, and a focused full player. The work must preserve stable native playback and keep player logic in the playback layer, not in composables.

## Non-Goals

- Do not use WebView or browser audio.
- Do not change Media3 playback behavior, foreground service behavior, notification controls, or Android Auto media browsing.
- Do not rebuild app navigation into a large multi-tab architecture in this phase.
- Do not add new playback features such as sleep timer, queue management, downloads, or account state.
- Do not copy Spotify branding, assets, or exact visual identity.

## Current Context

The current UI already supports station browsing, recent stations, live playback, timefree playback, a mini player, a full player overlay, song history, and program details. Its main weakness is presentation: large nested cards, glass styling, text-based controls, and equal visual weight for browse, metadata, and playback make the app feel prototype-like.

The Web app provides useful behavior references for recent stations, current program metadata, player progress, and timefree controls. Android should borrow those product behaviors where already present, but the phone UI should be native Compose and optimized for a mobile audio player.

## Recommended Direction

Use the selected Direction A: Spotify-like Home plus Mini Player.

The default screen should emphasize the path users take most often:

1. Reopen a recent station.
2. Start live playback from the current or remembered region.
3. Open the full player to control playback and inspect current program/song details.
4. Access timefree programs after selecting a station, without making timefree dominate the initial screen.

## Architecture

The refactor is scoped to `app/src/main/java/dev/yar/android/ui`.

Expected files:

- `YarTheme.kt`: update color tokens, shape scale, and app background.
- `UiKit.kt`: replace glass-heavy primitives with flatter player/list primitives and icon-first controls.
- `BrowserScreen.kt`: reshape the browse surface into a Home-style station experience.
- `PlayerSurfaces.kt`: redesign the mini player and full player overlay.
- `YarApp.kt`: keep existing state and callbacks; only adjust composition and layout wiring when needed.

The following should remain stable:

- `YarMediaLibraryService` and Media3 playback commands.
- Radiko data clients and XML parsing.
- Domain models.
- Android Auto media tree.

## Home Surface

The Home surface replaces the current large "Radio library" card as the first-viewport experience.

Structure:

- Top bar with `Yar`, selected region context, and optional current playback status.
- `Recently played` horizontal rail using station artwork/logo tiles.
- Compact region selector, preferably horizontal chips or a compact list row, with selected region clearly marked.
- Main station list for the selected region using dense rows: logo, station name, secondary name/id, and an icon play action.
- Timefree entry point for the selected station, shown as a secondary section rather than as an equal top-level mobile tab.

Mobile behavior:

- Default to the selected region's station list after regions load.
- Recent stations should be visible above the fold when available.
- If no recent stations exist, the selected region list should move up without leaving an empty gap.
- Timefree remains available after station selection through a compact section or sheet within the Home flow.

Wide behavior:

- Use available width for a richer browse layout, but preserve the same hierarchy: recent first, selected region stations next, timefree secondary.
- Avoid the current feeling of three equal panes competing for attention.

## Mini Player

The mini player becomes a persistent mature playback bar.

Structure:

- Left: square artwork or station logo with stable dimensions.
- Center: title/program name on the first line; station or performer on the second line.
- Progress: thin bottom progress track for timefree. For live, use a subtle status/accent strip rather than a seek-looking bar unless live program progress is available.
- Right: icon play/pause button; timefree skip buttons may be compact icons if space allows.

Behavior:

- Tap opens the full player.
- Upward drag continues to open the full player.
- Loading, buffering, seeking, and switching states lock affected controls and show understated progress.
- Playback errors remain visible but should not expand the bar dramatically.

## Full Player

The full player should put playback first and supporting information second.

Top:

- Drag handle.
- Station context: "Playing from" plus station logo/name.
- Close affordance as an icon/text action with enough hit target.

Core:

- Large square artwork with stable aspect ratio.
- Status chip: Live or Timefree.
- Program title and performer.
- Timefree progress slider and elapsed/duration labels.
- Centered controls: skip back, play/pause, skip forward for timefree; play/pause for live.
- Loading and buffering messaging near the controls, not at the top of the screen.

Supporting sections below core:

- Current song.
- Song history.
- Station program switch: back to live or recent timefree choices.
- Program description.
- Same-day timetable when relevant.

The supporting sections should use flatter list surfaces and small section headings. They should not appear as nested card stacks inside a large card.

## Visual System

Use a mature dark neutral theme:

- Background: near-black neutral with minimal gradient or no gradient.
- Surfaces: dark grays with subtle elevation and clear borders where needed.
- Primary accent: Spotify-like green for active playback controls.
- Live accent: cyan or green depending on final palette; use consistently for live.
- Timefree accent: red/pink, matching the Web app's destructive/timefree language.
- Text: high-contrast primary, muted secondary.

Shape scale:

- Most cards/lists: 8-12dp radius.
- Artwork: 8-12dp radius.
- Pills/chips and circular icon buttons can remain fully rounded.
- Avoid large 26-34dp rounded cards for broad layout containers.

Control language:

- Use icon-style controls for play, pause, skip back, skip forward, close, and expand/collapse.
- Avoid text controls such as pause bars, play triangles, `-30`, and `+30` except as accessibility labels or small secondary annotations.
- Maintain minimum tap targets for phone ergonomics.

## Data And State Flow

The UI should continue receiving data through the existing `PlaybackUiState` and `BrowserUiState` patterns. Any additions should be presentation-focused, such as selected/active labels or derived display text.

Playback actions remain routed through existing callbacks:

- `playLive`
- `playTimefree`
- `pauseResume`
- `seekTimefree`
- `skipTimefree`

The refactor should not introduce direct player/controller logic inside visual composables.

## Error And Loading States

Station loading:

- Show compact skeleton-like rows or existing empty/loading states restyled to fit the new surface.
- Keep error text readable and specific.

Playback loading:

- Mini player: show a small progress indicator or disabled primary control.
- Full player: show a clear loading/buffering/seeking state near controls.

Switching playback:

- Keep the existing switching target semantics.
- Disable conflicting station/program rows while switching.
- Preserve timeout error behavior.

## Accessibility

- Icon controls must have clear content descriptions.
- Text must not overflow buttons or fixed controls.
- Station and program rows should remain readable with long Japanese titles.
- Progress controls must remain usable with touch and should preserve existing seek behavior.
- Color should not be the only indicator for Live/Timefree; labels should remain present.

## Android Auto

No Android Auto implementation changes are planned in this UI phase. The phone UI can change independently because Android Auto consumes the media tree, metadata, and playback commands through `MediaLibraryService`.

After implementation, document any phone UI behavior that Web should later match only if the refactor changes cross-platform product behavior. Pure visual polish does not need a `docs/features` note.

## Testing And Verification

Before implementation, add tests where the project can support them without over-scoping the UI refactor.

Expected verification:

- Compile with `gradlew.bat :app:assembleDebug` when Android SDK and Gradle dependencies are available.
- Manually inspect the Compose UI on at least one phone-sized viewport/device or emulator if available.
- For playback-adjacent changes, test that mini player actions still call pause/resume, skip, seek, and open full player correctly.
- Confirm background playback, notification controls, lock screen controls, and Android Auto media tree are not modified by this phase.

## Implementation Boundaries

The first implementation plan should be small and focused:

1. Theme and UI primitives.
2. Home/browse surface.
3. Mini player.
4. Full player.
5. Compile verification and manual UI review.

If a step exposes playback bugs or Android Auto regressions, stop and handle them as separate playback work rather than folding them into the visual refactor.
