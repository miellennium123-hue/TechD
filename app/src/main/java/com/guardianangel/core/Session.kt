package com.guardianangel.core

import com.guardianangel.data.CbtLevel
import com.guardianangel.data.Kink
import com.guardianangel.data.SessionEnding
import com.guardianangel.data.SessionSettings
import com.guardianangel.data.Sessions
import kotlin.math.ceil
import kotlin.random.Random

/**
 * One kind of command in a session.
 * [tap]: the button you press to move on (the step waits for it, up to its seconds).
 * [still]: hands off, she expects no movement. [stroking]: she expects movement to her beat and to see you.
 * [watched]: being caught here earns a reprimand and an extra edge. Never during sounding or the ending.
 * [skippable]: shows "Too much", which moves on with no penalty.
 * [beat]: with motion checks, she checks your rhythm matches her beat.
 */
enum class StepKind(
    val line: Line,
    val tap: String? = null,
    val still: Boolean = false,
    val stroking: Boolean = false,
    val watched: Boolean = true,
    val skippable: Boolean = false,
    val beat: Boolean = false,
    /** Expected length of a [tap] step, for the time estimate. */
    val expected: Int = 0,
) {
    INTRO(Line.SESSION_START, watched = false),
    STROKE(Line.SESSION_STROKE, stroking = true, beat = true),
    FASTER(Line.SESSION_FASTER, stroking = true, beat = true),
    SLOWER(Line.SESSION_SLOWER, stroking = true, beat = true),
    TEASE(Line.SESSION_TEASE, stroking = true),
    EDGE(Line.SESSION_EDGE, tap = "I'm at the edge", stroking = true, expected = 45),
    EDGE_HOLD(Line.SESSION_EDGE_HOLD, still = true),
    STOP(Line.SESSION_STOP, still = true),
    HOLD(Line.SESSION_HOLD, still = true),
    NIPPLES(Line.SESSION_NIPPLES),
    CAGE_TEASE(Line.SESSION_CAGE_TEASE),
    TOY(Line.SESSION_TOY),
    CBT(Line.SESSION_CBT_SOFT, skippable = true),
    SOUND_IN(Line.SESSION_SOUND_IN, watched = false, skippable = true),
    SOUND_HOLD(Line.SESSION_SOUND_HOLD, still = true, watched = false, skippable = true),
    SOUND_OUT(Line.SESSION_SOUND_OUT, watched = false, skippable = true),
    COUNTDOWN(Line.SESSION_COUNTDOWN, watched = false),
    CAUGHT(Line.SESSION_CAUGHT, watched = false),
    UNLOCK(Line.SESSION_UNLOCK, tap = "Unlocked", watched = false, expected = 30),
    FINISH(Line.SESSION_FINISH, tap = "Done", watched = false, expected = 30),
    RUIN(Line.SESSION_RUIN, still = true, watched = false),
    DENIED(Line.SESSION_DENIED, still = true, watched = false),
    RELOCK(Line.SESSION_RELOCK, tap = "Locked again", watched = false, expected = 30),
}

/**
 * One command. [seconds] is how long it runs (for a tap step, the most it waits). [bpm] 0 means no beat.
 * [reps] counts CBT. [comment] is an extra praise or humiliation line shown with it.
 */
data class Step(
    val kind: StepKind,
    val seconds: Int,
    val bpm: Int = 0,
    val reps: Int = 0,
    val line: Line = kind.line,
    val comment: Line? = null,
) {
    /** Roughly how long it takes, for the time estimate. */
    val estimate: Int get() = if (kind.tap != null) minOf(seconds, kind.expected) else seconds
}

data class SessionScript(val ending: SessionEnding, val caged: Boolean, val steps: List<Step>) {
    val estimate: Int get() = steps.sumOf { it.estimate }
}

/** Builds her session. Pure Kotlin, tested in SessionTest. Mood never changes any of it. */
object Session {
    const val EDGE_MAX_SECONDS = 180
    const val TAP_MAX_SECONDS = 300
    const val RUIN_CLIP_SECONDS = 20
    /** Out of view this long during a stroking command and she catches you. */
    const val UNSEEN_SECONDS = 10
    /** At most this many reprimands per session, so it can't go on forever. */
    const val CAUGHT_LIMIT = 5
    const val CBT_SOFT_LIMIT = 2
    const val CBT_HARD_LIMIT = 4

    /** The kinks that can run right now. */
    fun kinks(settings: SessionSettings, caged: Boolean): Set<Kink> =
        settings.kinks.filter { if (caged) it.cageSafe else it.uncaged }.toSet()

    /** Permission, ruined and denied as whole percentages (permission is 0 during a lock). */
    fun endingShares(settings: SessionSettings, caged: Boolean): Triple<Int, Int, Int> {
        val p = if (caged) 0 else settings.permissionWeight.coerceIn(0, 100)
        val r = settings.ruinWeight.coerceIn(0, 100)
        val d = settings.denialWeight.coerceIn(0, 100)
        val total = p + r + d
        if (total == 0) return Triple(0, 0, 100)
        val pp = p * 100 / total
        val rp = r * 100 / total
        return Triple(pp, rp, 100 - pp - rp)
    }

