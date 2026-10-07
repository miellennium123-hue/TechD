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
 * [still]: hands off. [stroking]: stroking to her beat.
 * [watched]: her praise or humiliation remarks can come with it. Never during sounding or the ending.
 * [skippable]: shows "Too much", which moves on with no penalty.
 * Round 77: no more motion checks or catching you out of view; the camera only shows you yourself and films.
 */
enum class StepKind(
    val line: Line,
    val tap: String? = null,
    val still: Boolean = false,
    val stroking: Boolean = false,
    val watched: Boolean = true,
    val skippable: Boolean = false,
    /** Expected length of a [tap], [WATCH], [REPLAY] or [DEAL] step, for the time estimate. */
    val expected: Int = 0,
) {
    INTRO(Line.SESSION_START, watched = false),
    STROKE(Line.SESSION_STROKE, stroking = true),
    FASTER(Line.SESSION_FASTER, stroking = true),
    SLOWER(Line.SESSION_SLOWER, stroking = true),
    /** Round 79: her beat speeds up the whole step, faster and faster. */
    RAMP(Line.SESSION_RAMP, stroking = true),
    /** Round 79: exactly [Step.reps] strokes to her beat, counted on screen. */
    COUNT(Line.SESSION_COUNT, stroking = true),
    TEASE(Line.SESSION_TEASE, stroking = true),
    EDGE(Line.SESSION_EDGE, tap = "I'm at the edge", stroking = true, expected = 45),
    /** Round 79: stay right at the edge with her slowest strokes. */
    BALANCE(Line.SESSION_BALANCE, stroking = true),
    EDGE_HOLD(Line.SESSION_EDGE_HOLD, still = true),
    STOP(Line.SESSION_STOP, still = true),
    HOLD(Line.SESSION_HOLD, still = true),
    NIPPLES(Line.SESSION_NIPPLES),
    CAGE_TEASE(Line.SESSION_CAGE_TEASE),
    TOY(Line.SESSION_TOY),
    CBT(Line.SESSION_CBT_SOFT, skippable = true),
    /** Round 84, Clamps kink: put them on, her timer, then off. Never longer than 3 minutes. */
    CLAMP_ON(Line.SESSION_CLAMP_ON, tap = "They're on", watched = false, expected = 30),
    CLAMP(Line.SESSION_CLAMP, skippable = true),
    CLAMP_OFF(Line.SESSION_CLAMP_OFF, tap = "They're off", watched = false, expected = 20),
    SOUND_IN(Line.SESSION_SOUND_IN, watched = false, skippable = true),
    SOUND_HOLD(Line.SESSION_SOUND_HOLD, still = true, watched = false, skippable = true),
    SOUND_OUT(Line.SESSION_SOUND_OUT, watched = false, skippable = true),
    COUNTDOWN(Line.SESSION_COUNTDOWN, watched = false),
    /**
     * Round 77: she plays one of your clips and you watch yourself. Moves on when it ends. Since round 79
     * you stroke to her beat while you watch (hands still during a lock), with the live camera beside it.
     */
    WATCH(Line.SESSION_WATCH, watched = false, expected = 40),
    /** Round 79: her offer before the ending. A sure ruin now, or more edges for a coin flip at a full one. */
    DEAL(Line.SESSION_DEAL, watched = false, expected = 20),
    /** Round 79: the coin flip after you took her edges. [Step.line] says whether you won. */
    FLIP(Line.SESSION_FLIP_LOST, still = true, watched = false),
    /** Round 79, "Keep going" kink: stroke right through your orgasm or ruin. */
    POST(Line.SESSION_POST, watched = false),
    /** Round 79: she plays your ruin back right away (stroking through it with "Keep going"). */
    REPLAY(Line.SESSION_REPLAY, watched = false, expected = 20),
    UNLOCK(Line.SESSION_UNLOCK, tap = "Unlocked", watched = false, expected = 30),
    /** Round 79: cum on her exact command, then tell her honestly if you made it. */
    FINISH(Line.SESSION_FINISH, watched = false, expected = 30),
    RUIN(Line.SESSION_RUIN, still = true, watched = false),
    DENIED(Line.SESSION_DENIED, still = true, watched = false),
    RELOCK(Line.SESSION_RELOCK, tap = "Locked again", watched = false, expected = 30),
    /** Round 84: her cool-down after every ending. Hands off, breathe. */
    COOL(Line.SESSION_COOL, still = true, watched = false),
}

