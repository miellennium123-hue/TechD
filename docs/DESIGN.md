# Guardian Angel: Design Doc

> **Living document.** Updated after every message (see Update rule below). Source of truth for what the app is and how it should behave.
> **Status:** v0.5.0 (all of section 9 built). Latest APK: https://github.com/miellennium123-hue/TechD/releases/latest Partner remote control and Bluetooth toys are still later phases.
> **Last updated:** 2026-10-06 (round 13, v0.5.0)
>
> **Update rule:** Claude updates this doc after every message in the development chat, in the same commit as any code change. Each update refreshes "Last updated", records new decisions in the relevant section, and adds a changelog entry. If a message changes nothing, the changelog says so.

---

## 1. Concept

**Guardian Angel** is an Android app featuring a little femdom anime angel who manages your phone.

- **Who it's for:** One user, on their own phone (self-use)
- **Later:** A partner can remotely control her settings
- **Her role:** Watches over your phone, locks you out of things, demands photo proof, enforces chastity, checks in on you
- **Core principle:** You consent by turning her on. You can always turn her off.

---

## 2. Her personality

| Trait | Decision |
|---|---|
| **Tone** | Switches between **sweet** and **strict**, somewhat **randomly** (Mood mode setting: Sweet / Strict / Switching) |
| **What she calls you** | **Pet** |
| **Reacts to performance?** | **No.** Mood switches are not tied to obedience |
| **Appearance** | **One** anime angel image (**placeholder** until user provides art) |
| **Rewards** | Simple **praise**, plus **merit** points and levels (no unlocks) |
| **On failure** | **Degradation** messages, possibly **lockout**. **No content limits** set by user. **Claude writes all her lines** |

**Mood rule:** Mood affects **only her dialogue and presentation**. It **never** changes settings, lockouts, timers, or punishments. **One exception (round 12):** begging for early chastity release, where a strict mood denies and adds time more often (see 9.3).

---

## 3. Master controls (safety)

These are always available, no matter what she's doing.

- **On/off switch:** You can disable her yourself at any time
- **"Quit for now" button:** Instantly ends every active lock, timer, and restriction. Always visible, never blocked
- **Never blocked:** Phone dialer and emergency calls, the Guardian Angel app itself (so Quit is always reachable), plus the **Always-allowed list** (see 4.1)

---

## 4. Features

### 4.1 App lockouts
Two lockout scopes:

| Scope | What's blocked |
|---|---|
| **Social media** | Social media apps and **YouTube** |
| **Everything** | All apps **except** the Always-allowed list |

- **Always-allowed list:** WhatsApp, Phone, banking apps, and similar essentials. Editable list in Settings, prefilled with these defaults
- **Ask permission:** Opening a guarded app requires asking her first (she may grant, deny, or demand photo proof)
- Lockouts can be used as a **punishment for failure**

### 4.2 Bedtime
- **Optional setting**, off by default
- When on, she restricts phone use during a set window

### 4.3 Wallpaper control
- She sets your wallpaper to **femdom-themed** images (**placeholders** until user sends real ones)
- **Locked:** You can't change it while she's active

### 4.4 Photo proof
- **No task list.** Photo proof requests replace tasks
- She **requests photo proof** of things, mainly **being locked in chastity** (also usable for permission requests)
- Photos taken through the in-app camera only (no gallery uploads, so no cheating), stored privately on the device
- **What to photograph:** chastity requests always ask for the cage. Everything else (asking permission, Firm bypass, check-ins outside chastity) uses an **editable prompt list** in Settings. She picks one at random and the screen says exactly what to photograph (round 8)
- **Explicit prompts:** each prompt can be marked Explicit (round 8)
- **Verification (round 8):** every photo gets basic checks (rejects too dark, blank or very blurry). Photos for Explicit prompts must also pass an on-device nudity detector. Rejected photos are deleted and she asks for a retake. After 3 rejections, "Send anyway" appears: accepted, but no merit. Photos never leave the phone

### 4.5 Chastity mode
- **Toggleable** (for days without the cage)
- She sets **lock timers**
- **Photo proof** required (proof of being locked, and random check-ins)
- **Countdown is visible** to you
- **She can add time** (for failures, missed proof, or at her whim per settings)
- **Amount added is a setting** (user sets the minutes/hours per addition)

