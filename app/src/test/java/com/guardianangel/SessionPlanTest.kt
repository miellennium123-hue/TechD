package com.guardianangel

import com.guardianangel.data.SessionOutcome
import com.guardianangel.core.PaidOff
import com.guardianangel.core.LockGuard
import com.guardianangel.core.MINUTE
import com.guardianangel.core.Rules
import com.guardianangel.core.SessionPlan
import com.guardianangel.core.SessionReason
import com.guardianangel.data.CbtLevel
import com.guardianangel.data.GuardianConfig
import com.guardianangel.data.GuardianState
import com.guardianangel.data.Kink
import com.guardianangel.data.QuietHoursSettings
import com.guardianangel.data.SessionSettings
import com.guardianangel.data.SessionTheme
import com.guardianangel.data.Sessions
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Round 84: themes, training, her length, owed sessions and booked sessions. */
class SessionPlanTest {
    private val now = 1_000L * 24 * 60 * MINUTE
    private val mine = SessionSettings(on = true, kinks = setOf(Kink.EDGING, Kink.TEASING, Kink.PRAISE, Kink.SPEED))
    private val on = GuardianConfig(enabled = true, session = mine)

    @Test
    fun themesOnlyUseYourKinks() {
        SessionTheme.entries.forEach { theme ->
            val applied = SessionPlan.applyTheme(mine, theme)
            assertTrue("$theme", mine.kinks.containsAll(applied.kinks))
        }
        // CBT never sneaks in when you haven't switched it on.
        assertFalse(Kink.CBT in SessionPlan.applyTheme(mine, SessionTheme.PUNISHMENT).kinks)
        assertEquals(mine, SessionPlan.applyTheme(mine, SessionTheme.YOURS))
    }

    @Test
    fun themesSetEndingsAndLength() {
        val punish = SessionPlan.applyTheme(mine, SessionTheme.PUNISHMENT)
        assertEquals(0, punish.permissionWeight)
        assertEquals(0, punish.denialWeight)
        assertEquals(CbtLevel.HARD, punish.cbt)
        val reward = SessionPlan.applyTheme(mine, SessionTheme.REWARD)
        assertTrue(reward.permissionWeight > reward.ruinWeight + reward.denialWeight)
        assertEquals(30, SessionPlan.applyTheme(mine, SessionTheme.EDGE_MARATHON).minutes)
        assertEquals(0, SessionPlan.applyTheme(mine, SessionTheme.CBT_DISCIPLINE).permissionWeight)
    }

    @Test
    fun cbtDisciplineNeedsCbt() {
        assertFalse(SessionPlan.available(SessionTheme.CBT_DISCIPLINE, mine))
        assertTrue(SessionPlan.available(SessionTheme.CBT_DISCIPLINE, mine.copy(kinks = mine.kinks + Kink.CBT)))
        // Picked anyway: she falls back to your settings.
        assertEquals(SessionTheme.YOURS, SessionPlan.plan(on, GuardianState(), SessionTheme.CBT_DISCIPLINE, now, 0.0).theme)
    }

    @Test
    fun trainingWeeks() {
        assertEquals(0, SessionPlan.week(0, now))
        assertEquals(1, SessionPlan.week(now, now))
        assertEquals(2, SessionPlan.week(now - SessionPlan.WEEK_MS, now))
        assertEquals(SessionPlan.MAX_WEEK, SessionPlan.week(now - 50 * SessionPlan.WEEK_MS, now))
        val base = mine.copy(minutes = 10, permissionWeight = 20, denialWeight = 40)
        assertEquals(base, SessionPlan.applyTraining(base, 1))
        val week3 = SessionPlan.applyTraining(base, 3)
        assertEquals(16, week3.minutes)
        assertEquals(10, week3.permissionWeight)
        assertEquals(50, week3.denialWeight)
        // Never below zero, never past her longest.
        val late = SessionPlan.applyTraining(base.copy(minutes = 44, permissionWeight = 5), 8)
        assertEquals(0, late.permissionWeight)
        assertEquals(Sessions.HER_MAX_MINUTES, late.minutes)
    }

