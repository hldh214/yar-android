# Yar Android

Native Android client for Yar, a third-party radiko player.

Yar Android is built as a native Kotlin app, not a WebView wrapper. It focuses on stable background audio playback, system media controls, and a phone-first Compose UI for live and timefree listening.

## Features

- Native radiko station browsing grouped by region.
- Live HLS playback through Jetpack Media3 / ExoPlayer.
- Timefree playback for recent programs, with seek and 30-second skip controls.
- Foreground media playback service with notification and lock-screen controls.
- Recent stations persisted on device.
- Current program artwork, metadata, and radiko Music song history when available.
- Media3 `MediaLibraryService` foundation for Android Auto media browsing and playback.

## Requirements

- Android Studio or Android SDK command line tools.
- JDK 17.
- Android SDK 36 for compilation.
- Android 8.0+ device or emulator (`minSdk 26`).

## Build

On macOS/Linux:

```sh
./gradlew :app:assembleDebug
```

On Windows:

```bat
gradlew.bat :app:assembleDebug
```

Debug APK output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Release

Current app version: `1.0.0`.

Release APKs are built by GitHub Actions when a `v*` tag is pushed or when a GitHub Release is published. The workflow attaches a signed APK named `yar-android-<tag>.apk` to the release.

Local release builds are unsigned unless the same signing values are provided through environment variables.

Required repository secrets for release signing:

- `ANDROID_SIGNING_KEYSTORE_BASE64`: Base64-encoded Android keystore file.
- `ANDROID_SIGNING_STORE_PASSWORD`: Keystore password.
- `ANDROID_SIGNING_KEY_ALIAS`: Key alias.
- `ANDROID_SIGNING_KEY_PASSWORD`: Key password.

Recommended release flow:

1. Update `versionCode` and `versionName` in `app/build.gradle.kts`.
2. Build locally with `./gradlew :app:assembleDebug` or `gradlew.bat :app:assembleDebug`.
3. Tag the release, for example `v1.0.0`.
4. Push the tag to GitHub.
5. Confirm the `Android APK` workflow creates or updates the release asset.

## Project Structure

- `app/src/main/java/dev/yar/android/data`: radiko API, auth, XML parsing, stream resolution, recent station storage.
- `app/src/main/java/dev/yar/android/domain`: station, region, program, song, and playback request models.
- `app/src/main/java/dev/yar/android/playback`: Media3 player, media session, foreground service, media tree.
- `app/src/main/java/dev/yar/android/ui`: Compose phone UI, theme, browser, and player surfaces.
- `docs/features`: feature notes for behavior that the Web app may later mirror.

## Notes

- This repository is intentionally separate from the Yar Web app.
- Android Auto support is present at the media service/tree layer, but phone UI polish is the current product focus.
- The app is a third-party radiko client and is not affiliated with radiko.
