package com.guardianangel

import com.guardianangel.core.LockGuard
import com.guardianangel.core.Rules
import com.guardianangel.data.ActiveTask
import com.guardianangel.data.BedtimeSettings
import com.guardianangel.data.GuardianConfig
import com.guardianangel.data.GuardianState
import com.guardianangel.data.LockoutSettings
import com.guardianangel.data.MarkCorner
import com.guardianangel.data.MarkSettings
import com.guardianangel.data.Question
import com.guardianangel.data.QuestionKind
import com.guardianangel.data.RuleEnforcement
import com.guardianangel.data.Summons
import com.guardianangel.data.TaskKind
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Her mark (round 60): the collar badge and the dark tint during her blocks. */
class MarkTest {
    private val now = 1_000_000_000L
    private val noon = 12 * 60
    private val on = GuardianConfig(enabled = true, mark = MarkSettings(on = true))

    private fun tinted(config: GuardianConfig, state: GuardianState, minute: Int = noon, ownApp: Boolean = false) =
        Rules.markTinted(config, state, now, minute, ownApp)

    @Test
    fun badgeOnlyWhileSheAndHerMarkAreOnAndNotOverHerScreens() {
        assertTrue(Rules.markShown(on))
        assertFalse(Rules.markShown(on.copy(enabled = false)))
        assertFalse(Rules.markShown(on.copy(mark = MarkSettings(on = false))))
        assertFalse(Rules.markShown(GuardianConfig(enabled = true)))
        assertFalse(Rules.markShown(on, ownApp = true))
    }

    @Test
    fun noTintWithoutABlock() {
        assertFalse(tinted(on, GuardianState()))
    }

    @Test
    fun tintDuringHerTimedBlock() {
        val config = on.copy(lockouts = LockoutSettings(on = true))
        assertTrue(tinted(config, GuardianState(lockoutUntil = now + 60_000)))
        assertFalse(tinted(config, GuardianState(lockoutUntil = now - 1)))
    }

    @Test
    fun tintDuringBedtime() {
        val config = on.copy(bedtime = BedtimeSettings(on = true, startMinute = 23 * 60, endMinute = 7 * 60))
        assertTrue(tinted(config, GuardianState(), minute = 2 * 60))
        assertFalse(tinted(config, GuardianState(), minute = noon))
    }

    @Test
    fun tintDuringPunishmentRulesAndIgnoredSummons() {
        assertTrue(tinted(on, GuardianState(punishmentUntil = now + 1)))
        val rule = ActiveTask(1, "No social media", TaskKind.RULE, now, now + 60_000, ruleUntil = now + 60_000, enforce = RuleEnforcement.SOCIAL_MEDIA)
        assertTrue(tinted(on, GuardianState(task = rule)))
        assertFalse(tinted(on, GuardianState(task = rule.copy(enforce = RuleEnforcement.NONE))))
        val summons = Summons(1, now - 60_000, now - 1, Question("Who owns you?", QuestionKind.PHRASE, "You"))
        assertTrue(tinted(on, GuardianState(summons = summons)))
        assertFalse(tinted(on, GuardianState(summons = summons.copy(lockAt = now + 60_000))))
    }

    @Test
    fun noTintOverHerOwnScreensOrWithTintOff() {
        val state = GuardianState(punishmentUntil = now + 1)
        assertFalse(tinted(on, state, ownApp = true))
        assertFalse(tinted(on.copy(mark = MarkSettings(on = true, tint = false)), state))
        assertFalse(tinted(on.copy(mark = MarkSettings(on = false)), state))
        assertFalse(tinted(on.copy(enabled = false), state))
    }

    @Test
    fun switchingHerMarkOffLoosens() {
        val before = on
        assertTrue(LockGuard.loosens(before, before.copy(mark = MarkSettings(on = false))))
        assertTrue(LockGuard.loosens(before, before.copy(mark = MarkSettings(on = true, tint = false))))
        assertFalse(LockGuard.loosens(before, before.copy(mark = MarkSettings(on = true, corner = MarkCorner.BOTTOM_LEFT))))
        assertFalse(LockGuard.loosens(GuardianConfig(enabled = true), on))
    }

    @Test
    fun olderSavesLoadWithHerMarkOff() {
        val json = Json { ignoreUnknownKeys = true }
        val config = json.decodeFromString(GuardianConfig.serializer(), """{"enabled":true}""")
        assertEquals(MarkSettings(), config.mark)
        assertFalse(config.mark.on)
    }
}
