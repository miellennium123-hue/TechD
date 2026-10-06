# Guardian Angel: Test Checklist

> On-device checks for the current version. Tick items as you go and report anything odd (what you tapped, what you expected, what happened, phone model and Android version).
> **Version under test:** 0.19.0 (bedtime screen). Sections 0 to 0n cover what 0.6.0 to 0.19.0 changed

**Tip:** keep the risky settings low while testing. Use Social media scope, short timers, and remember **Quit for now** (top right) ends everything instantly.

## 0n. New in 0.19.0: bedtime screen
Bedtime on, set to start 2 minutes from now. Settings > Bedtime > **Bedtime screen** on (default).
- [ ] Settings > About shows **Version 0.19.0**
- [ ] On the home screen when bedtime starts: within about 30 seconds her full-screen "Locked out" screen appears
- [ ] It shows her picture, her line, "Locked out until HH:MM" and the time left
- [ ] Buttons for your installed Always-allowed apps open them. **Phone** opens the dialer
- [ ] Back does nothing. Home brings her screen back
- [ ] Opening a blocked app (from recents or a notification) brings her screen, not the old block screen
- [ ] Lock the phone and unlock it: her screen is back
- [ ] When bedtime ends, the screen closes and you're on the home screen
- [ ] Quit for now on her screen works and closes it
- [ ] **Bedtime screen off:** bedtime uses the normal block screen again
- [ ] **Lock guard on:** switching Bedtime screen off outside bedtime opens the 30 minute screen. During bedtime it says "Not now"
- [ ] Her lines > Asking and locks has **Bedtime screen**, and editing it changes what she says

## 0m. New in 0.18.0: timed app blocks, no easy way in
Set check-ins to 30 min, App lockouts on (Social media), Shortest block 30m, Longest block 1h.
- [ ] Settings > About shows **Version 0.18.0**
- [ ] Settings > App lockouts has **Shortest block** and **Longest block**. Making shortest longer than longest moves longest up (and the other way)
- [ ] Right after switching lockouts on, social apps still open (no block yet)
- [ ] Within a check-in or two, a notification says her apps are locked. Home shows **Apps locked** with time left
- [ ] Opening Instagram during the block: time left shows, no Wait, no Send a photo, no Ask her (even with Ask permission on)
- [ ] With 15+ merit: **Spend 15 merit for 10 minutes** opens the app and merit drops by 15. With less, the button is greyed out
- [ ] With merit off: the screen says there's no way in
- [ ] When the block ends, the apps open again (or show Ask her, if Ask permission is on)
- [ ] **Bedtime:** inside the window, no buttons except Go home and Quit for now. "No way in until bedtime ends"
- [ ] **Lock guard on, during a block:** switching lockouts off, changing scope or block lengths, adding an Always-allowed app, or switching Lock guard off shows "Not now"
- [ ] **Lock guard on, during bedtime:** changing bedtime hours or switching it off shows "Not now"
- [ ] **Lock guard on, no block:** Everything to Social media, shorter blocks, or moving bedtime hours opens the 30 minute screen. Longer blocks change instantly
- [ ] Quit for now still ends a block

## 0l. New in 0.17.0: Quickshot
- [ ] Settings > About shows **Version 0.17.1**
- [ ] Guided sessions screen: under **Start a session** there's **Quickshot (always ruined)**, always visible
- [ ] With her off, or Guided sessions off, both buttons are greyed out and the line under them says which switch to turn on
- [ ] Its setup says about 2 minutes, always ruined, always filmed. Start stays greyed out until the camera is allowed
- [ ] With **She watches (camera)** off in settings, the quickshot still uses the camera and films the ruin
- [ ] Order: her quickshot line, fast stroking, faster, edge (tap "I'm at the edge"), 3 second countdown, ruin with REC showing
- [ ] After "Ruined, as ordered": +5 merit, and the clip is in Photos. "I couldn't stop" counts as a failure
- [ ] During a chastity lock: unlock first, cage back on after, then a cage photo
- [ ] History shows "quickshot ruined"
- [ ] Her lines > Guided sessions has **Quickshot starts**, and editing it changes what she says

