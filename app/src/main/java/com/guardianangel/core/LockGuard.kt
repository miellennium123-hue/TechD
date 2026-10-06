package com.guardianangel.core

import com.guardianangel.data.GuardianConfig
import com.guardianangel.data.GuardianState

/**
 * Lock guard and the slow Quit for now (round 41). Pure Kotlin, tested in LockGuardTest.
 * Quit for now is always reachable and always finishes; it just takes about 3 minutes.
 */
object LockGuard {
    /** Quit for now: hold, type her line, then wait with her screen open. About 3 minutes. */
    const val QUIT_HOLD_SECONDS = 10
    const val QUIT_WAIT_SECONDS = 150
    const val QUIT_SENTENCE = "I am giving up on my angel for now, and I know she will be disappointed in me."

    /** Switching her off during a lock with Lock guard on: slower, and it counts as a failure. */
    const val OFF_HOLD_SECONDS = 10
    const val OFF_WAIT_SECONDS = 30 * 60
    const val OFF_SENTENCE = "I am switching my angel off in the middle of her lock, and I accept her punishment for it."

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

    /** Guarded right now: Lock guard on and a lock running. */
    fun guarding(config: GuardianConfig, state: GuardianState, now: Long): Boolean =
        config.lockGuard && locked(config, state, now)

    /** Settings and uninstall screens. When one shows her name during a lock, she sends you away. */
    fun guardedPackage(pkg: String): Boolean =
        pkg.contains("settings") || pkg.contains("packageinstaller") || pkg.contains("securitycenter") || pkg.contains("safecenter")

    /**
     * While guarding, Settings can't end the lock: Lock guard, chastity, punishments, Rules & Tasks
     * and Shows up stay on. Everything else can still change, and Quit for now still ends it all.
     */
    fun keepGuard(before: GuardianConfig, after: GuardianConfig, guarding: Boolean): GuardianConfig {
        if (!guarding) return after
        return after.copy(
            lockGuard = after.lockGuard || before.lockGuard,
            chastity = if (before.chastity.on && !after.chastity.on) after.chastity.copy(on = true) else after.chastity,
            punishment = if (before.punishment.on && !after.punishment.on) after.punishment.copy(on = true) else after.punishment,
            tasksOn = after.tasksOn || before.tasksOn,
            showsUpOn = after.showsUpOn || before.showsUpOn,
        )
    }

    /**
     * Her watch started again during a lock: the phone restarted, the app was force stopped, or
     * Accessibility was switched off and on. Not after an app update ([lastVersion] differs) or the
     * first start, and not twice for the same break ([offNoticed]).
     */
    fun tamperOnStart(guarding: Boolean, lastVersion: Int, version: Int, offNoticed: Boolean): Boolean =
        guarding && lastVersion != 0 && lastVersion == version && !offNoticed

    /** Her watch (Accessibility) is off during a lock. Counted once until it comes back. */
    fun tamperWhenOff(guarding: Boolean, watchOn: Boolean, offNoticed: Boolean): Boolean =
        guarding && !watchOn && !offNoticed
}
