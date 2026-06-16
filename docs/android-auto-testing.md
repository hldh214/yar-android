# Android Auto Testing

Android Auto media apps are tested through media browser/session behavior, not by rendering the phone UI on the car screen.

## What To Test

- App appears in Android Auto as a media app.
- Root media tree loads.
- Recent stations and region/station browsing work.
- Play, pause, next, previous, and seek commands behave correctly.
- Metadata updates: station, program title, performer, artwork.
- Playback survives screen off, app backgrounding, and route changes.
- Notification controls and lock screen controls remain synchronized.

## Development Testing

Use Google's Desktop Head Unit when possible.

Typical flow:
- Install the debug APK on a real Android phone.
- Enable Android Auto developer mode on the phone.
- Start the Desktop Head Unit on the development machine.
- Connect the phone to DHU.
- Open Yar in Android Auto and browse/play media.

Real phones are preferred over emulators for background playback behavior because vendor power management and audio focus behavior are closer to real use.

## Final Testing

Test on an actual car or head unit before considering Android Auto support complete. Some issues only appear on real hardware, especially USB/Bluetooth transitions, voice assistant behavior, and connection stability.
