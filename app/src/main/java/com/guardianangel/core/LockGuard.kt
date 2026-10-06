package com.guardianangel.core

import com.guardianangel.data.GuardianConfig
import com.guardianangel.data.GuardianState
import com.guardianangel.data.LockoutScope

/**
 * Lock guard and the slow Quit for now (rounds 41 and 43). Pure Kotlin, tested in LockGuardTest.
 * Quit for now is always reachable and always finishes; it just takes about 10 minutes.
 */
object LockGuard {
    /** Quit for now: hold, type her line, then wait with her screen open. About 10 minutes (round 43). */
    const val QUIT_HOLD_SECONDS = 10
    const val QUIT_WAIT_SECONDS = 570
    const val QUIT_SENTENCE = "I am giving up on my angel for now, and I know she will be disappointed in me."

    /** Switching her off with Lock guard on: slower than Quit for now, and it counts as a failure. */
    const val OFF_HOLD_SECONDS = 10
    const val OFF_WAIT_SECONDS = 30 * 60
    const val OFF_SENTENCE = "I am switching my angel off against her wishes, and I accept her punishment for it."

    /** Loosening one of her controls with Lock guard on: the same 30 minutes, not a failure. */
    const val LOOSEN_SENTENCE = "I am asking my angel to loosen her control over me, and I will wait as long as she wants."

    /** She says something new this often while you wait. */
    const val TALK_EVERY_SECONDS = 15

    /** She's in charge right now: a chastity lock, a punishment, one of her rules, or a summons. */
    fun locked(config: GuardianConfig, state: GuardianState, now: Long): Boolean =
        config.enabled && (
            state.chastity != null ||
                state.punishmentUntil > now ||
                (state.task?.ruleUntil ?: 0L) > now ||
                state.summons != null
            )

    /** Guarded right now: Lock guard on and she's on (round 43: no lock needed). */
    fun guarding(config: GuardianConfig): Boolean = config.enabled && config.lockGuard

    /** Settings and uninstall screens. When one shows her name while guarding, she sends you away. */
    fun guardedPackage(pkg: String): Boolean =
        pkg.contains("settings") || pkg.contains("packageinstaller") || pkg.contains("securitycenter") || pkg.contains("safecenter")

    /**
     * Whether a settings change loosens her control: one of her controls switched off, Quiet hours
     * switched on, or a new always-allowed app. Since round 53 also: lockout scope narrowed to social
     * media, shorter blocks, and any change to bedtime hours while bedtime is on. Those take the slow
     * way while guarding. Anything that makes her stricter, and every other detail, changes instantly.
     */
    fun loosens(before: GuardianConfig, after: GuardianConfig): Boolean {
        fun off(b: Boolean, a: Boolean) = b && !a
        val b = before.lockouts
        val a = after.lockouts
        val bedtimeMoved = before.bedtime.on && after.bedtime.on &&
            (before.bedtime.startMinute != after.bedtime.startMinute || before.bedtime.endMinute != after.bedtime.endMinute)
        return off(before.lockGuard, after.lockGuard) ||
            (b.scope == LockoutScope.EVERYTHING && a.scope != LockoutScope.EVERYTHING) ||
            a.minBlockMinutes < b.minBlockMinutes ||
            a.maxBlockMinutes < b.maxBlockMinutes ||
            bedtimeMoved ||
            off(before.lockouts.on, after.lockouts.on) ||
            off(before.askPermission.on, after.askPermission.on) ||
            off(before.bedtime.on, after.bedtime.on) ||
            off(before.wallpaper.on, after.wallpaper.on) ||
            off(before.chastity.on, after.chastity.on) ||
            off(before.photoProof.on, after.photoProof.on) ||
            off(before.tasksOn, after.tasksOn) ||
            off(before.showsUpOn, after.showsUpOn) ||
            off(before.sitesOn, after.sitesOn) ||
            off(before.degradation.on, after.degradation.on) ||
            off(before.punishment.on, after.punishment.on) ||
            off(!before.quietHours.on, !after.quietHours.on) ||
            !before.alwaysAllowed.containsAll(after.alwaysAllowed)
    }

    /**
     * Option C (round 53): while her timed block or bedtime is running, its settings can't be changed
     * at all, no app can be added to Always-allowed, and Lock guard can't be switched off. Checked while guarding, before [loosens].
     * Returns what's frozen, for her refusal, or null if the change can go ahead.
     */
    fun frozen(before: GuardianConfig, after: GuardianConfig, state: GuardianState, now: Long, minuteOfDay: Int): String? {
        // Switching Lock guard off would undo all of this, so it waits too.
        val loosened = !before.alwaysAllowed.containsAll(after.alwaysAllowed) || (before.lockGuard && !after.lockGuard)
        if (Rules.isBedtime(before.bedtime, minuteOfDay) && (before.bedtime != after.bedtime || loosened)) {
            return "Bedtime is running. Bedtime, Always-allowed and Lock guard are locked until it ends."
        }
        if (Rules.blockRunning(before, state, now) && (before.lockouts != after.lockouts || loosened)) {
            return "Her app block is running. App lockouts, Always-allowed and Lock guard are locked until it ends."
        }
        return null
    }

    /**
     * Her watch started again during a lock: the phone restarted, the app was force stopped, or
     * Accessibility was switched off and on. Only during a lock ([guarding] here means Lock guard
     * on and a lock running), since phones restart for updates and flat batteries. Not after an app
     * update ([lastVersion] differs) or the first start, and not twice for the same break ([offNoticed]).
     */
    fun tamperOnStart(guarding: Boolean, lastVersion: Int, version: Int, offNoticed: Boolean): Boolean =
        guarding && lastVersion != 0 && lastVersion == version && !offNoticed

    /** Her watch (Accessibility) is off while guarding. Counted once until it comes back. */
    fun tamperWhenOff(guarding: Boolean, watchOn: Boolean, offNoticed: Boolean): Boolean =
        guarding && !watchOn && !offNoticed
}
