# Project Instructions

This is the native Android client for Yar.

## Product Direction

- Use native Kotlin Android, not WebView audio, for playback.
- Prioritize stable background audio playback.
- Support Android Auto through Media3 `MediaLibraryService`.
- Use Jetpack Compose for phone UI.
- Use Jetpack Media3 / ExoPlayer for live and timefree HLS playback.
- Keep this repository separate from the Yar Web app repository.

## Implementation Rules

- Keep player logic in the native playback layer, not in UI composables.
- Design Android Auto media browsing at the same time as phone playback.
- Do not assume the phone UI is available in Android Auto. Android Auto consumes media tree, metadata, and playback commands.
- Keep cross-platform behavior notes in `docs/features/*.md` when adding features that Web should later match.
- Prefer small, focused changes.
- Use English for code comments.

## Architecture Targets

- `data`: radiko API, auth, XML parsing, playlist URL resolution.
- `domain`: station, region, program, and playback models.
- `playback`: Media3 player, media session, foreground service, Android Auto media tree.
- `ui`: Compose screens and phone-only interactions.

## Verification Expectations

- For frontend/UI changes, build the Android app if Gradle dependencies are available.
- For playback changes, test notification controls, lock screen controls, and background playback.
- For Android Auto changes, test with Desktop Head Unit when possible and document any untested areas.
