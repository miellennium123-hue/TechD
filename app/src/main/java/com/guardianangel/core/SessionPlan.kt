package com.guardianangel.core

import com.guardianangel.data.CbtLevel
import com.guardianangel.data.GuardianConfig
import com.guardianangel.data.GuardianState
import com.guardianangel.data.Kink
import com.guardianangel.data.SessionOutcome
import com.guardianangel.data.SessionSettings
import com.guardianangel.data.SessionTheme
import com.guardianangel.data.Sessions
import kotlin.random.Random

/** Why this session is what it is: what she chose, and what you owe her. */
enum class SessionReason { CHOSEN, PUNISHMENT, RUIN_OWED }

/** Round 104: what a finished session pays off: the punishment session you owed, and the ruin you owed. */
data class PaidOff(val punishment: Boolean, val ruin: Boolean)

/** One session's plan: the settings it runs with, its theme, and why (round 84). */
data class SessionPlanned(
    val settings: SessionSettings,
    val theme: SessionTheme,
    val reason: SessionReason,
    /** Week of her training program, 0 when not training. */
    val week: Int,
    /** She picked the length and doesn't tell you. */
    val secretLength: Boolean,
)

/**
 * Plans each session (round 84): themes, her training program, her own lengths, punishment sessions,
 * the ruin you owe after a Porn block catch, and booked sessions. Pure Kotlin, tested in SessionPlanTest.
 * Themes only use kinks you've switched on, so nothing you left off ever comes up. Mood is never used.
 */
object SessionPlan {
    const val WEEK_MS = 7 * 24 * 60 * MINUTE
    /** Her training program tops out at this week. */
    const val MAX_WEEK = 8

    /** A booked session: her reminder comes this long before, and you have this long either side to start. */
    const val BOOK_REMIND_MINUTES = 15
    const val BOOK_WINDOW_MINUTES = 15
    /** After a Porn block catch you owe her a ruin within this long. */
    const val RUIN_OWED_HOURS = 24

    /** The kinks a theme wants. Only the ones you've switched on are used. */
    fun themeKinks(theme: SessionTheme): Set<Kink>? = when (theme) {
        SessionTheme.YOURS -> null
        SessionTheme.TEASE_NIGHT -> setOf(Kink.TEASING, Kink.SPEED, Kink.STOP_AND_GO, Kink.COUNTDOWNS, Kink.PRAISE, Kink.NIPPLES, Kink.CAGE_TEASE)
        SessionTheme.EDGE_MARATHON -> setOf(Kink.EDGING, Kink.SPEED, Kink.COUNTDOWNS, Kink.HUMILIATION, Kink.CAGE_TEASE)
        SessionTheme.PUNISHMENT -> setOf(Kink.CBT, Kink.HUMILIATION, Kink.EDGING, Kink.STOP_AND_GO, Kink.CLAMPS, Kink.SMALL_SIZE, Kink.HOLDS)
        SessionTheme.REWARD -> setOf(Kink.PRAISE, Kink.TEASING, Kink.EDGING, Kink.SPEED, Kink.POST_ORGASM, Kink.TOYS)
        SessionTheme.RUIN_TRAINING -> setOf(Kink.EDGING, Kink.SPEED, Kink.COUNTDOWNS, Kink.HUMILIATION, Kink.POST_ORGASM)
        SessionTheme.CBT_DISCIPLINE -> setOf(Kink.CBT, Kink.COUNTDOWNS, Kink.HUMILIATION, Kink.CLAMPS, Kink.SMALL_SIZE, Kink.HOLDS)
    }

    /** A theme can run when it has something to do: CBT discipline needs CBT on. */
    fun available(theme: SessionTheme, settings: SessionSettings): Boolean =
        theme != SessionTheme.CBT_DISCIPLINE || Kink.CBT in settings.kinks

    /** Your settings as a theme runs them: its kinks (yours only), endings and length. */
    fun applyTheme(settings: SessionSettings, theme: SessionTheme): SessionSettings {
        val kinks = themeKinks(theme)?.let { it intersect settings.kinks } ?: return settings
        fun endings(p: Int, r: Int, d: Int) = settings.copy(kinks = kinks, permissionWeight = p, ruinWeight = r, denialWeight = d)
        return when (theme) {
            SessionTheme.YOURS -> settings
            SessionTheme.TEASE_NIGHT -> endings(10, 15, 75).copy(minutes = 20)
            SessionTheme.EDGE_MARATHON -> endings(10, 45, 45).copy(minutes = 30)
            SessionTheme.PUNISHMENT -> endings(0, 100, 0).copy(minutes = 15, cbt = CbtLevel.HARD)
            SessionTheme.REWARD -> endings(80, 10, 10).copy(minutes = 15)
            SessionTheme.RUIN_TRAINING -> endings(0, 100, 0).copy(minutes = 10)
            SessionTheme.CBT_DISCIPLINE -> endings(0, 30, 70).copy(minutes = 15)
        }
    }

    /** Her training week (1 to [MAX_WEEK]) since [start], or 0 when not training. */
    fun week(start: Long, now: Long): Int =
        if (start <= 0) 0 else ((now - start).coerceAtLeast(0) / WEEK_MS + 1).toInt().coerceAtMost(MAX_WEEK)

