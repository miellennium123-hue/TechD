package com.guardianangel

import com.guardianangel.core.ClipInfo
import com.guardianangel.core.ClipKind
import com.guardianangel.core.DayMark
import com.guardianangel.core.LockGuard
import com.guardianangel.core.MINUTE
import com.guardianangel.core.Release
import com.guardianangel.core.SessionPlan
import com.guardianangel.data.FailureRecord
import com.guardianangel.data.GuardianConfig
import com.guardianangel.data.GuardianState
import com.guardianangel.data.ReleaseEntry
import com.guardianangel.data.ReleaseLog
import com.guardianangel.data.ReleaseSettings
import com.guardianangel.data.ReleaseState
import com.guardianangel.data.SessionEnding
import com.guardianangel.data.SessionOutcome
import com.guardianangel.data.SessionRecord
import com.guardianangel.data.SessionSettings
import com.guardianangel.data.SessionTheme
import com.guardianangel.data.WheelResult
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Her release calendar (round 104). */
class ReleaseTest {
    private val s = ReleaseSettings(on = true, minDays = 3, maxDays = 14)
    private val today = 20_000L

    @Test
    fun sheRandomlyPicksADayInYourRange() {
        assertEquals(today + 3, Release.pickDay(today, s, 0.0))
        assertEquals(today + 14, Release.pickDay(today, s, 0.9999))
        (0 until 100).forEach { assertTrue(Release.pickDay(today, s, it / 100.0) in today + 3..today + 14) }
        // A broken range still lands inside 1 to 30 days.
        assertEquals(today + 1, Release.pickDay(today, ReleaseSettings(minDays = 0, maxDays = 0), 0.5))
        assertEquals(today + 30, Release.pickDay(today, ReleaseSettings(minDays = 40, maxDays = 50), 0.5))
        val started = Release.start(today, s, 0.0)
        assertEquals(today, started.startDay)
        assertEquals(today + 3, started.nextDay)
    }

    @Test
    fun releaseDayCarriesOverOneDayThenItsGone() {
        val r = ReleaseState(nextDay = today, startDay = today - 10)
        assertFalse(Release.isReleaseDay(r, today - 1))
        assertTrue(Release.isReleaseDay(r, today))
        assertTrue(Release.isReleaseDay(r, today + 1))
        assertTrue(Release.lastChance(r, today + 1))
        assertFalse(Release.isReleaseDay(r, today + 2))
        assertNull(Release.rollOver(r, today + 1, s, 0.0, 1L))
        val (next, log) = Release.rollOver(r.copy(spin = WheelResult.RUIN), today + 2, s, 0.0, 1L)!!
        assertEquals(ReleaseEntry.MISSED, log.kind)
        assertEquals(today, log.day)
        assertEquals(today + 2 + 3, next.nextDay)
        assertNull(next.spin)
        // Missing it isn't a release: your streak goes on.
        assertEquals(r.lastReleaseDay, next.lastReleaseDay)
    }

    @Test
    fun herWheel() {
        assertEquals(60, Release.permissionChance(s))
        assertEquals(WheelResult.RUIN, Release.spin(s, 0.0))
        assertEquals(WheelResult.RUIN, Release.spin(s, 0.249))
        assertEquals(WheelResult.WEEK, Release.spin(s, 0.25))
        assertEquals(WheelResult.WEEK, Release.spin(s, 0.399))
        assertEquals(WheelResult.PERMISSION, Release.spin(s, 0.4))
        assertEquals(WheelResult.PERMISSION, Release.spin(s, 0.999))
        // Chances that add up past 100 never go negative.
        val greedy = ReleaseSettings(ruinChance = 80, weekChance = 80)
        assertEquals(0, Release.permissionChance(greedy))
        assertEquals(WheelResult.WEEK, Release.spin(greedy, 0.99))
    }

