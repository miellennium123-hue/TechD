package com.guardianangel.core

import com.guardianangel.data.AppLists
import com.guardianangel.data.BedtimeSettings
import com.guardianangel.data.GuardianConfig
import com.guardianangel.data.GuardianState
import com.guardianangel.data.LockoutScope
import com.guardianangel.data.Mood
import com.guardianangel.data.ProofFrequency
import com.guardianangel.data.Question
import com.guardianangel.data.SiteVisit
import com.guardianangel.data.RuleEnforcement
import com.guardianangel.data.TaskKind
import com.guardianangel.data.TaskTemplate
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

const val MINUTE = 60_000L
const val HOUR = 60 * MINUTE

enum class RestrictionKind { LOCKOUT, PERMISSION, BEDTIME, PUNISHMENT, RULE, SUMMONS }

data class Restriction(val kind: RestrictionKind, val askAllowed: Boolean)

sealed interface Decision {
    data object Allow : Decision

    data class Block(
        val kind: RestrictionKind,
        val askAllowed: Boolean,
        val until: Long = 0,
    ) : Decision {
        /** Lockouts and bedtime have a way in: a short wait or an everyday photo. */
        val selfBypass: Boolean
            get() = kind == RestrictionKind.LOCKOUT || kind == RestrictionKind.BEDTIME
    }
}

enum class AskOutcome { GRANT, PROOF, DENY }

/** What a check-in turns into. QUIET: during bedtime she lets you sleep (no notification, no demands). */
enum class CheckInAction { SITE, TASK, SUMMONS, PROOF, PLAIN, QUIET }

/** Random rolls for one check-in, each in 0 until 1. Kept separate so [Rules.checkInAction] stays pure. */
data class CheckInRolls(val task: Double, val summons: Double, val proof: Double, val site: Double = 1.0)

/**
 * What a change of app in front means for an open site visit. ON_SITE: back in her browser.
 * PAUSE: her app, a call, Settings or another system screen (time stops, no failure). LEFT: anything
 * else, including the home screen and recents. NONE: no open visit.
 */
enum class SiteMove { ON_SITE, PAUSE, LEFT, NONE }

/** Her answer to "please let me out early". [addTime] only applies to a denial. */
data class BegOutcome(val released: Boolean, val addTime: Boolean)

data class MeritLevel(val level: Int, val title: String, val floor: Int, val next: Int?) {
    fun progress(points: Int): Float =
        if (next == null) 1f else ((points - floor).toFloat() / (next - floor)).coerceIn(0f, 1f)
}

/**
 * Pure decision logic. Mood is deliberately not an input to anything here,
 * except [begOutcome] (round 12: strict mood is harsher about begging).
 */
object Rules {
    const val BYPASS_MINUTES = 10
    const val GRANT_MINUTES = 15
    const val WAIT_SECONDS = 60
    const val ASK_COOLDOWN_MINUTES = 5
    const val BEG_COOLDOWN_MINUTES = 10

    fun isInWindow(startMinute: Int, endMinute: Int, minuteOfDay: Int): Boolean = when {
        startMinute == endMinute -> false
        startMinute < endMinute -> minuteOfDay in startMinute until endMinute
        else -> minuteOfDay >= startMinute || minuteOfDay < endMinute
    }

    fun isBedtime(bedtime: BedtimeSettings, minuteOfDay: Int): Boolean =
        bedtime.on && isInWindow(bedtime.startMinute, bedtime.endMinute, minuteOfDay)

    /** Quiet hours or bedtime: she lets you sleep. */
    fun isQuiet(config: GuardianConfig, minuteOfDay: Int): Boolean {
        val q = config.quietHours
        return (q.on && isInWindow(q.startMinute, q.endMinute, minuteOfDay)) || isBedtime(config.bedtime, minuteOfDay)
    }

    /** True if something due [minutes] from now would run into quiet time on the way. */
    fun reachesQuiet(config: GuardianConfig, minuteOfDay: Int, minutes: Int): Boolean =
        (0..minutes.coerceIn(0, 24 * 60)).any { isQuiet(config, (minuteOfDay + it) % (24 * 60)) }

    /**
     * How long a task gives you before you can fail it. App-enforced rules can't be failed, so 0.
     * Honor rules include the time to report back.
     */
    fun taskDeadlineMinutes(template: TaskTemplate): Int = when (template.kind) {
        TaskKind.RULE ->
            if (template.enforce != RuleEnforcement.NONE) 0 else template.minutes.coerceAtLeast(5) + RULE_REPORT_MINUTES
        TaskKind.PHOTO -> template.minutes.coerceAtLeast(5)
        TaskKind.STILLNESS, TaskKind.LINES -> TASK_DUE_MINUTES
    }

