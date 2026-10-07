package com.guardianangel.core

import com.guardianangel.data.AppLists
import com.guardianangel.data.AppTime
import com.guardianangel.data.DayReport
import com.guardianangel.data.Grade
import com.guardianangel.data.GuardianConfig
import com.guardianangel.data.ReportSettings
import com.guardianangel.data.UsageDay

/**
 * Daily report (round 68): she counts unlocks and time in each app, and at your report time she
 * grades the day against your goals. Pure Kotlin, tested in UsageTest. Only counts are kept, never
 * what was on screen.
 */
object Usage {
    private const val DAY_MS = 24 * 60 * 60_000L

    /** She keeps this many reports. */
    const val KEEP = 30

    /** How many apps a report names. */
    const val TOP = 5

    /** One stretch of time is never counted as more than this, in case the screen went off unnoticed. */
    const val MAX_STRETCH_MS = 15 * 60_000L

    /** Goal steppers in Settings. */
    const val MIN_UNLOCKS = 10
    const val MAX_UNLOCKS = 300
    const val UNLOCK_STEP = 10
    const val MIN_SCREEN_MINUTES = 30
    const val MAX_SCREEN_MINUTES = 12 * 60
    const val SCREEN_STEP = 15

    /** She counts while she's on and the daily report is on. */
    fun counting(config: GuardianConfig): Boolean = config.enabled && config.report.on

    /**
     * Her day number. Her day ends at your report time, so each report covers the day just finished.
     * [localMs] is the time in your time zone (epoch millis plus the zone's offset).
     */
    fun dayKey(localMs: Long, reportMinute: Int): Long = Math.floorDiv(localMs - reportMinute * 60_000L, DAY_MS)

    /** Time in an app counts, except her own screens, the home screen, the phone and system screens. */
    fun countsApp(pkg: String?, ownApp: Boolean, launcher: Boolean, protectedPackages: Set<String>): Boolean =
        pkg != null && !ownApp && !launcher && pkg !in AppLists.NEVER_BLOCK && pkg !in protectedPackages

    /**
     * At the start of a new day: the count to keep going with, and the finished day she reports on
     * (see [finished]). Moving the report time later can step the day back; the count just carries on.
     */
    fun rollover(usage: UsageDay, day: Long): Pair<UsageDay, UsageDay?> = when {
        usage.day == day -> usage to null
        usage.day > day -> usage.copy(day = day) to null
        else -> UsageDay(day = day) to finished(usage, day)
    }

    /** Today's count for [day]: a new day starts from zero. */
    fun forDay(usage: UsageDay, day: Long): UsageDay = if (usage.day == day) usage else UsageDay(day = day)

    fun addUnlock(usage: UsageDay, day: Long): UsageDay = forDay(usage, day).let { it.copy(unlocks = it.unlocks + 1) }

    fun addTime(usage: UsageDay, day: Long, pkg: String, ms: Long): UsageDay {
        val today = forDay(usage, day)
        val add = ms.coerceIn(0, MAX_STRETCH_MS)
        if (add == 0L) return today
        return today.copy(appMs = today.appMs + (pkg to (today.appMs[pkg] ?: 0L) + add))
    }

    /**
     * The day she should report on now, or null. Only the day just finished, and only if she counted
     * something in it. Older days (she was off, or the phone was) are dropped without a report.
     */
    fun finished(usage: UsageDay, day: Long): UsageDay? =
        usage.takeIf { it.day == day - 1 && (it.unlocks > 0 || it.appMs.isNotEmpty()) }

    /**
     * Her grade: how far over your goals you went, by whichever goal you did worse on.
     * Within both goals is at least a B; a quarter under both is an A.
     */
    fun grade(unlocks: Int, screenMs: Long, settings: ReportSettings): Grade {
        val unlockRatio = unlocks.toDouble() / settings.unlockGoal.coerceAtLeast(1)
        val screenRatio = screenMs.toDouble() / (settings.screenGoalMinutes.coerceAtLeast(1) * 60_000.0)
        val worst = maxOf(unlockRatio, screenRatio)
        return when {
            worst <= 0.75 -> Grade.A
            worst <= 1.0 -> Grade.B
            worst <= 1.25 -> Grade.C
            worst <= 1.5 -> Grade.D
            else -> Grade.F
        }
    }

    fun line(grade: Grade): Line = when (grade) {
        Grade.A -> Line.REPORT_A
        Grade.B -> Line.REPORT_B
        Grade.C -> Line.REPORT_C
        Grade.D -> Line.REPORT_D
        Grade.F -> Line.REPORT_F
    }

    /** Your most used apps, most first, by name ([label] turns a package into its name). */
    fun top(appMs: Map<String, Long>, label: (String) -> String): List<AppTime> =
        appMs.entries.sortedByDescending { it.value }.take(TOP).map { AppTime(label(it.key), it.value) }

    /** Builds her report on [day]. */
    fun report(day: UsageDay, settings: ReportSettings, at: Long, line: String, label: (String) -> String): DayReport =
        DayReport(
            at = at,
            unlocks = day.unlocks,
            screenMs = day.screenMs,
            top = top(day.appMs, label),
            grade = grade(day.unlocks, day.screenMs, settings),
            line = line,
            unlockGoal = settings.unlockGoal,
            screenGoalMinutes = settings.screenGoalMinutes,
        )

    /** Adds a report, newest last, keeping the newest [KEEP]. */
    fun add(reports: List<DayReport>, report: DayReport): List<DayReport> = (reports + report).takeLast(KEEP)
}