/** Round 79: how she wants your strokes on her beat. [label] is shown with the beat. */
enum class BeatPattern(val label: String) {
    STEADY("Follow the beat"),
    /** Two quick strokes, then a pause, like a heartbeat. */
    HEARTBEAT("Heartbeat"),
    /** She skips and doubles beats without warning. */
    STUTTER("Stutter"),
    /** Only every other beat is a stroke; the soft ones are hands still. */
    EVERY_OTHER("Every other beat"),
}

/** Round 79: grip and style commands that come with some strokes. */
enum class StrokeStyle(val label: String) {
    TIP("Only the tip"),
    TWO_FINGERS("Two fingers only"),
    OFF_HAND("Your other hand"),
    FULL("Full length, every stroke"),
    TIGHT("Squeeze tight"),
    LIGHT("Barely touching"),
    PALM("Flat palm only"),
}

/** Her offer (round 79): a sure ruin now, or more edges and a coin flip. */
enum class DealChoice { RUIN_NOW, EDGES }

/**
 * One command. [seconds] is how long it runs (for a tap step, the most it waits). [bpm] 0 means no beat.
 * [bpmTo] (round 79): the beat speeds up (or slows) to this by the end of the step; 0 keeps it steady.
 * [reps] counts CBT and exact counts. [comment] is an extra praise or humiliation line shown with it.
 * [ending]: part of her ending, which her deal can swap (round 79). [pattern] and [style]: how to stroke.
 * [tauntAt] (round 79): a countdown holds on this number while she taunts you; 0 for a straight count.
 */
data class Step(
    val kind: StepKind,
    val seconds: Int,
    val bpm: Int = 0,
    val reps: Int = 0,
    val line: Line = kind.line,
    val comment: Line? = null,
    val bpmTo: Int = 0,
    val ending: Boolean = false,
    val pattern: BeatPattern = BeatPattern.STEADY,
    val style: StrokeStyle? = null,
    val tauntAt: Int = 0,
    /** Round 84: part of her warm-up. */
    val warmup: Boolean = false,
) {
    /** Her beat [elapsedMs] into the step: steady, or partway from [bpm] to [bpmTo]. */
    fun bpmAt(elapsedMs: Long): Int {
        if (bpmTo <= 0 || seconds <= 0) return bpm
        val f = (elapsedMs / (seconds * 1_000.0)).coerceIn(0.0, 1.0)
        return (bpm + (bpmTo - bpm) * f).toInt()
    }

    /** Roughly how long it takes, for the time estimate. */
    val estimate: Int get() = when {
        kind.tap != null || kind.expected > 0 -> minOf(seconds, kind.expected)
        kind == StepKind.COUNTDOWN && tauntAt > 0 -> seconds + Session.TAUNT_SECONDS
        else -> seconds
    }
}

/** [quick]: a quickshot (round 49), short and always ruined. */
data class SessionScript(val ending: SessionEnding, val caged: Boolean, val steps: List<Step>, val quick: Boolean = false) {
    val estimate: Int get() = steps.sumOf { it.estimate }

    /** Round 79: edges in this session, for her edge goal. */
    val edgeGoal: Int get() = steps.count { it.kind == StepKind.EDGE }
}

/** Builds her session. Pure Kotlin, tested in SessionTest. Mood never changes any of it. */
object Session {
    const val EDGE_MAX_SECONDS = 180
    const val TAP_MAX_SECONDS = 300
    const val RUIN_CLIP_SECONDS = 20
    /** A quickshot's edge waits at most this long for your tap. */
    const val QUICKSHOT_EDGE_SECONDS = 120
    /** A clip she plays can run this long at most (a whole edge is up to 3 minutes). */
    const val WATCH_MAX_SECONDS = EDGE_MAX_SECONDS + 30
    const val CBT_SOFT_LIMIT = 2
    const val CBT_HARD_LIMIT = 4

