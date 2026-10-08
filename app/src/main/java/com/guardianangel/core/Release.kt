package com.guardianangel.core

import com.guardianangel.data.FailureRecord
import com.guardianangel.data.GuardianConfig
import com.guardianangel.data.ReleaseEntry
import com.guardianangel.data.ReleaseLog
import com.guardianangel.data.ReleaseSettings
import com.guardianangel.data.ReleaseState
import com.guardianangel.data.SessionOutcome
import com.guardianangel.data.SessionRecord
import com.guardianangel.data.WheelResult

/** What a day on her calendar shows. */
enum class DayMark(val label: String) {
    RELEASE_DAY("Her release day"),
    RELEASED("Came"),
    RUINED("Ruined"),
    DENIED("Edged or denied"),
    CAGED("Caged"),
    FAILED("A failure"),
}

/**
 * Her release calendar (round 104). Pure Kotlin, tested in ReleaseTest. Days are local days since
 * 1970 (Guardian works them out). Mood is never used, and nothing moves her day once it's set.
 */
object Release {
    const val MIN_DAYS = 1
    const val MAX_DAYS = 30
    /** A missed release day carries over this many days, then it's gone. */
    const val CARRY_DAYS = 1
    /** Her wheel's "another week". */
    const val WEEK_DAYS = 7
    /** Her log keeps about a year. */
    const val LOG_KEEP = 366

    /** Her next release day: [minDays] to [maxDays] after [fromDay], at random ([roll] is 0 until 1). */
    fun pickDay(fromDay: Long, settings: ReleaseSettings, roll: Double): Long {
        val min = settings.minDays.coerceIn(MIN_DAYS, MAX_DAYS)
        val max = settings.maxDays.coerceIn(min, MAX_DAYS)
        val span = max - min
        return fromDay + min + (roll * (span + 1)).toInt().coerceIn(0, span)
    }

    /** Switching it on: she picks your first day. */
    fun start(today: Long, settings: ReleaseSettings, roll: Double): ReleaseState =
        ReleaseState(nextDay = pickDay(today, settings, roll), startDay = today)

    /**
     * Round 105: you changed your range. If her day no longer fits it (counting from today), she picks
     * again; otherwise it stays. Never on release day itself. Null when nothing changes.
     */
    fun refit(release: ReleaseState, today: Long, settings: ReleaseSettings, roll: Double): ReleaseState? {
        if (isReleaseDay(release, today)) return null
        val min = settings.minDays.coerceIn(MIN_DAYS, MAX_DAYS)
        val max = settings.maxDays.coerceIn(min, MAX_DAYS)
        val left = release.nextDay - today
        if (left in min..max) return null
        return release.copy(nextDay = pickDay(today, settings, roll), notifiedDay = -1)
    }

    /** Today you may use her release day: the day itself, or the day after (carried over). */
    fun isReleaseDay(release: ReleaseState, today: Long): Boolean =
        today in release.nextDay..release.nextDay + CARRY_DAYS

    /** Today is the carried-over day: your last chance. */
    fun lastChance(release: ReleaseState, today: Long): Boolean = today == release.nextDay + CARRY_DAYS

    /**
     * Release day and the day after passed unused: logged as missed (denied), and she picks a new day
     * from today. Null when nothing was missed.
     */
    fun rollOver(release: ReleaseState, today: Long, settings: ReleaseSettings, roll: Double, now: Long): Pair<ReleaseState, ReleaseLog>? {
        if (today <= release.nextDay + CARRY_DAYS) return null
        val next = release.copy(nextDay = pickDay(today, settings, roll), spin = null, reelWatched = false)
        return next to ReleaseLog(release.nextDay, now, ReleaseEntry.MISSED)
    }

    /** Her wheel's chance of permission, in percent. */
    fun permissionChance(settings: ReleaseSettings): Int {
        val ruin = settings.ruinChance.coerceIn(0, 100)
        val week = settings.weekChance.coerceIn(0, 100 - ruin)
        return 100 - ruin - week
    }

    /** Her wheel ([roll] is 0 until 1). */
    fun spin(settings: ReleaseSettings, roll: Double): WheelResult {
        val ruin = settings.ruinChance.coerceIn(0, 100)
        val week = settings.weekChance.coerceIn(0, 100 - ruin)
        val x = roll * 100
        return when {
            x < ruin -> WheelResult.RUIN
            x < ruin + week -> WheelResult.WEEK
            else -> WheelResult.PERMISSION
        }
    }

    /** Days denied: since your last release, or since she started the calendar. */
    fun deniedDays(release: ReleaseState, today: Long): Int =
        (today - (if (release.lastReleaseDay >= 0) release.lastReleaseDay else release.startDay)).coerceAtLeast(0).toInt()