    /** Tasks she can issue now without a deadline landing in quiet time. */
    fun tasksThatFit(config: GuardianConfig, minuteOfDay: Int): List<TaskTemplate> =
        config.taskList.filter { !reachesQuiet(config, minuteOfDay, taskDeadlineMinutes(it)) }

    fun inScope(pkg: String, scope: LockoutScope): Boolean = when (scope) {
        LockoutScope.SOCIAL_MEDIA -> pkg in AppLists.SOCIAL_MEDIA
        LockoutScope.EVERYTHING -> true
    }

    fun isExempt(pkg: String, config: GuardianConfig, protectedPackages: Set<String>): Boolean =
        pkg in protectedPackages || pkg in AppLists.NEVER_BLOCK || pkg in config.alwaysAllowed

    fun restrictions(
        pkg: String,
        config: GuardianConfig,
        state: GuardianState,
        now: Long,
        minuteOfDay: Int,
        protectedPackages: Set<String>,
    ): List<Restriction> {
        if (!config.enabled || isExempt(pkg, config, protectedPackages)) return emptyList()
        // She sent you there, so nothing blocks her browser while you stay.
        val visit = state.visit
        if (visit != null && visit.open && pkg == visit.browser) return emptyList()
        val result = mutableListOf<Restriction>()
        val guarded = inScope(pkg, config.lockouts.scope)
        val ask = config.askPermission.on

        if (guarded && config.lockouts.on) {
            result += Restriction(RestrictionKind.LOCKOUT, ask)
        } else if (guarded && ask) {
            result += Restriction(RestrictionKind.PERMISSION, true)
        }
        if (isBedtime(config.bedtime, minuteOfDay)) {
            result += Restriction(RestrictionKind.BEDTIME, ask)
        }
        val summons = state.summons
        if (summons != null && now >= summons.lockAt) {
            result += Restriction(RestrictionKind.SUMMONS, false)
        }
        state.task?.takeIf { it.enforced && it.ruleUntil > now }?.let { rule ->
            if (rule.enforce == RuleEnforcement.EVERYTHING || pkg in AppLists.SOCIAL_MEDIA) {
                result += Restriction(RestrictionKind.RULE, false)
            }
        }
        if (state.punishmentUntil > now) {
            val everything = config.lockouts.on && config.lockouts.scope == LockoutScope.EVERYTHING
            if (everything || pkg in AppLists.SOCIAL_MEDIA) {
                result += Restriction(RestrictionKind.PUNISHMENT, false)
            }
        }
        return result
    }

    /** Opening a blocked app never counts as a failure (round 12). */
    fun decide(
        pkg: String,
        config: GuardianConfig,
        state: GuardianState,
        now: Long,
        minuteOfDay: Int,
        protectedPackages: Set<String> = emptySet(),
    ): Decision {
        val list = restrictions(pkg, config, state, now, minuteOfDay, protectedPackages)
        if (list.isEmpty()) return Decision.Allow
        // Ignored her for too long: everything locks until you answer. Only answering opens it.
        if (list.any { it.kind == RestrictionKind.SUMMONS }) return Decision.Block(RestrictionKind.SUMMONS, askAllowed = false)
        if (list.any { it.kind == RestrictionKind.PUNISHMENT }) {
            return Decision.Block(RestrictionKind.PUNISHMENT, askAllowed = false, until = state.punishmentUntil)
        }
        // Her rules have no way in, like punishment. Grants don't cover them.
        if (list.any { it.kind == RestrictionKind.RULE }) {
            return Decision.Block(RestrictionKind.RULE, askAllowed = false, until = state.task?.ruleUntil ?: 0)
        }
        if (state.grants.any { it.packageName == pkg && it.until > now }) return Decision.Allow

        val kinds = list.map { it.kind }
        val kind = listOf(RestrictionKind.BEDTIME, RestrictionKind.LOCKOUT, RestrictionKind.PERMISSION).first { it in kinds }
        return Decision.Block(kind, askAllowed = list.all { it.askAllowed })
    }

    /** At check-ins with Rules & Tasks on, she issues one about 1 in 3 times. */
    const val TASK_CHANCE = 1.0 / 3

