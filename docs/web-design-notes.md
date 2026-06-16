# Web Design Notes For Android

Source repository: Yar Web app repository.

## Current Web Product

Yar is a third-party web frontend for radiko. The Web app is built with Next.js App Router, TypeScript, Tailwind CSS, and hls.js.

Core features to preserve conceptually:
- Nationwide station browsing grouped by region.
- Live streaming.
- Timefree playback for programs from the last 7 days.
- Seek, skip, and progress display for timefree playback.
- Live program progress based on program `ft` and `to` boundaries.
- Now-playing song display via radiko Music API.
- Recent/frequent stations.
- Remembered volume and region preference.

## Important Web Files

- `src/lib/radiko-auth.ts`: radiko auth flow.
- `src/lib/auth-key-data.ts`: Android auth key data used by the Web proxy.
- `src/lib/radiko-parser.ts`: XML parsing and station-to-area mapping.
- `src/lib/radiko-stream.ts`: playlist URL extraction from stream XML.
- `src/lib/player-context.tsx`: Web playback behavior, live/timefree state, retry, Media Session API.
- `src/components/StationList.tsx`: station browsing and recent stations behavior.
- `src/components/PlayerBar.tsx`: phone player UX, seek bar, skip behavior.
- `src/app/api/*`: Web proxy endpoints needed because radiko lacks browser CORS headers.

## Android Differences

- Android should not play through WebView or browser audio.
- Android can call radiko endpoints directly from native networking, subject to the same auth and request rules.
- HLS playback should use Media3/ExoPlayer rather than hls.js.
- Background playback should be handled by a foreground media playback service.
- System media controls should be handled through MediaSession/MediaLibraryService.

## Playback Behavior To Port

- Android timefree skip controls currently use 30 seconds.
- Timefree availability is limited to 7 days.
- For timefree seeking, the Web app re-requests the m3u8 playlist with a `seek` parameter because native HLS seeking does not work reliably for radiko timefree streams.
- Pausing timefree should preserve the logical playback position for resume.
- Live mode should expose current program title, performer, station logo, and artwork when available.
- Live seek-back is represented as timefree playback behind the live edge.
- Fatal stream failures should retry with exponential backoff.

## Android Auto Mapping

Suggested media tree:
- Root: Yar.
- Recent Stations.
- Regions.
- Region children: stations.
- Station playable item: live stream.
- Optional station child: schedule/timefree programs, if browsing depth remains usable in car UI.

Android Auto metadata should include:
- Station name.
- Program title.
- Performer/personality when available.
- Station logo or program artwork.
- Live or timefree playback state.

## Cross-Repository Sync

When Android implements a feature first, add a feature note in this repository under `docs/features/*.md` before asking Web to match it.

Example prompt from Web repository:

```text
Reference `docs/features/sleep-timer.md` and implement matching behavior in the Web app.
```
