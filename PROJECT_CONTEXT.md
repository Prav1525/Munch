# Munch — Persistent Project Instructions

## Project boundaries
- Munch is a standalone project, separate from Project Samantha. Never mix their code, decisions, issues, or release artifacts.
- Repository: https://github.com/Prav1525/Munch
- Default branch: main.

## Product vision
Munch is a **general-purpose personal assistant first**. It should feel futuristic, minimal, personal, and voice-first. Riding safety is an important use case, but the app must not be presented as only a bike assistant. Music, navigation, reminders, daily planning, and device-specific functions are optional modular capabilities that should not define the whole product.

## Product roadmap
1. **Personal assistant foundation:** polished conversation experience, user-initiated voice input, clear assistant states, and a flexible app shell.
2. **Connected capabilities:** optional music controls, navigation, reminders, and device integrations.
3. **Smarter assistant:** better context and personalization, practical offline features, and background behavior within Android limits.

These are plans, not claims that features already exist. Keep the interface honest about what's implemented versus planned.

## Design direction
- Futuristic, minimal, personal aesthetic.
- Dark, near-black surfaces with restrained violet/cyan light accents, subtle borders, generous spacing, and a distinctive assistant orb.
- General everyday assistant language; do not label the home screen as “bike assistant”.
- Keep quick actions modular and clearly mark capabilities that are not connected yet.
- Avoid clutter, overdone sci-fi decoration, and false “listening/online” indicators.

## Agreed technical direction
- Android app using Kotlin and Jetpack Compose.
- Start with user-initiated speech recognition; only investigate wake phrase “Munch” after the basic voice flow is stable.
- Support offline voice commands where practical.
- Music controls through compatible apps: play, pause, skip, and search/request music.
- Voice-based destination requests that open Google Maps navigation.
- Support the user's OnePlus Bluetooth neckband for microphone and audio when connected.
- Investigate a foreground service for background listening only when justified, respecting Android restrictions, permissions, battery use, and privacy.
- Calls and broader integrations should be separate capabilities, not hardwired into the home screen.

## Non-negotiable build, test, and APK rules
- The user explicitly requires compiling and testing before an APK is delivered.
- Inspect the current repository and this file before making changes.
- For meaningful changes, compile/build and run available tests; fix failures and rerun.
- Never claim build, test, installation, or device-test success unless actually verified.
- Only deliver an APK after a successful build. State which checks passed and which were not performed.
- Emulator/build checks do not replace real-device tests for microphone input, OnePlus Bluetooth audio, wake-word performance, and motorcycle noise.
- If the available environment cannot build or test, explain the blocker clearly and do not call the app verified or fabricate an APK.

## Persistent continuity
- Keep this file updated with confirmed decisions, code changes, test results, blockers, and the next step.
- Do not ask the user to repeat decisions already recorded here.
- Separate confirmed facts from plans and untested features.

## Current status (2026-10-18)
- User installed the initial debug APK and reported that it works, but described the UI as too simple.
- Initial scaffold CI run passed: https://github.com/Prav1525/Munch/actions/runs/39100218318. It ran `gradle --no-daemon testDebugUnitTest assembleDebug` and uploaded the debug APK. This verifies the initial scaffold only, not the latest redesign.
- The user clarified the product should look futuristic, minimal, and personal, and should be a general assistant rather than a bike-only assistant.
- Latest changes committed to `main`: redesigned `MainActivity.kt` with dark personal-assistant UI, gradient orb, conversation entry point and modular shortcut cards; updated `AssistantRoadmap.kt` to make the personal assistant foundation the first phase; updated roadmap unit tests.
- The redesign's CI build/test status must be checked before delivering another APK. The shortcut cards and conversation button are visual preview entry points only; microphone, voice chat, AI answers, music, navigation, and reminders are not connected yet.
- Previous debug APK installation was confirmed by the user. Microphone, Bluetooth, and actual assistant capability tests have not been performed.

## Handoff checklist
1. Read this file and inspect the latest source before editing.
2. Make one testable milestone at a time.
3. Run GitHub Actions build, unit tests, and debug APK assembly; fix and rerun if any check fails.
4. Update this file with exact outcomes and the next step before ending.
5. Deliver a new APK only after the latest commit's build/tests pass.

## Next step
Check CI for the latest redesign commits. If green, provide the new debug APK and ask the user to confirm the new look. Then implement the first functional conversation milestone: microphone permission plus a user-initiated speech-to-text flow with visible listening/result/error states. Do not claim AI conversation until an answer engine is integrated.
