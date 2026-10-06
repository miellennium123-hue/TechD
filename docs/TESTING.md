# Guardian Angel: Test Checklist

> On-device checks for the current version. Tick items as you go and report anything odd (what you tapped, what you expected, what happened, phone model and Android version).
> **Version under test:** 0.2.2

**Tip:** keep the risky settings low while testing. Use Social media scope, short timers, and remember **Quit for now** (top right) ends everything instantly.

## 1. Install and setup (start here)
- [ ] Uninstall the old version, install from the [latest release](https://github.com/miellennium123-hue/TechD/releases/latest/download/guardian-angel.apk)
- [ ] Settings > About shows **Version 0.2.2**
- [ ] Permissions screen: all four show **Granted** (Accessibility may need App info > ⋮ > Allow restricted settings first)

## 2. Safety (most important)
- [ ] **Quit for now** works from the home screen, the block screen and the camera screen
- [ ] After Quit: she's off, no timers, no pending photos, blocked apps open normally
- [ ] Phone dialer opens even with lockouts on Everything
- [ ] Android Settings, your launcher and your keyboard are never blocked

## 3. App lockouts (Social media scope)
- [ ] **Gentle:** X shows a warning with "Continue anyway", which lets you in
- [ ] **Firm:** "Wait 60 seconds" counts down, then "Open now" works
- [ ] **Firm:** "Send photo proof instead" names what to photograph
- [ ] **Strict:** no way in (unless Ask permission is on)
- [ ] **Absolute:** opening X drops merit and shows a failure line (at most once per 15 min)
- [ ] Access lasts about 10 minutes, then she blocks again within about 30 seconds

## 4. Everything scope and Always-allowed
- [ ] Any normal app is blocked
- [ ] WhatsApp and apps you tick in Always-allowed open normally
- [ ] Adding your banking app to Always-allowed lets it open

## 5. Ask permission
- [ ] "Ask her" sometimes grants, sometimes denies, sometimes asks for a photo (depends on intensity)
- [ ] A deny shows "Ask again in 5:00" and counts down
- [ ] A photo request says **"Photograph: ..."** with a specific subject
- [ ] Sending the photo opens the app

## 6. Photo checks
- [ ] Finger over the lens: rejected as **too dark**
- [ ] Plain wall or ceiling: may be rejected as **blank** (fine either way, tell me which)
- [ ] Normal, sharp photo of a non-explicit prompt: accepted
- [ ] **Explicit prompt** (Settings > Photo proof > What she can ask for): a non-explicit photo is rejected
- [ ] **Explicit prompt:** a genuine photo is accepted. Report any wrong rejections, with the lighting and angle
- [ ] After 3 rejections, "Send anyway" appears and works
- [ ] Settings > Photos: thumbnails show the right way up, delete works

## 7. Chastity (set Longest lock to 1h for a quick test)
- [ ] "Lock me up" sets a timer and asks for cage proof within 15 minutes
- [ ] Countdown shows on home and the Chastity screen
- [ ] **Gentle:** "Unlock early" works. **Firm:** asks you to wait 10 minutes first. **Strict:** denied
- [ ] With "She can add time" on: missing a proof deadline adds time and drops merit
- [ ] When the timer ends, you get a notification and "Release me" gives merit

## 8. Check-ins and notifications (set frequency to 30 min)
- [ ] A check-in arrives within 30 minutes, even with the screen off
- [ ] **Discreet on:** notifications only say neutral things ("Reminder", "Time to check in")
- [ ] **Discreet off:** notifications show her actual line
- [ ] "I'm here and being good" on the home screen clears it and adds merit

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
| | | | |
