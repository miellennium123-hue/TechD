package com.guardianangel

import com.guardianangel.core.Line
import com.guardianangel.core.Motion
import com.guardianangel.core.MotionCheck
import com.guardianangel.core.MotionJudge
import com.guardianangel.core.MotionReading
import com.guardianangel.core.MotionSample
import com.guardianangel.core.MotionTracker
import com.guardianangel.core.Session
import com.guardianangel.core.Step
import com.guardianangel.core.StepKind
import com.guardianangel.data.MotionSensitivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

class MotionTest {
    /** Movement swinging up and down at [perMinute], sampled at [fps]. */
    private fun swing(perMinute: Double, seconds: Double, fps: Int = 20, axisX: Boolean = false, noise: Double = 0.01): List<MotionSample> {
        val random = Random(1)
        return (0 until (seconds * fps).toInt()).map { i ->
            val t = i * 1_000L / fps
            val pos = (0.5 + 0.2 * sin(2 * PI * perMinute / 60.0 * t / 1_000.0) + random.nextDouble(-noise, noise)).toFloat()
            if (axisX) MotionSample(t, 0.03f, pos, 0.5f) else MotionSample(t, 0.03f, 0.5f, pos)
        }
    }

    @Test
    fun findsTheRhythm() {
        listOf(30.0, 60.0, 90.0, 120.0, 180.0, 200.0).forEach { bpm ->
            val rate = Motion.rate(swing(bpm, 8.0))
            assertNotNull("no rate at $bpm", rate)
            assertTrue("$bpm read as $rate", abs(rate!! - bpm) <= bpm * 0.08)
        }
    }

    @Test
    fun worksWithThePhoneSideways() {
        val rate = Motion.rate(swing(80.0, 8.0, axisX = true))
        assertTrue(abs(rate!! - 80) <= 6)
    }

    @Test
    fun unevenFramesStillWork() {
        val even = swing(100.0, 8.0, fps = 30)
        val uneven = even.filterIndexed { i, _ -> i % 3 != 1 && i % 7 != 0 }
        val rate = Motion.rate(uneven)
        assertTrue(abs(rate!! - 100) <= 8)
    }

    @Test
    fun noRhythmFromNoiseOrStillness() {
        val random = Random(2)
        val noise = (0 until 160).map { MotionSample(it * 50L, 0.03f, random.nextFloat(), random.nextFloat()) }
        assertNull(Motion.rate(noise))
        val still = (0 until 160).map { MotionSample(it * 50L, 0.001f, 0.5f, 0.5f) }
        assertNull(Motion.rate(still))
        assertNull(Motion.rate(swing(80.0, 2.0)))
    }

    @Test
    fun beatTolerance() {
        assertTrue(Motion.onBeat(80, 80))
        assertTrue(Motion.onBeat(80, 100))
        assertTrue(Motion.onBeat(80, 60))
        assertFalse(Motion.onBeat(80, 110))
        assertFalse(Motion.onBeat(80, 40))
        assertFalse(Motion.onBeat(180, 90))
    }

    @Test
    fun brightnessChangeIsNotMovement() {
        val a = FloatArray(Motion.GRID_W * Motion.GRID_H) { 0.3f + (it % 7) * 0.02f }
        val brighter = FloatArray(a.size) { a[it] + 0.2f }
        val s = Motion.compare(a, brighter, 0)
        assertTrue(s.energy < 0.001f)
        assertTrue(s.x.isNaN())
    }

    @Test
    fun movementShowsWhereItIs() {
        val a = FloatArray(Motion.GRID_W * Motion.GRID_H) { 0.4f }
        // A bright patch appears in the top left quarter.
        val b = a.copyOf().also { g ->
            for (y in 0 until 9) for (x in 0 until 12) g[y * Motion.GRID_W + x] = 0.9f
        }
        val s = Motion.compare(a, b, 0)
        assertTrue(s.energy > 0.01f)
        assertTrue(s.x < 0.3f && s.y < 0.3f)
    }

    @Test
    fun gridReadsRgba() {
        val w = 96
        val h = 72
        val buffer = ByteBuffer.allocate(w * h * 4)
        for (i in 0 until w * h) {
            buffer.put(i * 4, 255.toByte())
            buffer.put(i * 4 + 1, 255.toByte())
            buffer.put(i * 4 + 2, 255.toByte())
        }
        val grid = Motion.grid(buffer, w, h, w * 4, 4)
        assertEquals(Motion.GRID_W * Motion.GRID_H, grid.size)
        assertTrue(grid.all { abs(it - 1f) < 0.01f })
    }

    @Test
    fun trackerNeedsLiveFrames() {
        val tracker = MotionTracker()
        assertFalse(tracker.reading(0, 1_000, MotionSensitivity.NORMAL).live)
        val g = FloatArray(Motion.GRID_W * Motion.GRID_H) { 0.5f }
        (0..20).forEach { tracker.add(g.copyOf(), it * 50L) }
        val r = tracker.reading(0, 1_000, MotionSensitivity.NORMAL)
        assertTrue(r.live)
        assertFalse(r.moving)
        // Frames stopped: she can't judge.
        assertFalse(tracker.reading(0, 5_000, MotionSensitivity.NORMAL).live)
    }