    /** Your best streak, counting the one you're on. */
    fun bestStreak(release: ReleaseState, today: Long): Int = maxOf(release.bestStreak, deniedDays(release, today))

    /**
     * Release day used: [entry] is RELEASED, RUINED or WEEK. Another week is 7 days from today; anything
     * else, she picks a new day from the range. A release ends your streak.
     */
    fun used(release: ReleaseState, today: Long, entry: ReleaseEntry, settings: ReleaseSettings, roll: Double): ReleaseState {
        val released = entry == ReleaseEntry.RELEASED
        return release.copy(
            nextDay = if (entry == ReleaseEntry.WEEK) today + WEEK_DAYS else pickDay(today, settings, roll),
            lastReleaseDay = if (released) today else release.lastReleaseDay,
            bestStreak = if (released) bestStreak(release, today) else release.bestStreak,
            spin = null,
            reelWatched = false,
        )
    }

    /** What a finished release session counts as: a ruin is a ruin; anything you came in is a release. */
    fun entryFor(outcome: SessionOutcome): ReleaseEntry = when (outcome) {
        SessionOutcome.RUINED -> ReleaseEntry.RUINED
        SessionOutcome.DENIED -> ReleaseEntry.WEEK
        else -> ReleaseEntry.RELEASED
    }

    /** You came without her permission and told her: your streak ends. Her day doesn't move. */
    fun confess(release: ReleaseState, today: Long): ReleaseState =
        release.copy(lastReleaseDay = today, bestStreak = bestStreak(release, today))

    /** The day you may see: null while hidden (until it's release day). */
    fun shownDay(release: ReleaseState, settings: ReleaseSettings, today: Long): Long? =
        release.nextDay.takeIf { settings.showDate || isReleaseDay(release, today) }

    fun daysLeft(release: ReleaseState, today: Long): Long = (release.nextDay - today).coerceAtLeast(0)

    /** Outside her release session, guided sessions never end with permission while the calendar is on. */
    fun permissionAllowed(config: GuardianConfig): Boolean = !(config.enabled && config.release.on)

    /** Her morning notice: once each release day, never in quiet time. */
    fun notifyDue(release: ReleaseState, today: Long, quiet: Boolean): Boolean =
        isReleaseDay(release, today) && release.notifiedDay != today && !quiet

    /** Days left to draw on her wallpaper, or null when she shouldn't. */
    fun wallpaperDays(config: GuardianConfig, release: ReleaseState?, today: Long): Long? {
        val s = config.release
        if (!config.enabled || !s.on || !s.showDate || !s.countdownWallpaper || release == null) return null
        return daysLeft(release, today)
    }

    /** Her ruin clips from the last 7 days, oldest first, for her reel before you spin. */
    fun weekReel(clips: List<ClipInfo>, now: Long): List<ClipInfo> =
        Clips.reel(clips).filter { now - it.at < WEEK_DAYS * 24 * 60 * MINUTE }

    /**
     * What a day on her calendar shows. [dayOf] turns a time into its local day. [cagedSince]: when
     * the lock you're in started, or null.
     */
    fun marks(
        day: Long,
        today: Long,
        release: ReleaseState?,
        settings: ReleaseSettings,
        log: List<ReleaseLog>,
        sessions: List<SessionRecord>,
        failures: List<FailureRecord>,
        cagedSince: Long?,
        dayOf: (Long) -> Long,
    ): Set<DayMark> {
        val marks = mutableSetOf<DayMark>()
        if (release != null && day == release.nextDay && shownDay(release, settings, today) != null) marks += DayMark.RELEASE_DAY
        log.filter { it.day == day }.forEach {
            marks += when (it.kind) {
                ReleaseEntry.RELEASED, ReleaseEntry.CONFESSED -> DayMark.RELEASED
                ReleaseEntry.RUINED -> DayMark.RUINED
                ReleaseEntry.WEEK, ReleaseEntry.MISSED -> DayMark.DENIED
            }
        }
        sessions.filter { dayOf(it.at) == day }.forEach {
            when (it.outcome) {
                SessionOutcome.FINISHED, SessionOutcome.MISSED_COMMAND, SessionOutcome.RUIN_FAILED -> marks += DayMark.RELEASED
                SessionOutcome.RUINED -> marks += DayMark.RUINED
                SessionOutcome.DENIED -> marks += DayMark.DENIED
            }
            if (it.edges > 0) marks += DayMark.DENIED
        }
        if (failures.any { dayOf(it.at) == day }) marks += DayMark.FAILED
        if (cagedSince != null && day in dayOf(cagedSince)..today) marks += DayMark.CAGED
        return marks
    }
}