    /** Round 79: a cruel countdown holds this long on her number while she taunts you. */
    const val TAUNT_SECONDS = 3
    /** Round 79: reaching the edge faster than this and she mocks you. */
    const val EDGE_FAST_SECONDS = 40
    /** Round 79: rests between edges start here and shrink by [REST_STEP] each edge, down to [REST_MIN]. */
    const val REST_START = 30
    const val REST_STEP = 5
    const val REST_MIN = 8
    /** Round 79: her deal adds this many edges before the coin flip, and how long you have to choose. */
    const val DEAL_EDGES = 5
    const val DEAL_SECONDS = 60
    /** Round 79: how often she offers her deal, plays a trick countdown, or taunts mid-countdown. */
    const val DEAL_CHANCE = 0.35
    const val TRICK_CHANCE = 0.25
    const val TAUNT_CHANCE = 0.3
    /** Round 84: clamps at most twice a session, each for at most this long. */
    const val CLAMPS_PER_SESSION = 2
    const val CLAMP_MAX_SECONDS = 180
    const val COOL_SECONDS = 45

    /** Round 84: the chapter a command belongs to, shown on screen when it changes. */
    fun chapter(step: Step): String = when {
        step.warmup -> "Warm-up"
        step.kind == StepKind.COOL -> "Cool-down"
        step.ending -> "The ending"
        step.kind == StepKind.INTRO -> ""
        else -> "Her session"
    }

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

    /** Round 79: late at night (22:00 to 06:00) her voice drops to a whisper, with Whisper on. */
    fun whisper(settings: SessionSettings, minuteOfDay: Int): Boolean =
        settings.voice && settings.whisper && (minuteOfDay >= 22 * 60 || minuteOfDay < 6 * 60)

    /** Round 79: the edge came quickly enough for her to mock you. */
    fun edgeFast(seconds: Int): Boolean = seconds in 0 until EDGE_FAST_SECONDS

    /** Round 79: the rest after your [n]th edge (0 for the first), shorter every time. */
    fun rest(n: Int): Int = (REST_START - REST_STEP * n).coerceAtLeast(REST_MIN)