### 4.6 Check-ins
- Only while she's **enabled**
- A few times a day, **at least once every 2 hours**
- **Discreet notifications** (neutral wording, nothing explicit on the lock screen)

### 4.7 Points / levels
- **Merit only.** A score and level that show how good a pet you've been
- **No unlocks**, no cosmetics, and no effect on rules or restrictions

### 4.8 Bluetooth toy control
- **Later phase.** Likely via the open Buttplug.io / Intiface protocol

---

## 5. Settings

> **Round 12 / v0.3.0:** the intensity scale below is **removed**. Each setting is one on/off toggle plus its details (see 9.1). The old scale is kept here for history.

User wants **a list of individual settings**, each toggled on/off.

**Old intensity scale** (removed in v0.3.0):

| Level | Name | Behavior |
|---|---|---|
| 1 | **Gentle** | Reminders and warnings only |
| 2 | **Firm** | Bypass requires a delay or photo proof |
| 3 | **Strict** | Blocked until timer ends |
| 4 | **Absolute** | Blocked, and failure adds punishment (more lock time, longer lockouts) |

*"Quit for now" overrides every level.*

**Draft settings list:**

| Setting | Default | Intensity range |
|---|---|---|
| Guardian Angel enabled | Off | n/a |
| App lockouts | Off | Scope: Social media / Everything. Intensity 1 to 4 |
| Always-allowed list | WhatsApp, Phone, banking | Editable app list |
| Ask permission for guarded apps | Off | 1 to 4 |
| Bedtime | Off | 1 to 4 |
| Wallpaper control | Off | Set only / Set and lock |
| Chastity mode | Off | 1 to 4 |
| Chastity: she can add time | Off | On / Off, plus **amount per addition** (user-set) |
| Photo proof requests | Off | Occasional / Frequent |
| Photo proof prompts | 6 defaults (1 explicit) | Editable list, each prompt Explicit or not |
| Check-in frequency | Every 2 hours | 30 min to 2 hours |
| Degradation on failure | Off | Mild / Harsh |
| Lockout as punishment | Off | Short / Long |
| Discreet notifications | On | On / Off |
| Mood mode (dialogue only) | Switching | Sweet / Strict / Switching (random) |
| Merit points and levels | On | On / Off |

---

## 6. Future: partner remote control

- A partner can **control her settings** from their own device
- **Design now for later:** All settings live in one central config so a remote sync can plug in without a rewrite
- **Proposed rule:** Partner can never disable "Quit for now"

---

## 7. Technical notes (for build phase)

- **Platform:** Android, Kotlin, Jetpack Compose
- **Distribution:** Sideloaded APK (Google Play bans sexual content)
- **App lockouts:** Android Accessibility Service or Usage Stats + overlay screen
- **Wallpaper lock:** True lock needs Device Owner mode (complex setup). Simpler option: she re-applies her wallpaper whenever it changes
- **Check-ins:** Exact alarms so the 2 hour minimum is reliable
- **Photos:** CameraX in-app capture, saved to private app storage
- **Photo checks:** brightness, contrast and edge-sharpness checks in pure Kotlin. Explicit check uses the NudeNet 320n model (YOLOv8, AGPL-3.0) through ONNX Runtime, fully offline. Counts as explicit when an exposed genitalia, breast, buttocks or anus detection scores 0.3 or higher. It can't recognize a chastity cage, so chastity photos get basic checks only
- **Permissions you'll grant knowingly:** Accessibility, Usage Access, Display over other apps, Notifications, Camera, Exact alarms

---

## 8. Build decisions

### v0.1 and v0.2

Choices made while building, where the spec left room:

