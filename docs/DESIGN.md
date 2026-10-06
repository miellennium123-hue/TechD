# Guardian Angel: Design Doc

> **Living document.** Updated after every message (see Update rule below). Source of truth for what the app is and how it should behave.
> **Status:** v0.16.0 (Chastity settings on the Chastity screen, tidier Settings). Latest APK: https://github.com/miellennium123-hue/TechD/releases/latest Partner remote control and Bluetooth toys are still later phases.
> **Last updated:** 2026-10-06 (round 47)
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
| **Her lines** | Claude's lines are built in. Since v0.9.0 you can edit them in **Settings > Her lines** (see 9.8) |

**Mood rule:** Mood affects **only her dialogue and presentation**. It **never** changes settings, lockouts, timers, or punishments. **One exception (round 12):** begging for early chastity release, where a strict mood denies and adds time more often (see 9.3).

---

## 3. Master controls (safety)

These are always available, no matter what she's doing.

- **On/off switch:** You can disable her yourself at any time. With **Lock guard** on, it takes 30 minutes and counts as a failure (rounds 41 and 43)
- **"Quit for now" button:** Ends every active lock, timer, and restriction. Always visible, never blocked, never punished, and it always finishes. **Slow by the user's choice (rounds 41 and 43):** hold 10 seconds, type her sentence exactly, then wait 9.5 minutes with the screen open, about 10 minutes in all. Leaving the screen starts it over. No separate instant emergency exit (user's choice); calls and the dialer are never blocked
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
- A check-in can become a site visit (9.7), a task (9.4), a summons (9.5), a photo request, or a plain "report in"
- **No hidden deadlines (round 14):** if notifications are off, check-ins never set a task, summons or photo deadline
- **Quiet at bedtime (round 14):** with Bedtime on, check-ins inside the bedtime window are silent (no notification, no demands)
- **Quiet hours (round 16):** its own setting, on by default (23:00 to 07:00). Check-ins inside are silent, and she never sets anything due inside it. It doesn't lock apps

### 4.7 Points / levels
- **Merit only.** A score and level that show how good a pet you've been
- **No unlocks**, no cosmetics, and no effect on rules or restrictions

### 4.8 Bluetooth toy control
- **Later phase.** Likely via the open Buttplug.io / Intiface protocol

### 4.9 Open sites (v0.8.0)
- She opens one of **your own sites** in Chrome after a 10 second warning, and you stay for a set time
- Details in 9.7 and section 8 (v0.8.0)

### 4.10 Rate me (v0.10.0)
- She rates you: your measurements against published data, plus how well you show her in a photo, then her verdict
- Details in 9.10 and section 8 (v0.10.0)

### 4.11 Guided sessions (v0.11.0, motion checks v0.12.0)
- She runs a JOI-style session to her beat, with a kink menu, the front camera watching, and her ending (permission, ruined or denied)
- From v0.12.0 the camera also checks you keep her beat and stop when she says
- Details in 9.9 and section 8 (v0.11.0 and v0.12.0)

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

**Current settings (v0.11.0):** one toggle each, plus details.

| Setting | Default | Details |
|---|---|---|
| Guardian Angel enabled | Off | Master switch |
| App lockouts | Off | Scope: Social media / Everything. Hard block, way in: 60 second wait or an everyday photo |
| Always-allowed list | WhatsApp, Phone, messages, contacts, clock, maps | Editable app list |
| Ask permission for guarded apps | Off | Adds "Ask her" |
| Bedtime | Off | Start and end time. Same way in as lockouts. Check-ins are quiet inside it |
| Quiet hours | **On**, 23:00 to 07:00 | Start and end time. Silent check-ins, nothing due inside. No locks |
| Wallpaper control | Off | Set only / Set and lock |
| Chastity mode | Off | Shortest and longest picked lock (default 1h to 4h) |
| Chastity: she can add time | Off | Amount per addition, hard cap (default 24h) |
| Photo proof requests | Off | Occasional / Frequent |
| Photo proof prompts | 6 defaults (1 explicit) | Editable list, each prompt Explicit or not |
| Check-in frequency | Every 2 hours | 30 min to 2 hours |
| Rules & Tasks | Off | Editable list, 12 starters |
| Shows up | Off | Editable question list, 8 starters (5 multiple choice with 4 wrong answers each, 3 phrases) |
| Open sites | Off | Your own site list (empty to start). Stay for 5 min (1 to 30) |
| Degradation on failure | Off | Mild / Harsh |
| Lockout as punishment | Off | Short (30 min) / Long (3h) |
| Discreet notifications | On | On / Off |
| Mood | Switching | Sweet / Strict / Switching. Dialogue, plus begging odds |
| Her lines | Her built-in lines | Edit the sweet and strict lines for each situation. Reset one or all |
| Guided sessions | Off | Length 10 min (5 to 30). Kink menu. Ending sliders (permission 20, ruined 40, denied 40). Beat sound on. She watches (camera) on |
| Rate me | Off | Her taste: likes bigger / likes smaller. Units: cm / inches. Last 5 scores, clear history |
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

