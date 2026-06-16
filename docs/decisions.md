# Decisions

## 2026-06-04: Use A Separate Native Android Repository

Decision:
- Create a separate Android repository from the Web app.
- Do not convert the existing Next.js repository into a monorepo for now.

Reason:
- Android uses Gradle, Kotlin, Android Studio, app signing, and Play Store release flows.
- The Web app uses Next.js, TypeScript, hls.js, and Cloudflare/OpenNext deployment.
- Keeping repositories separate reduces coupling and keeps Android media architecture clean.

## 2026-06-04: Use Native Kotlin For Playback

Decision:
- Build the Android app in native Kotlin.
- Use Media3 / ExoPlayer for playback.
- Use `MediaLibraryService` rather than a simple WebView wrapper.

Reason:
- The Web app can be killed in the background by browser and Android memory policies.
- Native media playback can run as a foreground media service with system-recognized media controls.
- Android Auto requires native media session/library integration.

## 2026-06-04: Treat Android Auto As A First-Class Constraint

Decision:
- Start with `MediaLibraryService` and a browsable media tree.
- The app should expose stations, recent stations, and playable media items to Android Auto.

Reason:
- Android Auto does not render the phone app UI for media apps.
- Car UI is generated from media browser APIs, metadata, and playback state.