    @Test
    fun trackerSeesMovement() {
        val tracker = MotionTracker()
        (0..40).forEach { i ->
            val g = FloatArray(Motion.GRID_W * Motion.GRID_H) { 0.3f }
            val row = (i % 10) * 3
            for (x in 0 until Motion.GRID_W) g[row * Motion.GRID_W + x] = 0.9f
            tracker.add(g, i * 50L)
        }
        assertTrue(tracker.reading(0, 2_000, MotionSensitivity.NORMAL).moving)
    }

    private val live = { moving: Boolean, rate: Int? -> MotionReading(live = true, level = 0f, moving = moving, rate = rate) }

    @Test
    fun offBeatIsCaughtAfterGraceAndTime() {
        val judge = MotionJudge(MotionCheck.BEAT, 80, startMs = 0)
        // During the grace time nothing counts.
        assertFalse(judge.update(4_000, live(true, 150)))
        var t = Motion.BEAT_GRACE_MS
        var caughtAt = -1L
        while (t <= 30_000 && caughtAt < 0) {
            if (judge.update(t, live(true, 150))) caughtAt = t
            t += 500
        }
        assertEquals(Motion.BEAT_GRACE_MS + Motion.OFF_BEAT_MS, caughtAt)
    }

    @Test
    fun onBeatOrUnclearIsNeverCaught() {
        val judge = MotionJudge(MotionCheck.BEAT, 80, startMs = 0)
        var t = 0L
        while (t <= 60_000) {
            assertFalse(judge.update(t, live(true, if (t % 2_000 == 0L) 85 else null)))
            t += 500
        }
    }

    @Test
    fun stoppingDuringStrokingCounts() {
        val judge = MotionJudge(MotionCheck.BEAT, 80, startMs = 0)
        var t = Motion.BEAT_GRACE_MS
        while (!judge.update(t, live(false, null))) t += 500
        assertEquals(Motion.BEAT_GRACE_MS + Motion.OFF_BEAT_MS, t)
    }

    @Test
    fun backOnBeatResetsTheCount() {
        val judge = MotionJudge(MotionCheck.BEAT, 80, startMs = 0)
        var t = Motion.BEAT_GRACE_MS
        repeat(10) {
            assertFalse(judge.update(t, live(true, 150)))
            t += 500
        }
        assertFalse(judge.update(t, live(true, 80)))
        t += 500
        assertFalse(judge.update(t, live(true, 150)))
    }

    @Test
    fun movingAfterStopIsCaught() {
        val judge = MotionJudge(MotionCheck.STILL, 0, startMs = 0)
        assertFalse(judge.update(1_500, live(true, null)))
        assertFalse(judge.update(2_000, live(true, null)))
        assertFalse(judge.update(3_000, live(true, null)))
        assertTrue(judge.update(3_500, live(true, null)))
    }

    @Test
    fun aShortTwitchIsFine() {
        val judge = MotionJudge(MotionCheck.STILL, 0, startMs = 0)
        assertFalse(judge.update(3_000, live(true, null)))
        assertFalse(judge.update(4_000, live(false, null)))
        assertFalse(judge.update(4_500, live(true, null)))
        assertFalse(judge.update(5_500, live(false, null)))
    }

    @Test
    fun noFramesNeverPunishes() {
        val dead = MotionReading(live = false, level = 0f, moving = false, rate = null)
        listOf(MotionCheck.BEAT, MotionCheck.STILL).forEach { check ->
            val judge = MotionJudge(check, 80, startMs = 0)
            var t = 0L
            while (t <= 60_000) {
                assertFalse(judge.update(t, dead))
                t += 500
            }
        }
    }

    @Test
    fun whatGetsChecked() {
        assertEquals(MotionCheck.BEAT, Session.motionCheck(Step(StepKind.STROKE, 30, bpm = 80)))
        assertEquals(MotionCheck.BEAT, Session.motionCheck(Step(StepKind.FASTER, 20, bpm = 170)))
        assertEquals(MotionCheck.BEAT, Session.motionCheck(Step(StepKind.SLOWER, 30, bpm = 40)))
        assertEquals(MotionCheck.STILL, Session.motionCheck(Step(StepKind.STOP, 20)))
        assertEquals(MotionCheck.STILL, Session.motionCheck(Step(StepKind.EDGE_HOLD, 20)))
        assertEquals(MotionCheck.STILL, Session.motionCheck(Step(StepKind.HOLD, 20)))
        listOf(StepKind.TEASE, StepKind.EDGE, StepKind.CBT, StepKind.SOUND_HOLD, StepKind.RUIN, StepKind.DENIED, StepKind.COUNTDOWN)
            .forEach { assertEquals(it.name, MotionCheck.NONE, Session.motionCheck(Step(it, 30, bpm = 80))) }
    }

    @Test
    fun caughtStepsSayWhy() {
        val steps = Session.caughtSteps(caged = false, why = Line.SESSION_OFF_BEAT)
        assertEquals(Line.SESSION_OFF_BEAT, steps.first().line)
        assertEquals(StepKind.EDGE, steps[1].kind)
        val caged = Session.caughtSteps(caged = true, why = Line.SESSION_MOVED)
        assertEquals(Line.SESSION_MOVED, caged.first().line)
        assertEquals(StepKind.HOLD, caged[1].kind)
    }
}
