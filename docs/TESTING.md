# Guardian Angel: Test Checklist

> On-device checks for the current version. Tick items as you go and report anything odd (what you tapped, what you expected, what happened, phone model and Android version).
> **Version under test:** 0.7.0 (adds Quiet hours, harder Shows up questions). Section 0 covers what 0.6.0 and 0.7.0 changed

**Tip:** keep the risky settings low while testing. Use Social media scope, short timers, and remember **Quit for now** (top right) ends everything instantly.

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
- [ ] Settings > About shows **Version 0.7.0**, and your old settings are still there
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