    /** [roll] is 0 until 1. All weights 0 means denied. During a lock permission is never picked. */
    fun pickEnding(settings: SessionSettings, caged: Boolean, roll: Double): SessionEnding {
        val p = if (caged) 0 else settings.permissionWeight.coerceIn(0, 100)
        val r = settings.ruinWeight.coerceIn(0, 100)
        val d = settings.denialWeight.coerceIn(0, 100)
        val total = p + r + d
        if (total == 0) return SessionEnding.DENIED
        val x = roll * total
        return when {
            x < p -> SessionEnding.PERMISSION
            x < p + r -> SessionEnding.RUINED
            else -> SessionEnding.DENIED
        }
    }

    /**
     * The whole session. [soundingReady] is your "sterile sound and lube ready" tick; without it
     * there's no sounding. During a lock ([caged]) only cage-safe commands run until the ending.
     */
    fun build(settings: SessionSettings, caged: Boolean, random: Random, soundingReady: Boolean = false): SessionScript {
        val kinks = kinks(settings, caged).let { if (soundingReady) it else it - Kink.SOUNDING }
        val target = settings.minutes.coerceIn(Sessions.MIN_MINUTES, Sessions.MAX_MINUTES) * 60
        val ending = pickEnding(settings, caged, random.nextDouble())
        val endingSteps = endingSteps(ending, caged, kinks, random)
        val bodyTarget = (target - endingSteps.sumOf { it.estimate }).coerceAtLeast(60)
        val commentary = listOfNotNull(
            Line.SESSION_PRAISE.takeIf { Kink.PRAISE in kinks },
            Line.SESSION_HUMILIATION.takeIf { Kink.HUMILIATION in kinks },
        )
        var cbtLeft = if (settings.cbt == CbtLevel.HARD) CBT_HARD_LIMIT else CBT_SOFT_LIMIT
        val soundAt = if (Kink.SOUNDING in kinks) bodyTarget / 3 else -1
        var sounded = false

        val steps = mutableListOf(Step(StepKind.INTRO, 6))
        var elapsed = steps.sumOf { it.estimate }
        var count = 0
        while (elapsed < bodyTarget) {
            val next: List<Step> = when {
                !sounded && soundAt >= 0 && elapsed >= soundAt -> {
                    sounded = true
                    soundingBlock(random)
                }
                // Without a lock, stroking is the base: after anything else, back to the beat.
                !caged && !steps.last().kind.stroking -> listOf(base(caged, kinks, random))
                else -> {
                    val extra = extra(kinks, settings.cbt, cbtLeft, steps.last().kind, random)
                    if (extra == null || random.nextDouble() < 0.3) listOf(base(caged, kinks, random)) else extra
                }
            }
            if (next.any { it.kind == StepKind.CBT }) cbtLeft--
            val withCountdown =
                if (Kink.COUNTDOWNS in kinks && next.first().kind != StepKind.SOUND_IN && random.nextDouble() < 0.3) {
                    listOf(Step(StepKind.COUNTDOWN, random.nextInt(5, 11))) + next
                } else {
                    next
                }
            for (step in withCountdown) {
                count++
                val commented = if (commentary.isNotEmpty() && count % 3 == 0 && step.kind.watched) {
                    step.copy(comment = commentary[random.nextInt(commentary.size)])
                } else {
                    step
                }
                steps += commented
                elapsed += commented.estimate
            }
        }
        return SessionScript(ending, caged, steps + endingSteps)
    }

    /**
     * What she inserts when she catches you: a reprimand ([why]: out of view, off beat or moved),
     * then an extra edge (a hold during a lock).
     */
    fun caughtSteps(caged: Boolean, why: Line = Line.SESSION_CAUGHT): List<Step> =
        if (caged) {
            listOf(Step(StepKind.CAUGHT, 5, line = why), Step(StepKind.HOLD, 30))
        } else {
            listOf(Step(StepKind.CAUGHT, 5, line = why), Step(StepKind.EDGE, EDGE_MAX_SECONDS, bpm = 130), Step(StepKind.EDGE_HOLD, 20))
        }

    /**
     * What the camera checks during [step]: her beat on plain stroking commands, stillness on
     * watched hands-off commands. Teasing, edging, sounding, CBT, countdowns and the ending aren't checked.
     */
    fun motionCheck(step: Step): MotionCheck = when {
        !step.kind.watched -> MotionCheck.NONE
        step.kind.beat && step.bpm > 0 -> MotionCheck.BEAT
        step.kind.still -> MotionCheck.STILL
        else -> MotionCheck.NONE
    }

