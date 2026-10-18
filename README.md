# Munch

Munch begins as a motorcycle/bike assistant and is designed to grow into a general-purpose assistant through separate, modular capabilities.

## Current milestone: Android app shell

The first scaffold includes:
- Kotlin + Jetpack Compose Android app
- Initial dark-themed bike-assistant home screen
- Explicit roadmap phases: bike assistant, smarter riding assistant, general assistant
- Unit tests for the roadmap model
- GitHub Actions workflow intended to compile, run unit tests, assemble a debug APK, and upload it as a workflow artifact

**Important:** this is an early UI scaffold, not yet a functioning voice assistant. Microphone access, wake-word detection, music controls, Google Maps intents, and Bluetooth headset handling are not connected yet.

## Build and test

Requirements: JDK 17, Android SDK platform 35, and Gradle 8.9.

```bash
gradle --no-daemon testDebugUnitTest assembleDebug
```

Expected debug APK path after a successful build:

`app/build/outputs/apk/debug/app-debug.apk`

Do not distribute an APK until the build and tests have actually passed. Real-device testing is still required for microphone, Bluetooth audio, wake-word performance, and noisy riding conditions.

See [PROJECT_CONTEXT.md](PROJECT_CONTEXT.md) for the durable product plan and handoff rules.
