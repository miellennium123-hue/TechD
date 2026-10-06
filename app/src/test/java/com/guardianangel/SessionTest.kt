package com.guardianangel

import com.guardianangel.core.Line
import com.guardianangel.core.Session
import com.guardianangel.core.StepKind
import com.guardianangel.data.CbtLevel
import com.guardianangel.data.Kink
import com.guardianangel.data.SessionEnding
import com.guardianangel.data.SessionSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SessionTest {
    private val all = SessionSettings(on = true, kinks = Kink.entries.toSet())
    private val seeds = 0 until 200

    private fun builds(settings: SessionSettings, caged: Boolean, soundingReady: Boolean = true) =
        seeds.map { Session.build(settings, caged, Random(it), soundingReady) }

    @Test
    fun endingFollowsTheSliders() {
        val s = SessionSettings(permissionWeight = 20, ruinWeight = 40, denialWeight = 40)
        assertEquals(SessionEnding.PERMISSION, Session.pickEnding(s, caged = false, roll = 0.1))
        assertEquals(SessionEnding.RUINED, Session.pickEnding(s, caged = false, roll = 0.3))
        assertEquals(SessionEnding.DENIED, Session.pickEnding(s, caged = false, roll = 0.9))
    }

    @Test
    fun ruinedOnlyMode() {
        val s = SessionSettings(permissionWeight = 0, ruinWeight = 50, denialWeight = 0)
        listOf(0.0, 0.5, 0.999).forEach { assertEquals(SessionEnding.RUINED, Session.pickEnding(s, false, it)) }
    }

    @Test
    fun allZeroMeansDenied() {
        val s = SessionSettings(permissionWeight = 0, ruinWeight = 0, denialWeight = 0)
        assertEquals(SessionEnding.DENIED, Session.pickEnding(s, false, 0.5))
        assertEquals(Triple(0, 0, 100), Session.endingShares(s, false))
    }

    @Test
    fun neverPermissionDuringALock() {
        val permissionOnly = SessionSettings(permissionWeight = 100, ruinWeight = 0, denialWeight = 0)
        assertEquals(SessionEnding.DENIED, Session.pickEnding(permissionOnly, caged = true, roll = 0.0))
        val s = SessionSettings(permissionWeight = 50, ruinWeight = 25, denialWeight = 25)
        assertEquals(Triple(0, 50, 50), Session.endingShares(s, caged = true))
        builds(s, caged = true).forEach { assertTrue(it.ending != SessionEnding.PERMISSION) }
    }

    @Test
    fun sharesAddUpTo100() {
        val (p, r, d) = Session.endingShares(SessionSettings(permissionWeight = 30, ruinWeight = 30, denialWeight = 30), false)
        assertEquals(100, p + r + d)
    }

    @Test
    fun lockedSessionsOnlyUseCageSafeCommandsUntilTheEnding() {
        builds(all, caged = true).forEach { script ->
            val body = script.steps.dropLast(Session.endingSteps(script.ending, true, Session.kinks(all, true), Random(0)).size)
            body.forEach { step ->
                assertFalse("${step.kind} while locked", step.kind.stroking)
                assertFalse(step.kind in setOf(StepKind.TEASE, StepKind.SOUND_IN, StepKind.SOUND_HOLD, StepKind.SOUND_OUT))
            }
        }
    }

    @Test
    fun cageTeaseOnlyWhileLocked() {
        builds(all, caged = false).forEach { script -> assertTrue(script.steps.none { it.kind == StepKind.CAGE_TEASE }) }
        assertFalse(Kink.CAGE_TEASE in Session.kinks(all, caged = false))
        assertFalse(Kink.EDGING in Session.kinks(all, caged = true))
    }

    @Test
    fun lockedRuinUnlocksAndRelocks() {
        val ruinOnly = all.copy(permissionWeight = 0, ruinWeight = 100, denialWeight = 0)
        val script = Session.build(ruinOnly, caged = true, Random(3))
        val kinds = script.steps.map { it.kind }
        assertEquals(StepKind.RELOCK, kinds.last())
        assertTrue(kinds.indexOf(StepKind.UNLOCK) < kinds.indexOf(StepKind.RUIN))
        // Without a lock there's nothing to unlock.
        val free = Session.build(ruinOnly, caged = false, Random(3)).steps.map { it.kind }
        assertFalse(StepKind.UNLOCK in free)
        assertEquals(StepKind.RUIN, free.last())
    }

    @Test
    fun quickshotIsShortAndAlwaysRuined() {
        seeds.forEach { seed ->
            val script = Session.quickshot(caged = false, Random(seed))
            assertTrue(script.quick)
            assertEquals(SessionEnding.RUINED, script.ending)
            assertEquals(StepKind.RUIN, script.steps.last().kind)
            assertEquals(Line.SESSION_QUICKSHOT, script.steps.first().line)
            assertTrue(script.estimate in 60..150)
        }
    }

    @Test
    fun lockedQuickshotUnlocksAndRelocks() {
        val kinds = Session.quickshot(caged = true, Random(5)).steps.map { it.kind }
        assertEquals(StepKind.RELOCK, kinds.last())
        assertTrue(kinds.indexOf(StepKind.UNLOCK) < kinds.indexOf(StepKind.RUIN))
        assertFalse(StepKind.UNLOCK in Session.quickshot(caged = false, Random(5)).steps.map { it.kind })
    }

    @Test
    fun endingsEndRight() {
        fun last(p: Int, r: Int, d: Int) = Session.build(all.copy(permissionWeight = p, ruinWeight = r, denialWeight = d), false, Random(1)).steps.last().kind
        assertEquals(StepKind.FINISH, last(100, 0, 0))
        assertEquals(StepKind.RUIN, last(0, 100, 0))
        assertEquals(StepKind.DENIED, last(0, 0, 100))
    }

    @Test
    fun lengthIsRoughlyWhatYouSet() {
        listOf(5, 10, 30).forEach { minutes ->
            builds(all.copy(minutes = minutes), caged = false).forEach { script ->
                val estimate = script.estimate
                assertTrue("$minutes min gave $estimate s", estimate >= minutes * 60 - 30)
                assertTrue("$minutes min gave $estimate s", estimate <= minutes * 60 + 360)
            }
        }
    }

    @Test
    fun cbtIsLimitedAndNeverBackToBack() {
        val cbtOnly = SessionSettings(kinks = setOf(Kink.CBT), minutes = 30)
        builds(cbtOnly, caged = true).forEach { script ->
            val kinds = script.steps.map { it.kind }
            assertTrue(kinds.count { it == StepKind.CBT } <= Session.CBT_SOFT_LIMIT)
            kinds.zipWithNext().forEach { (a, b) -> assertFalse(a == StepKind.CBT && b == StepKind.CBT) }
        }
        builds(cbtOnly.copy(cbt = CbtLevel.HARD), caged = true).forEach { script ->
            assertTrue(script.steps.count { it.kind == StepKind.CBT } <= Session.CBT_HARD_LIMIT)
        }
    }

    @Test
    fun cbtLevels() {
        seeds.forEach {
            val soft = Session.cbtStep(CbtLevel.SOFT, Random(it))
            assertTrue(soft.reps in 3..5)
            assertEquals(Line.SESSION_CBT_SOFT, soft.line)
            val hard = Session.cbtStep(CbtLevel.HARD, Random(it))
            assertTrue(hard.reps in 6..12)
            assertEquals(Line.SESSION_CBT_HARD, hard.line)
            // Long enough to count every one at its beat.
            assertTrue(hard.seconds * hard.bpm >= hard.reps * 60)
        }
    }

    @Test
    fun soundingNeedsYourTickAndHappensOnce() {
        val sounding = SessionSettings(kinks = setOf(Kink.SOUNDING), minutes = 15)
        builds(sounding, caged = false, soundingReady = false).forEach { script ->
            assertTrue(script.steps.none { it.kind == StepKind.SOUND_IN })
        }
        builds(sounding, caged = false, soundingReady = true).forEach { script ->
            val kinds = script.steps.map { it.kind }
            assertEquals(1, kinds.count { it == StepKind.SOUND_IN })
            val i = kinds.indexOf(StepKind.SOUND_IN)
            assertEquals(listOf(StepKind.SOUND_IN, StepKind.SOUND_HOLD, StepKind.SOUND_OUT), kinds.subList(i, i + 3))
            assertTrue(script.steps.filter { it.kind.name.startsWith("SOUND") }.all { it.bpm == 0 && it.kind.skippable && !it.kind.watched })
        }
        builds(sounding, caged = true, soundingReady = true).forEach { script ->
            assertTrue(script.steps.none { it.kind == StepKind.SOUND_IN })
        }
    }

    @Test
    fun commentsOnlyWithPraiseOrHumiliation() {
        builds(SessionSettings(kinks = setOf(Kink.EDGING)), false).forEach { script ->
            assertTrue(script.steps.all { it.comment == null })
        }
        val remarks = builds(SessionSettings(kinks = setOf(Kink.HUMILIATION)), false).flatMap { s -> s.steps.mapNotNull { it.comment } }
        assertTrue(remarks.isNotEmpty())
        assertTrue(remarks.all { it == Line.SESSION_HUMILIATION })
    }

    @Test
    fun stoppedKinksStayOut() {
        builds(SessionSettings(kinks = emptySet()), caged = false).forEach { script ->
            val allowed = setOf(StepKind.INTRO, StepKind.STROKE, StepKind.EDGE, StepKind.EDGE_HOLD, StepKind.COUNTDOWN, StepKind.FINISH, StepKind.RUIN, StepKind.DENIED)
            script.steps.forEach { assertTrue("${it.kind}", it.kind in allowed) }
        }
    }

    @Test
    fun caughtMeansAnExtraEdge() {
        val free = Session.caughtSteps(caged = false).map { it.kind }
        assertEquals(listOf(StepKind.CAUGHT, StepKind.EDGE, StepKind.EDGE_HOLD), free)
        assertTrue(Session.caughtSteps(caged = true).none { it.kind.stroking })
    }

    @Test
    fun sameSeedSameSession() {
        assertEquals(Session.build(all, false, Random(42)), Session.build(all, false, Random(42)))
    }

    @Test
    fun tapStepsHaveAButton() {
        StepKind.entries.filter { it.tap != null }.forEach { assertTrue(it.expected > 0) }
        assertTrue(StepKind.RUIN.still)
        assertFalse(StepKind.RUIN.watched)
    }
}
