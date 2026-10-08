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
        assertEquals(listOf(StepKind.RELOCK, StepKind.COOL), kinds.takeLast(2))
        assertTrue(kinds.indexOf(StepKind.UNLOCK) < kinds.indexOf(StepKind.RUIN))
        // Without a lock there's nothing to unlock.
        val free = Session.build(ruinOnly, caged = false, Random(3)).steps.map { it.kind }.dropLast(1)
        assertFalse(StepKind.UNLOCK in free)
        // Round 95: no replay. With "Keep going" you stroke through the ruin; without, the ruin ends it.
        assertEquals(listOf(StepKind.RUIN, StepKind.POST), free.takeLast(2))
        val noPost = Session.build(ruinOnly.copy(kinks = ruinOnly.kinks - Kink.POST_ORGASM), caged = false, Random(3))
        assertEquals(StepKind.RUIN, noPost.steps.dropLast(1).last().kind)
    }

    @Test
    fun quickshotIsShortAndAlwaysRuined() {
        seeds.forEach { seed ->
            val script = Session.quickshot(caged = false, Random(seed))
            assertTrue(script.quick)
            assertEquals(SessionEnding.RUINED, script.ending)
            assertEquals(listOf(StepKind.FASTER, StepKind.RUSH, StepKind.RUIN), script.steps.takeLast(3).map { it.kind })
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
        fun last(p: Int, r: Int, d: Int) = Session.build(noPost.copy(permissionWeight = p, ruinWeight = r, denialWeight = d), false, Random(1))
            .steps.last { it.kind != StepKind.COOL }.kind
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
            val allowed = setOf(
                StepKind.INTRO, StepKind.STROKE, StepKind.COUNT, StepKind.EDGE, StepKind.EDGE_HOLD, StepKind.COUNTDOWN,
                StepKind.FINISH, StepKind.RUIN, StepKind.DENIED, StepKind.DEAL, StepKind.COOL,
            )
            script.steps.forEach { assertTrue("${it.kind}", it.kind in allowed) }
        }
    }

    @Test
    fun noClipsPlayInSessions() {
        // Round 95: no watch breaks and no replays, in any session.
        val kinds = (builds(all, caged = false) + builds(all, caged = true)).flatMap { it.steps } +
            Session.quickshot(caged = false, Random(1)).steps + Session.owedRuin(caged = false, Random(1)).steps
        assertTrue(kinds.none { it.kind.name == "WATCH" || it.kind.name == "REPLAY" })
        assertTrue(StepKind.entries.none { it.name == "WATCH" || it.name == "REPLAY" })
    }

    @Test
    fun everyRuinIsEdgeThenCountdownThenHandsOff() {
        val ruinOnly = all.copy(permissionWeight = 0, ruinWeight = 100, denialWeight = 0)
        val scripts = listOf(false, true).flatMap { caged ->
            seeds.take(20).map { seed -> Session.build(ruinOnly, caged, Random(seed)) }
        }
        scripts.forEach { script ->
            val steps = script.steps
            val at = steps.indexOfFirst { it.kind == StepKind.RUIN }
            assertTrue(at >= 2)
            val edge = steps[at - 2]
            val countdown = steps[at - 1]
            assertEquals(StepKind.EDGE, edge.kind)
            assertEquals(Line.SESSION_RUIN_EDGE, edge.line)
            assertEquals(StepKind.COUNTDOWN, countdown.kind)
            assertEquals(Line.SESSION_RUIN_COUNTDOWN, countdown.line)
            assertEquals(Session.RUIN_COUNTDOWN_SECONDS, countdown.seconds)
            // You keep stroking to her beat through the countdown, with no taunt holding it up.
            assertTrue(countdown.bpm > 0)
            assertEquals(0, countdown.tauntAt)
        }
        // The regular ending goes straight to the edge, no strokes before it.
        // (Quickshots and the owed ruin have no edge at all: see straightStrokesToTheRuin.)
        val free = Session.endingSteps(SessionEnding.RUINED, caged = false, all.kinks, Random(1)).map { it.kind }
        assertEquals(StepKind.EDGE, free.first())
    }

    @Test
    fun straightStrokesToTheRuin() {
        // Round 99: quickshots and the owed ruin have no edge and no countdown. Faster and faster strokes,
        // one tap when you're about to cum, then hands off.
        listOf(false, true).forEach { caged ->
            seeds.take(20).forEach { seed ->
                listOf(Session.quickshot(caged, Random(seed)), Session.owedRuin(caged, Random(seed))).forEach { script ->
                    val kinds = script.steps.map { it.kind }
                    assertFalse(StepKind.EDGE in kinds)
                    assertFalse(StepKind.COUNTDOWN in kinds)
                    val at = kinds.indexOf(StepKind.RUIN)
                    val rush = script.steps[at - 1]
                    assertEquals(StepKind.RUSH, rush.kind)
                    assertEquals(Line.SESSION_RUSH, rush.line)
                    assertEquals("I'm about to cum", rush.kind.tap)
                    assertTrue(rush.kind.stroking)
                    assertTrue(rush.bpmTo > rush.bpm)
                    assertEquals(0, script.edgeGoal)
                }
            }
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
        assertEquals(listOf(StepKind.FINISH, StepKind.POST, StepKind.COOL), kinds.takeLast(3))
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
            assertEquals(StepKind.FINISH, s.steps.last { it.kind != StepKind.POST && it.kind != StepKind.COOL }.kind)
        }
        // A denial at the end of a countdown.
        val denied = builds(all.copy(permissionWeight = 0, ruinWeight = 0, denialWeight = 100), caged = false)
        assertTrue(denied.any { s -> s.steps.last { it.kind != StepKind.COOL }.line == Line.SESSION_NO })
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

    // ---- Round 84 ---------------------------------------------------------------------------

    @Test
    fun warmUpFirstCoolDownLast() {
        builds(all, caged = false).forEach { script ->
            assertTrue(script.steps[1].warmup)
            assertEquals(Line.SESSION_WARMUP, script.steps[1].line)
            assertTrue(script.steps.filter { it.warmup }.all { it.bpm in 1..70 })
            assertEquals(StepKind.COOL, script.steps.last().kind)
            assertEquals("Cool-down", Session.chapter(script.steps.last()))
            assertEquals("Warm-up", Session.chapter(script.steps[1]))
        }
        builds(all, caged = true).forEach { script ->
            assertEquals(StepKind.HOLD, script.steps[1].kind)
            assertTrue(script.steps[1].warmup)
        }
        val body = Step(StepKind.STROKE, 30, bpm = 90)
        assertEquals("Her session", Session.chapter(body))
        assertEquals("The ending", Session.chapter(body.copy(ending = true)))
    }

    @Test
    fun clampsOnTheirTimerAtMostTwice() {
        val clamps = SessionSettings(on = true, kinks = setOf(Kink.CLAMPS), minutes = 30)
        builds(clamps, caged = true).forEach { script ->
            val kinds = script.steps.map { it.kind }
            assertTrue(kinds.count { it == StepKind.CLAMP } <= Session.CLAMPS_PER_SESSION)
            script.steps.forEachIndexed { i, step ->
                if (step.kind == StepKind.CLAMP) {
                    assertTrue(step.seconds <= Session.CLAMP_MAX_SECONDS)
                    assertEquals(StepKind.CLAMP_ON, kinds[i - 1])
                    assertEquals(StepKind.CLAMP_OFF, kinds[i + 1])
                    assertTrue(step.kind.skippable)
                }
            }
        }
        assertTrue(builds(clamps, caged = false).any { s -> s.steps.any { it.kind == StepKind.CLAMP } })
    }

    @Test
    fun sizeRemarksNeedARating() {
        val sph = SessionSettings(on = true, kinks = setOf(Kink.SMALL_SIZE, Kink.EDGING))
        val without = seeds.map { Session.build(sph, false, Random(it)) }.flatMap { it.steps }
        assertTrue(without.none { it.comment == Line.SESSION_SPH })
        val with = seeds.map { Session.build(sph, false, Random(it), sizeKnown = true) }.flatMap { it.steps }
        assertTrue(with.any { it.comment == Line.SESSION_SPH })
    }

    // ---- Round 85 ---------------------------------------------------------------------------

    @Test
    fun owedRuinIsAMinuteOfCbtThenAQuickshotWatchingIt() {
        seeds.take(50).forEach { seed ->
            val script = Session.owedRuin(caged = false, Random(seed))
            assertTrue(script.quick)
            assertEquals(SessionEnding.RUINED, script.ending)
            val kinds = script.steps.map { it.kind }
            assertEquals(Line.SESSION_OWED_RUIN, script.steps.first().line)
            // A full minute of hard CBT first, every slap counted, "Too much" still there.
            val cbt = script.steps[1]
            assertEquals(StepKind.CBT, cbt.kind)
            assertEquals(Line.SESSION_CBT_HARD, cbt.line)
            assertEquals(Session.OWED_CBT_SECONDS * Session.OWED_CBT_BPM / 60, cbt.reps)
            assertTrue(cbt.seconds >= Session.OWED_CBT_SECONDS)
            assertTrue(cbt.kind.skippable)
            // Then straight strokes to the ruin: no edge, no countdown (round 99). No clips on screen (round 95).
            assertEquals(
                listOf(StepKind.STROKE, StepKind.FASTER, StepKind.RUSH, StepKind.RUIN),
                kinds.drop(2),
            )
        }
        // Locked: CBT with the cage on, then unlock, and back on after.
        val locked = Session.owedRuin(caged = true, Random(1)).steps.map { it.kind }
        assertTrue(locked.indexOf(StepKind.CBT) < locked.indexOf(StepKind.UNLOCK))
        assertTrue(locked.indexOf(StepKind.UNLOCK) < locked.indexOf(StepKind.STROKE))
        assertEquals(StepKind.RELOCK, locked.last())
    }
}