## 0k. New in 0.16.0: Chastity screen and tidier Settings
- [ ] Settings > About shows **Version 0.16.0**
- [ ] Home always shows **Chastity**. Its screen has the lock and, below, all chastity settings
- [ ] Settings: master switch and Lock guard at the top, then grouped cards that fold open and shut with On/Off badges
- [ ] Reset is inside the About card
- [ ] With Lock guard on, switching chastity off still opens the 30 minute screen

## 0j. New in 0.15.0: Guided sessions screen
- [ ] Settings > About shows **Version 0.15.0**
- [ ] Home has a **Guided sessions** button. It opens a screen with Start, the toggle and every session setting
- [ ] Settings no longer has a Guided sessions section
- [ ] Kink menu opens from the new screen, and Back returns to it
- [ ] Your old session settings are kept

## 0i. New in 0.14.1: theme
- [ ] Settings > About shows **Version 0.14.1**
- [ ] Black backgrounds, gold buttons and switches, white text. No purple left anywhere
- [ ] Her picture shows clearly on black (gold outlines). App icon is black and gold
- [ ] Quit for now is still red and easy to spot

## 0h. New in 0.14.0: Lock guard without a lock
- [ ] Settings > About shows **Version 0.14.0**
- [ ] **Quit for now** waits about 9:30 after the hold and the sentence
- [ ] Lock guard on, no lock running: switching her off asks for the 30 minute way, and finishing it is a failure
- [ ] Switching off **App lockouts** (or any of her controls) opens "Loosen her control". Cancel: the switch stays on. Finish the 30 minutes: it switches off, no failure
- [ ] Adding an always-allowed app or switching Quiet hours on opens the same screen
- [ ] Switching things **on**, and changing details like times, is instant
- [ ] Switching Lock guard **off** takes the 30 minutes too
- [ ] Her App info and uninstall screens are blocked even without a lock
- [ ] Restart the phone **without** a lock: no tampering failure. **With** a lock: a failure