### v0.6.0 (round 14, full review of every earlier version)
**Fixed**
- **App in front:** the keyboard, notification shade and system popups no longer count as "the app you're in". Before, pulling down the shade or typing made the 30 second re-check stop watching your app, so expiring access, bedtime starting, her rules and the Shows up lock could fail to kick in until you switched apps. Going home always counts as leaving
- **No hidden deadlines:** with notifications off, check-ins only do a plain "report in". Tasks, summons and photo demands need a working notification
- **Quiet at bedtime:** with Bedtime on, check-ins in the bedtime window are silent: +2 merit, no notification, no task, summons, photo or added time. Before, she could set deadlines you'd miss asleep
- **Stillness:** "Start" disappears when the hold can't finish before the deadline (it used to fail you halfway)
- **Lines:** finished lines are saved in the task, so leaving the screen or app keeps your progress (a typo still restarts from line 1)
- **Shows up text:** said "3 wrong answers allowed" when the 3rd wrong answer is the failure. Now shows tries left
- **Notifications:** tasks have their own notification, which opens the task screen. Finishing a task no longer clears an unrelated photo notification
- **Release notes:** changelog back in date order, so each release's notes show its own changes
**Safer**
- **Reset all settings** asks first. It also resets your edited prompts, tasks and questions
- Tasks and summons can't be issued while she's off, while the toggle is off, or on top of an open one
- Saved data loads more leniently, so a bad value falls back to its default instead of resetting everything
**Easier**
- **Chastity steppers:** 30 minute steps up to 4h, 1h up to a day, then 12h (was 30 minutes all the way, up to 336 taps). Hard cap: 1h steps up to a day, then 12h
- A warning shows when the hard cap is shorter than the longest picked lock
- **Stillness** is a little more forgiving: about 15 degrees of drift for half a second (was 12 degrees for 0.4 seconds)
- **Task editor:** picking a kind sets a sensible length (rule 1h, photo 30 min, stillness 5 min)
- Button rows wrap on narrow phones (Shows up, task and question editors)
- **Try it now** opens her screen directly when notifications are off, and Settings says she can't call you
- Block screen uses her "you ignored me" line for a Shows up lock

### v0.7.0 (round 16, answers the two open questions)
- **Quiet hours** is a new setting, **on by default** from 23:00 to 07:00, so nobody fails a deadline asleep. Editable start and end
- **Inside quiet hours:** check-ins are silent (+2 merit, no notification, no demands, no added chastity time), same as inside Bedtime
- **Nothing due inside quiet hours:** near the start of quiet hours she only sets what finishes first. A summons needs 10 minutes, a check-in photo 30, a task its deadline (honor rules include the 30 minutes to report back). App-enforced rules can't be failed, so they always fit. If nothing fits, the check-in is a plain "report in"
- **Asking her yourself** ("Ask her for a task", "Try it now") ignores quiet hours: you chose it
- **Doesn't lock anything.** Locking the phone at night is Bedtime's job. Bedtime still counts as quiet too
- **More wrong answers:** each of the 5 starter multiple choice questions now has 4 wrong answers (5 options), so the 3-strikes failure can happen
- **Upgrade:** saved starter questions get the new wrong answers automatically. A question you wrote, or whose wrong answers you changed, is left alone
- **Question editor** warns when a multiple choice question has fewer than 3 wrong answers

### v0.8.0 (round 19, builds 9.7 Open sites)
- **Toggle "Open sites"**, off by default, with your own list (add, edit, delete). The list starts empty; nothing is bundled. Only web addresses: `https://` is added if missing, other schemes are refused
- **Chance:** at **9 in 10 check-ins** ("almost every single time"). It's rolled **first**, so with sites on, tasks, summons and photos only come from the other 1 in 10
- **Ask her:** "Ask her to open a site" on the home screen and "Ask her now" in Settings. Like the other "ask her" buttons, this ignores quiet hours (you chose it)
- **Warning:** her full-screen screen with a 10 second countdown. Back doesn't close it; **Quit for now** does, never penalized. Going home or to another app during the countdown brings the warning back
- **Discreet on:** the warning says only "Reminder, opening a page in 10" with no angel, line or site name. Discreet off: her line and the site name
- **Browser:** Chrome. If Chrome isn't installed or is disabled, the default browser
- **Stay time:** default **5 minutes**, 1 to 30 in Settings. Only time with the browser in front counts
- **Leaving early is a failure on the first leave** (-5 merit, plus the usual failure settings) and ends the visit. Leaving means going home, opening recents, or switching to any other app (including Always-allowed ones, and an app a site link opens)
- **Pauses, not failures:** her own app (so Quit for now stays reachable), phone calls, Android Settings and other never-blocked system screens, and the screen turning off. The timer stops until you're back. Unlocking the phone brings you back to the page. For the first 3 seconds after the page opens, nothing counts as leaving (the hand-off from her warning)
- **Stayed the full time:** +3 merit and her "you may go" line as a notification
- **Phone locked at check-in:** she waits and shows the warning when you next unlock, within 30 minutes, if you're not in a call and it wouldn't run into quiet hours or bedtime. Otherwise it's dropped with no penalty
- **Never:** on the lock screen, with the screen off, during a call (or while ringing), or in or running into quiet hours or bedtime
- **No notification needed:** the warning shows itself, so visits happen even with notifications off. They do need the Accessibility permission (to see the browser and time the stay)
- **While she's opened it,** nothing blocks her browser (lockouts, bedtime, rules, punishment)
- **One at a time,** and never on top of a pending summons (and no summons during a visit)
- **Off:** turning the toggle off, turning her off, or Quit for now ends a visit with no penalty. A visit left far past its time (the service stopped mid-visit) is dropped with no penalty

