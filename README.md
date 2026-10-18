# Munch

Munch is a general-purpose personal assistant for Android with a dark, minimal interface.

## Current integrated prototype

Implemented in the current development branch:
- Kotlin + Jetpack Compose assistant home screen
- User-initiated speech input using Android speech recognition
- Spoken answers using Android TextToSpeech
- Free offline responses for greetings, time/date, help, and common questions
- Local fallback for unsupported open-ended questions without calling a cloud AI
- On-device conversation history and clear-history control
- Shortcut to open a compatible music app
- Google Maps navigation for typed destinations
- Reminders scheduled through Android alarms and notifications

Known limitations:
- “Hey Munch” hands-free wake-word activation is not implemented yet.
- Open-ended generative AI is intentionally not enabled in this free offline build. The local responder is deterministic and limited; it is not a full language model. Maps handoff, speech recognition, and music services may still depend on installed apps or connectivity.
- Music shortcut opens the music app; reliable direct play/pause/skip media-session controls are not implemented yet.
- Exact reminders depend on exact-alarm and notification permissions.
- Physical-device testing is still required for microphone, Bluetooth headset routing, reminder notifications, and all driving/noise scenarios.

## Offline mode

Munch does not call Gemini or require an API key for conversations. Common replies and native command routing work locally. It does not yet bundle a full on-device language model, so arbitrary questions receive an honest offline-limit response. Maps and Android speech recognition may use network services depending on the device and settings.

## Build and test

Requirements: JDK 17, Android SDK platform 35, and Gradle 8.9.

```bash
gradle --no-daemon testDebugUnitTest assembleDebug
```

Expected APK path after a successful build:

`app/build/outputs/apk/debug/app-debug.apk`

Do not distribute an APK until the latest source has passed tests and assembly. A successful CI build does not replace real-device testing.

See [PROJECT_CONTEXT.md](PROJECT_CONTEXT.md) for durable product decisions, actual status, and handoff rules.
