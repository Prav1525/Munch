# Munch

Munch is a general-purpose personal assistant for Android with a dark, minimal interface.

## Current integrated prototype

Implemented in the current development branch:
- Kotlin + Jetpack Compose assistant home screen
- User-initiated speech input using Android speech recognition
- Spoken answers using Android TextToSpeech
- Online Gemini answers using a user-supplied API key in Settings
- On-device conversation history and clear-history control
- Shortcut to open a compatible music app
- Google Maps navigation for typed destinations
- Reminders scheduled through Android alarms and notifications

Known limitations:
- “Hey Munch” hands-free wake-word activation is not implemented yet.
- Gemini requires internet and a valid API key. The key is stored in app-private preferences for this personal prototype; a public production release should use a secured backend.
- Music shortcut opens the music app; reliable direct play/pause/skip media-session controls are not implemented yet.
- Exact reminders depend on exact-alarm and notification permissions.
- Physical-device testing is still required for microphone, Bluetooth headset routing, reminder notifications, and all driving/noise scenarios.

## Build and test

Requirements: JDK 17, Android SDK platform 35, and Gradle 8.9.

```bash
gradle --no-daemon testDebugUnitTest assembleDebug
```

Expected APK path after a successful build:

`app/build/outputs/apk/debug/app-debug.apk`

Do not distribute an APK until the latest source has passed tests and assembly. A successful CI build does not replace real-device testing.

See [PROJECT_CONTEXT.md](PROJECT_CONTEXT.md) for durable product decisions, actual status, and handoff rules.