    fun endingSteps(ending: SessionEnding, caged: Boolean, kinks: Set<Kink>, random: Random): List<Step> = when (ending) {
        SessionEnding.PERMISSION -> buildList {
            if (Kink.EDGING in kinks) {
                add(Step(StepKind.EDGE, EDGE_MAX_SECONDS, bpm = random.nextInt(110, 141)))
                add(Step(StepKind.EDGE_HOLD, 15))
            }
            add(Step(StepKind.STROKE, 30, bpm = 120))
            add(Step(StepKind.COUNTDOWN, 10))
            add(Step(StepKind.FINISH, TAP_MAX_SECONDS))
        }
        SessionEnding.RUINED -> buildList {
            if (caged) add(Step(StepKind.UNLOCK, TAP_MAX_SECONDS))
            add(Step(StepKind.STROKE, 30, bpm = 110))
            add(Step(StepKind.EDGE, EDGE_MAX_SECONDS, bpm = 130))
            add(Step(StepKind.COUNTDOWN, 3))
            add(Step(StepKind.RUIN, RUIN_CLIP_SECONDS))
            if (caged) add(Step(StepKind.RELOCK, TAP_MAX_SECONDS))
        }
        SessionEnding.DENIED -> if (caged) {
            listOf(Step(StepKind.HOLD, 20), Step(StepKind.DENIED, 15))
        } else {
            listOf(Step(StepKind.EDGE, EDGE_MAX_SECONDS, bpm = random.nextInt(110, 141)), Step(StepKind.DENIED, 15))
        }
    }

    /** Stroking without a lock, holding still during one. */
    private fun base(caged: Boolean, kinks: Set<Kink>, random: Random): Step =
        if (caged) {
            Step(StepKind.HOLD, random.nextInt(15, 31))
        } else {
            val bpm = if (Kink.SPEED in kinks) random.nextInt(50, 141) else random.nextInt(60, 101)
            Step(StepKind.STROKE, random.nextInt(20, 46), bpm = bpm)
        }

    /** One command from the kink menu, or null if nothing fits right now. */
    private fun extra(kinks: Set<Kink>, cbt: CbtLevel, cbtLeft: Int, previous: StepKind, random: Random): List<Step>? {
        val options = kinks.filter {
            when (it) {
                Kink.CBT -> cbtLeft > 0 && previous != StepKind.CBT
                Kink.PRAISE, Kink.HUMILIATION, Kink.COUNTDOWNS, Kink.SOUNDING -> false
                else -> true
            }
        }
        if (options.isEmpty()) return null
        return when (options[random.nextInt(options.size)]) {
            Kink.EDGING -> listOf(
                Step(StepKind.EDGE, EDGE_MAX_SECONDS, bpm = random.nextInt(110, 141)),
                Step(StepKind.EDGE_HOLD, random.nextInt(15, 31)),
            )
            Kink.STOP_AND_GO -> listOf(Step(StepKind.STOP, random.nextInt(10, 31)))
            Kink.SPEED -> listOf(
                if (random.nextBoolean()) {
                    Step(StepKind.FASTER, random.nextInt(10, 26), bpm = random.nextInt(150, 201))
                } else {
                    Step(StepKind.SLOWER, random.nextInt(20, 41), bpm = random.nextInt(30, 51))
                },
            )
            Kink.TEASING -> listOf(Step(StepKind.TEASE, random.nextInt(20, 46), bpm = random.nextInt(20, 41)))
            Kink.HOLDS -> listOf(Step(StepKind.HOLD, random.nextInt(15, 46)))
            Kink.NIPPLES -> listOf(Step(StepKind.NIPPLES, random.nextInt(20, 46), bpm = random.nextInt(40, 71)))
            Kink.CAGE_TEASE -> listOf(Step(StepKind.CAGE_TEASE, random.nextInt(20, 46), bpm = random.nextInt(50, 91)))
            Kink.TOYS -> listOf(Step(StepKind.TOY, random.nextInt(30, 61)))
            Kink.CBT -> listOf(cbtStep(cbt, random))
            else -> null
        }
    }

    /** Counted to a slow beat. Hard means more of them, a little faster; never "as hard as you can". */
    fun cbtStep(level: CbtLevel, random: Random): Step {
        val hard = level == CbtLevel.HARD
        val reps = if (hard) random.nextInt(6, 13) else random.nextInt(3, 6)
        val bpm = if (hard) 30 else 20
        val seconds = ceil(reps * 60.0 / bpm).toInt() + 3
        return Step(StepKind.CBT, seconds, bpm = bpm, reps = reps, line = if (hard) Line.SESSION_CBT_HARD else Line.SESSION_CBT_SOFT)
    }

    /** In, hold, out. No beat and no countdown pressure; "Too much" moves on any time. */
    private fun soundingBlock(random: Random): List<Step> = listOf(
        Step(StepKind.SOUND_IN, 90),
        Step(StepKind.SOUND_HOLD, random.nextInt(60, 181)),
        Step(StepKind.SOUND_OUT, 60),
    )
}