    /**
     * The whole session. [soundingReady] is your "sterile sound and lube ready" tick; without it
     * there's no sounding. During a lock ([caged]) only cage-safe commands run until the ending.
     * [watchClip] (round 77, see Clips.inSession): somewhere in the middle she plays one of your clips.
     */
    fun build(
        settings: SessionSettings,
        caged: Boolean,
        random: Random,
        soundingReady: Boolean = false,
        watchClip: Boolean = false,
        sizeKnown: Boolean = false,
    ): SessionScript {
        val kinks = kinks(settings, caged).let { if (soundingReady) it else it - Kink.SOUNDING }
        val target = settings.minutes.coerceIn(Sessions.MIN_MINUTES, Sessions.HER_MAX_MINUTES) * 60
        val ending = pickEnding(settings, caged, random.nextDouble())
        val endingSteps = endingSteps(ending, caged, kinks, random)
        val bodyTarget = (target - endingSteps.sumOf { it.estimate }).coerceAtLeast(60)
        val commentary = listOfNotNull(
            Line.SESSION_PRAISE.takeIf { Kink.PRAISE in kinks },
            Line.SESSION_HUMILIATION.takeIf { Kink.HUMILIATION in kinks },
            // Round 84: small-size remarks need your Rate me result.
            Line.SESSION_SPH.takeIf { Kink.SMALL_SIZE in kinks && sizeKnown },
        )
        var cbtLeft = if (settings.cbt == CbtLevel.HARD) CBT_HARD_LIMIT else CBT_SOFT_LIMIT
        val soundAt = if (Kink.SOUNDING in kinks) bodyTarget / 3 else -1
        var sounded = false
        var edges = 0
        var clampsLeft = CLAMPS_PER_SESSION

        val steps = mutableListOf(Step(StepKind.INTRO, 6))
        steps += warmup(caged, random)
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
                    val extra = extra(kinks, settings.cbt, cbtLeft, clampsLeft, steps.last().kind, edges, random)
                    if (extra == null || random.nextDouble() < 0.3) listOf(base(caged, kinks, random)) else extra
                }
            }
            if (next.any { it.kind == StepKind.CBT }) cbtLeft--
            if (next.any { it.kind == StepKind.CLAMP }) clampsLeft--
            edges += next.count { it.kind == StepKind.EDGE }
            val withCountdown =
                if (Kink.COUNTDOWNS in kinks && next.first().kind != StepKind.SOUND_IN && random.nextDouble() < 0.3) {
                    listOf(countdown(random.nextInt(5, 11), random)) + next
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
        if (watchClip) insertWatch(steps, caged, random)
        // Her deal before the ending: never during a lock or when you'd already get permission.
        if (!caged && ending != SessionEnding.PERMISSION && random.nextDouble() < DEAL_CHANCE) {
            steps += Step(StepKind.DEAL, DEAL_SECONDS)
        }
        return SessionScript(ending, caged, steps + endingSteps)
    }

    /**
     * Round 79: your answer to her deal. RUIN_NOW: the ending becomes a ruin right away. EDGES:
     * [DEAL_EDGES] more edges with shorter and shorter rests, then a coin flip: heads, permission;
     * tails, the ending you had. Only the ending commands you haven't started are replaced.
     */
    fun takeDeal(
        script: SessionScript,
        steps: List<Step>,
        index: Int,
        choice: DealChoice,
        kinks: Set<Kink>,
        random: Random,
    ): Pair<SessionEnding, List<Step>> {
        val kept = steps.filterIndexed { i, s -> i <= index || !s.ending }
        return when (choice) {
            DealChoice.RUIN_NOW -> SessionEnding.RUINED to kept + endingSteps(SessionEnding.RUINED, script.caged, kinks, random)
            DealChoice.EDGES -> {
                val won = random.nextBoolean()
                val ending = if (won) SessionEnding.PERMISSION else script.ending
                val edges = (0 until DEAL_EDGES).flatMap { n ->
                    listOf(edge(random.nextInt(110, 141)), Step(StepKind.EDGE_HOLD, rest(n + 2)))
                }
                val flip = Step(StepKind.FLIP, 6, line = if (won) Line.SESSION_FLIP_WON else Line.SESSION_FLIP_LOST)
                ending to kept.take(index + 1) + edges + flip + kept.drop(index + 1) + endingSteps(ending, script.caged, kinks, random)
            }
        }
    }

    /** Round 84: her warm-up. Slow, light strokes (hands still during a lock) before she starts for real. */
    private fun warmup(caged: Boolean, random: Random): List<Step> =
        if (caged) {
            listOf(Step(StepKind.HOLD, 20, line = Line.SESSION_WARMUP, warmup = true))
        } else {
            listOf(
                Step(StepKind.STROKE, random.nextInt(25, 36), bpm = random.nextInt(40, 56), line = Line.SESSION_WARMUP, warmup = true),
                Step(StepKind.STROKE, random.nextInt(20, 31), bpm = random.nextInt(55, 71), warmup = true),
            )
        }

    /** A countdown, sometimes with her taunt held on a number partway down. */
    private fun countdown(seconds: Int, random: Random): Step =
        if (seconds >= 3 && random.nextDouble() < TAUNT_CHANCE) {
            Step(StepKind.COUNTDOWN, seconds, tauntAt = random.nextInt(1, minOf(seconds, 4) + 1))
        } else {
            Step(StepKind.COUNTDOWN, seconds)
        }

    /**
     * Her watch break: stroke slowly to her beat while your clip plays (round 79), or hands still
     * during a lock.
     */
    fun watchStep(caged: Boolean, random: Random): Step =
        if (caged) {
            Step(StepKind.WATCH, WATCH_MAX_SECONDS)
        } else {
            Step(StepKind.WATCH, WATCH_MAX_SECONDS, bpm = random.nextInt(60, 91), line = Line.SESSION_WATCH_STROKE)
        }

    /** One "watch yourself" break in the middle half of the body, never inside sounding. */
    private fun insertWatch(steps: MutableList<Step>, caged: Boolean, random: Random) {
        val inSounding = setOf(StepKind.SOUND_IN, StepKind.SOUND_HOLD)
        val places = (1..steps.size).filter { i ->
            steps[i - 1].kind !in inSounding && steps[i - 1].kind != StepKind.COUNTDOWN
        }
        val middle = places.filter { it >= steps.size / 4 && it <= steps.size * 3 / 4 }.ifEmpty { places }
        steps.add(middle[random.nextInt(middle.size)], watchStep(caged, random))
    }

    /**
     * A quickshot (round 49): about two minutes, fast to her beat, always a ruin she films.
     * Ignores the kink menu, the length and the ending sliders. During a lock: unlock first, cage back on after.
     * [watchClip] (round 78): she plays one of your clips after the first strokes, like every session.
     * Round 79: her edge speeds up, and she replays your ruin right after.
     */
    fun quickshot(caged: Boolean, random: Random, watchClip: Boolean = false): SessionScript = SessionScript(
        SessionEnding.RUINED,
        caged,
        buildList {
            add(Step(StepKind.INTRO, 4, line = Line.SESSION_QUICKSHOT))
            if (caged) add(Step(StepKind.UNLOCK, TAP_MAX_SECONDS))
            add(Step(StepKind.STROKE, 15, bpm = random.nextInt(130, 151)))
            if (watchClip) add(watchStep(caged = false, random = random))
            add(Step(StepKind.FASTER, 10, bpm = random.nextInt(170, 191)))
            add(Step(StepKind.EDGE, QUICKSHOT_EDGE_SECONDS, bpm = 150, bpmTo = 180))
            add(Step(StepKind.COUNTDOWN, 3))
            add(Step(StepKind.RUIN, RUIN_CLIP_SECONDS))
            add(Step(StepKind.REPLAY, RUIN_CLIP_SECONDS + 5))
            if (caged) add(Step(StepKind.RELOCK, TAP_MAX_SECONDS))
        }.map { it.copy(ending = true) },
        quick = true,
    )

    /**
     * Her ending. Edges speed up to the edge (round 79). Every step is marked [Step.ending].
     * Round 79 cruelty: sometimes her countdown to cum stops at 1 ("not yet") for one more edge, and a
     * denial can come at the end of a countdown. After a ruin she replays it; with "Keep going" (never
     * during a lock) you stroke right through your orgasm or the replay. Permission is on her exact command.
     */
    fun endingSteps(ending: SessionEnding, caged: Boolean, kinks: Set<Kink>, random: Random): List<Step> {
        val keepGoing = Kink.POST_ORGASM in kinks && !caged
        val steps = when (ending) {
            SessionEnding.PERMISSION -> buildList {
                if (Kink.EDGING in kinks) {
                    add(edge(random.nextInt(110, 141)))
                    add(Step(StepKind.EDGE_HOLD, 15))
                }
                add(Step(StepKind.STROKE, 30, bpm = 120))
                if (random.nextDouble() < TRICK_CHANCE) {
                    // The 1 that never comes: down to 1, then "not yet", and one more edge.
                    add(Step(StepKind.COUNTDOWN, 10))
                    add(edge(random.nextInt(110, 141)).copy(line = Line.SESSION_NOT_YET))
                    add(Step(StepKind.EDGE_HOLD, REST_MIN))
                }
                add(countdown(10, random))
                add(Step(StepKind.FINISH, TAP_MAX_SECONDS))
                if (keepGoing) add(postStep(random))
            }
            SessionEnding.RUINED -> buildList {
                if (caged) add(Step(StepKind.UNLOCK, TAP_MAX_SECONDS))
                add(Step(StepKind.STROKE, 30, bpm = 110))
                add(edge(130))
                add(Step(StepKind.COUNTDOWN, 3))
                add(Step(StepKind.RUIN, RUIN_CLIP_SECONDS))
                // Instant replay, stroking through it with "Keep going".
                add(Step(StepKind.REPLAY, RUIN_CLIP_SECONDS + 5, bpm = if (keepGoing) random.nextInt(50, 71) else 0))
                if (caged) add(Step(StepKind.RELOCK, TAP_MAX_SECONDS))
            }
            SessionEnding.DENIED -> if (caged) {
                listOf(Step(StepKind.HOLD, 20), Step(StepKind.DENIED, 15))
            } else if (random.nextDouble() < TRICK_CHANCE) {
                // She counts you all the way down... then no.
                listOf(edge(random.nextInt(110, 141)), Step(StepKind.COUNTDOWN, 5), Step(StepKind.DENIED, 15, line = Line.SESSION_NO))
            } else {
                listOf(edge(random.nextInt(110, 141)), Step(StepKind.DENIED, 15))
            }
        }
        // Round 84: her cool-down after every ending.
        return (steps + Step(StepKind.COOL, COOL_SECONDS)).map { it.copy(ending = true) }
    }

    /** An edge: her beat starts at [bpm] and speeds up as you get closer. */
    private fun edge(bpm: Int): Step = Step(StepKind.EDGE, EDGE_MAX_SECONDS, bpm = bpm, bpmTo = bpm + 30)

    /** Keep going: slow strokes right through it, 30 to 45 seconds. */
    private fun postStep(random: Random): Step = Step(StepKind.POST, random.nextInt(30, 46), bpm = random.nextInt(50, 71))

    /**
     * Stroking without a lock, holding still during one. Round 79: sometimes an exact count, sometimes a
     * grip or style command, and with Speed changes a beat pattern.
     */
    private fun base(caged: Boolean, kinks: Set<Kink>, random: Random): Step {
        if (caged) return Step(StepKind.HOLD, random.nextInt(15, 31))
        val style = if (random.nextDouble() < 0.3) StrokeStyle.entries[random.nextInt(StrokeStyle.entries.size)] else null
        if (random.nextDouble() < 0.15) {
            val reps = random.nextInt(2, 7) * 10
            val bpm = random.nextInt(70, 111)
            return Step(StepKind.COUNT, ceil(reps * 60.0 / bpm).toInt() + 2, bpm = bpm, reps = reps, style = style)
        }
        val bpm = if (Kink.SPEED in kinks) random.nextInt(50, 141) else random.nextInt(60, 101)
        val pattern = if (Kink.SPEED in kinks && random.nextDouble() < 0.3) {
            listOf(BeatPattern.HEARTBEAT, BeatPattern.STUTTER, BeatPattern.EVERY_OTHER)[random.nextInt(3)]
        } else {
            BeatPattern.STEADY
        }
        return Step(StepKind.STROKE, random.nextInt(20, 46), bpm = bpm, pattern = pattern, style = style)
    }

    /** One command from the kink menu, or null if nothing fits right now. [edges]: edges so far, for shrinking rests. */
    private fun extra(kinks: Set<Kink>, cbt: CbtLevel, cbtLeft: Int, clampsLeft: Int, previous: StepKind, edges: Int, random: Random): List<Step>? {
        val options = kinks.filter {
            when (it) {
                Kink.CBT -> cbtLeft > 0 && previous != StepKind.CBT
                Kink.CLAMPS -> clampsLeft > 0
                Kink.PRAISE, Kink.HUMILIATION, Kink.COUNTDOWNS, Kink.SOUNDING, Kink.POST_ORGASM, Kink.SMALL_SIZE -> false
                else -> true
            }
        }
        if (options.isEmpty()) return null
        return when (options[random.nextInt(options.size)]) {
            Kink.EDGING -> buildList {
                add(edge(random.nextInt(110, 141)))
                // Round 79: sometimes she keeps you balanced right at the edge first.
                if (random.nextDouble() < 0.4) add(Step(StepKind.BALANCE, random.nextInt(10, 16), bpm = 20))
                add(Step(StepKind.EDGE_HOLD, rest(edges)))
            }
            Kink.STOP_AND_GO -> listOf(Step(StepKind.STOP, random.nextInt(10, 31)))
            Kink.SPEED -> listOf(
                when (random.nextInt(3)) {
                    0 -> Step(StepKind.FASTER, random.nextInt(10, 26), bpm = random.nextInt(150, 201))
                    1 -> Step(StepKind.SLOWER, random.nextInt(20, 41), bpm = random.nextInt(30, 51))
                    // Round 79: faster and faster, from slow to frantic.
                    else -> Step(StepKind.RAMP, random.nextInt(30, 46), bpm = random.nextInt(60, 81), bpmTo = random.nextInt(170, 201))
                },
            )
            Kink.TEASING -> listOf(Step(StepKind.TEASE, random.nextInt(20, 46), bpm = random.nextInt(20, 41)))
            Kink.HOLDS -> listOf(Step(StepKind.HOLD, random.nextInt(15, 46)))
            Kink.NIPPLES -> listOf(Step(StepKind.NIPPLES, random.nextInt(20, 46), bpm = random.nextInt(40, 71)))
            Kink.CAGE_TEASE -> listOf(Step(StepKind.CAGE_TEASE, random.nextInt(20, 46), bpm = random.nextInt(50, 91)))
            Kink.TOYS -> listOf(Step(StepKind.TOY, random.nextInt(30, 61)))
            Kink.CBT -> listOf(cbtStep(cbt, random))
            Kink.CLAMPS -> listOf(
                Step(StepKind.CLAMP_ON, 120),
                Step(StepKind.CLAMP, random.nextInt(60, CLAMP_MAX_SECONDS + 1)),
                Step(StepKind.CLAMP_OFF, 60),
            )
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