    /** How long you have to start a stillness task or finish lines, and to report back after an honor rule. */
    const val TASK_DUE_MINUTES = 60
    const val RULE_REPORT_MINUTES = 30

    /** Shows up: chance per check-in, minutes before an ignored summons locks everything, wrong answers allowed. */
    const val SUMMON_CHANCE = 0.25
    const val SUMMON_LOCK_MINUTES = 10
    const val MAX_WRONG_ANSWERS = 3

    /** Open sites: chance per check-in ("almost every single time", round 19), warning length, how long a locked phone can delay it. */
    const val SITE_CHANCE = 0.9
    const val SITE_WARNING_SECONDS = 10
    const val SITE_SHOW_WITHIN_MINUTES = 30
    const val SITE_OPEN_GRACE_MS = 3_000L

    /** Minutes a visit needs, counting the warning, so it can be checked against quiet time. */
    fun siteMinutes(config: GuardianConfig): Int = config.siteMinutes + 1

    /** Time spent on the site so far. */
    fun stayedMs(visit: SiteVisit, now: Long): Long =
        visit.stayedMs + if (visit.onSiteSince > 0) (now - visit.onSiteSince).coerceAtLeast(0L) else 0L

    fun visitDone(visit: SiteVisit, now: Long): Boolean = visit.open && stayedMs(visit, now) >= visit.stayMs

    /**
     * A visit that never got shown (phone stayed locked) is dropped at [SiteVisit.showBy].
     * Anything left far past its time (service killed mid-visit) is dropped too, never penalized.
     */
    fun visitExpired(visit: SiteVisit, now: Long): Boolean =
        (visit.pending && now > visit.showBy) ||
            now > visit.showBy + visit.stayMs + HOUR

    /** She only shows a waiting visit outside quiet time, with room to finish before it. Asking her yourself skips that. */
    fun canShowVisit(config: GuardianConfig, visit: SiteVisit, minuteOfDay: Int): Boolean =
        visit.asked || !reachesQuiet(config, minuteOfDay, (visit.stayMs / MINUTE).toInt() + 1)

    /**
     * Leaving the browser before time is up is a failure (round 19). Going home or to another app
     * counts. Her own app (Quit for now must stay reachable), calls and system screens only pause.
     * [exempt] is the never-blocked set; launchers are checked first because they're in it too.
     * For a moment after the page opens, other windows only pause, so the hand-off from her
     * warning screen to the browser can't count as leaving.
     */
    fun siteMove(visit: SiteVisit?, pkg: String, now: Long, ownApp: Boolean, launcher: Boolean, exempt: Boolean): SiteMove = when {
        visit == null || !visit.open -> SiteMove.NONE
        pkg == visit.browser -> SiteMove.ON_SITE
        now - visit.openedAt < SITE_OPEN_GRACE_MS -> SiteMove.PAUSE
        launcher -> SiteMove.LEFT
        ownApp || exempt -> SiteMove.PAUSE
        else -> SiteMove.LEFT
    }

    /** Choice answers must match the right option. Phrases must match exactly, ignoring case, spacing at the ends and curly quotes. */
    fun isCorrect(question: Question, given: String): Boolean =
        normalizeAnswer(given) == normalizeAnswer(question.answer)

    private fun normalizeAnswer(text: String): String =
        text.trim().lowercase().replace('’', '\'').replace('‘', '\'').replace(Regex("\\s+"), " ")

    /** Chance a check-in asks for photo proof. Chastity always includes random cage checks. */
    fun proofChance(config: GuardianConfig, state: GuardianState): Double = when {
        config.photoProof.on && config.photoProof.frequency == ProofFrequency.FREQUENT -> 0.6
        config.photoProof.on -> 0.25
        state.chastity != null -> 0.25
        else -> 0.0
    }

    /** How long you get for a photo she asks for at a check-in. */
    const val CHECK_IN_PROOF_MINUTES = 30

