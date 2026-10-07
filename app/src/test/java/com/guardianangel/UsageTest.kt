package com.guardianangel

import com.guardianangel.core.LockGuard
import com.guardianangel.core.Usage
import com.guardianangel.core.Voice
import com.guardianangel.data.DayReport
import com.guardianangel.data.Grade
import com.guardianangel.data.GuardianConfig
import com.guardianangel.data.GuardianState
import com.guardianangel.data.Mood
import com.guardianangel.data.ReportSettings
import com.guardianangel.data.UsageDay
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Daily report (round 68): her day, what counts, her grade, and what she keeps. */
class UsageTest {
    private val minute = 60_000L
    private val day = 24 * 60 * minute
    private val settings = ReportSettings(on = true, reportMinute = 21 * 60 + 30, unlockGoal = 60, screenGoalMinutes = 180)

    @Test
    fun herDayEndsAtTheReportTime() {
        val reportAt = 21 * 60 + 30
        val today = 100L
        val justBefore = today * day + reportAt * minute - 1
        val atReport = today * day + reportAt * minute
        assertEquals(Usage.dayKey(justBefore, reportAt) + 1, Usage.dayKey(atReport, reportAt))
        // Midnight doesn't start a new day; the report time does.
        assertEquals(Usage.dayKey(today * day - 1, reportAt), Usage.dayKey(today * day + 1, reportAt))
    }

    @Test
    fun countsOnlyWhileSheAndTheReportAreOn() {
        assertTrue(Usage.counting(GuardianConfig(enabled = true, report = settings)))
        assertFalse(Usage.counting(GuardianConfig(enabled = false, report = settings)))
        assertFalse(Usage.counting(GuardianConfig(enabled = true, report = settings.copy(on = false))))
    }

    @Test
    fun countsOrdinaryAppsOnly() {
        assertTrue(Usage.countsApp("com.example.app", ownApp = false, launcher = false, protectedPackages = emptySet()))
        assertFalse(Usage.countsApp(null, ownApp = false, launcher = false, protectedPackages = emptySet()))
        assertFalse(Usage.countsApp("com.guardianangel", ownApp = true, launcher = false, protectedPackages = emptySet()))
        assertFalse(Usage.countsApp("com.launcher", ownApp = false, launcher = true, protectedPackages = emptySet()))
        assertFalse(Usage.countsApp("com.android.dialer", ownApp = false, launcher = false, protectedPackages = emptySet()))
        assertFalse(Usage.countsApp("com.keyboard", ownApp = false, launcher = false, protectedPackages = setOf("com.keyboard")))
    }

    @Test
    fun addsUnlocksAndTime() {
        var usage = UsageDay(day = 5)
        usage = Usage.addUnlock(usage, 5)
        usage = Usage.addUnlock(usage, 5)
        usage = Usage.addTime(usage, 5, "a", 2 * minute)
        usage = Usage.addTime(usage, 5, "a", 3 * minute)
        usage = Usage.addTime(usage, 5, "b", minute)
        assertEquals(2, usage.unlocks)
        assertEquals(5 * minute, usage.appMs["a"])
        assertEquals(6 * minute, usage.screenMs)
    }

    @Test
    fun aLongStretchIsCapped() {
        val usage = Usage.addTime(UsageDay(day = 5), 5, "a", 3 * 60 * minute)
        assertEquals(Usage.MAX_STRETCH_MS, usage.screenMs)
        assertEquals(0L, Usage.addTime(UsageDay(day = 5), 5, "a", -minute).screenMs)
    }

    @Test
    fun aNewDayStartsFromZero() {
        val yesterday = UsageDay(day = 4, unlocks = 9, appMs = mapOf("a" to minute))
        val today = Usage.addUnlock(yesterday, 5)
        assertEquals(5L, today.day)
        assertEquals(1, today.unlocks)
        assertTrue(today.appMs.isEmpty())
    }

    @Test
    fun reportsOnlyOnTheDayJustFinished() {
        val yesterday = UsageDay(day = 4, unlocks = 9, appMs = mapOf("a" to minute))
        val (next, done) = Usage.rollover(yesterday, 5)
        assertEquals(UsageDay(day = 5), next)
        assertEquals(yesterday, done)
        // An older day (she or the phone was off) is dropped without a report.
        assertNull(Usage.rollover(yesterday, 6).second)
        // Nothing counted, nothing to report.
        assertNull(Usage.rollover(UsageDay(day = 4), 5).second)
        // Same day: nothing happens.
        assertEquals(yesterday to null, Usage.rollover(yesterday, 4))
    }