- **Quit for now** also switches her off, otherwise lockouts would re-apply instantly. It is never penalized
- **Turning her off** stops enforcement and clears pending photo requests, but keeps a running chastity timer (Quit clears it)
- **Never blocked** also includes system Settings, the home launcher, the keyboard and permission dialogs, so she can always be turned off
- **Ask permission** with lockouts off still guards the lockout scope: you must ask before opening those apps
- **Ask permission intensity** sets how hard she is to convince: Gentle always grants, Firm grants or wants proof, Strict and Absolute often deny
- **Firm bypass:** wait 60 seconds or send photo proof, for 10 minutes of access. Grants from asking last 15 minutes
- **Absolute:** each attempt to open a blocked app (or to beg early chastity release) is a failure, at most once per 15 minutes so it can't spiral
- **Chastity timer ranges:** Gentle 1 to 4h, Firm 4 to 12h, Strict 12 to 48h, Absolute 1 to 3 days. **New setting:** "Longest lock" cap (default 24h, includes added time)
- **Early release:** Gentle allowed, Firm after a 10 minute wait, Strict denied, Absolute denied and counts as failure
- **Merit:** +5 on-time proof, +10 plus 1 per hour for a finished, proven lock, +2 per check-in while enabled, +1 for answering a check-in. Failures: -5 to -8
- **Punishment lockout** blocks social media (or everything if the lockout scope is Everything) for 30 min (Short) or 3h (Long)
- **Art:** `assets/angel.png` replaces the placeholder angel, images in `assets/wallpapers/` replace the placeholder wallpaper
- **Photos** are private app storage only, screenshots blocked on photo screens, app backups disabled
- **Releases (round 10):** every merge into `main` publishes a GitHub Release with `guardian-angel.apk`. The stable link always points to the newest build. Settings > About shows the installed version and links there
- **Signing (round 9):** every build is signed with one fixed key (`app/signing/guardian.keystore`) so new APKs install over old ones and keep settings and photos. Before v0.2.1 each CI build had a random key, so updating needed an uninstall

> Several v0.1 decisions above (Ask permission intensity, Firm bypass, Absolute, timer ranges, early release) are replaced by v0.3.0 below.

### v0.3.0 (round 13, builds 9.1 to 9.3)
- **Grouping:** 9.1 to 9.3 ship together. Removing intensity means lockouts, bedtime and chastity each need their new behavior at the same time
- **Kept sub-options:** photo proof frequency (Occasional / Frequent), degradation (Mild / Harsh), punishment length (Short / Long), wallpaper mode and mood stay. They are details, not intensity levels
- **Way in (lockouts and bedtime):** wait 60 seconds for 10 minutes of access, or send an everyday photo for 15 minutes. Photos for the way in and for "Ask her" only use non-explicit prompts
- **Ask permission:** still a toggle. Adds an "Ask her" button, and on its own (lockouts off) guards the lockout scope. Fixed odds, never mood: 40% grant, 40% everyday photo, 20% deny (5 minute cooldown)
- **Punishment lockout** has no way in. It only comes from real failures (missed proof), never from opening a blocked app. Quit for now still ends it
- **Lock length:** "Shortest lock" and "Longest picked lock" (30 minute steps, default 1h to 4h). She picks between them, rounded to 15 minutes. The old "Longest lock" is now "Hard cap (incl. added time)", default 24h
- **Begging:** she releases 35% of the time in a sweet mood, 10% in a strict mood. Denials add time 20% (sweet) or 50% (strict), about 1 in 3 overall. After a denial you wait 10 minutes to beg again. An early release earns no merit
- **Whim:** with "She can add time" on, each check-in during a lock has a 10% chance of adding time (was Strict and up only)
- **Upgrade:** old saved intensity values are ignored. Existing settings and photos are kept

### v0.4.0 (round 13, builds 9.4 Rules & Tasks)
- **Four kinds:** Rule (obey for a set time), Photo task (do it, then photo proof of the task before its deadline), Stillness (motion sensor), Lines (typing)
- **When:** at each check-in with the toggle on, 1 in 3 chance she issues one (if nothing else is pending). That check-in is the task. **"Ask her for a task"** on the home screen issues one on demand (handy for testing)
- **One at a time:** a single rule or task can be open
- **App-enforced rules** lock social media or everything except Always-allowed, with **no way in** (like a punishment lockout). They finish on their own when time is up (+5 merit)
- **Honor rules** (not enforceable): when time is up, report "I obeyed" (+5 merit) or "I broke it" (failure) within 30 minutes. No report is a failure
- **Photo tasks** use the normal photo checks, including the nudity check if marked Explicit. On time: +5 merit
- **Stillness:** 5 seconds to get into position, then the accelerometer watches. Fails if the phone tilts or shifts (more than 2 m/s² from its resting reading for 0.4 seconds). Small tremors and single bumps don't count. Leaving the screen cancels the attempt without failing; start again before the deadline (60 minutes). Phones without a motion sensor just get the timer
- **Lines:** Easy 5, Medium 15, Hard 30 lines, longer sentences at higher difficulty. Pasting and keyboard suggestions are blocked (more than one character at once is ignored; the keyboard opens without suggestions). Any typo restarts from line 1. Capitals and curly apostrophes don't count as typos. Deadline 60 minutes
- **Harder after failure:** every failure raises the lowest lines difficulty by one (up to Hard). Finishing a lines task resets it
- **Failures:** missing a deadline -8 merit, moving or admitting a broken rule -5. Both follow the usual failure settings (degradation, added chastity time, punishment lockout)
- **Starter list:** 12 entries Claude wrote, editable in Settings > Rules & Tasks (add, edit, delete, reset)
- **Off:** turning the toggle off, turning her off, or Quit for now clears any open task with no penalty

