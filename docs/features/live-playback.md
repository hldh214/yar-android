# Live Playback

Android now resolves radiko live HLS streams natively and plays them through Media3/ExoPlayer.

Implemented behavior:
- Phone station rows start live playback through `YarMediaLibraryService`.
- Android Auto station media items use the same native playback path where Android Auto hosts are available.
- Radiko Android auth is performed natively with per-area token caching.
- HLS playlist and segment requests include `X-Radiko-AuthToken` through Media3 HTTP data sources.
- System media session metadata includes current program title, performer, station name, and artwork URL.
- Hardware/media key play and pause are handled by the Media3 session.
- Android 13+ shows an in-app prompt that lets the user request `POST_NOTIFICATIONS`, so Media3 playback notifications and lock-screen controls can be shown when allowed.
- Phone UI shows the current live program title after station playback starts.
- Phone UI exposes recently ended programs for the selected station and can start timefree playback.
- Phone UI can switch among the last 7 broadcast days for timefree program loading.
- Timefree playback resolves native radiko HLS URLs with `start_at`, `ft`, `seek`, `end_at`, and `to` parameters.
- Android Auto station nodes expose Live plus recent timefree programs as playable children.
- Phone UI includes live play/pause controls and timefree play/pause, seek, and 30-second skip controls.
- Playback control intents are handled by `YarMediaLibraryService` so phone UI and future surfaces use the same playback layer.
- Timefree skip re-requests the radiko playlist with a recalculated `seek` timestamp instead of relying on native HLS seeking.
- Recent stations are persisted with `SharedPreferences` after live or timefree playback.
- Phone UI and Android Auto `Recent Stations` both use the persisted recent station list.
- Phone UI connects to the Media3 session with `MediaController` so the displayed title and play/pause state come from the playback layer.
- Phone UI polls `MediaController` position and duration to show elapsed time and a finite progress bar when duration is available.

Parity notes:
- This matches the Web app's live playlist resolution flow conceptually.
- Live program progress is not implemented yet on Android.
- Long-running foreground playback has had short burn-in coverage, but should still receive longer real-device validation before broad release claims.
- Live skip-back/live seek-back is not implemented; live playback intentionally exposes play/pause only in the phone UI.
- Android Auto browse structure is implemented, but DHU and real-car verification remain release risks.
