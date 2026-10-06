# Guardian Angel

An Android app with a dominant angel who manages your phone: app lockouts, bedtime, wallpaper control, chastity timers with photo proof, check-ins, and merit levels. Self-use only. You consent by turning her on, and **Quit for now** always ends everything instantly.

Full spec: [`docs/DESIGN.md`](docs/DESIGN.md). Section 8 lists the decisions made while building it.

## Get the APK

**[Download the latest version](https://github.com/miellennium123-hue/TechD/releases/latest/download/guardian-angel.apk)** ([all releases](https://github.com/miellennium123-hue/TechD/releases))

1. Open the link on your phone and download `guardian-angel.apk`
2. Open it (allow "Install unknown apps" for your browser if asked)
3. Updates install over the old version and keep your settings and photos. Settings > About > **Get the latest version** opens the same page

Every merge into `main` publishes a new release automatically. To build locally instead (needs the Android SDK and JDK 17):

```sh
./gradlew assembleDebug        # APK lands in app/build/outputs/apk/debug/
./gradlew testDebugUnitTest    # logic tests
```

## First-run setup

Open **Permissions** in the app and grant:

| Permission | Why |
|---|---|
| **Accessibility** | Sees which app is in front so she can lock it. Reads the app name only, never screen content |
| **Notifications** | Check-ins and her messages |
| **Exact alarms** | Keeps check-ins on time (at least every 2 hours) |
| **Camera** | In-app photo proof. Photos stay inside the app |

**Android 13+ sideloading note:** the Accessibility switch is greyed out for sideloaded apps at first. Go to **App info > ⋮ menu > Allow restricted settings**, then turn Accessibility on.

## Safety

- **Quit for now** sits on every screen (top right, block screen, camera screen). It clears every lock, timer, proof request and punishment, and switches her off. Never penalized
- **Never blocked:** phone and emergency calls, system Settings, your launcher and keyboard, this app, and your Always-allowed list
- You can also turn off the Accessibility service in Android Settings at any time
- Mood only changes her words, with one exception: in a strict mood she's harsher when you beg for early chastity release
- Opening a blocked app never counts as a failure

## Customizing

- **Her picture:** put `angel.png` (or `.jpg`/`.webp`) in `app/src/main/assets/` and rebuild
- **Wallpapers:** put images in `app/src/main/assets/wallpapers/`. She picks one at random
- **Photo prompts:** Settings > Photo proof > "What she can ask for". Mark a prompt **Explicit** and she checks the photo with an on-device nudity detector
- **Her lines:** all dialogue is in `app/src/main/java/com/guardianangel/core/Voice.kt`, sweet and strict versions per situation

## Code map

| Path | What it does |
|---|---|
| `data/Config.kt` | Every setting in one serializable object (ready for partner sync later) |
| `data/State.kt` | What she's doing right now: locks, proofs, grants, merit |
| `core/Rules.kt` | Pure decision logic: what's blocked, ask and beg outcomes, timer lengths, levels |
| `core/Guardian.kt` | All actions: enable, quit, ask, proof, chastity, failures, check-ins |
| `core/Voice.kt` | Her lines |
| `service/GuardianAccessibilityService.kt` | Detects the foreground app and shows the block screen |
| `ui/` | Compose screens: home, settings, chastity, photos, block screen, camera |

## Photo checks

- **Every photo:** rejected if too dark, blank or very blurry
- **Explicit prompts:** must also pass [NudeNet](https://github.com/notAI-tech/nudenet) (bundled at `app/src/main/assets/models/`), run offline with ONNX Runtime. It detects exposed body parts. It can't recognize a chastity cage, so chastity photos get the basic checks only
- **False rejections:** after 3 failed checks, "Send anyway" appears (accepted, no merit)
- **License note:** the NudeNet model is AGPL-3.0. Fine for personal use. If you ever distribute the app, the AGPL applies

## Not built yet (later phases)

- Partner remote control (config is already centralized for it)
- Bluetooth toy control (Buttplug.io / Intiface)