### v0.5.0 (round 13, builds 9.5 Shows up)
- **When:** at each check-in with the toggle on, 1 in 4 chance (if no task was issued that time). **"Try it now"** in Settings > Shows up summons her on demand
- **Notification:** discreet mode says only "Please open the app." Tapping it opens her full screen with one question
- **Ignored for 10 minutes:** everything except exempt apps (phone, emergency, Settings, launcher, keyboard, this app, Always-allowed) locks until you answer. Grants don't help. The block screen has an **Answer her** button. A reminder notification goes out when the lock starts
- **Leaving is allowed:** "Later" closes her screen. The 10 minute clock keeps running
- **Answers:** multiple choice (right answer shuffled in with the wrong ones) or a phrase shown on screen to type exactly. Case, spaces at the ends and curly apostrophes don't matter; words and punctuation do
- **Wrong answers:** she asks the same question again. The third wrong answer in one visit is a failure (-5 merit, plus the usual failure settings) and ends the visit, which lifts the lock
- **Right answer:** +3 merit, lock lifted
- **Questions:** 8 non-explicit starters (5 multiple choice, 3 phrases), editable in Settings > Shows up > Her questions
- **Off:** turning the toggle off, turning her off, or Quit for now clears a pending summons with no penalty

## 9. Planned next (round 12)

Agreed in round 12. Build these next, one release at a time.

**Build status:** all built. 9.1 to 9.3 in v0.3.0, 9.4 in v0.4.0, 9.5 in v0.5.0.

### 9.1 Simpler settings (built, v0.3.0)
- **Remove the 1 to 4 intensity levels** (Gentle, Firm, Strict, Absolute) from every setting
- Each setting becomes **one on/off toggle**
- **Keep the details:** lockout scope, bedtime hours, chastity amounts, check-in frequency, Always-allowed list, photo prompts

### 9.2 Lockouts and bedtime (built, v0.3.0)
- When on, locked apps are **hard blocked**
- **Way in:** a short wait, or an everyday (non-explicit) photo prompt from the prompt list
- **Attempts never count as failures:** opening a blocked app costs no merit and adds no punishment

### 9.3 Chastity (built, v0.3.0)
- **Lock length:** two settings, **minimum and maximum**. She picks a random length in between (still capped by Longest lock)
- **Early release:** "Beg to be released". She decides
- **Denied begs:** about 1 in 3 denials add your add-time amount. Only if "She can add time" is on, capped by Longest lock
- **Mood rule change:** strict mood is harsher about begging (denies more often, adds time more often), sweet mood is kinder. This is the only place mood affects outcomes; everywhere else it stays dialogue-only
- More chastity features to be discussed later

### 9.4 Rules & Tasks (new toggle, built, v0.4.0)
- **Source:** a starter list Claude writes, editable in Settings (add, edit, delete), like the photo prompts
- **When:** at about 1 in 3 check-ins she issues one
- **Rules:** obey for a set time. **App-enforced rules** where possible, e.g. "No social media for 2 hours" auto-locks those apps
- **Tasks:** do once before a deadline
- **Stillness tasks:** e.g. "Kneel for 5 minutes holding your phone still". The motion sensor fails you if you move
- **Line writing:** type her sentence repeatedly. **Easy 5, Medium 15, Hard 30 lines**, longer sentences at higher difficulty. Pasting blocked. **Any typo restarts from line 1**. Difficulty is random, and failures make the next one harder
- **Proof:** use phone-checked proof wherever possible (sensor, typing, app-enforced, photo checks). Missing a deadline is a failure

### 9.5 Shows up (new toggle, built, v0.5.0)
- **Trigger:** at some check-ins she sends a "she wants you" notification. Tapping it brings her up full screen
- **Ignored for 10 minutes:** everything locks until you open it and answer (phone, emergency calls and Quit for now still work)
- **Answers:** multiple choice and exact typed phrases she checks. Works offline
- **Questions:** a starter list Claude writes, editable in Settings. Non-explicit
- **Wrong answers:** she asks again. Three wrong answers in one visit counts as a failure

