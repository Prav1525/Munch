# Munch — Persistent Project Instructions

## Project boundaries
- Munch is a standalone project, separate from Project Samantha. Never mix their code, decisions, issues, or release artifacts.
- Repository: https://github.com/Prav1525/Munch
- Default branch: main.

## Product vision and confirmed release choices
- Munch is a general-purpose personal assistant first, with a futuristic, minimal, personal voice-first feel.
- Keep the existing dark near-black interface, violet/cyan accents, orb, and modular shortcut layout.
- The user approved an integrated release containing voice conversation, online AI with offline native actions where possible, music launch, Google Maps navigation, reminders/daily planning, conversation history and settings.
- AI choice: Google Gemini, configured by the user at runtime. Never commit a key or bake one into source/APK.
- User prioritizes hands-free “Hey Munch” activation. This remains a known blocker for this build: robust local wake-word detection needs a vetted on-device keyword-spotting model and a visible, permissioned foreground microphone service. Android speech recognition alone is not a reliable privacy-preserving wake-word engine. Do not claim wake-word support until implemented and tested on-device.

## Design direction
- Dark, near-black surfaces with restrained violet/cyan light accents, subtle borders, generous spacing, and a distinctive assistant orb.
- General everyday assistant language; do not label the home screen as “bike assistant”.
- Keep quick actions modular and honest about what is implemented versus not yet connected.
- Avoid false “listening/online” indicators.

## Technical direction
- Android app using Kotlin and Jetpack Compose; min SDK 26, target SDK 35, Java 17.
- User-initiated Android speech recognition and Android TextToSpeech.
- Gemini REST requests are made online using a user-provided key stored in app-private preferences. This is acceptable only for a personal prototype; a production public app should use a secured backend because keys in client apps can be extracted.
- Native music shortcut opens a compatible installed music app, with a web fallback; it does not yet implement reliable in-app media-session controls.
- Destination shortcut opens Google Maps navigation, with a web fallback.
- Reminders use AlarmManager and notification receiver. Exact alarms and notifications depend on user/device permissions and Android settings.
- Conversation history is stored locally on device.
- Offline capability currently means native actions and local history; Gemini answers require internet and a configured key.

## Non-negotiable build, test, and APK rules
- The user explicitly requires compiling and testing before an APK is delivered.
- Inspect the current repository and this file before making changes.
- For meaningful changes, compile/build and run available tests; fix failures and rerun.
- Never claim build, test, installation, or device-test success unless actually verified.
- Only deliver an APK after a successful build. State which checks passed and which were not performed.
- Emulator/build checks do not replace real-device tests for microphone input, OnePlus Bluetooth audio, reminders/notifications, wake-word performance, and motorcycle noise.
- If the available environment cannot build or test, explain the blocker clearly and do not call the app verified or fabricate an APK.

## Current implementation status (2026-10-18)
- Latest UI redesign passed GitHub Actions run https://github.com/Prav1525/Munch/actions/runs/39101342194.
- Added native service helpers for Gemini requests, local conversation history, Maps navigation, music-app launch, and reminders.
- Connected the home-screen shortcuts and conversation panel; added speech-to-text launcher, TextToSpeech responses, Gemini API key settings, history clearing, destination entry, and reminder scheduling UI.
- Added internet, microphone, notification and exact-alarm permissions and the reminder broadcast receiver.
- Latest code is being compiled by GitHub Actions. Early commits in this multi-file change can fail because a file was not committed yet; verify the final latest-commit workflow, not an intermediate run.
- Not yet implemented: a robust hands-free wake phrase, secure server-side AI proxy, full media-session controls, or a real-device test. No APK may be delivered until the final commit's tests and assembleDebug succeed.

## Handoff checklist
1. Read this file and inspect the latest source before editing.
2. Build integrated features while preserving the home-screen design.
3. Run GitHub Actions unit tests and debug APK assembly; fix failures and rerun.
4. Update this file with exact outcomes and the next step before ending.
5. Deliver a new APK only after the latest commit's build/tests pass; separately state that physical-device tests remain outstanding.