    @Test
    fun usingReleaseDay() {
        val r = ReleaseState(nextDay = today, startDay = today - 20, lastReleaseDay = today - 12, bestStreak = 5, spin = WheelResult.PERMISSION, reelWatched = true)
        val released = Release.used(r, today, ReleaseEntry.RELEASED, s, 0.0)
        assertEquals(today, released.lastReleaseDay)
        assertEquals(12, released.bestStreak)
        assertEquals(today + 3, released.nextDay)
        assertNull(released.spin)
        assertFalse(released.reelWatched)
        // A ruin isn't a release: the streak goes on.
        val ruined = Release.used(r, today, ReleaseEntry.RUINED, s, 0.0)
        assertEquals(today - 12, ruined.lastReleaseDay)
        assertEquals(5, ruined.bestStreak)
        // Another week: 7 days from today, whatever the range.
        assertEquals(today + 7, Release.used(r, today, ReleaseEntry.WEEK, s, 0.0).nextDay)
        assertEquals(ReleaseEntry.RUINED, Release.entryFor(SessionOutcome.RUINED))
        assertEquals(ReleaseEntry.RELEASED, Release.entryFor(SessionOutcome.FINISHED))
        assertEquals(ReleaseEntry.RELEASED, Release.entryFor(SessionOutcome.MISSED_COMMAND))
        assertEquals(ReleaseEntry.RELEASED, Release.entryFor(SessionOutcome.RUIN_FAILED))
    }

    @Test
    fun streaksAndConfessing() {
        val r = ReleaseState(nextDay = today + 5, startDay = today - 9)
        assertEquals(9, Release.deniedDays(r, today))
        val confessed = Release.confess(r, today)
        assertEquals(0, Release.deniedDays(confessed, today))
        assertEquals(9, confessed.bestStreak)
        // Confessing never moves her day.
        assertEquals(r.nextDay, confessed.nextDay)
        assertEquals(9, Release.bestStreak(confessed, today + 2))
        assertEquals(12, Release.bestStreak(confessed, today + 12))
    }

    @Test
    fun aHiddenDateShowsOnlyOnTheDay() {
        val r = ReleaseState(nextDay = today + 4, startDay = today)
        val hidden = s.copy(showDate = false)
        assertEquals(today + 4, Release.shownDay(r, s, today))
        assertNull(Release.shownDay(r, hidden, today))
        assertEquals(today + 4, Release.shownDay(r, hidden, today + 4))
        assertEquals(4L, Release.daysLeft(r, today))
        assertEquals(0L, Release.daysLeft(r, today + 6))
    }

    @Test
    fun herMorningNoticeOncePerReleaseDay() {
        val r = ReleaseState(nextDay = today, startDay = today - 5)
        assertTrue(Release.notifyDue(r, today, quiet = false))
        assertFalse(Release.notifyDue(r, today, quiet = true))
        assertFalse(Release.notifyDue(r.copy(notifiedDay = today), today, quiet = false))
        // The carried-over day gets its own reminder.
        assertTrue(Release.notifyDue(r.copy(notifiedDay = today), today + 1, quiet = false))
        assertFalse(Release.notifyDue(r, today - 1, quiet = false))
    }

    @Test
    fun countdownOnHerWallpaper() {
        val r = ReleaseState(nextDay = today + 6, startDay = today)
        val on = GuardianConfig(enabled = true, release = s)
        assertEquals(6L, Release.wallpaperDays(on, r, today))
        assertNull(Release.wallpaperDays(on.copy(release = s.copy(showDate = false)), r, today))
        assertNull(Release.wallpaperDays(on.copy(release = s.copy(countdownWallpaper = false)), r, today))
        assertNull(Release.wallpaperDays(on.copy(release = s.copy(on = false)), r, today))
        assertNull(Release.wallpaperDays(on.copy(enabled = false), r, today))
        assertNull(Release.wallpaperDays(on, null, today))
    }

    @Test
    fun noPermissionEndingsOutsideReleaseDay() {
        val all = SessionSettings(permissionWeight = 50, ruinWeight = 25, denialWeight = 25)
        val off = GuardianConfig(enabled = true, session = all)
        val on = off.copy(release = s)
        assertTrue(Release.permissionAllowed(off))
        assertFalse(Release.permissionAllowed(on))
        assertEquals(50, SessionPlan.plan(off, GuardianState(), SessionTheme.YOURS, 0, 0.5).settings.permissionWeight)
        assertEquals(0, SessionPlan.plan(on, GuardianState(), SessionTheme.YOURS, 0, 0.5).settings.permissionWeight)
        assertEquals(0, SessionPlan.plan(on, GuardianState(), SessionTheme.REWARD, 0, 0.5).settings.permissionWeight)
    }

