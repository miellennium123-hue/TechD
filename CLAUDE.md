# Guardian Angel

Android app (Kotlin, Jetpack Compose) built from `docs/DESIGN.md`. Read the design doc before changing behavior.

## Design doc rule (always)

- **Update `docs/DESIGN.md` after every user message**, so the doc never falls behind the code.
- Each update: refresh **Last updated** (date and round number), record any new or changed decision in its section (build decisions go in section 8), and add a **Changelog** entry.
- If a message changes nothing, still add a short changelog entry saying so.
- Commit the doc update together with the related code change.

## Building

- The cloud dev environment has no Android SDK (`dl.google.com` is blocked), so builds run on GitHub Actions (`.github/workflows/build.yml`). Push, then check the **Build APK** run. The APK is uploaded as the `guardian-angel-debug-apk` artifact.
- Locally with an SDK: `./gradlew testDebugUnitTest assembleDebug`.
- Every build is signed with `app/signing/guardian.keystore` so updates install over the old app. Never replace or regenerate it, or the user has to uninstall and loses their data.
- Bump `versionCode` and `versionName` in `app/build.gradle.kts` with each user-facing release.

## Code conventions

- Every setting lives in `data/GuardianConfig` (one serializable object, ready for partner sync). Runtime state lives in `data/GuardianState`.
- All actions go through `core/Guardian`. Decision logic stays pure in `core/Rules` and is covered by `app/src/test/.../RulesTest.kt`.
- Mood only changes dialogue (`core/Voice`). It must never affect rules, timers or punishments.
- **Quit for now** must stay reachable on every screen, never be penalized, and never be removable by any setting or future partner sync.
- Writing style in docs: plain language, short bullets, no em dashes.