    @Test
    fun whatYouOweComesFirst() {
        val owing = on.copy(session = mine.copy(punishmentSessions = true, ruinAfterCatch = true))
        val ruin = SessionPlan.plan(owing, GuardianState(ruinOwedBy = now + 1, owedPunishment = true), SessionTheme.REWARD, now, 0.5)
        assertEquals(SessionReason.RUIN_OWED, ruin.reason)
        assertEquals(100, ruin.settings.ruinWeight)
        assertEquals(0, ruin.settings.permissionWeight + ruin.settings.denialWeight)
        val punish = SessionPlan.plan(owing, GuardianState(owedPunishment = true), SessionTheme.REWARD, now, 0.5)
        assertEquals(SessionReason.PUNISHMENT, punish.reason)
        assertEquals(SessionTheme.PUNISHMENT, punish.theme)
        assertEquals(0, punish.settings.permissionWeight)
        val chosen = SessionPlan.plan(owing, GuardianState(), SessionTheme.REWARD, now, 0.5)
        assertEquals(SessionReason.CHOSEN, chosen.reason)
        assertEquals(SessionTheme.REWARD, chosen.theme)
        // Switched off, nothing is owed. An overdue ruin isn't owed any more (it was a failure).
        val off = on.copy(session = mine.copy(punishmentSessions = false, ruinAfterCatch = false))
        assertEquals(SessionReason.CHOSEN, SessionPlan.plan(off, GuardianState(ruinOwedBy = now + 1, owedPunishment = true), SessionTheme.YOURS, now, 0.5).reason)
        assertEquals(SessionReason.CHOSEN, SessionPlan.plan(owing, GuardianState(ruinOwedBy = now - 1), SessionTheme.YOURS, now, 0.5).reason)
    }

    @Test
    fun herLengthIsHerSecret() {
        val hers = on.copy(session = mine.copy(herLength = true))
        val short = SessionPlan.plan(hers, GuardianState(), SessionTheme.YOURS, now, 0.0)
        val long = SessionPlan.plan(hers, GuardianState(), SessionTheme.YOURS, now, 0.9999)
        assertTrue(short.secretLength)
        assertEquals(Sessions.MIN_MINUTES, short.settings.minutes)
        assertEquals(Sessions.HER_MAX_MINUTES, long.settings.minutes)
        assertFalse(SessionPlan.plan(on, GuardianState(), SessionTheme.YOURS, now, 0.5).secretLength)
    }

    @Test
    fun bookingsKeepClearOfQuietTime() {
        val config = on.copy(quietHours = QuietHoursSettings(on = true, startMinute = 21 * 60, endMinute = 10 * 60))
        repeat(50) { i ->
            val roll = i / 50.0
            val at = SessionPlan.nextBooking(config, now, 12 * 60, roll, roll)
            assertNotNull(at)
            at!!
            val startOfToday = now - 12 * 60 * MINUTE
            val days = (at - startOfToday) / (24 * 60 * MINUTE)
            assertTrue(days in 1L..2L)
            val minute = ((at - startOfToday) / MINUTE % (24 * 60)).toInt()
            assertEquals(0, minute % 15)
            (minute - SessionPlan.BOOK_REMIND_MINUTES..minute + SessionPlan.BOOK_WINDOW_MINUTES).forEach {
                assertFalse(Rules.isQuiet(config, it))
            }
        }
        // Quiet all day: no time to book.
        val always = on.copy(quietHours = QuietHoursSettings(on = true, startMinute = 0, endMinute = 23 * 60 + 59))
        assertEquals(null, SessionPlan.nextBooking(always, now, 12 * 60, 0.5, 0.5))
    }

