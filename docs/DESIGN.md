# Guardian Angel: Design Doc

> **Living document.** Updated every conversation. Source of truth for what the app is and how it should behave.
> **Status:** v0.1 built (see README). Partner remote control and Bluetooth toys are still later phases.
> **Last updated:** 2026-10-06 (round 5)

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

**Mood rule:** Mood affects **only her dialogue and presentation**. It **never** changes settings, lockouts, timers, or punishments.

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
- **Proposed:** Photos taken through the in-app camera only (no gallery uploads, so no cheating), stored privately on the device

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

## 5. Settings and intensity

User wants **a list of individual settings**, each toggled on/off, spanning **varying intensity**.

**Draft intensity scale** (applies per setting):

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
- **Permissions you'll grant knowingly:** Accessibility, Usage Access, Display over other apps, Notifications, Camera, Exact alarms

---

## 8. Build decisions (v0.1)

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

## 9. Open questions

None. Design complete.

### Answered
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

## 10. Changelog

- **2026-10-06:** Doc created from first round of answers (personality, phone control, chastity, check-ins, safety, partner control)
- **2026-10-06 (round 2):** Mood set in Settings, photo proof replaces tasks, visible chastity countdown with added time, no degradation limits, cosmetic points and levels
- **2026-10-06 (round 3):** Two lockout scopes with Always-allowed list, placeholder art and wallpapers, mood is random and dialogue-only, added chastity time is a setting, Claude writes her lines, points are merit only
- **2026-10-06 (round 4):** Merit sources and social media list confirmed. Design marked complete; Claude will not build the app
- **2026-10-06 (round 5):** v0.1 built: Kotlin + Compose app with every feature in sections 3 to 5. Build decisions recorded in section 8
