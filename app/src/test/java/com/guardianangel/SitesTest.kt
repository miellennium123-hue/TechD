package com.guardianangel

import com.guardianangel.core.CheckInAction
import com.guardianangel.core.CheckInRolls
import com.guardianangel.core.Decision
import com.guardianangel.core.MINUTE
import com.guardianangel.core.Rules
import com.guardianangel.core.SiteMove
import com.guardianangel.data.GuardianConfig
import com.guardianangel.data.GuardianState
import com.guardianangel.data.LockoutScope
import com.guardianangel.data.LockoutSettings
import com.guardianangel.data.PhotoProofSettings
import com.guardianangel.data.QuietHoursSettings
import com.guardianangel.data.SiteVisit
import com.guardianangel.data.Sites
import com.guardianangel.data.Summons
import com.guardianangel.data.Questions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SitesTest {
    private val noon = 12 * 60
    private val t = 1_000_000_000L
    private val chrome = "com.android.chrome"
    private val sites = GuardianConfig(
        enabled = true,
        sitesOn = true,
        siteList = listOf("https://example.com"),
        tasksOn = true,
        showsUpOn = true,
        photoProof = PhotoProofSettings(on = true),
    )
    private val sure = CheckInRolls(task = 0.0, summons = 0.0, proof = 0.0, site = 0.0)

    private fun visit(
        warnedAt: Long = 0,
        openedAt: Long = 0,
        stayedMs: Long = 0,
        onSiteSince: Long = 0,
        asked: Boolean = false,
    ) = SiteVisit(
        id = t, url = "https://example.com", browser = chrome, createdAt = t,
        showBy = t + Rules.SITE_SHOW_WITHIN_MINUTES * MINUTE, stayMs = 5 * MINUTE, asked = asked,
        warnedAt = warnedAt, openedAt = openedAt, stayedMs = stayedMs, onSiteSince = onSiteSince,
    )

    @Test
    fun sitesComeFirstAlmostEveryTime() {
        assertEquals(CheckInAction.SITE, Rules.checkInAction(sites, GuardianState(), noon, true, sure, canOpenSites = true))
        assertEquals(CheckInAction.SITE, Rules.checkInAction(sites, GuardianState(), noon, true, sure.copy(site = 0.89), true))
        // The other 10%: the usual task, summons, photo order.
        assertEquals(CheckInAction.TASK, Rules.checkInAction(sites, GuardianState(), noon, true, sure.copy(site = 0.9), true))
    }

    @Test
    fun noSiteWithoutTheToggleAListOrTheService() {
        assertEquals(CheckInAction.TASK, Rules.checkInAction(sites.copy(sitesOn = false), GuardianState(), noon, true, sure, true))
        assertEquals(CheckInAction.TASK, Rules.checkInAction(sites.copy(siteList = emptyList()), GuardianState(), noon, true, sure, true))
        assertEquals(CheckInAction.TASK, Rules.checkInAction(sites, GuardianState(), noon, true, sure, canOpenSites = false))
    }

    @Test
    fun siteDoesntNeedNotificationsBecauseTheWarningShowsItself() {
        assertEquals(CheckInAction.SITE, Rules.checkInAction(sites, GuardianState(), noon, canNotify = false, rolls = sure, canOpenSites = true))
        assertEquals(CheckInAction.PLAIN, Rules.checkInAction(sites, GuardianState(), noon, false, sure.copy(site = 0.95), true))
    }

    @Test
    fun oneVisitAtATimeAndNotOverASummons() {
        val visiting = GuardianState(visit = visit())
        assertEquals(CheckInAction.TASK, Rules.checkInAction(sites, visiting, noon, true, sure, true))
        // No summons on top of a visit either.
        assertEquals(CheckInAction.PROOF, Rules.checkInAction(sites.copy(tasksOn = false), visiting, noon, true, sure, true))
        val summoned = GuardianState(summons = Summons(1, t, t + 10 * MINUTE, Questions.FALLBACK))
        assertEquals(CheckInAction.TASK, Rules.checkInAction(sites, summoned, noon, true, sure, true))
    }

    @Test
    fun neverInQuietHoursOrRunningIntoThem() {
        val quiet = sites.copy(quietHours = QuietHoursSettings(on = true, startMinute = 23 * 60, endMinute = 7 * 60))
        assertEquals(CheckInAction.QUIET, Rules.checkInAction(quiet, GuardianState(), 23 * 60 + 30, true, sure, true))
        // 5 minute stay plus the warning: 22:54 runs into 23:00, 22:50 doesn't.
        assertEquals(CheckInAction.PLAIN, Rules.checkInAction(quiet.copy(tasksOn = false), GuardianState(), 22 * 60 + 54, true, sure, true))
        assertEquals(CheckInAction.SITE, Rules.checkInAction(quiet, GuardianState(), 22 * 60 + 50, true, sure, true))
    }

    @Test
    fun waitingVisitIsShownOnlyOutsideQuietTimeUnlessAsked() {
        val quiet = sites.copy(quietHours = QuietHoursSettings(on = true, startMinute = 23 * 60, endMinute = 7 * 60))
        assertTrue(Rules.canShowVisit(quiet, visit(), noon))
        assertFalse(Rules.canShowVisit(quiet, visit(), 23 * 60 + 10))
        assertFalse(Rules.canShowVisit(quiet, visit(), 22 * 60 + 57))
        assertTrue(Rules.canShowVisit(quiet, visit(asked = true), 23 * 60 + 10))
    }

    @Test
    fun timeOnlyCountsOnTheSite() {
        val open = visit(warnedAt = t, openedAt = t + 10_000, stayedMs = 60_000, onSiteSince = t + 100_000)
        assertEquals(60_000 + 30_000L, Rules.stayedMs(open, t + 130_000))
        val paused = open.copy(onSiteSince = 0)
        assertEquals(60_000L, Rules.stayedMs(paused, t + 999_000))
        assertFalse(Rules.visitDone(open, t + 100_000 + 4 * MINUTE - 1))
        assertTrue(Rules.visitDone(open, t + 100_000 + 4 * MINUTE))
        // Not open yet: never done, however long the warning sits there.
        assertFalse(Rules.visitDone(visit(warnedAt = t, stayedMs = 10 * MINUTE), t + HOUR_MS))
    }

    @Test
    fun waitingVisitsExpire() {
        assertFalse(Rules.visitExpired(visit(), t + 29 * MINUTE))
        assertTrue(Rules.visitExpired(visit(), t + 31 * MINUTE))
        // Once shown, only something long abandoned expires.
        val open = visit(warnedAt = t, openedAt = t)
        assertFalse(Rules.visitExpired(open, t + 31 * MINUTE))
        assertTrue(Rules.visitExpired(open, t + 30 * MINUTE + 5 * MINUTE + HOUR_MS + 1))
    }

    @Test
    fun leavingTheBrowserIsLeavingButHerAppAndCallsOnlyPause() {
        val open = visit(warnedAt = t, openedAt = t, onSiteSince = t)
        val later = t + 10_000
        assertEquals(SiteMove.ON_SITE, Rules.siteMove(open, chrome, later, ownApp = false, launcher = false, exempt = false))
        assertEquals(SiteMove.LEFT, Rules.siteMove(open, "com.whatsapp", later, ownApp = false, launcher = false, exempt = false))
        assertEquals(SiteMove.LEFT, Rules.siteMove(open, "launcher", later, ownApp = false, launcher = true, exempt = true))
        assertEquals(SiteMove.PAUSE, Rules.siteMove(open, "com.guardianangel", later, ownApp = true, launcher = false, exempt = true))
        assertEquals(SiteMove.PAUSE, Rules.siteMove(open, "com.android.dialer", later, ownApp = false, launcher = false, exempt = true))
        // Right after the page opens, the hand-off from her warning can't count as leaving.
        assertEquals(SiteMove.PAUSE, Rules.siteMove(open, "com.whatsapp", t + 1_000, ownApp = false, launcher = false, exempt = false))
        // Before the page opens there's nothing to leave.
        assertEquals(SiteMove.NONE, Rules.siteMove(visit(warnedAt = t), "com.whatsapp", later, false, false, false))
        assertEquals(SiteMove.NONE, Rules.siteMove(null, "com.whatsapp", later, false, false, false))
    }

    @Test
    fun herBrowserIsNeverBlockedDuringAVisit() {
        val locked = sites.copy(lockouts = LockoutSettings(on = true, scope = LockoutScope.EVERYTHING))
        // Her timed block running (round 53).
        val open = GuardianState(visit = visit(warnedAt = t, openedAt = t), lockoutUntil = t + 60 * 60_000L)
        assertEquals(Decision.Allow, Rules.decide(chrome, locked, open, t, noon))
        assertTrue(Rules.decide("com.example.other", locked, open, t, noon) is Decision.Block)
        // Still in the warning: the usual rules apply.
        assertTrue(Rules.decide(chrome, locked, open.copy(visit = visit(warnedAt = t)), t, noon) is Decision.Block)
    }

    @Test
    fun sitesAreCleanedUp() {
        assertEquals("https://example.com", Sites.normalize("  example.com "))
        assertEquals("https://example.com/a?b=c", Sites.normalize("example.com/a?b=c"))
        assertEquals("http://example.com", Sites.normalize("http://example.com"))
        assertEquals("HTTPS://Example.com", Sites.normalize("HTTPS://Example.com"))
        assertEquals("https://example.com:8080/x", Sites.normalize("example.com:8080/x"))
        assertNull(Sites.normalize(""))
        assertNull(Sites.normalize("not a site"))
        assertNull(Sites.normalize("javascript:alert(1)"))
        assertNull(Sites.normalize("file:///sdcard/x.html"))
        assertNull(Sites.normalize("mailto:me@example.com"))
        assertNull(Sites.normalize("https://"))
        assertEquals("example.com", Sites.host("https://www.example.com/page?x=1"))
    }

    private companion object {
        const val HOUR_MS = 60 * MINUTE
    }
}