    /**
     * Decides what a check-in does. Anything with a deadline (task, summons, photo) only happens
     * when she can actually notify you, and never during or running into quiet hours or bedtime,
     * so you can't fail while asleep or without being told.
     *
     * Site visits come first, almost every time (round 19). They need no notification because the
     * warning shows itself, but they do need [canOpenSites]: the accessibility service and a browser.
     */
    fun checkInAction(
        config: GuardianConfig,
        state: GuardianState,
        minuteOfDay: Int,
        canNotify: Boolean,
        rolls: CheckInRolls,
        canOpenSites: Boolean = false,
    ): CheckInAction {
        if (isQuiet(config, minuteOfDay)) return CheckInAction.QUIET
        val canSite = canOpenSites && config.sitesOn && config.siteList.isNotEmpty() && state.visit == null &&
            state.summons == null && !reachesQuiet(config, minuteOfDay, siteMinutes(config))
        if (canSite && rolls.site < SITE_CHANCE) return CheckInAction.SITE
        if (!canNotify) return CheckInAction.PLAIN
        val proofPending = state.proofs.any { it.reason.penalized }
        val canTask = config.tasksOn && state.task == null && !proofPending && tasksThatFit(config, minuteOfDay).isNotEmpty()
        val canSummon = config.showsUpOn && state.summons == null && state.visit == null &&
            !reachesQuiet(config, minuteOfDay, SUMMON_LOCK_MINUTES)
        val canProof = !proofPending && !reachesQuiet(config, minuteOfDay, CHECK_IN_PROOF_MINUTES)
        return when {
            canTask && rolls.task < TASK_CHANCE -> CheckInAction.TASK
            canSummon && rolls.summons < SUMMON_CHANCE -> CheckInAction.SUMMONS
            canProof && rolls.proof < proofChance(config, state) -> CheckInAction.PROOF
            else -> CheckInAction.PLAIN
        }
    }

    /** Settings stepper for lock lengths: 30 minute steps up to 4h, 1h steps up to a day, then 12h, up to 7 days. */
    fun stepLockMinutes(current: Int, up: Boolean): Int {
        // Going down uses the step of the band below, so up then down lands back where you started.
        val probe = if (up) current else current - 1
        val step = when {
            probe < 4 * 60 -> 30
            probe < 24 * 60 -> 60
            else -> 12 * 60
        }
        return (if (up) current + step else current - step).coerceIn(30, 7 * 24 * 60)
    }

    /** Settings stepper for the hard cap: 1h steps up to a day, then 12h, up to 7 days. */
    fun stepCapHours(current: Int, up: Boolean): Int {
        val probe = if (up) current else current - 1
        val step = if (probe < 24) 1 else 12
        return (if (up) current + step else current - step).coerceIn(1, 7 * 24)
    }

    /** How she answers "may I?". Fixed odds, never affected by mood. */
    fun askOutcome(roll: Double): AskOutcome = when {
        roll < 0.4 -> AskOutcome.GRANT
        roll < 0.8 -> AskOutcome.PROOF
        else -> AskOutcome.DENY
    }

    /**
     * Begging for early release. Sweet mood is kinder, strict mood denies and adds time more often.
     * Roughly 1 in 3 denials add time on average. The only place mood affects an outcome.
     */
    fun begOutcome(mood: Mood, releaseRoll: Double, timeRoll: Double): BegOutcome {
        val (releaseChance, addChance) = when (mood) {
            Mood.STRICT -> 0.1 to 0.5
            else -> 0.35 to 0.2
        }
        return if (releaseRoll < releaseChance) {
            BegOutcome(released = true, addTime = false)
        } else {
            BegOutcome(released = false, addTime = timeRoll < addChance)
        }
    }

    /** The lock length she picks between the user's min and max, rounded to 15 minutes, never above the cap. */
    fun lockMinutes(minMinutes: Int, maxMinutes: Int, capHours: Int, random: Random): Int {
        val cap = (capHours * 60).coerceAtLeast(15)
        val hi = min(max(minMinutes, maxMinutes), cap).coerceAtLeast(15)
        val lo = min(minMinutes, maxMinutes).coerceIn(15, hi)
        val picked = random.nextInt(lo, hi + 1)
        return ((picked + 7) / 15 * 15).coerceIn(15, cap)
    }

    private val LEVELS = listOf(
        0 to "Stray",
        25 to "Pet",
        75 to "Good pet",
        150 to "Obedient pet",
        300 to "Devoted pet",
        500 to "Treasured pet",
        800 to "Perfect pet",
        1200 to "Her favorite",
    )

    fun meritLevel(points: Int): MeritLevel {
        val index = LEVELS.indexOfLast { points >= it.first }.coerceAtLeast(0)
        return MeritLevel(
            level = index + 1,
            title = LEVELS[index].second,
            floor = LEVELS[index].first,
            next = LEVELS.getOrNull(index + 1)?.first,
        )
    }
}