    @Test
    fun startingNearHerTimeKeepsIt() {
        val at = now
        assertTrue(SessionPlan.keepsBooking(at, at - 10 * MINUTE))
        assertTrue(SessionPlan.keepsBooking(at, at + 15 * MINUTE))
        assertFalse(SessionPlan.keepsBooking(at, at + 16 * MINUTE))
        assertFalse(SessionPlan.keepsBooking(at, at - 20 * MINUTE))
    }

    @Test
    fun lockGuardOnSwitchingHerPlansOff() {
        val strict = on.copy(lockGuard = true, session = mine.copy(training = true, booked = true))
        fun off(change: (SessionSettings) -> SessionSettings) = LockGuard.loosens(strict, strict.copy(session = change(strict.session)))
        assertTrue(off { it.copy(training = false) })
        assertTrue(off { it.copy(booked = false) })
        assertTrue(off { it.copy(punishmentSessions = false) })
        assertTrue(off { it.copy(ruinAfterCatch = false) })
        assertFalse(off { it.copy(herLength = true) })
        assertFalse(off { it.copy(theme = SessionTheme.REWARD) })
    }

    @Test
    fun olderSavesLoad() {
        val json = Json { ignoreUnknownKeys = true }
        val config = json.decodeFromString(GuardianConfig.serializer(), """{"session":{"on":true}}""")
        assertEquals(SessionTheme.YOURS, config.session.theme)
        assertTrue(config.session.punishmentSessions)
        assertFalse(config.session.booked)
        val state = json.decodeFromString(GuardianState.serializer(), """{"merit":3}""")
        assertEquals(0L, state.trainingStart)
        assertEquals(null, state.booked)
        assertFalse(state.owedPunishment)
    }

    @Test
    fun theOwedRuinPaysOffEverythingTheCatchCost() {
        // Round 104: a catch owes a ruin and (as a failure) a punishment session. The ruin pays both.
        assertEquals(PaidOff(punishment = true, ruin = true), SessionPlan.paysOff(SessionTheme.RUIN_TRAINING, SessionOutcome.RUINED, ruinOwed = true))
        // A punishment session pays the punishment; a ruin in it also pays an owed ruin.
        assertEquals(PaidOff(punishment = true, ruin = false), SessionPlan.paysOff(SessionTheme.PUNISHMENT, SessionOutcome.DENIED, ruinOwed = false))
        assertEquals(PaidOff(punishment = true, ruin = true), SessionPlan.paysOff(SessionTheme.PUNISHMENT, SessionOutcome.RUINED, ruinOwed = true))
        // An ordinary ruin with nothing owed pays nothing; a failed ruin never pays the ruin.
        assertEquals(PaidOff(punishment = false, ruin = false), SessionPlan.paysOff(SessionTheme.YOURS, SessionOutcome.RUINED, ruinOwed = false))
        assertEquals(PaidOff(punishment = false, ruin = false), SessionPlan.paysOff(SessionTheme.RUIN_TRAINING, SessionOutcome.RUIN_FAILED, ruinOwed = true))
    }

    @Test
    fun theOldBugsLeftoverPunishmentIsCleared() {
        // Round 104: owed after a catch whose ruin is done: cleared once on update.
        assertTrue(SessionPlan.leftoverFromCatch(owedPunishment = true, ruinOwedBy = 0, lastFailureKind = "CAUGHT_PORN"))
        // The ruin still owed, a later failure, or nothing owed: left alone.
        assertFalse(SessionPlan.leftoverFromCatch(owedPunishment = true, ruinOwedBy = 5_000, lastFailureKind = "CAUGHT_PORN"))
        assertFalse(SessionPlan.leftoverFromCatch(owedPunishment = true, ruinOwedBy = 0, lastFailureKind = "MISSED_TASK"))
        assertFalse(SessionPlan.leftoverFromCatch(owedPunishment = true, ruinOwedBy = 0, lastFailureKind = null))
        assertFalse(SessionPlan.leftoverFromCatch(owedPunishment = false, ruinOwedBy = 0, lastFailureKind = "CAUGHT_PORN"))
    }
}
