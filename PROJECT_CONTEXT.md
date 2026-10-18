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
- Verified GitHub Actions run for app code commit `dc32631a3551e52a24fcdfcae863fde737d3fd6` succeeded: https://github.com/Prav1525/Munch/actions/runs/39102303673. The workflow completed `gradle --no-daemon testDebugUnitTest assembleDebug` and uploaded the `munch-debug-apk` artifact.
- The artifact ZIP was downloaded and inspected; it contains `app-debug.apk` (9,501,617 bytes). SHA-256: `ac0a6294c65ae2088808335489400134d54eedb0c151b44b73106289ac30c093`.
- Not yet implemented: a robust hands-free wake phrase, secure server-side AI proxy, full media-session controls, or real-device testing. CI validates unit tests and APK assembly, not physical-device behavior.

## Handoff checklist
1. Read this file and inspect the latest source before editing.
2. Build integrated features while preserving the home-screen design.
3. Run GitHub Actions unit tests and debug APK assembly; fix failures and rerun.
4. Update this file with exact outcomes and the next step before ending.
5. Deliver a new APK only after the latest commit's build/tests pass; separately state that physical-device tests remain outstanding.

## Latest verification outcome (2026-10-18)
- Android unit tests: passed in GitHub Actions run 39102303673.
- Debug APK assembly: passed in the same run.
- Artifact upload: passed; artifact was downloaded and ZIP contents and APK file size were checked locally.
- Real-device microphone, Gemini API call, Bluetooth headset, reminder notification, Google Maps, and wake-phrase tests have not been performed.
- Next work: implement/test a privacy-conscious on-device wake phrase strategy and practical media-session controls; consider moving Gemini requests behind a secure backend before any public distribution.


## Offline-first update (2026-10-18)
- User chose a free/offline-first assistant and asked to remove dependency on Gemini API calls.
- Added `OfflineAssistant.kt` with deterministic local replies for greetings, time/date, help, identity, thanks, and a joke; unknown prompts receive a concise, honest offline-limit response.
- Updated `MainActivity.kt` so general conversation prompts use the local responder and speak the response. Existing local command routing for music launch, Maps handoff, and reminders remains in place. Removed the Gemini API key field from Settings and replaced it with an offline-mode explanation.
- Added `OfflineAssistantTest.kt` unit tests for greetings, help, time responses, and honest handling of unsupported questions.
- Important limitation: this change does NOT bundle a full local language model. General free-form AI quality is limited. Android Gemini Nano/ML Kit GenAI is free of per-request cloud inference costs but only works on supported devices and may require model setup/download; it is not assumed to work on every phone. A future model integration must check runtime availability and preserve deterministic offline fallback.
- Maps navigation and Android speech recognition can still rely on installed apps, device speech services, or internet depending on device/settings. Do not promise every feature works without internet.
- Build and tests must be confirmed from the latest GitHub Actions run before delivering an APK.


## External app launcher update (2026-10-18)
- Added explicit Android intent routing for common spoken/typed commands: open WhatsApp, YouTube, browser/Chrome, camera, phone dialer, Messages, Calendar, Settings, Contacts, email, and Clock/alarms.
- Existing music and Google Maps handoffs remain in place. This launches the target app or system handler; it does not grant Munch unrestricted access to read/control third-party app data. Sending messages, making calls, or controlling media still needs specific safe intent integrations and user confirmation where appropriate.
- Changes committed to main: `2a9aafff3a19812882fb1ba0956edfcd8f8f569b` (external app helper) and `6817f695ea50126d9839ded0fb157e8c66fd02f1` (command routing).
- CI build/tests for these latest commits are not yet verified; do not deliver an APK until Actions passes.
- Latency note: the checked-in main branch's `OfflineAssistant.kt` is a deterministic rule-based responder and the README says the GGUF model integration is not included in this source snapshot. Therefore, do not attribute the user's observed 2–3 second delay to local LLM inference until the actual model-enabled source/build is reconciled. Speech recognition service startup and TTS startup may contribute to perceived delay. Next step is to inspect the model-enabled source and profile timing on-device before claiming a speed improvement.

## Hybrid local-first AI update (2026-10-18)
- Added optional Groq chat-completions fallback using model `openai/gpt-oss-20b`, with a short reply prompt, a 5-second connect timeout and 10-second read timeout. HTTP failures return to the offline fallback rather than leaving the UI busy indefinitely.
- Added Groq API key entry and an explicit “Allow online fallback” switch in Settings. Online fallback defaults OFF; users must supply their own key and enable it. The key is stored in app-private SharedPreferences, which is suitable only for a personal prototype and is not a secure secret store against a rooted/compromised device or APK reverse engineering.
- Request routing remains local-first: Maps/music/reminder/app-launch commands are handled directly, and OfflineAssistant handles known simple questions. Only the generic offline “can't answer fully” fallback triggers Groq when enabled. Groq is not called for known local responses.
- Important limitation: the currently checked-in `main` source does **not** include a verified GGUF inference engine. Thus this commit implements offline-rule-based-first + optional Groq fallback, not a confirmed local-LLM-first stack. Before claiming GGUF support, restore/integrate the native inference code and verify model loading on a device.
- Build/test status for the Groq integration: pending GitHub Actions verification; do not distribute an APK until `testDebugUnitTest assembleDebug` passes and the artifact is inspected.


## Hey Munch + app/music commands update (2026-10-18)
- Added an opt-in Android foreground microphone service for the phrase “Hey Munch”, with an ongoing notification, explicit microphone permission flow, and an attempt to prefer Android's on-device speech recognizer when available.
- Wake-word listening uses Android general speech recognition, not a dedicated always-on keyword-spotting model. Availability, offline behavior, background restrictions, battery optimization, and recognition accuracy vary by device; this requires real-device testing and must not be advertised as guaranteed offline.
- Wake phrase followed by a command forwards the command to the same local command router. A bare wake phrase opens voice input.
- Added generic launcher-app lookup by visible app label (subject to Android package visibility) and music search for “play [song/artist]”. Music search opens a compatible YouTube Music/browser search; actual autoplay and media-session controls depend on the installed music app and are not guaranteed.
- Direct native commands still run before AI. Groq remains the online answer provider for open-ended prompts when configured and enabled; on provider failure, the deterministic offline responder is used.
- Changes committed across latest main commits d12d4c3, 2428417, 9de4c8e, 56b665f, and e1e0038. GitHub Actions build/test runs are in progress; do not distribute an APK until the latest source's tests and assembly pass and its artifact is inspected.
