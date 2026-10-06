# Guardian Angel

Android app (Kotlin, Jetpack Compose) built from `docs/DESIGN.md`. Read the design doc before changing behavior. It is the source of truth and records every decision so far. Section 8 has the build decisions per version, section 9 the plans (9.1 to 9.5 built, **9.7 Open sites planned next**), section 10 the open questions.

## Design doc rule (always)

- **Update `docs/DESIGN.md` after every user message**, so the doc never falls behind the code.
- Each update: refresh **Last updated** (date and round number), record any new or changed decision in its section (build decisions go in section 8), and add a **Changelog** entry.
- If a message changes nothing, still add a short changelog entry saying so.
- Commit the doc update together with the related code change.

## Workflow

1. Work on the session's feature branch, cut from the latest `main`.
2. Bump `versionCode` and `versionName` in `app/build.gradle.kts` for every user-facing change. A release is only published when the version is new, so doc-only changes don't need a bump.
3. Push, then wait for the **Build APK** workflow to pass (`.github/workflows/build.yml`). Check it with the GitHub MCP `actions_list` tool, or poll `https://api.github.com/repos/miellennium123-hue/TechD/actions/runs?branch=<branch>`.
4. Open a pull request into `main` and merge it once CI is green. The user asked for changes to be merged so new versions get published (round 10).
5. Merging to `main` publishes a GitHub Release automatically. Give the user the stable download link:
   - **Direct APK:** https://github.com/miellennium123-hue/TechD/releases/latest/download/guardian-angel.apk
   - **Releases page:** https://github.com/miellennium123-hue/TechD/releases/latest

## Testing with the user

- `docs/TESTING.md` is the on-device test checklist. Update it when features change, and record results the user reports.

## Building

- The cloud dev environment has no Android SDK (`dl.google.com` is blocked), so builds run on GitHub Actions. Pure Kotlin logic in `core/Rules.kt`, `core/PhotoCheck.kt` and `core/TaskChecks.kt` has JVM unit tests in `app/src/test/`, which CI runs.
- **Quick local check without the SDK:** a plain Kotlin JVM Gradle project that copies in `data/` (except `Store.kt`), `core/Rules.kt`, `core/Voice.kt`, `core/PhotoCheck.kt`, `core/TaskChecks.kt` and the tests can run them (Kotlin 2.1.0, kotlinx-serialization 1.7.3, JUnit 4). Use it before pushing; CI is still the real build.
- Maven Central sometimes rate-limits this environment (HTTP 429), so local JVM test runs may fail to resolve dependencies. Rely on CI.
- Locally with an SDK: `./gradlew testDebugUnitTest assembleDebug`.
- Every build is signed with `app/signing/guardian.keystore` so updates install over the old app. Never replace or regenerate it, or the user has to uninstall and loses their data.

## Code conventions

- Every setting lives in `data/GuardianConfig` (one serializable object, ready for partner sync). Runtime state lives in `data/GuardianState`. Add new fields with defaults so saved data from older versions still loads.
- All actions go through `core/Guardian`. Decision logic stays pure in `core/Rules` and is covered by `RulesTest.kt`.
- Mood only changes dialogue (`core/Voice`). The one exception (round 12): begging for early chastity release, in `Rules.begOutcome`. Nothing else may use mood. Every `Line` needs sweet and strict versions (a test enforces this).
- There are no intensity levels (removed in v0.3.0). Each setting is an on/off toggle plus its details.
- **Quit for now** must stay reachable on every screen, never be penalized, and never be removable by any setting or future partner sync.
- Proof photos stay in private app storage and never leave the phone. Photo checks run on device (`core/PhotoVerifier.kt`, NudeNet model in `assets/models/`, AGPL-3.0).
- Check-ins decide what to do in the pure `Rules.checkInAction`: no deadlines when she can't notify, silent during quiet hours and bedtime, and nothing set at a check-in may be due inside quiet time (`Rules.reachesQuiet`).
- The **Changelog** in `docs/DESIGN.md` is oldest first. The release workflow uses its last 3 entries as release notes, so always append at the bottom.
- Writing style in docs and replies: plain language, short bullets, bold cues, no em dashes.

## Where things are

| Path | What it does |
|---|---|
| `data/Config.kt` | All settings, proof prompts |
| `data/State.kt` | Locks, proof requests, grants, merit |
| `core/Guardian.kt` | Every action: enable, quit, ask, proof, chastity, failures, check-ins |
| `core/Rules.kt` | Pure blocking and timer logic |
| `core/PhotoCheck.kt`, `core/PhotoVerifier.kt` | Photo quality and explicit checks |
| `data/Tasks.kt`, `core/TaskChecks.kt` | Rules & Tasks list, active task, lines and stillness checks (tested in `TaskChecksTest.kt`) |
| `data/ShowsUp.kt`, `ui/ShowUpActivity.kt` | Shows up: questions, the pending summons, her full-screen visit |
| `core/Voice.kt` | Her lines |
| `service/GuardianAccessibilityService.kt` | Foreground app detection, opens the block screen |
| `ui/` | Compose screens and the Block and Proof activities |
