package com.guardianangel

import com.guardianangel.core.AskOutcome
import com.guardianangel.core.Decision
import com.guardianangel.core.HOUR
import com.guardianangel.core.Line
import com.guardianangel.core.RestrictionKind
import com.guardianangel.core.Rules
import com.guardianangel.core.Voice
import com.guardianangel.data.BedtimeSettings
import com.guardianangel.data.Grant
import com.guardianangel.data.GuardianConfig
import com.guardianangel.data.GuardianState
import com.guardianangel.data.Intensity
import com.guardianangel.data.LockoutScope
import com.guardianangel.data.LockoutSettings
import com.guardianangel.data.AskPermissionSettings
import com.guardianangel.data.Mood
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class RulesTest {
    private val now = 1_000_000_000L
    private val noon = 12 * 60
    private val instagram = "com.instagram.android"
    private val bank = "com.example.bank"

    private fun config(scope: LockoutScope = LockoutScope.SOCIAL_MEDIA, intensity: Intensity = Intensity.STRICT) =
        GuardianConfig(enabled = true, lockouts = LockoutSettings(on = true, scope = scope, intensity = intensity))

    @Test
    fun disabledNeverBlocks() {
        val c = config().copy(enabled = false)
        assertEquals(Decision.Allow, Rules.decide(instagram, c, GuardianState(), now, noon))
    }

    @Test
    fun socialScopeBlocksOnlySocialApps() {
        val c = config()
        assertTrue(Rules.decide(instagram, c, GuardianState(), now, noon) is Decision.Block)
        assertEquals(Decision.Allow, Rules.decide(bank, c, GuardianState(), now, noon))
    }

    @Test
    fun everythingScopeRespectsAllowedAndProtectedLists() {
        val c = config(LockoutScope.EVERYTHING).copy(alwaysAllowed = setOf(bank))
        assertTrue(Rules.decide("com.example.game", c, GuardianState(), now, noon) is Decision.Block)
        assertEquals(Decision.Allow, Rules.decide(bank, c, GuardianState(), now, noon))
        assertEquals(Decision.Allow, Rules.decide("com.android.settings", c, GuardianState(), now, noon))
        assertEquals(Decision.Allow, Rules.decide("com.android.phone", c, GuardianState(), now, noon))
        assertEquals(Decision.Allow, Rules.decide("com.launcher", c, GuardianState(), now, noon, setOf("com.launcher")))
    }

    @Test
    fun grantOnlyCoversItsLevel() {
        val gentleGrant = GuardianState(grants = listOf(Grant(instagram, now + HOUR, Intensity.GENTLE)))
        assertTrue(Rules.decide(instagram, config(intensity = Intensity.STRICT), gentleGrant, now, noon) is Decision.Block)
        assertEquals(Decision.Allow, Rules.decide(instagram, config(intensity = Intensity.GENTLE), gentleGrant, now, noon))

        val expired = GuardianState(grants = listOf(Grant(instagram, now - 1, Intensity.ABSOLUTE)))
        assertTrue(Rules.decide(instagram, config(), expired, now, noon) is Decision.Block)
    }

    @Test
    fun punishmentOverridesGrantsAndAsking() {
        val c = config().copy(askPermission = AskPermissionSettings(on = true))
        val st = GuardianState(punishmentUntil = now + HOUR, grants = listOf(Grant(instagram, now + HOUR, Intensity.ABSOLUTE)))
        val d = Rules.decide(instagram, c, st, now, noon) as Decision.Block
        assertEquals(RestrictionKind.PUNISHMENT, d.kind)
        assertFalse(d.askAllowed)
    }

    @Test
    fun askPermissionAloneGuardsScope() {
        val c = GuardianConfig(enabled = true, askPermission = AskPermissionSettings(on = true))
        val d = Rules.decide(instagram, c, GuardianState(), now, noon) as Decision.Block
        assertEquals(RestrictionKind.PERMISSION, d.kind)
        assertTrue(d.askAllowed)
        assertFalse(d.countsAsFailure)
    }

    @Test
    fun bedtimeWindowWrapsMidnight() {
        assertTrue(Rules.isInWindow(23 * 60, 7 * 60, 23 * 60 + 30))
        assertTrue(Rules.isInWindow(23 * 60, 7 * 60, 3 * 60))
        assertFalse(Rules.isInWindow(23 * 60, 7 * 60, 7 * 60))
        assertFalse(Rules.isInWindow(23 * 60, 7 * 60, noon))
        assertTrue(Rules.isInWindow(13 * 60, 15 * 60, 14 * 60))
        assertFalse(Rules.isInWindow(9 * 60, 9 * 60, 9 * 60))
    }

    @Test
    fun bedtimeBlocksEverythingNotAllowed() {
        val c = GuardianConfig(enabled = true, bedtime = BedtimeSettings(on = true, intensity = Intensity.FIRM))
        val d = Rules.decide("com.example.game", c, GuardianState(), now, 0) as Decision.Block
        assertEquals(RestrictionKind.BEDTIME, d.kind)
        assertTrue(d.selfBypass)
        assertEquals(Decision.Allow, Rules.decide("com.example.game", c, GuardianState(), now, noon))
    }

    @Test
    fun absoluteAttemptsCountAsFailures() {
        val d = Rules.decide(instagram, config(intensity = Intensity.ABSOLUTE), GuardianState(), now, noon) as Decision.Block
        assertTrue(d.countsAsFailure)
    }

    @Test
    fun askOutcomes() {
        assertEquals(AskOutcome.GRANT, Rules.askOutcome(Intensity.GENTLE, 0.99))
        assertEquals(AskOutcome.PROOF, Rules.askOutcome(Intensity.FIRM, 0.9))
        assertEquals(AskOutcome.DENY, Rules.askOutcome(Intensity.STRICT, 0.9))
        assertEquals(AskOutcome.GRANT, Rules.askOutcome(Intensity.ABSOLUTE, 0.05))
    }

    @Test
    fun lockLengthNeverExceedsCap() {
        val random = Random(42)
        repeat(500) {
            val minutes = Rules.lockMinutes(Intensity.ABSOLUTE, 6, random)
            assertTrue(minutes in 15..360)
            assertEquals(0, minutes % 15)
        }
        repeat(500) {
            assertTrue(Rules.lockMinutes(Intensity.GENTLE, 24, random) in 60..240)
        }
    }

    @Test
    fun meritLevels() {
        assertEquals(1, Rules.meritLevel(0).level)
        assertEquals(2, Rules.meritLevel(25).level)
        assertEquals("Her favorite", Rules.meritLevel(5000).title)
        assertEquals(1f, Rules.meritLevel(5000).progress(5000))
    }

    @Test
    fun everyLineHasBothMoods() {
        Line.entries.forEach { line ->
            assertTrue("$line sweet", Voice.hasLines(line, Mood.SWEET))
            assertTrue("$line strict", Voice.hasLines(line, Mood.STRICT))
        }
    }
}
