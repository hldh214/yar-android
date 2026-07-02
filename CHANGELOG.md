# Changelog

All notable changes are documented from the local git history and repository files.

## v1.1.0 - 2026-07-01

- Reworked the phone UI into a station-first home surface.
- Added a compact region picker bottom sheet instead of persistent region chips on Home.
- Moved Timefree discovery into the full player for the currently selected or playing station.
- Added player-scoped Timefree date selection and cached Timefree schedules by station/date.
- Improved mini player and full player layout, drag behavior, loading states, and playback switching feedback.
- Fixed Timefree seek position display so the UI uses the Media3 content position without adding the seek offset twice.
- Added UI helper coverage for playback display behavior and Timefree schedule caching.

Notes:
- `v1.1.0` is the latest local tag.
- `app/build.gradle.kts` still reports `versionName = "1.0.1"` and `versionCode = 10001` at this tag.

## 1.0.1 - 2026-06-17

- Added signed release APK support through the GitHub Actions `Android APK` workflow.
- Added playback notification configuration with a Yar notification icon and launcher session activity.
- Added command filtering so live playback exposes play/pause while Timefree exposes seek and 30-second skip commands.
- Improved phone player surfaces with loading, buffering, seeking, and playback error states.
- Added support for opening the full player through mini player drag gestures.

Notes:
- This version exists in the Gradle app version history, but there is no local `v1.0.1` tag.

## v1.0.0 - 2026-06-16

- Initial native Android client for Yar.
- Added native radiko region and station browsing.
- Added Media3 / ExoPlayer live and Timefree HLS playback.
- Added foreground playback service, media session integration, notification and lock-screen control foundation.
- Added Android Auto media browsing foundation through `MediaLibraryService`.
- Added persisted recent stations and current program/song metadata display.
- Added project documentation, Android Auto testing notes, and release APK workflow.