    @Test
    fun movingTheReportTimeLaterKeepsTheCount() {
        val today = UsageDay(day = 5, unlocks = 3)
        val (next, done) = Usage.rollover(today, 4)
        assertEquals(today.copy(day = 4), next)
        assertNull(done)
    }

    @Test
    fun gradesByTheWorseGoal() {
        val hour = 60 * minute
        assertEquals(Grade.A, Usage.grade(30, hour, settings))
        assertEquals(Grade.B, Usage.grade(60, 3 * hour, settings))
        assertEquals(Grade.C, Usage.grade(70, hour, settings))
        assertEquals(Grade.D, Usage.grade(10, 4 * hour + 30 * minute, settings))
        assertEquals(Grade.F, Usage.grade(100, hour, settings))
        assertEquals(Grade.F, Usage.grade(0, 5 * hour, settings))
    }

    @Test
    fun meritForGoodGradesACostForBadOnes() {
        assertTrue(Grade.A.merit > Grade.B.merit && Grade.B.merit > 0)
        assertEquals(0, Grade.C.merit)
        assertTrue(Grade.F.merit < Grade.D.merit && Grade.D.merit < 0)
    }

    @Test
    fun reportNamesTheTopAppsMostFirst() {
        val apps = (1..7).associate { "pkg$it" to it * minute }
        val usage = UsageDay(day = 4, unlocks = 12, appMs = apps)
        val report = Usage.report(usage, settings, at = 1L, line = "Hi") { it.uppercase() }
        assertEquals(Usage.TOP, report.top.size)
        assertEquals("PKG7", report.top.first().app)
        assertEquals(7 * minute, report.top.first().ms)
        assertEquals(12, report.unlocks)
        assertEquals(usage.screenMs, report.screenMs)
        assertEquals(60, report.unlockGoal)
    }

    @Test
    fun keepsTheNewestReports() {
        val one = Usage.report(UsageDay(day = 1, unlocks = 1), settings, at = 0L, line = "") { it }
        var reports = emptyList<DayReport>()
        repeat(Usage.KEEP + 5) { i -> reports = Usage.add(reports, one.copy(at = i.toLong())) }
        assertEquals(Usage.KEEP, reports.size)
        assertEquals((Usage.KEEP + 4).toLong(), reports.last().at)
    }

    @Test
    fun everyGradeHasLinesInBothMoods() {
        Grade.entries.forEach { grade ->
            val line = Usage.line(grade)
            assertTrue(Voice.hasLines(line, Mood.SWEET))
            assertTrue(Voice.hasLines(line, Mood.STRICT))
        }
    }

    @Test
    fun lockGuardSlowsLooserReports() {
        val strict = GuardianConfig(enabled = true, report = settings.copy(failOnF = true))
        assertTrue(LockGuard.loosens(strict, strict.copy(report = strict.report.copy(on = false))))
        assertTrue(LockGuard.loosens(strict, strict.copy(report = strict.report.copy(failOnF = false))))
        assertTrue(LockGuard.loosens(strict, strict.copy(report = strict.report.copy(unlockGoal = 70))))
        assertTrue(LockGuard.loosens(strict, strict.copy(report = strict.report.copy(screenGoalMinutes = 195))))
        // Stricter goals and a new report time are instant.
        assertFalse(LockGuard.loosens(strict, strict.copy(report = strict.report.copy(unlockGoal = 50))))
        assertFalse(LockGuard.loosens(strict, strict.copy(report = strict.report.copy(screenGoalMinutes = 165))))
        assertFalse(LockGuard.loosens(strict, strict.copy(report = strict.report.copy(reportMinute = 22 * 60))))
    }

    @Test
    fun olderSavesLoadWithTheReportOff() {
        val json = Json { ignoreUnknownKeys = true }
        val config = json.decodeFromString(GuardianConfig.serializer(), """{"enabled":true}""")
        assertEquals(ReportSettings(), config.report)
        assertFalse(config.report.on)
        val state = json.decodeFromString(GuardianState.serializer(), """{"merit":3}""")
        assertEquals(UsageDay(), state.usage)
        assertTrue(state.reports.isEmpty())
    }
}