### 9.6 Dropped
- **Porn and website popups:** dropped in round 12

---

## 10. Open questions

None right now.

### Fixed issues
- **Vague permission proof prompt (round 8):** "Earn it. Send her a photo." didn't say what to photograph. Fixed in v0.2: every request names its subject

### Answered
- **Proof subject outside chastity:** an editable prompt list she picks from at random (round 8)
- **Photo verification:** basic checks on every photo, plus an on-device explicit check for prompts marked Explicit (round 8)
- **Mood:** Set via Settings, switches somewhat randomly, affects dialogue only (rounds 2 and 3)
- **Tasks:** None. Photo proof requests instead, like being locked in chastity (round 2)
- **Chastity countdown:** Visible, and she can add time. Amount is a setting (rounds 2 and 3)
- **Degradation limits:** None (round 2)
- **Points / levels:** Merit only, no unlocks (round 3)
- **Lockout scope:** Social media + YouTube, or Everything minus an Always-allowed list (round 3)
- **Wallpapers:** Placeholders now, user sends real ones later (round 3)
- **Art:** One angel image for now (round 3)
- **Her lines:** Claude writes them (round 3)
- **Merit:** Earned from on-time photo proof, finished chastity timers, and keeping her enabled. Failures subtract (round 4)
- **Social media list:** Instagram, TikTok, X, Snapchat, Facebook, Reddit, YouTube (round 4)

---

## 11. Changelog

- **2026-10-06:** Doc created from first round of answers (personality, phone control, chastity, check-ins, safety, partner control)
- **2026-10-06 (round 2):** Mood set in Settings, photo proof replaces tasks, visible chastity countdown with added time, no degradation limits, cosmetic points and levels
- **2026-10-06 (round 3):** Two lockout scopes with Always-allowed list, placeholder art and wallpapers, mood is random and dialogue-only, added chastity time is a setting, Claude writes her lines, points are merit only
- **2026-10-06 (round 4):** Merit sources and social media list confirmed. Design marked complete; Claude will not build the app
- **2026-10-06 (round 5):** v0.1 built: Kotlin + Compose app with every feature in sections 3 to 5. Build decisions recorded in section 8
- **2026-10-06 (round 6):** Development continues in the same chat. Added the update rule: Claude updates this doc after every message. Added `CLAUDE.md` with the same rule
- **2026-10-06 (round 7):** No design changes. User is downloading v0.1 to test on their phone
- **2026-10-06 (round 8):** First test feedback: permission proof didn't say what to photograph, and photos weren't verified. Decided on an editable prompt list (with Explicit flag) and on-device photo checks. Built in v0.2
- **2026-10-06 (round 9):** Questions about the context limit and updating. Found that CI builds had random signing keys (updates wouldn't install over old versions); fixed with a fixed keystore in v0.2.1. One last uninstall needed
- **2026-10-06 (round 10):** Merged all work into `main`. Added automatic GitHub Releases with a stable download link, an About section in Settings (version and update link), and a full workflow guide in `CLAUDE.md` for future sessions
- **2026-10-06 (round 11):** No design changes. Added `docs/TESTING.md`, an on-device test checklist for v0.2.2. Releases now publish only when the version number changes
- **2026-10-06 (round 12):** Planned the next version: one toggle per setting (no intensity levels), hard-block lockouts, chastity min/max length and begging, Rules & Tasks, and Shows up. Popups dropped. Written up in section 9 for the next chat; not built yet
- **2026-10-06 (round 13, v0.5.0):** Built 9.5 Shows up: she summons you at some check-ins, everything locks if you ignore her for 10 minutes, and she asks a multiple choice or typed-phrase question. Section 9 is now fully built
- **2026-10-06 (round 13, v0.4.0):** Built 9.4 Rules & Tasks: app-enforced and honor rules, photo tasks, stillness with the motion sensor, and line writing. Decisions in section 8
- **2026-10-06 (round 13):** Building section 9, one part at a time. v0.3.0 builds 9.1 to 9.3: intensity levels removed, lockouts and bedtime hard blocked with a wait or everyday photo as the way in, attempts never fail, chastity min/max lock length and mood-aware begging. Decisions in section 8
