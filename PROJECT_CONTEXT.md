# Munch — Persistent Project Instructions

## Project boundaries
- Munch is a standalone project, separate from Project Samantha. Never mix their code, decisions, issues, or release artifacts.
- Repository: https://github.com/Prav1525/Munch
- Default branch: main.

## Product vision
Munch is an Android voice assistant designed for motorcycle riding, with hands-free interaction as a core goal.

## Agreed initial technical direction
- Android app using Kotlin and Jetpack Compose.
- Explore a wake phrase: “Munch”.
- Support offline voice commands where practical.
- Music controls through compatible music apps: play, pause, skip, and search/request music.
- Voice-based destination requests that open Google Maps navigation.
- Use the user's OnePlus Bluetooth neckband for microphone and audio when connected.
- Investigate a foreground service for background listening, respecting Android platform restrictions, permissions, battery use, and privacy.
- Calls and broader general-assistant capabilities are future modules, not claims about existing functionality.

These are agreed goals, not implemented or verified features. Confirm technical feasibility as implementation proceeds; explain platform limits instead of promising unsupported always-on behavior.

## Non-negotiable build, test, and APK rules
- The user explicitly does not want repeated coding mistakes or an APK delivered without verification.
- Inspect the current repository, history, and this file before making changes.
- For every meaningful change, compile/build and run the available tests; fix failures and rerun the relevant checks.
- Never claim build, test, installation, or device-test success unless actually verified.
- Only deliver an APK after a successful build. State the exact checks that passed and which checks were not performed.
- If the available environment cannot build or test, explain the blocker clearly and do not call the app verified or fabricate an APK.
- Emulator/build checks do not replace real-device tests for microphone input, OnePlus Bluetooth audio, wake-word performance, and motorcycle noise.
- Track Android permissions, background-service restrictions, and privacy implications.

## Persistent continuity
- Treat this file as the source of truth for project requirements and handoffs.
- Do not make the user repeat decisions already recorded here.
- After meaningful work, update this file with confirmed decisions, files changed, exact build/test commands and outcomes, blockers, and the next step.
- Separate confirmed facts from plans, assumptions, and untested features.

## Current status (2026-10-18)
- PROJECT_CONTEXT.md exists on the main branch.
- The earlier repository inspection reported the repository empty; current Android source/build configuration has not yet been confirmed in this context.
- No successful Android build, automated test run, APK generation, installation, or real-device verification is confirmed yet.
- The product direction and initial feature goals above have been recovered from the prior discussion and recorded here.

## Handoff checklist
1. Fetch this file and inspect the latest repository contents and history.
2. Identify whether an Android project already exists before initializing anything.
3. Implement a small, testable first milestone; do not silently expand scope.
4. Build and test, fix errors, and repeat checks before packaging.
5. Update this file with verified results and the next concrete step before ending the session.

## Next step
Inspect the current repository contents. If there is no Android project, initialize the Kotlin/Jetpack Compose project with a minimal first milestone, then compile and test it. Do not offer an APK until the build succeeds and the verification status is clearly reported.
