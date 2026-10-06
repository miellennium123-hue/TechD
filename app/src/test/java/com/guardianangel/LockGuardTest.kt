package com.guardianangel

import com.guardianangel.core.Line
import com.guardianangel.core.LockGuard
import com.guardianangel.core.Voice
import com.guardianangel.data.ActiveTask
import com.guardianangel.data.ChastityLock
import com.guardianangel.data.ChastitySettings
import com.guardianangel.data.GuardianConfig
import com.guardianangel.data.GuardianState
import com.guardianangel.data.LockoutSettings
import com.guardianangel.data.Mood
import com.guardianangel.data.PunishmentSettings
import com.guardianangel.data.QuietHoursSettings
import com.guardianangel.data.TaskKind
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LockGuardTest {
    private val now = 1_000_000L
    private val on = GuardianConfig(enabled = true, lockGuard = true)
    private val caged = GuardianState(chastity = ChastityLock(startedAt = 0, endsAt = now + 60_000))

    @Test
    fun whatCountsAsLocked() {
        assertFalse(LockGuard.locked(on, GuardianState(), now))
        assertTrue(LockGuard.locked(on, caged, now))
        assertTrue(LockGuard.locked(on, GuardianState(punishmentUntil = now + 1), now))
        assertFalse(LockGuard.locked(on, GuardianState(punishmentUntil = now - 1), now))
        val rule = ActiveTask(id = 1, text = "x", kind = TaskKind.RULE, issuedAt = 0, dueAt = now + 1, ruleUntil = now + 1)
        assertTrue(LockGuard.locked(on, GuardianState(task = rule), now))
        // Switched off, nothing is locked.
        assertFalse(LockGuard.locked(on.copy(enabled = false), caged, now))
    }

    @Test
    fun guardingNeedsOnlyTheToggleAndHer() {
        assertTrue(LockGuard.guarding(on))
        assertFalse(LockGuard.guarding(on.copy(lockGuard = false)))
        assertFalse(LockGuard.guarding(on.copy(enabled = false)))
    }

    @Test
    fun switchingOffHerControlsLoosens() {
        val strict = on.copy(
            lockouts = LockoutSettings(on = true),
            chastity = ChastitySettings(on = true),
            punishment = PunishmentSettings(on = true),
            tasksOn = true,
            showsUpOn = true,
            sitesOn = true,
            quietHours = QuietHoursSettings(on = false),
        )
        listOf(
            strict.copy(lockGuard = false),
            strict.copy(lockouts = LockoutSettings(on = false)),
            strict.copy(chastity = ChastitySettings(on = false)),
            strict.copy(punishment = PunishmentSettings(on = false)),
            strict.copy(tasksOn = false),
            strict.copy(showsUpOn = false),
            strict.copy(sitesOn = false),
            strict.copy(quietHours = QuietHoursSettings(on = true)),
            strict.copy(alwaysAllowed = strict.alwaysAllowed + "com.instagram.android"),
        ).forEach { assertTrue(it.toString(), LockGuard.loosens(strict, it)) }
    }

    @Test
    fun strictnessAndDetailsDontLoosen() {
        val before = on.copy(lockGuard = false, chastity = ChastitySettings(on = false))
        assertFalse(LockGuard.loosens(before, before.copy(lockGuard = true)))
        assertFalse(LockGuard.loosens(before, before.copy(chastity = ChastitySettings(on = true))))
        assertFalse(LockGuard.loosens(before, before.copy(meritOn = !before.meritOn)))
        assertFalse(LockGuard.loosens(before, before.copy(alwaysAllowed = emptySet())))
        assertFalse(LockGuard.loosens(before, before))
    }

    @Test
    fun guardedScreens() {
        assertTrue(LockGuard.guardedPackage("com.android.settings"))
        assertTrue(LockGuard.guardedPackage("com.google.android.packageinstaller"))
        assertTrue(LockGuard.guardedPackage("com.miui.securitycenter"))
        assertFalse(LockGuard.guardedPackage("com.google.android.dialer"))
        assertFalse(LockGuard.guardedPackage("com.guardianangel"))
        assertFalse(LockGuard.guardedPackage("com.instagram.android"))
    }

    @Test
    fun restartDuringALockIsTampering() {
        assertTrue(LockGuard.tamperOnStart(guarding = true, lastVersion = 16, version = 16, offNoticed = false))
    }

    @Test
    fun updatesFirstStartsAndOpenLocksAreNot() {
        assertFalse(LockGuard.tamperOnStart(guarding = true, lastVersion = 15, version = 16, offNoticed = false))
        assertFalse(LockGuard.tamperOnStart(guarding = true, lastVersion = 0, version = 16, offNoticed = false))
        assertFalse(LockGuard.tamperOnStart(guarding = false, lastVersion = 16, version = 16, offNoticed = false))
        // Already punished for switching her watch off: coming back isn't a second failure.
        assertFalse(LockGuard.tamperOnStart(guarding = true, lastVersion = 16, version = 16, offNoticed = true))
    }

    @Test
    fun watchOffIsCountedOnce() {
        assertTrue(LockGuard.tamperWhenOff(guarding = true, watchOn = false, offNoticed = false))
        assertFalse(LockGuard.tamperWhenOff(guarding = true, watchOn = false, offNoticed = true))
        assertFalse(LockGuard.tamperWhenOff(guarding = true, watchOn = true, offNoticed = false))
        assertFalse(LockGuard.tamperWhenOff(guarding = false, watchOn = false, offNoticed = false))
    }

    @Test
    fun quitIsSlowButBounded() {
        val total = LockGuard.QUIT_HOLD_SECONDS + LockGuard.QUIT_WAIT_SECONDS
        assertTrue(total in 540..660)
        assertTrue(LockGuard.OFF_WAIT_SECONDS > total)
    }

    @Test
    fun newLinesHaveBothMoods() {
        listOf(Line.QUIT_TALK, Line.OFF_TALK, Line.LOOSEN_TALK, Line.ADMIN_OFF, Line.GUARDED, Line.TAMPERED).forEach {
            assertTrue(it.name, Voice.builtIn(it, Mood.SWEET).isNotEmpty())
            assertTrue(it.name, Voice.builtIn(it, Mood.STRICT).isNotEmpty())
        }
    }

    @Test
    fun olderSavedDataLoads() {
        val json = Json { ignoreUnknownKeys = true }
        assertFalse(json.decodeFromString(GuardianConfig.serializer(), "{\"enabled\":true}").lockGuard)
        val st = json.decodeFromString(GuardianState.serializer(), "{\"merit\":3}")
        assertEquals(0, st.guardVersion)
        assertFalse(st.tamperOffNoticed)
    }
}
