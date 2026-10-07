package com.guardianangel.core

import com.guardianangel.data.GuardianConfig
import com.guardianangel.data.GuardianState
import com.guardianangel.data.LockoutScope
import com.guardianangel.data.PornBlockSettings

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

    /** She's in charge right now: a chastity lock, a punishment, one of her rules, a summons, or her porn block lock. */
    fun locked(config: GuardianConfig, state: GuardianState, now: Long): Boolean =
        config.enabled && (
            state.chastity != null ||
                state.punishmentUntil > now ||
                PornBlock.locked(config, state, now) ||
                (state.task?.ruleUntil ?: 0L) > now ||
                state.summons != null
            )

    /** Guarded right now: Lock guard on and she's on (round 43: no lock needed). Debug mode doesn't change this (round 57). */
    fun guarding(config: GuardianConfig): Boolean = config.enabled && config.lockGuard

    /** Debug mode only changes while she's off, so it's never a way out of her (round 56). */
    fun canSetDebug(config: GuardianConfig): Boolean = !config.enabled

    /** Settings and uninstall screens. When one shows her name while guarding, she sends you away. */
    fun guardedPackage(pkg: String): Boolean =
        pkg.contains("settings") || pkg.contains("packageinstaller") || pkg.contains("securitycenter") || pkg.contains("safecenter")

    /**
     * Whether a settings change loosens her control: one of her controls switched off, Quiet hours
     * switched on, or a new always-allowed app. Since round 53 also: lockout scope narrowed to social
     * media, shorter blocks, and any change to bedtime hours while bedtime is on. Since round 68 also:
     * higher daily report goals, or an F no longer a failure. Since round 73 also: any Porn block
     * switch off, or a shorter lock. Since round 74 also: checking less often, an app off her list,
     * an adult app taken off, her hours switched on, or her hours moved. Since round 77 also: her clips
     * at check-ins or on her lock screens switched off. Since round 84 also: punishment sessions, the ruin
     * after a catch, booked sessions or her training switched off. Those take the slow way while guarding.
     * Anything that makes her stricter, and every other detail, changes instantly.
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
            off(before.bedtime.screen, after.bedtime.screen) ||
            off(before.wallpaper.on, after.wallpaper.on) ||
            off(before.mark.on, after.mark.on) ||
            off(before.mark.tint, after.mark.tint) ||
            off(before.peek.on, after.peek.on) ||
            off(before.session.watchAtCheckIns, after.session.watchAtCheckIns) ||
            off(before.session.watchOnLockScreens, after.session.watchOnLockScreens) ||
            off(before.session.punishmentSessions, after.session.punishmentSessions) ||
            off(before.session.ruinAfterCatch, after.session.ruinAfterCatch) ||
            off(before.session.booked, after.session.booked) ||
            off(before.session.training, after.session.training) ||
            off(before.report.on, after.report.on) ||
            off(before.pornBlock.on, after.pornBlock.on) ||
            off(before.pornBlock.lockScreen, after.pornBlock.lockScreen) ||
            off(before.pornBlock.failure, after.pornBlock.failure) ||
            off(before.pornBlock.privateTabs, after.pornBlock.privateTabs) ||
            after.pornBlock.lockMinutes < before.pornBlock.lockMinutes ||
            pornLoosens(before.pornBlock, after.pornBlock) ||
            off(before.report.failOnF, after.report.failOnF) ||
            after.report.unlockGoal > before.report.unlockGoal ||
            after.report.screenGoalMinutes > before.report.screenGoalMinutes ||
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

    /** Round 74: Porn block's lists, check rate and hours. */
    private fun pornLoosens(b: PornBlockSettings, a: PornBlockSettings): Boolean {
        val hoursMoved = b.hoursOn && a.hoursOn && (b.startMinute != a.startMinute || b.endMinute != a.endMinute)
        return PornBlock.scanSeconds(a) > PornBlock.scanSeconds(b) ||
            !a.watched.containsAll(b.watched) ||
            !b.unwatched.containsAll(a.unwatched) ||
            !a.adultApps.containsAll(b.adultApps) ||
            (!b.hoursOn && a.hoursOn) ||
            hoursMoved
    }

    /**
     * Option C (round 53): while her timed block or bedtime (since round 73 also her porn block lock) is running, its settings can't be changed
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
        if (PornBlock.locked(before, state, now) && (before.pornBlock != after.pornBlock || loosened)) {
            return "She caught you, so your phone is locked. Porn block, Always-allowed and Lock guard are locked until it ends."
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