    /** Each training week: 3 more minutes, and 5 points of permission moved to denial. */
    fun applyTraining(settings: SessionSettings, week: Int): SessionSettings {
        if (week <= 1) return settings
        val moved = minOf(settings.permissionWeight, 5 * (week - 1))
        return settings.copy(
            minutes = (settings.minutes + 3 * (week - 1)).coerceAtMost(Sessions.HER_MAX_MINUTES),
            permissionWeight = settings.permissionWeight - moved,
            denialWeight = settings.denialWeight + moved,
        )
    }

    /**
     * The session you're about to start. A ruin you owe comes first (always ruined), then a punishment
     * you owe (the Punishment theme), then the theme you picked. Training and her own length go on top.
     * [lengthRoll] (0 until 1) picks her length.
     */
    fun plan(config: GuardianConfig, state: GuardianState, chosen: SessionTheme, now: Long, lengthRoll: Double): SessionPlanned {
        val s = config.session
        val reason = when {
            ruinOwed(config, state, now) -> SessionReason.RUIN_OWED
            s.punishmentSessions && state.owedPunishment -> SessionReason.PUNISHMENT
            else -> SessionReason.CHOSEN
        }
        val theme = when (reason) {
            SessionReason.RUIN_OWED -> SessionTheme.RUIN_TRAINING
            SessionReason.PUNISHMENT -> SessionTheme.PUNISHMENT
            SessionReason.CHOSEN -> chosen.takeIf { available(it, s) } ?: SessionTheme.YOURS
        }
        var planned = applyTheme(s, theme)
        val week = if (s.training) week(state.trainingStart, now) else 0
        planned = applyTraining(planned, week)
        if (s.herLength) {
            val span = Sessions.HER_MAX_MINUTES - Sessions.MIN_MINUTES
            planned = planned.copy(minutes = Sessions.MIN_MINUTES + (lengthRoll * (span + 1)).toInt().coerceAtMost(span))
        }
        // Owed sessions always end ruined, whatever the theme or training would have said.
        if (reason != SessionReason.CHOSEN) planned = planned.copy(permissionWeight = 0, ruinWeight = 100, denialWeight = 0)
        // Round 104: with her release calendar on, only her release session gives permission.
        if (!Release.permissionAllowed(config)) planned = planned.copy(permissionWeight = 0)
        return SessionPlanned(planned, theme, reason, week, s.herLength)
    }

    /**
     * Round 104: what a finished session pays off. A punishment session pays the punishment you owed.
     * A ruin pays the ruin you owed after a catch, and since that ruin is her punishment for the catch
     * (which also counted as a failure), it pays the punishment session too.
     */
    fun paysOff(theme: SessionTheme, outcome: SessionOutcome, ruinOwed: Boolean): PaidOff {
        val ruin = outcome == SessionOutcome.RUINED && ruinOwed
        return PaidOff(punishment = theme == SessionTheme.PUNISHMENT || ruin, ruin = ruin)
    }

    /**
     * Round 104, once on update: before the fix, doing the ruin you owed after a catch left the catch's
     * punishment session owed. If your last failure was that catch and its ruin is paid, it's cleared.
     */
    fun leftoverFromCatch(owedPunishment: Boolean, ruinOwedBy: Long, lastFailureKind: String?): Boolean =
        owedPunishment && ruinOwedBy == 0L && lastFailureKind == "CAUGHT_PORN"

    /** You owe her a ruin (Porn block caught you) and it isn't overdue yet. */
    fun ruinOwed(config: GuardianConfig, state: GuardianState, now: Long): Boolean =
        config.enabled && config.session.ruinAfterCatch && state.ruinOwedBy > now

    /**
     * Her next booked session: one or two days from now, at a whole quarter hour she picks, never inside
     * (or within a window of) quiet hours or bedtime. [dayRoll] and [timeRoll] are 0 until 1. Returns
     * the time, or null if she can't find one. [minuteOfDayNow] is the local time now.
     */
    fun nextBooking(config: GuardianConfig, now: Long, minuteOfDayNow: Int, dayRoll: Double, timeRoll: Double): Long? {
        val startOfToday = now - minuteOfDayNow * MINUTE
        val day = if (dayRoll < 0.5) 1 else 2
        // Every quarter hour from 09:00 to 22:00 that keeps clear of quiet time on both sides.
        val options = (9 * 60..22 * 60 step 15).filter { m ->
            (m - BOOK_REMIND_MINUTES..m + BOOK_WINDOW_MINUTES).none { Rules.isQuiet(config, ((it % 1440) + 1440) % 1440) }
        }
        if (options.isEmpty()) return null
        val minute = options[(timeRoll * options.size).toInt().coerceIn(0, options.lastIndex)]
        return startOfToday + day * 24 * 60 * MINUTE + minute * MINUTE
    }

    /** Starting a session now keeps her booking. */
    fun keepsBooking(bookedAt: Long, now: Long): Boolean =
        now in (bookedAt - BOOK_WINDOW_MINUTES * MINUTE)..(bookedAt + BOOK_WINDOW_MINUTES * MINUTE)
}