## 0g. New in 0.13.0: Lock guard and the slow Quit for now
Before any lock: if Lock guard's screen check doesn't work, switch her Accessibility off and on once (not during a lock, that's tampering).
- [ ] Settings > About shows **Version 0.13.0**
- [ ] **Quit for now** (any screen): hold 10 s (letting go resets), type the sentence (a typo starts over), wait 2:30 with her talking. Then "Quit for now" ends everything, no penalty
- [ ] Leaving the Quit screen (home button, screen off) starts it over. "Never mind, I'll stay" closes it with nothing changed
- [ ] Settings > **Lock guard** on. Start a chastity lock (or get a punishment)
- [ ] Lock guard, chastity, punishments, Rules & Tasks and Shows up can't be switched off in Settings
- [ ] The on/off switch asks for the 30 minute way. Finishing it is a failure, then she's off
- [ ] Open her App info (long-press her icon): she sends you home and back to her. Same for Settings > Accessibility and the uninstall dialog
- [ ] Permissions > **Device admin**: grant it. Uninstalling now asks to remove the admin first, and that screen is blocked during a lock
- [ ] Restart the phone during a lock: when she's back, it's a failure with her Tampering line
- [ ] After an app update during a lock: **no** tampering failure
- [ ] Outside a lock, nothing above blocks you, and switching her off is instant

## 0f. New in 0.12.0: Motion checks in Guided sessions
Phone on a stand, front camera facing you, decent light. Settings > Guided sessions: camera on, **She checks your motion** on, sensitivity Normal.
- [ ] Settings > About shows **Version 0.12.1**
- [ ] Session setup shows **Camera: front**. Tap **Switch** and it shows back. Start: the preview is the camera you picked
- [ ] During a session, **Use back camera** / **Use front camera** swaps the camera, and the swap itself doesn't get you caught
- [ ] Your camera choice is remembered next session
- [ ] During a stroke command, the line under the beat shows **She sees about N per minute**. Is N close to your real pace?
- [ ] Keep her beat for a whole command: you're never caught
- [ ] Go clearly faster or slower than her beat (or stop) for about 10 seconds: she scolds you **off beat** and adds an edge
- [ ] On **Stop** or **Hold still**: keep still and it shows **She sees you still**; you're never caught for breathing
- [ ] On **Stop**: keep moving past 2 seconds: she scolds you for **not stopping** and adds an edge
- [ ] Teasing, edging, CBT, sounding, countdowns and the ending are never motion-checked
- [ ] During a lock: moving during a hold gets caught, with a hold (not an edge) added
- [ ] Turn **She checks your motion** off: no "She sees" line and no motion catches
- [ ] Caught while still? Try **Low**. Moving and not noticed? Try **High**. Note which setting worked for you
- [ ] Caught at most 5 times per session in total

## 0e. New in 0.11.0: Guided sessions
- [ ] Settings > About shows **Version 0.11.0**
- [ ] Settings has a **Guided sessions** card: toggle, Length (10m, steps of 5), Kink menu, three ending sliders with percentages, Beat sound, She watches (camera)
- [ ] **Kink menu:** 13 kinks, each with a note and "Cage-safe", "Not in a cage" or "Locked only". CBT shows Soft / Hard when ticked
- [ ] Turn it on (with her on): **Start a session** appears on home
- [ ] **Setup screen** shows the length, ending shares and kinks. With sounding ticked in the menu, the sterile tick appears; without it, no sounding happens
- [ ] **The beat:** the pulse and tick follow the "per minute" number. Faster and slower commands change it
- [ ] **Edge:** "I'm at the edge" moves on to hands off
- [ ] **CBT:** the counter stops at the count. "Too much" skips
- [ ] **She watches:** prop the phone up facing you. Step out of view during a stroking command: within about 10 to 15 seconds she scolds you and adds an edge. **Tell me if she catches you when you're in view** (the detector may miss you while your hand covers you)
- [ ] **Ruined only:** set Permission and Denied to 0. The session ends with "Hands off, now", REC shows, then "Did you ruin it?". The clip is in Photos with a ▶ and plays
- [ ] **"I couldn't stop"** counts as a failure. "Ruined, as ordered" gives merit
- [ ] **Permission** ends with "You may finish" and Done. **Denied** ends with hands off
- [ ] **During a chastity lock:** no stroking commands until the end, never permission. A ruin starts with "Unlocked", ends with "Locked again", then "Show her the cage" opens the camera
- [ ] **Stop** ends it with no penalty. **Quit for now** works. Back does nothing. Rotating the phone doesn't restart it
- [ ] Screenshots are blocked, and the screen doesn't turn off during a session
- [ ] **Her lines:** a new "Guided sessions" group, and editing a line changes what she says
- [ ] Settings shows your last sessions, and **Clear session history** empties it

## 0d. New in 0.10.0: Rate me
- [ ] Settings has a **Rate me** card, off, with "She likes bigger / smaller" and "cm / Inches"
- [ ] Turn it on (with her on): **Rate me** appears on home
- [ ] **Measurements:** Next stays greyed out until both numbers are filled in and sensible. Switching cm to Inches converts what you typed
- [ ] **Photo of something else** (a wall, your hand): rejected with "That's not what she asked for". After 3 rejections, "Rate me on my numbers (presentation 0)" appears
- [ ] **A real photo:** she rates you. Scorecard shows a score out of 10, length and girth percentiles, and presentation out of 100
- [ ] **Average numbers** (13.1 cm and 11.7 cm, about 5.2 in and 4.6 in) show around the 50th percentile
- [ ] **Presentation reacts:** a centred, sharp, well-lit photo that fills a good part of the frame scores higher than a small, dark or off-centre one. **Tell me the presentation numbers you get**, so the scoring can be tuned
- [ ] **Taste:** switch to "She likes smaller" with the same numbers. Percentiles stay the same, her score changes
- [ ] **Photo deleted:** the rating photo never shows in Photos
- [ ] Screenshots are blocked on the Rate me screen
- [ ] **Her lines:** a new "Rate me" group (4 score tiers and "Couldn't see it"). Editing a tier changes her verdict
- [ ] Settings shows "Her last scores", and **Clear her scores** empties it
- [ ] Next time, your measurements are filled in already
- [ ] **Quit for now** on the Rate me screen works and keeps your score history

## 0c. New in 0.9.0: Her lines
- [ ] Settings > Presentation has a **Her lines** button. It opens a list of situations in groups (On and off, Asking and locks, Photo proof, and so on), each with a short note on when it's used
- [ ] Open **Praise**: Sweet shows her 3 sweet lines, Strict her 3 strict lines
- [ ] **Add** a line, **Edit** one, **Delete** one. The list in Settings now shows **Edited** next to Praise, and the Settings button says "(1 edited)"
- [ ] **Your lines are used:** delete all but one sweet greeting, set Mood to Sweet, switch her off and on. She says your line
- [ ] **Empty falls back:** delete every sweet line in a situation. The screen says she uses her built-in ones, and she still says one of hers
- [ ] **Reset this situation** puts her lines back and removes the Edited mark
- [ ] **Reset all** (on the list) asks first, then clears every edit
- [ ] **Discreet notifications on:** a check-in notification still says only "Time to check in." whatever you wrote
- [ ] **Before you switch her on:** on a fresh install the home screen shows a line from that situation (hard to test on an existing install; skip if so)
- [ ] **Reset all settings** also resets your lines
- [ ] Updating keeps your edited lines

## 0b. New in 0.8.0: Open sites
- [ ] Settings has an **Open sites** card, off, Stay for **5m**. The stepper goes 1m to 30m
- [ ] **Your sites:** starts empty. Add `example.com`: it's saved as `https://example.com`. Edit and Delete work. `not a site` and `mailto:...` can't be added
- [ ] **Ask her now** (or **Ask her to open a site** on home) shows the warning with a 10 second countdown, then opens the site in Chrome
- [ ] **Back** doesn't close the warning. Pressing **Home** during the countdown brings the warning back within a few seconds
- [ ] **Quit for now** on the warning ends it, no merit lost
- [ ] **Discreet on:** the warning shows only "Reminder, opening a page in". **Discreet off:** her picture, a line and the site name
- [ ] **Stay the full time** (set 1m to test): +3 merit and a "you may go" notification
- [ ] **Leave early** (Home, recents, or another app): counts as a failure right away (-5 merit) and the visit ends
- [ ] **Pauses, no failure:** open Guardian Angel during a visit (home shows "Back to the site" and the time left, not counting down), take a call, or turn the screen off. Unlocking takes you back to the page
- [ ] **Lockouts on Everything:** Chrome isn't blocked while she has a site open
- [ ] **Locked phone:** lock the phone before a check-in. The warning appears after you unlock (within 30 minutes), never on the lock screen
- [ ] **Quiet hours:** no visits inside quiet hours or bedtime, or within about 6 minutes before they start
- [ ] **Chance:** with sites on, nearly every check-in opens a site; tasks, summons and photos become rare
- [ ] **No Chrome:** with Chrome disabled, she uses your default browser and Settings says so
- [ ] **Watch for:** a site link that opens another app (YouTube, Play Store) counts as leaving. Tell me if that happens a lot

## 0a. New in 0.7.0 (quick checks)
- [ ] Settings has a **Quiet hours** card, on, 23:00 to 07:00
- [ ] **Quiet test:** set quiet hours to start 1 minute from now, check-ins every 30 min. No check-in notifications arrive inside the window
- [ ] **Near the start:** set quiet hours to start 20 minutes from now. Check-ins in those 20 minutes may summon you (10 min) but never ask for a 30 minute photo
- [ ] **Ask her for a task** still works inside quiet hours (you asked)
- [ ] Quiet hours don't block any app
- [ ] **Shows up:** multiple choice questions show 5 options. Three wrong answers count as a failure
- [ ] Her questions: your old starter questions now have 4 wrong answers each. Any question you edited is unchanged
- [ ] Question editor: a multiple choice question with 1 or 2 wrong answers shows a warning

## 0. New in 0.6.0 (quick checks)
- [ ] **Shade test:** lockouts on, grant yourself access to X (wait 60 seconds). Inside X, pull down the notification shade and close it. When the 10 minutes run out, X gets blocked within about 30 seconds without leaving it
- [ ] **Keyboard test:** same as above, but type something in X instead of pulling down the shade
- [ ] **Notifications off:** turn off notifications for the app. Check-ins never give you a task, a photo demand or a summons. Settings > Shows up says she can't call you, and **Try it now** opens her screen directly
- [ ] **Bedtime quiet:** Bedtime on and set to now, check-ins every 30 min. No check-in notifications arrive during the window
- [ ] **Stillness:** with less time left than the hold needs, Start is replaced by "Too late to finish before the deadline"
- [ ] **Lines:** finish a few lines, go back to home, open the task again. Your line count is kept
- [ ] **Task notification:** tapping it opens the task screen, not home
- [ ] **Reset all settings** asks before resetting
- [ ] **Chastity steppers:** Shortest lock goes 30m, 1h, 1h 30m ... 4h, 5h ... 24h, 36h, 48h. The hard cap goes 1h steps to 24h, then 12h steps
- [ ] Set the hard cap below the longest picked lock: a red warning appears

## 1. Install and setup (start here)
- [ ] Install over the old version from the [latest release](https://github.com/miellennium123-hue/TechD/releases/latest/download/guardian-angel.apk)
- [ ] Settings > About shows **Version 0.16.0**, and your old settings are still there
- [ ] Settings has no Gentle / Firm / Strict / Absolute choices anywhere
- [ ] Permissions screen: all four show **Granted** (Accessibility may need App info > ⋮ > Allow restricted settings first)

## 2. Safety (most important)
- [ ] **Quit for now** works from the home screen, the block screen and the camera screen
- [ ] After Quit: she's off, no timers, no pending photos, blocked apps open normally
- [ ] Phone dialer opens even with lockouts on Everything
- [ ] Android Settings, your launcher and your keyboard are never blocked

## 3. App lockouts (Social media scope)
- [ ] Opening X shows the block screen with **Wait 60 seconds** and **Send a photo instead**
- [ ] The wait counts down, then **Open now** lets you in
- [ ] The photo screen names an **everyday** prompt, never one marked Explicit
- [ ] Opening a blocked app several times never drops merit or shows a failure line
- [ ] Access lasts about 10 minutes (wait) or 15 minutes (photo), then she blocks again within about 30 seconds

## 4. Everything scope and Always-allowed
- [ ] Any normal app is blocked
- [ ] WhatsApp and apps you tick in Always-allowed open normally
- [ ] Adding your banking app to Always-allowed lets it open

## 5. Ask permission
- [ ] "Ask her" sometimes grants, sometimes denies, sometimes asks for a photo
- [ ] A deny shows "Ask again in 5:00" and counts down
- [ ] A photo request says **"Photograph: ..."** with a specific, non-explicit subject
- [ ] Sending the photo opens the app

## 6. Photo checks
- [ ] Finger over the lens: rejected as **too dark**
- [ ] Plain wall or ceiling: may be rejected as **blank** (fine either way, tell me which)
- [ ] Normal, sharp photo of a non-explicit prompt: accepted
- [ ] **Explicit prompt** (Settings > Photo proof > What she can ask for): a non-explicit photo is rejected
- [ ] **Explicit prompt:** a genuine photo is accepted. Report any wrong rejections, with the lighting and angle
- [ ] After 3 rejections, "Send anyway" appears and works
- [ ] Settings > Photos: thumbnails show the right way up, delete works

## 7. Chastity (set Shortest lock 30m, Longest picked lock 1h for a quick test)
- [ ] The Chastity screen says she picks between your shortest and longest lock
- [ ] "Lock me up" sets a timer in that range and asks for cage proof within 15 minutes
- [ ] Countdown shows on home and the Chastity screen
- [ ] **Beg to be released:** sometimes she lets you out, usually not. Mood set to Strict should feel harsher than Sweet
- [ ] After a denial, "You may beg again in 10:00" counts down
- [ ] With "She can add time" on: some denials add your amount. Hard cap is never passed
- [ ] With "She can add time" on: missing a proof deadline adds time and drops merit
- [ ] When the timer ends, you get a notification and "Release me" gives merit

## 7b. Rules & Tasks (Settings > Rules & Tasks on)
Use **Ask her for a task** on the home screen to get one now. Quit for now clears any task.
- [ ] Settings > Rules & Tasks > Her list: add, edit and delete work, Reset brings back 12
- [ ] **Enforced rule** ("No social media..."): social apps are blocked with "locked by her rule", no wait or photo option. When time is up you get merit
- [ ] **Honor rule:** when time is up, a notification asks you to report. "I obeyed" gives merit, "I broke it" counts as a failure
- [ ] **Photo task:** shows on home as "Her task" with the task as the photo subject
- [ ] **Stillness:** 5 second countdown, then holding still passes. Tilting the phone (about 15 degrees or more) fails. Leaving the screen just cancels. Tell me if normal holding fails you
- [ ] **Lines:** a typo sends you back to line 1. Pasting is ignored. Keyboard shows no suggestions. Finishing gives merit
- [ ] Let a task's deadline pass: it counts as a failure, and the next lines task is harder
- [ ] With the toggle on and check-ins every 30 min, a task arrives at roughly 1 in 3 check-ins

## 7c. Shows up (Settings > Shows up on)
Use **Try it now** in Settings to summon her.
- [ ] A notification arrives. Discreet on: it only says "Please open the app." Tapping it opens her full screen
- [ ] Multiple choice: the right answer gives merit and closes the visit
- [ ] Phrase: typing it exactly is accepted, a missing word is wrong
- [ ] Wrong answers show tries left ("2 tries left", then "Last try."). The third wrong answer is a failure and ends the visit
- [ ] Tap **Later** and wait 10 minutes: other apps get blocked with an **Answer her** button. Phone dialer and Always-allowed apps still open
- [ ] Answering lifts the lock right away
- [ ] Quit for now on her screen ends everything
- [ ] Settings > Shows up > Her questions: add, edit, delete and reset work

## 8. Check-ins and notifications (set frequency to 30 min)
- [ ] A check-in arrives within 30 minutes, even with the screen off
- [ ] **Discreet on:** notifications only say neutral things ("Reminder", "Time to check in")
- [ ] **Discreet off:** notifications show her actual line
- [ ] "I'm here and being good" on the home screen clears it and adds merit
- [ ] With Bedtime on, check-ins inside the bedtime window are silent

## 9. Bedtime
- [ ] Set the window to start 2 minutes from now. Within about 30 seconds of it starting, the app you're in gets blocked
- [ ] Block screen shows "Until HH:MM" with the right end time

## 10. Wallpaper
- [ ] **Set only:** "Apply now" sets the placeholder wallpaper (home and lock screen)
- [ ] **Set and lock:** change your wallpaper manually. Open any app and it changes back within about 30 seconds

## 11. Survives restarts
- [ ] Reboot the phone: she's still on, chastity timer intact, check-ins keep coming
- [ ] Next update: installs over this version without uninstalling, settings kept

## Results log
| Date | Version | Item | Result |
|---|---|---|---|
| 2026-10-06 | 0.6.0 | General use | User: "Looks like it works." No specific checklist items reported yet |
| 2026-10-06 | 0.17.0 | Quickshot button | User couldn't see it (hidden while her or Guided sessions were off). Fixed in 0.17.1 |
