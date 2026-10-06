package com.guardianangel

import com.guardianangel.core.AskOutcome
import com.guardianangel.core.BegOutcome
import com.guardianangel.core.CheckInAction
import com.guardianangel.core.CheckInRolls
import com.guardianangel.core.Decision
import com.guardianangel.core.HOUR
import com.guardianangel.core.Line
import com.guardianangel.core.RestrictionKind
import com.guardianangel.core.Rules
import com.guardianangel.core.Voice
import com.guardianangel.data.ActiveTask
import com.guardianangel.data.BedtimeSettings
import com.guardianangel.data.ChastityLock
import com.guardianangel.data.PhotoProofSettings
import com.guardianangel.data.ProofReason
import com.guardianangel.data.ProofRequest
import com.guardianangel.data.Question
import com.guardianangel.data.QuestionKind
import com.guardianangel.data.Questions
import com.guardianangel.data.QuietHoursSettings
import com.guardianangel.data.RuleEnforcement
import com.guardianangel.data.Summons
import com.guardianangel.data.TaskKind
import com.guardianangel.data.TaskTemplate
import com.guardianangel.data.Grant
import com.guardianangel.data.GuardianConfig
import com.guardianangel.data.GuardianState
import com.guardianangel.data.LockoutScope
import com.guardianangel.data.LockoutSettings
import com.guardianangel.data.AskPermissionSettings
import com.guardianangel.data.Mood
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class RulesTest {
    private val now = 1_000_000_000L
    private val noon = 12 * 60
    private val instagram = "com.instagram.android"
    private val bank = "com.example.bank"

    private fun config(scope: LockoutScope = LockoutScope.SOCIAL_MEDIA) =
        GuardianConfig(enabled = true, lockouts = LockoutSettings(on = true, scope = scope))

    /** Her timed block running for another hour (round 53). */
    private val blocked = GuardianState(lockoutUntil = now + HOUR)

    @Test
    fun disabledNeverBlocks() {
        val c = config().copy(enabled = false)
        assertEquals(Decision.Allow, Rules.decide(instagram, c, blocked, now, noon))
    }

    @Test
    fun socialScopeBlocksOnlySocialApps() {
        val c = config()
        assertTrue(Rules.decide(instagram, c, blocked, now, noon) is Decision.Block)
        assertEquals(Decision.Allow, Rules.decide(bank, c, blocked, now, noon))
    }

    @Test
    fun everythingScopeRespectsAllowedAndProtectedLists() {
        val c = config(LockoutScope.EVERYTHING).copy(alwaysAllowed = setOf(bank))
        assertTrue(Rules.decide("com.example.game", c, blocked, now, noon) is Decision.Block)
        assertEquals(Decision.Allow, Rules.decide(bank, c, blocked, now, noon))
        assertEquals(Decision.Allow, Rules.decide("com.android.settings", c, blocked, now, noon))
        assertEquals(Decision.Allow, Rules.decide("com.android.phone", c, blocked, now, noon))
        assertEquals(Decision.Allow, Rules.decide("com.launcher", c, blocked, now, noon, setOf("com.launcher")))
    }

    @Test
    fun lockoutsOnlyBlockDuringHerTimedBlock() {
        val d = Rules.decide(instagram, config(), blocked, now, noon) as Decision.Block
        assertEquals(RestrictionKind.LOCKOUT, d.kind)
        assertEquals(now + HOUR, d.until)
        assertTrue(d.canBuy)
        assertFalse(d.askAllowed)
        // No block running, or it ended: free.
        assertEquals(Decision.Allow, Rules.decide(instagram, config(), GuardianState(), now, noon))
        assertEquals(Decision.Allow, Rules.decide(instagram, config(), blocked, now + HOUR, noon))
        // Lockouts switched off: an old block doesn't count.
        assertEquals(Decision.Allow, Rules.decide(instagram, GuardianConfig(enabled = true), blocked, now, noon))
    }

    @Test
    fun noAskingDuringABlock() {
        val c = config().copy(askPermission = AskPermissionSettings(on = true))
        val d = Rules.decide(instagram, c, blocked, now, noon) as Decision.Block
        assertEquals(RestrictionKind.LOCKOUT, d.kind)
        assertFalse(d.askAllowed)
        // Outside a block, Ask permission works as before.
        val outside = Rules.decide(instagram, c, GuardianState(), now, noon) as Decision.Block
        assertEquals(RestrictionKind.PERMISSION, outside.kind)
        assertTrue(outside.askAllowed)
    }

    @Test
    fun onlyBoughtTimeGetsThroughABlock() {
        val asked = blocked.copy(grants = listOf(Grant(instagram, now + HOUR)))
        assertTrue(Rules.decide(instagram, config(), asked, now, noon) is Decision.Block)
        val bought = blocked.copy(grants = listOf(Grant(instagram, now + HOUR, bought = true)))
        assertEquals(Decision.Allow, Rules.decide(instagram, config(), bought, now, noon))
        val expired = blocked.copy(grants = listOf(Grant(instagram, now - 1, bought = true)))
        assertTrue(Rules.decide(instagram, config(), expired, now, noon) is Decision.Block)
    }

    @Test
    fun grantLetsYouInOutsideABlock() {
        val c = GuardianConfig(enabled = true, askPermission = AskPermissionSettings(on = true))
        val granted = GuardianState(grants = listOf(Grant(instagram, now + HOUR)))
        assertEquals(Decision.Allow, Rules.decide(instagram, c, granted, now, noon))
        val expired = GuardianState(grants = listOf(Grant(instagram, now - 1)))
        assertTrue(Rules.decide(instagram, c, expired, now, noon) is Decision.Block)
    }

    @Test
    fun buyingTimeNeedsMerit() {
        assertTrue(Rules.canBuyTime(config(), GuardianState(merit = Rules.BUY_MERIT)))
        assertFalse(Rules.canBuyTime(config(), GuardianState(merit = Rules.BUY_MERIT - 1)))
        assertFalse(Rules.canBuyTime(config().copy(meritOn = false), GuardianState(merit = 100)))
    }

    @Test
    fun checkInsStartBlocks() {
        val c = config()
        assertTrue(Rules.startsBlock(c, GuardianState(), now, noon, 0.1))
        assertFalse(Rules.startsBlock(c, GuardianState(), now, noon, 0.9))
        // Not while one runs, not with lockouts off, not in quiet time.
        assertFalse(Rules.startsBlock(c, blocked, now, noon, 0.1))
        assertFalse(Rules.startsBlock(GuardianConfig(enabled = true), GuardianState(), now, noon, 0.1))
        assertFalse(Rules.startsBlock(c, GuardianState(), now, 0, 0.1))
        // A finished block lets her start the next one.
        assertTrue(Rules.startsBlock(c, blocked, now + HOUR, noon, 0.1))
    }

    @Test
    fun blockLengthStaysBetweenYourShortestAndLongest() {
        val c = config().copy(lockouts = LockoutSettings(on = true, minBlockMinutes = 60, maxBlockMinutes = 240))
        val random = Random(4)
        repeat(200) {
            val m = Rules.blockMinutes(c, random)
            assertTrue("$m", m in 60..240)
            assertEquals(0, m % 15)
        }
    }

    @Test
    fun punishmentOverridesGrantsAndAsking() {
        val c = config().copy(askPermission = AskPermissionSettings(on = true))
        val st = GuardianState(punishmentUntil = now + HOUR, grants = listOf(Grant(instagram, now + HOUR)))
        val d = Rules.decide(instagram, c, st, now, noon) as Decision.Block
        assertEquals(RestrictionKind.PUNISHMENT, d.kind)
        assertFalse(d.askAllowed)
    }

    @Test
    fun askPermissionAloneGuardsScope() {
        val c = GuardianConfig(enabled = true, askPermission = AskPermissionSettings(on = true))
        val d = Rules.decide(instagram, c, GuardianState(), now, noon) as Decision.Block
        assertEquals(RestrictionKind.PERMISSION, d.kind)
        assertTrue(d.askAllowed)
        assertFalse(d.canBuy)
    }

    @Test
    fun bedtimeWindowWrapsMidnight() {
        assertTrue(Rules.isInWindow(23 * 60, 7 * 60, 23 * 60 + 30))
        assertTrue(Rules.isInWindow(23 * 60, 7 * 60, 3 * 60))
        assertFalse(Rules.isInWindow(23 * 60, 7 * 60, 7 * 60))
        assertFalse(Rules.isInWindow(23 * 60, 7 * 60, noon))
        assertTrue(Rules.isInWindow(13 * 60, 15 * 60, 14 * 60))
        assertFalse(Rules.isInWindow(9 * 60, 9 * 60, 9 * 60))
    }

    @Test
    fun bedtimeBlocksEverythingNotAllowed() {
        val c = GuardianConfig(enabled = true, bedtime = BedtimeSettings(on = true))
        val d = Rules.decide("com.example.game", c, GuardianState(), now, 0) as Decision.Block
        assertEquals(RestrictionKind.BEDTIME, d.kind)
        assertFalse(d.canBuy)
        assertFalse(d.askAllowed)
        assertEquals(Decision.Allow, Rules.decide("com.example.game", c, GuardianState(), now, noon))
    }

    @Test
    fun bedtimeScreenCoversHomeAndBlockedApps() {
        val c = GuardianConfig(enabled = true, bedtime = BedtimeSettings(on = true), alwaysAllowed = setOf(bank))
        val game = Rules.decide("com.example.game", c, GuardianState(), now, 0)
        val allowed = Rules.decide(bank, c, GuardianState(), now, 0)
        // Home screen and a blocked app: covered. An Always-allowed app: not.
        assertTrue(Rules.bedtimeScreen(c, 0, Decision.Allow, launcher = true))
        assertTrue(Rules.bedtimeScreen(c, 0, game, launcher = false))
        assertFalse(Rules.bedtimeScreen(c, 0, allowed, launcher = false))
        // Outside bedtime, with the screen off, or with her off: never.
        assertFalse(Rules.bedtimeScreen(c, noon, Decision.Allow, launcher = true))
        assertFalse(Rules.bedtimeScreen(c.copy(bedtime = c.bedtime.copy(screen = false)), 0, game, launcher = true))
        assertFalse(Rules.bedtimeScreen(c.copy(enabled = false), 0, Decision.Allow, launcher = true))
        // A summons isn't bedtime's block: her Shows up screen handles it.
        val summonsBlock = Decision.Block(RestrictionKind.SUMMONS, askAllowed = false)
        assertFalse(Rules.bedtimeScreen(c, 0, summonsBlock, launcher = false))
    }

    @Test
    fun bedtimeHasNoWayInUntilMorning() {
        val c = GuardianConfig(enabled = true, bedtime = BedtimeSettings(on = true), askPermission = AskPermissionSettings(on = true))
        val st = GuardianState(grants = listOf(Grant("com.example.game", now + HOUR, bought = true)))
        val d = Rules.decide("com.example.game", c, st, now, 0) as Decision.Block
        assertEquals(RestrictionKind.BEDTIME, d.kind)
        assertFalse(d.askAllowed)
        assertFalse(d.canBuy)
    }

    @Test
    fun bedtimeWinsOverLockoutOnTheBlockScreen() {
        val c = config(LockoutScope.EVERYTHING).copy(bedtime = BedtimeSettings(on = true))
        val d = Rules.decide("com.example.game", c, blocked, now, 0) as Decision.Block
        assertEquals(RestrictionKind.BEDTIME, d.kind)
    }

    @Test
    fun enforcedRuleBlocksWithNoWayInAndIgnoresGrants() {
        val rule = ActiveTask(
            id = 1, text = "No social media", kind = TaskKind.RULE, issuedAt = now, dueAt = now + HOUR,
            ruleUntil = now + HOUR, enforce = RuleEnforcement.SOCIAL_MEDIA,
        )
        val c = GuardianConfig(enabled = true)
        val st = GuardianState(task = rule, grants = listOf(Grant(instagram, now + HOUR)))
        val d = Rules.decide(instagram, c, st, now, noon) as Decision.Block
        assertEquals(RestrictionKind.RULE, d.kind)
        assertFalse(d.canBuy)
        assertFalse(d.askAllowed)
        assertEquals(now + HOUR, d.until)
        // Other apps, and the time after the rule, are free.
        assertEquals(Decision.Allow, Rules.decide(bank, c, st, now, noon))
        assertEquals(Decision.Allow, Rules.decide(instagram, c, st, now + HOUR, noon))
        // Honor rules don't lock anything.
        val honor = st.copy(task = rule.copy(enforce = RuleEnforcement.NONE))
        assertEquals(Decision.Allow, Rules.decide(instagram, c, honor, now, noon))
    }

    @Test
    fun ignoredSummonsLocksEverythingButExemptApps() {
        val summons = Summons(id = 1, createdAt = now, lockAt = now + 10 * 60_000L, question = Questions.FALLBACK)
        val c = GuardianConfig(enabled = true, alwaysAllowed = setOf(bank))
        val st = GuardianState(summons = summons, grants = listOf(Grant("com.example.game", now + 2 * HOUR)))
        // Not locked during the first 10 minutes.
        assertEquals(Decision.Allow, Rules.decide("com.example.game", c, st, now, noon))
        val later = now + 11 * 60_000L
        val d = Rules.decide("com.example.game", c, st, later, noon) as Decision.Block
        assertEquals(RestrictionKind.SUMMONS, d.kind)
        assertFalse(d.canBuy)
        assertEquals(Decision.Allow, Rules.decide(bank, c, st, later, noon))
        assertEquals(Decision.Allow, Rules.decide("com.android.phone", c, st, later, noon))
    }

    @Test
    fun answers() {
        val choice = Question("Who?", QuestionKind.CHOICE, "My angel", listOf("Myself"))
        assertTrue(Rules.isCorrect(choice, "My angel"))
        assertFalse(Rules.isCorrect(choice, "Myself"))
        val phrase = Question("Say it", QuestionKind.PHRASE, "I'm your good pet.")
        assertTrue(Rules.isCorrect(phrase, "  i’m your  good pet. "))
        assertFalse(Rules.isCorrect(phrase, "I'm your good pet"))
        assertFalse(Rules.isCorrect(phrase, "I'm your pet."))
    }

    private val sure = CheckInRolls(task = 0.0, summons = 0.0, proof = 0.0)
    private val never = CheckInRolls(task = 0.99, summons = 0.99, proof = 0.99)
    private val busy = GuardianConfig(
        enabled = true,
        tasksOn = true,
        showsUpOn = true,
        photoProof = PhotoProofSettings(on = true),
    )

    @Test
    fun checkInPicksTaskThenSummonsThenProof() {
        assertEquals(CheckInAction.TASK, Rules.checkInAction(busy, GuardianState(), noon, true, sure))
        assertEquals(CheckInAction.SUMMONS, Rules.checkInAction(busy, GuardianState(), noon, true, sure.copy(task = 0.99)))
        assertEquals(CheckInAction.PROOF, Rules.checkInAction(busy, GuardianState(), noon, true, sure.copy(task = 0.99, summons = 0.99)))
        assertEquals(CheckInAction.PLAIN, Rules.checkInAction(busy, GuardianState(), noon, true, never))
    }

    @Test
    fun checkInNeverSetsDeadlinesItCantAnnounce() {
        assertEquals(CheckInAction.PLAIN, Rules.checkInAction(busy, GuardianState(), noon, canNotify = false, rolls = sure))
    }

    @Test
    fun checkInIsQuietAtBedtime() {
        val c = busy.copy(bedtime = BedtimeSettings(on = true), quietHours = QuietHoursSettings(on = false))
        assertEquals(CheckInAction.QUIET, Rules.checkInAction(c, GuardianState(), 2 * 60, true, sure))
        assertEquals(CheckInAction.TASK, Rules.checkInAction(c, GuardianState(), noon, true, sure))
    }

    @Test
    fun checkInIsQuietDuringQuietHours() {
        // On by default, 23:00 to 07:00.
        assertEquals(CheckInAction.QUIET, Rules.checkInAction(busy, GuardianState(), 23 * 60 + 30, true, sure))
        assertEquals(CheckInAction.QUIET, Rules.checkInAction(busy, GuardianState(), 6 * 60 + 59, true, sure))
        assertEquals(CheckInAction.TASK, Rules.checkInAction(busy, GuardianState(), 7 * 60, true, sure))
        val off = busy.copy(quietHours = QuietHoursSettings(on = false))
        assertEquals(CheckInAction.TASK, Rules.checkInAction(off, GuardianState(), 23 * 60 + 30, true, sure))
    }

    @Test
    fun nothingDueInsideQuietHours() {
        val c = GuardianConfig(enabled = true)
        assertTrue(Rules.reachesQuiet(c, 22 * 60 + 30, 30))
        assertFalse(Rules.reachesQuiet(c, 22 * 60 + 30, 29))
        assertFalse(Rules.reachesQuiet(c, noon, 60))
        // A window that wraps midnight is still found.
        val early = c.copy(quietHours = QuietHoursSettings(startMinute = 0, endMinute = 6 * 60))
        assertTrue(Rules.reachesQuiet(early, 23 * 60 + 50, 15))
    }

    @Test
    fun checkInsNearQuietHoursOnlySetWhatFits() {
        // 22:45: a 10 minute summons fits, a 30 minute photo doesn't.
        val nearlyNight = 22 * 60 + 45
        val summonsOnly = busy.copy(tasksOn = false)
        assertEquals(CheckInAction.SUMMONS, Rules.checkInAction(summonsOnly, GuardianState(), nearlyNight, true, sure))
        val proofOnly = busy.copy(tasksOn = false, showsUpOn = false)
        assertEquals(CheckInAction.PLAIN, Rules.checkInAction(proofOnly, GuardianState(), nearlyNight, true, sure))
        // Only app-enforced rules (nothing to fail) fit 15 minutes before quiet hours.
        val fits = Rules.tasksThatFit(busy, nearlyNight)
        assertTrue(fits.isNotEmpty())
        assertTrue(fits.all { it.kind == TaskKind.RULE && it.enforce != RuleEnforcement.NONE })
        assertEquals(CheckInAction.TASK, Rules.checkInAction(busy, GuardianState(), nearlyNight, true, sure))
        // Without any enforced rules, no task at all.
        val noEnforced = busy.copy(taskList = busy.taskList.filter { it.enforce == RuleEnforcement.NONE })
        assertEquals(CheckInAction.SUMMONS, Rules.checkInAction(noEnforced, GuardianState(), nearlyNight, true, sure))
    }

    @Test
    fun taskDeadlines() {
        assertEquals(0, Rules.taskDeadlineMinutes(TaskTemplate("x", TaskKind.RULE, 120, RuleEnforcement.SOCIAL_MEDIA)))
        assertEquals(150, Rules.taskDeadlineMinutes(TaskTemplate("x", TaskKind.RULE, 120)))
        assertEquals(45, Rules.taskDeadlineMinutes(TaskTemplate("x", TaskKind.PHOTO, 45)))
        assertEquals(Rules.TASK_DUE_MINUTES, Rules.taskDeadlineMinutes(TaskTemplate("x", TaskKind.LINES)))
    }

    @Test
    fun everyStarterChoiceQuestionCanBeFailed() {
        Questions.DEFAULTS.filter { it.kind == QuestionKind.CHOICE }.forEach { q ->
            assertTrue(q.text, q.wrong.distinct().size >= Rules.MAX_WRONG_ANSWERS)
            assertFalse(q.text, q.answer in q.wrong)
        }
        assertTrue(Questions.MIN_WRONG >= Rules.MAX_WRONG_ANSWERS)
    }

    @Test
    fun savedStarterQuestionsUpgradeButEditsAreKept() {
        val old = Question("Who do you belong to?", QuestionKind.CHOICE, "My angel", listOf("Myself", "Nobody"))
        val edited = Question("What are you?", QuestionKind.CHOICE, "Your pet", listOf("In charge", "A brat"))
        val mine = Question("Favorite color?", QuestionKind.CHOICE, "Pink", listOf("Blue"))
        val phrase = Question("Tell me what you are.", QuestionKind.PHRASE, "I am your good pet.")
        val upgraded = Questions.upgrade(listOf(old, edited, mine, phrase))
        assertEquals(Questions.DEFAULTS.first(), upgraded[0])
        assertEquals(edited, upgraded[1])
        assertEquals(mine, upgraded[2])
        assertEquals(phrase, upgraded[3])
        // Already up to date: nothing changes.
        assertEquals(Questions.DEFAULTS, Questions.upgrade(Questions.DEFAULTS))
    }

    @Test
    fun checkInDoesntStackDemands() {
        val pendingProof = GuardianState(
            proofs = listOf(ProofRequest(id = 1, reason = ProofReason.CHECK_IN, createdAt = now, dueAt = now + HOUR)),
        )
        // A photo is already owed: no task and no second photo, but she can still show up.
        assertEquals(CheckInAction.SUMMONS, Rules.checkInAction(busy, pendingProof, noon, true, sure))
        assertEquals(CheckInAction.PLAIN, Rules.checkInAction(busy.copy(showsUpOn = false), pendingProof, noon, true, sure))
        // An empty task list never issues a task.
        assertEquals(CheckInAction.SUMMONS, Rules.checkInAction(busy.copy(taskList = emptyList()), GuardianState(), noon, true, sure))
    }

    @Test
    fun chastityAlwaysIncludesCageChecks() {
        val locked = GuardianState(chastity = ChastityLock(startedAt = now, endsAt = now + HOUR))
        assertEquals(0.0, Rules.proofChance(GuardianConfig(enabled = true), GuardianState()), 0.0)
        assertEquals(0.25, Rules.proofChance(GuardianConfig(enabled = true), locked), 0.0)
    }

    @Test
    fun lockSteppersGrowAndReturn() {
        assertEquals(90, Rules.stepLockMinutes(60, up = true))
        assertEquals(300, Rules.stepLockMinutes(240, up = true))
        assertEquals(240, Rules.stepLockMinutes(300, up = false))
        assertEquals(36 * 60, Rules.stepLockMinutes(24 * 60, up = true))
        assertEquals(24 * 60, Rules.stepLockMinutes(36 * 60, up = false))
        assertEquals(30, Rules.stepLockMinutes(30, up = false))
        assertEquals(7 * 24 * 60, Rules.stepLockMinutes(7 * 24 * 60, up = true))
        // From 30 minutes to 7 days takes far fewer taps than 30 minute steps (335).
        var minutes = 30
        var taps = 0
        while (minutes < 7 * 24 * 60) {
            minutes = Rules.stepLockMinutes(minutes, up = true)
            taps++
        }
        assertTrue("taps $taps", taps < 50)
        // Every value on the way up comes back down to where it was.
        var v = 30
        repeat(40) {
            val upOne = Rules.stepLockMinutes(v, up = true)
            if (upOne != v) assertEquals(v, Rules.stepLockMinutes(upOne, up = false))
            v = upOne
        }
    }

    @Test
    fun capStepper() {
        assertEquals(2, Rules.stepCapHours(1, up = true))
        assertEquals(1, Rules.stepCapHours(1, up = false))
        assertEquals(36, Rules.stepCapHours(24, up = true))
        assertEquals(24, Rules.stepCapHours(36, up = false))
        assertEquals(23, Rules.stepCapHours(24, up = false))
        assertEquals(168, Rules.stepCapHours(168, up = true))
    }

    @Test
    fun askOutcomes() {
        assertEquals(AskOutcome.GRANT, Rules.askOutcome(0.1))
        assertEquals(AskOutcome.PROOF, Rules.askOutcome(0.5))
        assertEquals(AskOutcome.DENY, Rules.askOutcome(0.9))
    }

    @Test
    fun strictMoodIsHarsherAboutBegging() {
        // A roll that sweet mood releases on, strict mood denies.
        assertEquals(BegOutcome(released = true, addTime = false), Rules.begOutcome(Mood.SWEET, 0.2, 0.0))
        assertFalse(Rules.begOutcome(Mood.STRICT, 0.2, 0.0).released)
        // A time roll that strict mood adds time on, sweet mood doesn't.
        assertTrue(Rules.begOutcome(Mood.STRICT, 0.9, 0.4).addTime)
        assertFalse(Rules.begOutcome(Mood.SWEET, 0.9, 0.4).addTime)
        // Released begs never add time.
        assertFalse(Rules.begOutcome(Mood.STRICT, 0.0, 0.0).addTime)
    }

    @Test
    fun aboutOneInThreeDenialsAddTime() {
        val random = Random(7)
        var denials = 0
        var added = 0
        repeat(20_000) {
            val mood = if (random.nextBoolean()) Mood.SWEET else Mood.STRICT
            val o = Rules.begOutcome(mood, random.nextDouble(), random.nextDouble())
            if (!o.released) {
                denials++
                if (o.addTime) added++
            }
        }
        val share = added.toDouble() / denials
        assertTrue("share $share", share in 0.28..0.42)
    }

    @Test
    fun lockLengthStaysBetweenMinMaxAndCap() {
        val random = Random(42)
        repeat(500) {
            val minutes = Rules.lockMinutes(60, 240, 24, random)
            assertTrue(minutes in 60..240)
            assertEquals(0, minutes % 15)
        }
        repeat(500) {
            val minutes = Rules.lockMinutes(600, 1200, 6, random)
            assertTrue(minutes in 15..360)
        }
        // Swapped min and max still works.
        repeat(100) { assertTrue(Rules.lockMinutes(240, 60, 24, random) in 60..240) }
    }

    @Test
    fun meritLevels() {
        assertEquals(1, Rules.meritLevel(0).level)
        assertEquals(2, Rules.meritLevel(25).level)
        assertEquals("Her favorite", Rules.meritLevel(5000).title)
        assertEquals(1f, Rules.meritLevel(5000).progress(5000))
    }

    @Test
    fun everyLineHasBothMoods() {
        Line.entries.forEach { line ->
            assertTrue("$line sweet", Voice.hasLines(line, Mood.SWEET))
            assertTrue("$line strict", Voice.hasLines(line, Mood.STRICT))
        }
    }
}