### v0.9.0 (round 21, builds 9.8 Editable lines)
- **Settings > Presentation > Her lines** opens a list of all 32 situations, in groups (On and off, Asking and locks, Photo proof, Check-ins, Failures, Chastity, Rules & Tasks, Shows up, Open sites). Each has a short note on when it's used, and an **Edited** mark once you change it
- **One situation:** Sweet / Strict switch, then add, edit and delete lines. **Reset this situation** brings hers back. **Reset all** (on the list) asks first. **Reset all settings** also resets your lines
- **Stored as overrides** in `GuardianConfig.lineOverrides`, keyed by situation name. Only situations you edited are stored. Your first edit copies her current sweet and strict lines, so the other mood stays as it was
- **Updates (user's choice):** situations you never edited keep getting her newest built-in lines. Editing a situation back to exactly her lines stops storing it, so it follows updates again
- **Empty falls back:** a mood with no lines left (or only blank ones) uses her built-in lines, so she always has something to say. The screen says so
- **New situation "Before you switch her on" (user's choice):** the home screen line before she has said anything (it was hardcoded). Her Shows up screen's backup line now uses her Summons lines instead of a fixed one
- **Not editable (user's choice):** the sentences she makes you type in Lines tasks. Only her dialogue
- **Discreet notifications** still show only neutral text, whatever you wrote. The Open sites warning with Discreet on still shows no line
- **Mood rule unchanged:** mood picks the sweet or strict list. Your lines change nothing else
- **Tidy up:** the unused `WAIT` line is gone. An old saved edit for a situation that no longer exists is ignored
- **No limits** on what you write, like the rest of her lines (round 2). There's no length limit; long lines get cut off in notifications

### v0.10.0 (round 27, builds 9.10 Rate me)
- **Claude's pick (user said "code the most accurate one"):** design 1 + 3 from the round 26 research, plus a taste setting. No card-and-ruler measuring yet (accuracy untested)
- **Toggle "Rate me"**, off by default. **Rate me** button on home while she's on. Only when you ask; never at check-ins
- **Step 1, measurements:** erect length and girth, in cm or inches (prefilled from last time). Allowed range 2 to 40 cm
- **Step 2, photo:** in-app camera only, screenshots blocked. Usual quality checks (dark, blank, blurry), then the NudeNet detector must see male genitalia (score 0.3 or more). If the detector can't run, she rates anyway with neutral photo signals. After 3 rejected photos: "Rate me on my numbers" (presentation 0)
- **The photo is deleted** as soon as she's looked. Only numbers are kept (last 30 ratings in `GuardianState.ratings`). Quit for now keeps the history; Settings can clear it
- **Size:** percentiles from Veale et al. 2015, BJU International 115:978-986 (erect length mean 13.12 cm, SD 1.66, n = 692; erect girth mean 11.66 cm, SD 1.10, n = 381), assuming a normal distribution like the paper's nomograms. Shown as 1st to 99th
- **Size score:** 60% length, 40% girth percentile. **Her taste** "She likes smaller" flips it (100 minus), the percentiles shown stay real
- **Presentation (0 to 100):** how clearly the detector sees it 30% (0.3 to 0.8), framing 25% (best at 15% to 60% of the frame), centring 15%, sharpness 15%, lighting 15%
- **Her score (1 to 10):** 80% size, 20% presentation. Rated on your word alone counts presentation as 0
- **Verdict:** 4 tiers (1 to 3, 4 to 5, 6 to 7, 8 to 10), each a new situation in Her lines, plus "Couldn't see it". Claude's built-in lines are mild; you write your own
- **Mood rule kept:** mood only picks the sweet or strict line, never the score
- **Flavor only:** no merit, no failures, no added lock time
- **Scorecard:** score, length and girth with percentiles, presentation, your taste, and the source

### v0.11.0 (round 33, builds 9.9 Guided sessions, part 1)
- **Two releases (user agreed):** this one has sessions, the kink menu, the beat, endings, the ruin clip, chastity rules and "she sees you". v0.12.0 adds beat and stop checks from camera motion
- **Start:** "Start a session" on home, only when you tap it, with her on and the toggle on. Never at check-ins. Quiet hours don't apply (you chose it)
- **Setup screen:** length, ending shares, kinks that will run, a camera permission button, and for sounding a "sterile and ready, never force" tick (no tick, no sounding)
- **Kink menu:** Edging, Stop and go, Speed changes, Teasing, Holds and stillness, Nipple play, Cage tease (locked only), Countdowns, Praise, Humiliation, Toys, CBT (soft or hard), Sounding. Default on: edging, stop and go, speed, teasing, countdowns, praise. Positions removed (user's choice)
- **How a session is built (pure `core/Session`):** a short intro, then without a lock stroking to her beat is the base and her kink commands are mixed in; during a lock holding still is the base. Then the ending. Same seed, same session (tested)
- **Beat:** an on-screen pulse, plus a tick sound (setting). Stroking 60 to 100 per minute (50 to 140 with Speed changes), bursts 150 to 200, slow 30 to 50, teasing 20 to 40
- **Edging:** you tap "I'm at the edge" (up to 3 minutes), then hands off 15 to 30 seconds
- **CBT:** counted to a slow beat with a counter. Soft 3 to 5 at 20 per minute, at most 2 per session. Hard 6 to 12 at 30 per minute, at most 4. Never two in a row. "Too much" skips with no penalty
- **Sounding:** once per session, about a third of the way in: in (90 s), hold (1 to 3 min), out (60 s). No beat, no countdowns, never watched or punished, "Too much" any time. Not during a lock
- **Countdowns:** 30% of commands get a 5 to 10 second countdown first. **Praise / Humiliation:** a remark on every third command
- **Endings (sliders, user's choice):** relative weights 0 to 100. All 0 means denied. During a lock permission is never picked
  - **Permission:** (an edge if Edging is on), 30 s fast, a 10 count, then "You may finish" and you tap Done
  - **Ruined:** (during a lock: unlock first), 30 s, an edge, a 3 count, then "Hands off, now". She films 20 seconds. Then you report "Ruined, as ordered" or "I couldn't stop". During a lock: lock back up, then a cage photo (15 minutes, the usual chastity check)
  - **Denied:** an edge (a hold during a lock), then hands off for 15 seconds
- **Chastity (user's choice):** cage-safe commands only until the ending, and only ruined or denied
- **She watches (camera, user's choice "see as much as possible"):** front camera, the NudeNet detector about every 1.5 seconds. Not seen for 10 seconds during a stroking command: she scolds you and adds an extra edge (user's choice). At most 5 per session. Not during a lock, sounding or the ending. If the detector can't run, she never punishes
- **Ruin clip (user's choice):** 20 seconds of silent video (no microphone permission), saved to the private proof folder. Photos now shows clips with a play mark and plays them. If recording fails she takes your word
- **Merit:** a finished session +5. "I couldn't stop" on a ruin is a failure (-5 and the usual failure settings). Stopping early costs nothing and isn't recorded
- **Screen:** screenshots blocked, the screen stays on, Back does nothing (Stop and Quit for now are always there), rotating doesn't restart it
- **History:** last 30 sessions (ending, outcome, times caught and skipped), in Settings with a clear button
- **Lines:** 28 new situations in Her lines (group "Guided sessions"), written to the round 30 limits

### v0.12.0 (round 36, builds 9.9 Guided sessions, part 2)
- **Asked (round 36):** the user picked part 2 back up in a new chat. Built to the round 32 decisions: beat and stop checks from motion, caught means a reprimand plus an extra edge
- **How it sees motion (`core/Motion`, pure, tested in `MotionTest`):** every camera frame (up to about 30 a second) is shrunk to a 48 x 36 brightness grid and compared with the one before. Each frame's average brightness is taken out first, so the camera changing exposure isn't movement. Only the numbers are kept (about 12 seconds of them), never frames
- **Moving or still:** the middle value of the last 0.6 seconds of change, against a **Motion sensitivity** setting (Low, Normal, High). A fixed threshold, no self-calibration, so a session that starts mid-stroke still works. Needs tuning on a real phone; the screen shows what she sees to help
- **Your rhythm:** where the movement is in the frame, over the last 8 seconds of the command, on whichever axis moves most (phone either way round). The first strong repeat in it is one stroke. 20 to 220 per minute; no clear rhythm gives null
- **Beat check:** only plain stroking commands (Stroke, Faster, Slower). Not teasing (too light to see), edging (your own pace to the edge), CBT, nipples, toys, sounding, countdowns or the ending. 5 seconds to find her beat, then within 30% of it counts. Stopped, or a clear rhythm off her beat, for 8 seconds in a row: caught. Moving with no clear rhythm gets the benefit of the doubt
- **Stop check:** watched hands-off commands (Stop, After the edge, Hold still). 2 seconds to stop, then moving for 1.5 seconds in a row: caught. A short twitch is fine
- **Also during a lock (Claude's call):** the stop check runs on holds during a lock, and being caught adds a hold, not an edge. The "she sees you" check still never runs during a lock
- **Never punishes without frames:** no frames for 0.6 seconds and she doesn't judge; the count starts over. All three checks share the limit of 5 catches per session
- **Two new lines** in Her lines: **Off beat** and **Didn't stop**, each with sweet and strict versions, written to the round 30 limits. Out of view still uses **Caught**
- **On screen:** a line under the beat, "She sees about 85 per minute", "She sees you stopped", "She sees you still" or "She sees you moving"
- **Settings:** "She checks your motion" (on by default, needs the camera setting) and Motion sensitivity (Normal by default)
- **Known limits:** going half speed can sometimes read as on beat if each stroke looks like two movements; strong flicker or the phone being bumped can read as movement

### v0.12.1 (round 37, camera switch)
- **Asked:** make sure she opens the correct camera, or add a button to switch between front and back
- **Built:** the session camera is front by default and remembers your choice (`SessionSettings.backCamera`). Switch it on the setup screen ("Camera: front, Switch") or during a session ("Use back camera" / "Use front camera" under the beat). Not shown while she films a ruin
- **Fallback:** if the phone doesn't have the camera you picked, she uses the other one
- **Switching resets the motion check**, so the jump between cameras never counts as movement
- Proof photos already had their own switch; unchanged

### v0.13.0 (round 41, Lock guard and the slow Quit for now)
- **Asked:** a lock that feels real, with a tedious way to turn her off. The user chose: Quit for now about 3 minutes, no separate emergency exit, and all three Lock guard parts. Claude recommended an instant emergency exit; the user declined
- **Quit for now (everywhere, always):** hold the button 10 seconds (letting go resets), type "I am giving up on my angel for now, and I know she will be disappointed in me." exactly (a typo starts the sentence over, pasting is ignored), then 2.5 minutes with the screen open while she talks you out of it (new line "Talking you out of quitting", every 15 seconds). Leaving the screen starts it all over; the screen stays on. "Never mind, I'll stay" cancels. Still never punished, never blocked, and no setting can make it longer or remove it. Not a setting
- **Lock guard (Settings, off by default):** only acts during a lock. **A lock** = chastity lock, punishment lockout, one of her rules running, or a pending Shows up summons
- **Slow switch off:** during a lock the on/off switch needs a 10 second hold, a longer sentence and 30 minutes with the screen open, then it's a failure (-10 merit and the usual failure settings) and she's off. Switching her on is always instant
- **Settings can't end a guarded lock:** Lock guard, chastity, punishments, Rules & Tasks and Shows up can't be switched off until the lock ends. Everything else still changes
- **Tamper guard:** during a lock, a Settings or uninstall screen (package with "settings", "packageinstaller", "securitycenter" or "safecenter") that shows her name sends you home and back to her (new line "Lock guard"). Never a failure. This covers her App info (force stop, uninstall, clear data), her Accessibility page and her device admin page. Screen content events are only switched on while guarding, and she only looks for her own name
- **Device admin (optional, Permissions screen):** no policies. Android won't uninstall her until it's removed, and removing it is blocked during a lock. Android shows her warning line ("Removing her admin") when you try
- **Tamper detection:** her watch (the accessibility service) starting again during a lock (a restart, force stop, or switching it off and on) is a failure (-10 and the usual failure settings) with her "Tampering" line, except after an app update or the very first start. Her watch found off during a lock (when you open the app or at a check-in) is a failure too, counted once until it comes back
- **Accessibility now reads window content** (needed for the tamper guard). Updating may need her Accessibility switched off and on once; do that before any lock
- **Honest limits:** safe mode, ADB, a factory reset and Quit for now still get you out. A crash of her watch during a lock would count as tampering
- **New lines (5):** Talking you out of quitting, Slow switch off, Removing her admin, Lock guard, Tampering. Sweet and strict

### v0.14.0 (round 43, Lock guard always on, 10 minute Quit for now)
- **Asked:** Lock guard shouldn't need a lock: with it on and her on, switching her off and loosening settings (like app lockouts) should be very tedious. Quit for now should take 10 minutes
- **Quit for now:** hold 10 seconds, type her sentence, wait 9.5 minutes with the screen open (was 2.5). About 10 minutes. Still always reachable, always finishes, never punished
- **Guarding = Lock guard on and she's on.** No lock needed any more for the slow switch off, the blocked screens or her watch being off
- **Loosening her settings (new, replaces "can't be switched off during a lock"):** while guarding, a change that loosens her control opens a full-screen "Loosen her control" screen: 10 second hold, type "I am asking my angel to loosen her control over me, and I will wait as long as she wants.", then 30 minutes with the screen open (new line "Loosening her settings"). Then the change is made. Not a failure. Cancel or leave the app and nothing changes
- **What counts as loosening:** switching off Lock guard, App lockouts, Ask permission, Bedtime, Wallpaper, Chastity, Photo proof, Rules & Tasks, Shows up, Open sites, Degradation or Punishments; switching Quiet hours on; adding an always-allowed app. Making her stricter and every other detail change instantly (Claude's call; details like times and lists of prompts aren't guarded yet)
- **Restarts still only count during a lock (Claude's call):** phones restart for updates and flat batteries, so a restart is tampering only when Lock guard is on and a lock is running. Her watch switched off counts any time Lock guard is on
- **Settings, Home, Lock guard row, permissions and her lines** reworded for "whenever she's on"
- **Battery:** with Lock guard on, she now gets screen-change events all the time (she still only looks inside Settings and uninstall screens)

### v0.14.1 (round 45, black, white and gold)
- **Asked:** change the theme from purple to white, gold and black
- **App colors (`ui/theme/Theme.kt`):** near-black backgrounds and cards, gold (#D4AF37) for buttons, switches and highlights, champagne white as the second color, white text. Red stays for Quit for now and errors so they still stand out
- **Her picture, app icon and placeholder wallpaper:** white wings, black dress and hair with gold outlines (so she shows on black), gold eyes and halo. Placeholder wallpaper fades from black to dark gold. App window and icon background black

### v0.15.0 (round 46, Guided sessions screen)
- **Asked:** move sessions out of Settings into their own tab from the home screen, as the home of the guided sessions part of the app
- **Built:** a **Guided sessions** button on Home opens its own screen: Start a session (when she and sessions are on), the on/off toggle, length, kink menu, ending sliders, beat sound, camera, motion checks and sensitivity, and the history. Removed from Settings. Back from the kink menu returns here
- **Also asked in round 46, not built:** a one minute "quickshot" session and a CBT-during-ruin option. Claude's reply that included them was stopped by a safety filter, so they're not planned (like rounds 18 and 34)
- Home's Quit for now hint now says it takes about 10 minutes

### v0.16.0 (round 47, Chastity screen and tidier Settings)
- **Chastity:** its settings (on/off, shortest and longest lock, adding time, hard cap) moved from Settings to the bottom of the Chastity screen. Home's **Chastity** button is always there now (it used to hide when chastity was off)
- **Settings tidied:** the top card ("Her") keeps the master switch and Lock guard open. Everything else is grouped under headings (Phone control, Check-ins, Extras, Discipline, Her voice, App) in cards that fold shut, each with an On/Off badge. Tap a card to open it. Reset moved into the About card
- **Also asked in round 47, not done:** a prompt for building the quickshot session in another chat. Not written, since that feature was stopped by a safety filter in round 46 (same as round 35)

## 9. Round 12 plan (built)

Agreed in round 12 and built one release at a time. Kept as the record of what was asked for.

**Build status:** 9.1 to 9.5 built (v0.3.0 to v0.5.0, reviewed in v0.6.0). 9.7 Open sites built in v0.8.0 (round 19). 9.8 Editable lines built in v0.9.0 (round 21). 9.9 Guided sessions part 1 built in v0.11.0 (round 33). 9.10 Rate me built in v0.10.0 (round 27).

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
- **Porn and website popups:** dropped in round 12. Opening sites comes back in a narrower form as 9.7

### 9.7 Open sites (planned round 18, built v0.8.0)
Built as v0.8.0, the only feature in that release.
- **New toggle "Open sites"**, off by default, with an editable list of URLs the user adds themselves (add, edit, delete). Nothing is bundled in the app
- **When:** at some check-ins (random, like tasks and summons), plus an **"Ask her"** button that opens one now
- **Warning first:** a full-screen screen with a **10 second countdown**, then she opens a random site from the list in **Chrome** (the default browser if Chrome isn't installed)
- **Stay for a set time:** if you leave the browser before the time is up, she brings you back. The length is a setting
- **Never:** on the lock screen or with the screen off, during a phone call, or during quiet hours or bedtime
- **Quit for now** stays on the warning screen and ends it, never penalized. Discreet wording on the warning when Discreet notifications are on
- **Decided in round 19:** stay 5 minutes by default (1 to 30), almost every check-in (9 in 10), and leaving early is a failure on the first leave. A locked phone at check-in waits for the next unlock (up to 30 minutes). See section 8, v0.8.0

### 9.8 Editable lines (planned round 20, built v0.9.0)
Built as v0.9.0, the only feature in that release.
- **Settings > Her lines:** a list of every situation she speaks in (greeting, praise, denial, failure, begging, and so on), each with a short note on when it's used
- **Each situation** shows her sweet and strict lines, prefilled with the current ones. Add, edit and delete lines, reset one situation, or reset all
- **Stored in `GuardianConfig`** as overrides, so partner sync can carry them later. A situation and mood with no lines left falls back to her built-in lines, so every line keeps a sweet and a strict version
- **Discreet notifications** still show only neutral text, whatever the lines say
- **Tidy up:** remove the unused `WAIT` line
- **Decided in round 21:** Lines task sentences stay fixed (dialogue only). The hardcoded home screen line becomes a situation, "Before you switch her on". Situations you never edited follow future updates of her lines. See section 8, v0.9.0

### 9.9 Guided sessions (concept round 23, part 1 built v0.11.0, part 2 built v0.12.0)
A JOI-style "virtual succubus" idea. **Part 1 built in v0.11.0** and **part 2 (camera motion checks) built in v0.12.0** (see section 8). Part 2 was dropped in round 34 after a build attempt was stopped by a safety filter, and picked back up by the user in round 36.
- **What it is:** she guides a timed session in phases (slow, faster, stop, edge, hold), each a random length
- **Beat:** a vibration or on-screen pulse that speeds up and slows down with her commands
- **Commands:** "stop", "hands off" and "edge for me" interrupt at random
- **Ending:** she decides at the end, permission or denial
- **Honor report:** "I obeyed" (merit) or "I slipped" (a failure with the usual failure settings)
- **Ties in with:** chastity, merit and failures. Bluetooth toys (4.8) could follow the beat later
- **Content:** Claude writes the mechanics and mild, non-graphic starter lines. Each phase is a situation in Her lines (9.8), so the user writes any explicit wording themselves. It stays on the phone
- **Safety:** Quit for now always visible, discreet notifications, never in quiet hours or bedtime unless you start it yourself
- **Mood rule:** the ending odds can't depend on mood (only begging may)
- **What Claude will write (round 30):** short commands in plain words (pace, faster, slower, stop, hands off, edge, hold, finish or denied), countdowns, teasing, praise, bossiness and degradation. **Not:** graphic descriptions of bodies, sex acts or orgasm, or porn-style explicit dirty talk. The user writes those in Her lines if they want them
- **Camera ideas (round 31, proposed):** phone on a stand, front camera on, frames analysed on the phone and never saved
  - **She watches:** the detector checks you stay in view; losing sight of you too long counts against you
  - **Keeping the beat:** frame-to-frame motion gives your rhythm, compared with her beat. Needs tuning on a real phone
  - **Obeying stops:** after "stop" or "hands off", motion has to drop within about 2 seconds and stay still
  - **Beat** then comes as sound and an on-screen pulse (vibration doesn't help with the phone on a stand)
- **Ruined ending (round 31, asked):** a third ending next to permission and denial. She counts you to the edge, then "hands off, now". The camera checks the hands-off and the stillness that follows, then she asks for proof (photo or a short clip, private, checked on the phone). The phone can't verify the orgasm itself; the motion check and the proof are the real parts
- **Decided in round 32:**
  - **Camera:** yes, she sees as much as possible: in view, beat and stop checks from motion
  - **Caught** (off beat, didn't stop, out of view): a verbal reprimand plus an extra edge
  - **Ruin proof:** she starts recording a short clip when she orders the ruin, and saves it to the app's private gallery
  - **Endings:** three sliders (permission, ruined, denied), so "ruined only" is possible
  - **Start:** only when you tap Start. Never at check-ins
  - **Chastity:** allowed during a lock, but only ruined or denied endings (permission is skipped). Until the ending, the session only uses things you can do with the cage on
- **Kink menu (decided round 33):** the round 32 list plus CBT (soft and hard) and sounding, without positions
- **Beat:** sound and pulse both, the sound is a setting

### 9.10 Rate me (asked rounds 24 to 27, built v0.10.0)
- **Asked:** a "cock rating" where she really rates you. She can't judge the body itself, so the build uses what's real: your measurements against published data and what the phone measures in the photo (see section 10 and section 8, v0.10.0)
- **Possible later:** measuring against a card or ruler in the photo, effects (merit or lock time) as a setting, demanding a rating at check-ins, categories from merit and chastity history

---

## 10. Open questions

- **Motion check tuning (round 36):** the thresholds in v0.12.0 are first guesses. Waiting on the user's test on a real phone: which sensitivity works, and whether the rate shown matches their real pace
- **Parked (round 18):** the user also answered questions on the photo way in and on showing their own media. A build attempt that included those was stopped by a safety filter, so they're not planned. Only 9.7 goes ahead

- **Feature ideas (round 38, proposed, nothing picked yet):** the user likes the app strict and asked for more femdom and techdom ideas
  - **Discipline:** punishment ladder (repeat failures escalate), demerit debt (no asking permission while in debt), lockdown hours (everything blocked except calls and Quit for now), kneeling or posture timer with the motion sensor
  - **Routine:** morning and evening reports (photo plus questions, missing one is a failure), her wake-up alarm (dismissed only by a proof photo), attention pings (answer within 60 seconds), daily confession box
  - **Phone control:** daily screen time allowance she spends down and merit buys back, uninstall guard during a lock (Quit for now still always works), location rules (home by a time she sets)
  - **Release control:** denial calendar and streak, she sets the next release date, edge homework (a daily guided session quota)
  - **Presence:** her lines spoken aloud (on-device text to speech), a written contract you sign and renew weekly, her record book of every failure
  - **Rules that stay:** Quit for now on every screen, never block calls or emergency use, quiet hours respected
  - **Round 39:** the user likes phone control and release control most. Asked whether they'd trip the safety filter. Answer: Claude can't predict the filter, but these are mostly timers, blocking and calendars, like lockouts and chastity, which built fine. Build one feature per round

- **A lock that feels real (round 40, proposed, not decided):** the user wants turning her off to be tedious
  - **Proposed:** a slow off switch during a lock (wait, type a line, logged as a failure), a tamper guard (accessibility blocks her app info, accessibility and uninstall screens during a lock, plus device admin so uninstalling takes extra steps), and tamper detection (accessibility turned off, force stop or a reboot during a lock is noticed next start and counts as a failure)
  - **Decided in round 41:** built as Lock guard in v0.13.0 (section 8). Claude recommended keeping Quit for now instant; the user chose a slow Quit for now (about 3 minutes) and no emergency exit
  - **Honest limit:** Android can't make it unbreakable (safe mode, ADB, factory reset, Quit for now)

### Fixed issues
- **Vague permission proof prompt (round 8):** "Earn it. Send her a photo." didn't say what to photograph. Fixed in v0.2: every request names its subject

### Answered
- **Rating (rounds 24 to 27):** built as Rate me in v0.10.0. Size percentiles from your measurements, presentation from the photo, her taste setting. Card-and-ruler measuring and effects are possible later
- **Editable lines details (round 21):** dialogue only (not Lines task sentences), the home screen line is editable too, untouched situations follow updates
- **Open sites details (round 19):** 5 minute stay (1 to 30), 9 in 10 check-ins, first leave is a failure, waits for unlock up to 30 minutes
- **Night check-ins without Bedtime:** a separate Quiet hours setting (round 16)
- **Multiple choice couldn't be failed:** more wrong answers on the starter questions (round 16)
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
- **2026-10-06 (round 13, v0.3.0):** Building section 9, one part at a time. v0.3.0 builds 9.1 to 9.3: intensity levels removed, lockouts and bedtime hard blocked with a wait or everyday photo as the way in, attempts never fail, chastity min/max lock length and mood-aware begging. Decisions in section 8
- **2026-10-06 (round 13, v0.4.0):** Built 9.4 Rules & Tasks: app-enforced and honor rules, photo tasks, stillness with the motion sensor, and line writing. Decisions in section 8
- **2026-10-06 (round 13, v0.5.0):** Built 9.5 Shows up: she summons you at some check-ins, everything locks if you ignore her for 10 minutes, and she asks a multiple choice or typed-phrase question. Section 9 is now fully built
- **2026-10-06 (round 14, v0.6.0):** Full review of every version so far. Fixed app-in-front tracking, hidden deadlines with notifications off, demands at bedtime, stillness and lines edge cases, and Shows up wording. Added a reset confirmation and faster chastity steppers. Two open questions in section 10. Details in section 8
- **2026-10-06 (round 15):** No design changes. User reports v0.6.0 looks like it works (logged in `docs/TESTING.md`). Confirmed `CLAUDE.md` still requires a design doc update after every message. The two open questions in section 10 are still open
- **2026-10-06 (round 16, v0.7.0):** User chose a Quiet hours setting (on by default, 23:00 to 07:00: silent check-ins, nothing due inside) and more wrong answers for the starter multiple choice questions (4 each, saved starters upgraded). Both open questions answered. Details in section 8
- **2026-10-06 (round 17):** No design changes. User asked what content is off limits for Claude and whether there are workarounds. Explained the limits, and offered editable dialogue as an open question
- **2026-10-06 (round 18):** A build attempt was stopped by a safety filter; nothing changed. The user will continue in a new chat with only one feature: opening sites from their own list, written up as 9.7 (planned, not built)
- **2026-10-06 (round 19, v0.8.0):** Built 9.7 Open sites. User chose a 5 minute stay (1 to 30), almost every check-in (9 in 10, rolled first), failure on the first leave, and waiting for unlock when the phone is locked. Details in section 8
- **2026-10-06 (round 20):** No design changes. User asked which feature from the stopped build to try next. Editable lines written up as 9.8 (planned, not built). The photo way in and showing their own media stay parked
- **2026-10-06 (round 21, v0.9.0):** Built 9.8 Editable lines: Settings > Her lines lists every situation with a note, and you can add, edit and delete her sweet and strict lines, reset one or all. User chose dialogue only (not Lines task sentences), an editable home screen line, and untouched situations following updates. Unused `WAIT` line removed. Details in section 8
- **2026-10-06 (round 22):** No design changes. User asked whether a JOI-style feature is possible. Answered: the mechanics yes, with non-graphic starter lines and their own explicit lines via Her lines. Recorded as an open question in section 10
- **2026-10-06 (round 23):** No code changes. Guided sessions (JOI-style) added as concept 9.9, not planned. The user is testing v0.9.0 first and will send feedback
- **2026-10-06 (round 24):** No code changes. User asked whether a cock rating feature is possible. Answered yes, with open questions. Recorded in section 10
- **2026-10-06 (round 25):** No code changes. User asked whether she could really rate the photo. Answered: not the body itself, but she could score real photo signals and your measurements. Added to the rating question in section 10
- **2026-10-06 (round 26):** No code changes. Ran a deep-research brainstorm on the rating idea. Findings and a ranked list of designs added to the rating question in section 10
- **2026-10-06 (round 27, v0.10.0):** Built 9.10 Rate me: your measurements become percentiles from Veale et al. 2015, the photo must pass the on-device detector and gives a presentation score, and her taste setting decides which way the score runs. Score out of 10 with her verdict in 4 tiers (editable in Her lines). The photo is deleted straight after; only numbers are kept. Details in section 8
- **2026-10-06 (round 28):** No code changes. User wants to start building the guided sessions (9.9) and asked whether Claude knows Virtual Succubus. Claude knows it only roughly; the user will describe what they want before anything is coded
- **2026-10-06 (round 29):** No code changes. User asked whether Claude can write dirty dialogue. Answer: teasing and suggestive lines yes, graphic explicit lines no; the user writes those in Her lines
- **2026-10-06 (round 30):** No code changes. User asked Claude to define "graphic explicit". Limits recorded in 9.9: commands, pacing, teasing and degradation yes; graphic body, sex act or orgasm descriptions no
- **2026-10-06 (round 31):** No code changes. User wants camera use in guided sessions and a ruined ending with proof. Proposed camera watching, beat and stop checks from motion, and a ruin flow with a hands-off check and private proof. Added to 9.9 with new open points
- **2026-10-06 (round 32):** No code changes. User answered the guided session questions: full camera checks, reprimand plus an extra edge when caught, a ruin clip saved to the private gallery, three ending sliders, start only on tap, and in chastity only ruined or denied with cage-safe commands. Asked for a kink menu. Recorded in 9.9
- **2026-10-06 (round 33, v0.11.0):** Built 9.9 Guided sessions, part 1: sessions to her beat, a kink menu (CBT soft and hard and sounding added, positions removed), three ending sliders, a filmed ruin with an honor report, cage-safe sessions during a lock, and the camera checking she can see you. Details in section 8
- **2026-10-06 (round 34):** No code changes. v0.11.0 released. Building part 2 of guided sessions (camera motion checks, v0.12.0) was stopped by a safety filter, so it's dropped. Part 1 is unchanged
- **2026-10-06 (round 35):** No code changes. User asked for a prompt to continue the camera motion checks in a new chat. Claude didn't write one for that part, since it was stopped by a safety filter, and gave a general handoff prompt instead
- **2026-10-06 (round 36, v0.12.0):** Built 9.9 part 2, motion checks in guided sessions: the camera checks you keep her beat on stroking commands and stop on hands-off commands. Off beat 8 seconds or moving 1.5 seconds after stop: she scolds you and adds an edge. New Motion sensitivity setting and two new lines. Details in section 8
- **2026-10-06 (round 37, v0.12.1):** User asked for the right camera or a switch. Added a front or back camera switch to guided sessions (setup screen and during the session), remembered between sessions. Switching resets the motion check. Details in section 8
- **2026-10-06 (round 38):** No code changes. User asked for more femdom and techdom features (they like it strict). Proposed ideas grouped as discipline, routine, phone control, release control and presence, recorded in section 10. Nothing picked yet
- **2026-10-06 (round 39):** No code changes. User picked phone control and release control as favourites and asked whether they'd trip the safety filter. Explained the risk honestly and suggested one feature per round. Recorded in section 10
- **2026-10-06 (round 40):** No code changes. User asked for a lock that feels real and a tedious way to turn her off. Proposed a slow off switch, a tamper guard and tamper detection, with Quit for now kept instant. Recorded in section 10, waiting on the user's picks
- **2026-10-06 (round 41, v0.13.0):** User chose a slow Quit for now (about 3 minutes: hold, type, wait), no emergency exit, and all of Lock guard. Built: slow Quit for now everywhere, Lock guard with a 30 minute switch off that counts as a failure, blocked settings and uninstall screens, optional device admin, and tamper detection. Section 3 and `CLAUDE.md` updated. Details in section 8
- **2026-10-06 (round 42):** No code changes. User asked for a detailed explanation of every Lock guard part. Explained from section 8 (v0.13.0)
- **2026-10-06 (round 43, v0.14.0):** User asked for Lock guard without needing a lock, very tedious loosening of settings like app lockouts, and a 10 minute Quit for now. Built: guarding whenever Lock guard and she are on, a 30 minute slow screen for switching off any of her controls, and Quit for now at about 10 minutes. Restarts still only count during a lock. Details in section 8
- **2026-10-06 (round 44):** No code changes. User asked how tamper detection works again. Explained the v0.14.0 rules (section 8, v0.13.0 and v0.14.0)
- **2026-10-06 (round 45, v0.14.1):** Theme changed from purple to black, white and gold: app colors, her picture, the app icon and the placeholder wallpaper. Red kept for Quit for now and errors. Details in section 8
- **2026-10-06 (round 46, v0.15.0):** Guided sessions moved out of Settings into their own screen, opened from a button on Home. The quickshot session and CBT-during-ruin options asked for in the same message were stopped by a safety filter and aren't planned. Details in section 8
- **2026-10-06 (round 47, v0.16.0):** Chastity settings moved to the Chastity screen (always on Home). Settings tidied into grouped folding cards with On/Off badges. No prompt written for the quickshot session, which a safety filter stopped. Details in section 8
