package com.guardianangel

import com.guardianangel.core.Line
import com.guardianangel.core.Session
import com.guardianangel.core.BeatPattern
import com.guardianangel.core.DealChoice
import com.guardianangel.core.Step
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
        // Round 79: she replays the ruin right after it, and you stroke through it with "Keep going".
        assertEquals(listOf(StepKind.RUIN, StepKind.REPLAY), free.takeLast(2))
        assertTrue(Session.build(ruinOnly, caged = false, Random(3)).steps.last().bpm > 0)
        val noPost = Session.build(ruinOnly.copy(kinks = ruinOnly.kinks - Kink.POST_ORGASM), caged = false, Random(3))
        assertEquals(0, noPost.steps.last().bpm)
    }

    @Test
    fun quickshotIsShortAndAlwaysRuined() {
        seeds.forEach { seed ->
            val script = Session.quickshot(caged = false, Random(seed))
            assertTrue(script.quick)
            assertEquals(SessionEnding.RUINED, script.ending)
            assertEquals(listOf(StepKind.RUIN, StepKind.REPLAY), script.steps.takeLast(2).map { it.kind })
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
        val noPost = all.copy(kinks = all.kinks - Kink.POST_ORGASM)
        fun last(p: Int, r: Int, d: Int) = Session.build(noPost.copy(permissionWeight = p, ruinWeight = r, denialWeight = d), false, Random(1)).steps.last().kind
        assertEquals(StepKind.FINISH, last(100, 0, 0))
        assertEquals(StepKind.REPLAY, last(0, 100, 0))
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
            val allowed = setOf(
                StepKind.INTRO, StepKind.STROKE, StepKind.COUNT, StepKind.EDGE, StepKind.EDGE_HOLD, StepKind.COUNTDOWN,
                StepKind.FINISH, StepKind.RUIN, StepKind.REPLAY, StepKind.DENIED, StepKind.DEAL,
            )
            script.steps.forEach { assertTrue("${it.kind}", it.kind in allowed) }
        }
    }

    @Test
    fun quickshotWatchBreak() {
        assertTrue(Session.quickshot(caged = false, Random(1)).steps.none { it.kind == StepKind.WATCH })
        val kinds = Session.quickshot(caged = false, Random(1), watchClip = true).steps.map { it.kind }
        assertEquals(1, kinds.count { it == StepKind.WATCH })
        assertEquals(StepKind.STROKE, kinds[kinds.indexOf(StepKind.WATCH) - 1])
    }

    @Test
    fun watchBreakOnlyWhenAsked() {
        repeat(30) { seed ->
            val plain = Session.build(all, false, Random(seed))
            assertTrue(plain.steps.none { it.kind == StepKind.WATCH })
            val watched = Session.build(all, false, Random(seed), soundingReady = true, watchClip = true)
            val at = watched.steps.indexOfFirst { it.kind == StepKind.WATCH }
            assertEquals(1, watched.steps.count { it.kind == StepKind.WATCH })
            assertTrue(at > 0)
            // Never inside sounding, never right after a countdown, never in the ending.
            val before = watched.steps[at - 1].kind
            assertTrue("$before", before != StepKind.SOUND_IN && before != StepKind.SOUND_HOLD && before != StepKind.COUNTDOWN)
            val endingAt = watched.steps.indexOfFirst { it.ending }
            assertTrue(at < endingAt)
        }
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

    // ---- Round 79 ---------------------------------------------------------------------------

    @Test
    fun keepGoingAfterFinishingNeverDuringALock() {
        val permission = all.copy(permissionWeight = 100, ruinWeight = 0, denialWeight = 0)
        val kinds = Session.build(permission, false, Random(2)).steps.map { it.kind }
        assertEquals(listOf(StepKind.FINISH, StepKind.POST), kinds.takeLast(2))
        builds(all, caged = true).forEach { script -> assertTrue(script.steps.none { it.kind == StepKind.POST }) }
        assertFalse(Kink.POST_ORGASM in Session.kinks(all, caged = true))
    }

    @Test
    fun edgesAndRampsSpeedUp() {
        builds(all, caged = false).flatMap { it.steps }.filter { it.kind == StepKind.EDGE || it.kind == StepKind.RAMP }.forEach {
            assertTrue("${it.kind} ${it.bpm} to ${it.bpmTo}", it.bpmTo > it.bpm)
        }
        assertTrue(builds(all, caged = false).any { s -> s.steps.any { it.kind == StepKind.RAMP } })
        val ramp = Step(StepKind.RAMP, 40, bpm = 60, bpmTo = 180)
        assertEquals(60, ramp.bpmAt(0))
        assertEquals(120, ramp.bpmAt(20_000))
        assertEquals(180, ramp.bpmAt(60_000))
        assertEquals(90, Step(StepKind.STROKE, 30, bpm = 90).bpmAt(15_000))
    }

    @Test
    fun endingStepsAreMarked() {
        builds(all, caged = false).forEach { script ->
            val first = script.steps.indexOfFirst { it.ending }
            assertTrue(first > 0)
            assertTrue(script.steps.drop(first).all { it.ending })
        }
    }

    @Test
    fun youStrokeWhileYouWatchUnlessLocked() {
        val free = Session.build(all, false, Random(4), watchClip = true).steps.first { it.kind == StepKind.WATCH }
        assertTrue(free.bpm > 0)
        assertEquals(Line.SESSION_WATCH_STROKE, free.line)
        val locked = Session.build(all, true, Random(4), watchClip = true).steps.first { it.kind == StepKind.WATCH }
        assertEquals(0, locked.bpm)
        assertEquals(Line.SESSION_WATCH, locked.line)
    }

    @Test
    fun herDealOnlyWhenItCouldMatter() {
        val scripts = builds(all, caged = false)
        assertTrue(scripts.any { s -> s.steps.any { it.kind == StepKind.DEAL } })
        scripts.forEach { script ->
            val deals = script.steps.count { it.kind == StepKind.DEAL }
            assertTrue(deals <= 1)
            if (script.ending == SessionEnding.PERMISSION) assertEquals(0, deals)
            if (deals == 1) assertTrue(script.steps[script.steps.indexOfFirst { it.kind == StepKind.DEAL } + 1].ending)
        }
        builds(all, caged = true).forEach { script -> assertTrue(script.steps.none { it.kind == StepKind.DEAL }) }
    }

    @Test
    fun whatHerDealDoes() {
        val denied = Session.build(all.copy(permissionWeight = 0, ruinWeight = 0, denialWeight = 100), false, Random(6))
        val kinks = Session.kinks(all, false)
        val at = denied.steps.indexOfFirst { it.ending } - 1
        // A sure ruin now.
        val (e1, ruined) = Session.takeDeal(denied, denied.steps, at, DealChoice.RUIN_NOW, kinks, Random(1))
        assertEquals(SessionEnding.RUINED, e1)
        assertTrue(StepKind.RUIN in ruined.map { it.kind })
        assertFalse(StepKind.DENIED in ruined.map { it.kind })
        assertEquals(denied.steps.take(at + 1), ruined.take(at + 1))
        // Five edges, then her coin: heads permission, tails the ending you had.
        repeat(10) { seed ->
            val (e2, edged) = Session.takeDeal(denied, denied.steps, at, DealChoice.EDGES, kinks, Random(seed))
            val added = edged.subList(at + 1, at + 1 + Session.DEAL_EDGES * 2)
            assertEquals(Session.DEAL_EDGES, added.count { it.kind == StepKind.EDGE })
            val flip = edged[at + 1 + Session.DEAL_EDGES * 2]
            assertEquals(StepKind.FLIP, flip.kind)
            if (e2 == SessionEnding.PERMISSION) {
                assertEquals(Line.SESSION_FLIP_WON, flip.line)
                assertTrue(StepKind.FINISH in edged.map { it.kind })
            } else {
                assertEquals(SessionEnding.DENIED, e2)
                assertEquals(Line.SESSION_FLIP_LOST, flip.line)
            }
        }
    }

    @Test
    fun restsShrinkEveryEdge() {
        assertEquals(Session.REST_START, Session.rest(0))
        assertEquals(Session.REST_START - Session.REST_STEP, Session.rest(1))
        assertEquals(Session.REST_MIN, Session.rest(10))
        // In a session, rests after edges never grow.
        val edging = SessionSettings(on = true, kinks = setOf(Kink.EDGING), minutes = 30)
        builds(edging, caged = false).take(30).forEach { script ->
            val body = script.steps.takeWhile { !it.ending }
            val rests = body.filter { it.kind == StepKind.EDGE_HOLD }.map { it.seconds }
            assertEquals(rests, rests.sortedDescending())
        }
    }

    @Test
    fun countsStylesAndPatterns() {
        val steps = builds(all, caged = false).flatMap { it.steps }
        val counts = steps.filter { it.kind == StepKind.COUNT }
        assertTrue(counts.isNotEmpty())
        counts.forEach {
            assertTrue(it.reps in 20..60 && it.reps % 10 == 0)
            assertTrue("long enough for every stroke", it.seconds * it.bpm >= it.reps * 60)
        }
        assertTrue(steps.any { it.style != null })
        assertTrue(steps.any { it.pattern != BeatPattern.STEADY })
        // Patterns only come with Speed changes; locked sessions never get strokes, so no styles.
        val noSpeed = builds(all.copy(kinks = all.kinks - Kink.SPEED), caged = false).flatMap { it.steps }
        assertTrue(noSpeed.all { it.pattern == BeatPattern.STEADY })
        assertTrue(builds(all, caged = true).flatMap { it.steps }.all { it.style == null })
    }

    @Test
    fun crueltyInCountdowns() {
        val scripts = builds(all.copy(permissionWeight = 100, ruinWeight = 0, denialWeight = 0), caged = false)
        // The 1 that never comes: a countdown, then "not yet" and one more edge.
        assertTrue(scripts.any { s -> s.steps.any { it.line == Line.SESSION_NOT_YET } })
        scripts.forEach { s ->
            val i = s.steps.indexOfFirst { it.line == Line.SESSION_NOT_YET }
            if (i >= 0) assertEquals(StepKind.COUNTDOWN, s.steps[i - 1].kind)
            assertEquals(StepKind.FINISH, s.steps.last { it.kind != StepKind.POST }.kind)
        }
        // A denial at the end of a countdown.
        val denied = builds(all.copy(permissionWeight = 0, ruinWeight = 0, denialWeight = 100), caged = false)
        assertTrue(denied.any { s -> s.steps.last().line == Line.SESSION_NO })
        // Taunts hold on a number she actually reaches.
        builds(all, caged = false).flatMap { it.steps }.filter { it.kind == StepKind.COUNTDOWN && it.tauntAt > 0 }.forEach {
            assertTrue(it.tauntAt in 1..it.seconds)
            assertEquals(it.seconds + Session.TAUNT_SECONDS, it.estimate)
        }
    }

    @Test
    fun edgeTimingAndWhisper() {
        assertTrue(Session.edgeFast(25))
        assertFalse(Session.edgeFast(Session.EDGE_FAST_SECONDS))
        val s = SessionSettings()
        assertTrue(Session.whisper(s, 23 * 60))
        assertTrue(Session.whisper(s, 2 * 60))
        assertFalse(Session.whisper(s, 12 * 60))
        assertFalse(Session.whisper(s.copy(whisper = false), 23 * 60))
        assertFalse(Session.whisper(s.copy(voice = false), 23 * 60))
    }
}