    @Test
    fun herReelIsThisWeeksRuins() {
        val now = 100L * 24 * 60 * MINUTE
        val day = 24 * 60 * MINUTE
        val clips = listOf(
            ClipInfo("a", now - 2 * day, ClipKind.RUIN),
            ClipInfo("b", now - 8 * day, ClipKind.RUIN),
            ClipInfo("c", now - 1 * day, ClipKind.EDGE),
            ClipInfo("d", now - 6 * day, ClipKind.RUIN),
        )
        assertEquals(listOf("d", "a"), Release.weekReel(clips, now).map { it.name })
    }

    @Test
    fun lockGuardOnHerCalendar() {
        val strict = GuardianConfig(enabled = true, lockGuard = true, release = s.copy(showDate = false))
        fun loosens(change: ReleaseSettings) = LockGuard.loosens(strict, strict.copy(release = change))
        val r = strict.release
        assertTrue(loosens(r.copy(on = false)))
        assertTrue(loosens(r.copy(showDate = true)))
        assertTrue(loosens(r.copy(minDays = 2)))
        assertTrue(loosens(r.copy(maxDays = 13)))
        assertTrue(loosens(r.copy(ruinChance = 20)))
        assertTrue(loosens(r.copy(reel = false)))
        // Stricter, or her wallpaper countdown: instant.
        assertFalse(loosens(r.copy(maxDays = 20)))
        assertFalse(loosens(r.copy(ruinChance = 30)))
        assertFalse(loosens(r.copy(countdownWallpaper = false)))
        assertFalse(LockGuard.loosens(strict.copy(release = ReleaseSettings()), strict))
    }

    @Test
    fun whatADayShows() {
        val day = 24 * 60 * MINUTE
        fun dayOf(t: Long) = t / day
        val r = ReleaseState(nextDay = today + 3, startDay = today - 10)
        val log = listOf(ReleaseLog(today - 2, 0, ReleaseEntry.RELEASED), ReleaseLog(today - 1, 0, ReleaseEntry.MISSED))
        val sessions = listOf(
            SessionRecord(at = (today - 3) * day + 5, minutes = 10, ending = SessionEnding.RUINED, caged = false, skipped = 0, outcome = SessionOutcome.RUINED),
            SessionRecord(at = (today - 4) * day + 5, minutes = 10, ending = SessionEnding.DENIED, caged = false, skipped = 0, outcome = SessionOutcome.DENIED, edges = 3),
        )
        val failures = listOf(FailureRecord(at = (today - 4) * day + 9, kind = "MISSED_TASK", label = "", merit = 8))
        fun marks(d: Long, settings: ReleaseSettings = s, caged: Long? = null) =
            Release.marks(d, today, r, settings, log, sessions, failures, caged, ::dayOf)
        assertEquals(setOf(DayMark.RELEASE_DAY), marks(today + 3))
        assertEquals(emptySet<DayMark>(), marks(today + 3, s.copy(showDate = false)))
        assertEquals(setOf(DayMark.RELEASED), marks(today - 2))
        assertEquals(setOf(DayMark.DENIED), marks(today - 1))
        assertEquals(setOf(DayMark.RUINED), marks(today - 3))
        assertEquals(setOf(DayMark.DENIED, DayMark.FAILED), marks(today - 4))
        assertEquals(setOf(DayMark.CAGED), marks(today, caged = (today - 1) * day))
        assertEquals(setOf(DayMark.CAGED, DayMark.DENIED), marks(today - 1, caged = (today - 1) * day))
    }

    @Test
    fun olderSavesLoad() {
        val json = Json { ignoreUnknownKeys = true }
        val config = json.decodeFromString(GuardianConfig.serializer(), "{}")
        assertFalse(config.release.on)
        assertEquals(3, config.release.minDays)
        assertEquals(14, config.release.maxDays)
        val state = json.decodeFromString(GuardianState.serializer(), "{}")
        assertNull(state.release)
        assertTrue(state.releaseLog.isEmpty())
    }
}
