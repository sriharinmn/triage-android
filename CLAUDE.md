@.private/CLAUDE.md

# CLAUDE.md

Guidance for Claude Code sessions working in this repo. The first line imports the maintainer's private notes when
they're present; without them, this file is the whole guide.

## Project
**Triage**: an Android app (Kotlin + Jetpack Compose) for watching and triaging Sentry issues from a phone. Not
affiliated with Sentry / Functional Software, Inc. Licensed under Apache-2.0.

## Hard product rules
1. **No backend / near-zero running cost.** The app talks only to the user's Sentry server. No servers, databases,
   analytics or paid APIs.
2. **Minimal typing.** Prefer taps, swipes, pickers, and one-time paste.
3. **Sentry legal hygiene.** Use only the public, documented Sentry API (docs.sentry.io/api). Honor rate limits and
   `Retry-After`. Don't use Sentry's logo or brand colors, don't use "Sentry" as the app name, and don't claim to be
   official. Keep the "Not affiliated with Sentry / Functional Software, Inc." disclaimer in the README, site and About.
4. Never log or commit auth tokens or signing keys. Scrub real Sentry responses before saving them as test fixtures.

## Build & test commands
Needs JDK 21 (`JAVA_HOME`) and the Android SDK (`ANDROID_HOME`). The Kotlin toolchain (JDK 17) is auto-provisioned.
- Debug APK: `./gradlew assembleDebug` (package `io.github.sriharinmn.triage.debug`, label "Triage debug"; installs next to the release)
- Unit tests (JVM): `./gradlew testDebugUnitTest`
- Lint: `./gradlew lintDebug`
- Device tests: `./gradlew connectedDebugAndroidTest` (phone connected, awake and unlocked)
- Install and launch debug: `./gradlew installDebug`, then
  `adb shell am start -n io.github.sriharinmn.triage.debug/io.github.sriharinmn.triage.MainActivity`
- Force an alert check (debug): `adb shell am broadcast -n io.github.sriharinmn.triage.debug/io.github.sriharinmn.triage.debug.CheckNowReceiver`
- Demo build (sample data, its own package): `./gradlew installSample`, launch
  `adb shell am start -n io.github.sriharinmn.triage.sample/io.github.sriharinmn.triage.MainActivity`

## Code conventions
- Kotlin, Compose, Material 3, single activity, Navigation 3. Layers: `ui` → `data` (repositories, DataStore stores)
  → `remote` (Sentry API) and `local` (Room cache, behind `data/issues/IssueCache`).
- Signed-in screens get their ViewModels from a session-scoped store (`AccountGate` in `TriageApp.kt`). Create them
  inside a Navigation entry with an explicit initializer.
- Manual DI via `AppContainer` (no Hilt).
- Small focused files. One screen = one composable file + one ViewModel.
- Write tests alongside features. Fixture-based API tests use MockWebServer.
- Device tests never use the app's real Keystore alias or DataStore/Room files; use test aliases, unique test files and
  in-memory Room.

## Git
- Default branch `main`. Work on a branch per milestone, open a pull request into `main`, merge with a merge commit,
  delete the branch.
