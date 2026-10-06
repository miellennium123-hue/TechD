package com.guardianangel

import com.guardianangel.core.AskOutcome
import com.guardianangel.core.BegOutcome
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

    private fun config(scope: LockoutScope = LockoutScope.SOCIAL_MEDIA) =
        GuardianConfig(enabled = true, lockouts = LockoutSettings(on = true, scope = scope))

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
    fun lockoutIsHardBlockWithWayIn() {
        val d = Rules.decide(instagram, config(), GuardianState(), now, noon) as Decision.Block
        assertEquals(RestrictionKind.LOCKOUT, d.kind)
        assertTrue(d.selfBypass)
        assertFalse(d.askAllowed)
    }

    @Test
    fun grantLetsYouIn() {
        val granted = GuardianState(grants = listOf(Grant(instagram, now + HOUR)))
        assertEquals(Decision.Allow, Rules.decide(instagram, config(), granted, now, noon))

        val expired = GuardianState(grants = listOf(Grant(instagram, now - 1)))
        assertTrue(Rules.decide(instagram, config(), expired, now, noon) is Decision.Block)
    }

    @Test
    fun punishmentOverridesGrantsAndAsking() {
        val c = config().copy(askPermission = AskPermissionSettings(on = true))
        val st = GuardianState(punishmentUntil = now + HOUR, grants = listOf(Grant(instagram, now + HOUR)))
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
        assertFalse(d.selfBypass)
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
        val c = GuardianConfig(enabled = true, bedtime = BedtimeSettings(on = true))
        val d = Rules.decide("com.example.game", c, GuardianState(), now, 0) as Decision.Block
        assertEquals(RestrictionKind.BEDTIME, d.kind)
        assertTrue(d.selfBypass)
        assertEquals(Decision.Allow, Rules.decide("com.example.game", c, GuardianState(), now, noon))
    }

    @Test
    fun bedtimeWinsOverLockoutOnTheBlockScreen() {
        val c = config(LockoutScope.EVERYTHING).copy(bedtime = BedtimeSettings(on = true))
        val d = Rules.decide("com.example.game", c, GuardianState(), now, 0) as Decision.Block
        assertEquals(RestrictionKind.BEDTIME, d.kind)
    }

    @Test
    fun askOutcomes() {
        assertEquals(AskOutcome.GRANT, Rules.askOutcome(0.1))
        assertEquals(AskOutcome.PROOF, Rules.askOutcome(0.5))
        assertEquals(AskOutcome.DENY, Rules.askOutcome(0.9))
    }

    @Test
    fun strictMoodIsHarsherAboutBegging() {
        // A roll that sweet mood releases on, strict mood denies.
        assertEquals(BegOutcome(released = true, addTime = false), Rules.begOutcome(Mood.SWEET, 0.2, 0.0))
        assertFalse(Rules.begOutcome(Mood.STRICT, 0.2, 0.0).released)
        // A time roll that strict mood adds time on, sweet mood doesn't.
        assertTrue(Rules.begOutcome(Mood.STRICT, 0.9, 0.4).addTime)
        assertFalse(Rules.begOutcome(Mood.SWEET, 0.9, 0.4).addTime)
        // Released begs never add time.
        assertFalse(Rules.begOutcome(Mood.STRICT, 0.0, 0.0).addTime)
    }

    @Test
    fun aboutOneInThreeDenialsAddTime() {
        val random = Random(7)
        var denials = 0
        var added = 0
        repeat(20_000) {
            val mood = if (random.nextBoolean()) Mood.SWEET else Mood.STRICT
            val o = Rules.begOutcome(mood, random.nextDouble(), random.nextDouble())
            if (!o.released) {
                denials++
                if (o.addTime) added++
            }
        }
        val share = added.toDouble() / denials
        assertTrue("share $share", share in 0.28..0.42)
    }

    @Test
    fun lockLengthStaysBetweenMinMaxAndCap() {
        val random = Random(42)
        repeat(500) {
            val minutes = Rules.lockMinutes(60, 240, 24, random)
            assertTrue(minutes in 60..240)
            assertEquals(0, minutes % 15)
        }
        repeat(500) {
            val minutes = Rules.lockMinutes(600, 1200, 6, random)
            assertTrue(minutes in 15..360)
        }
        // Swapped min and max still works.
        repeat(100) { assertTrue(Rules.lockMinutes(240, 60, 24, random) in 60..240) }
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
