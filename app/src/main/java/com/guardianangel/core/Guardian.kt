package com.guardianangel.core

import android.content.Context
import com.guardianangel.data.ActiveTask
import com.guardianangel.data.ChastityLock
import com.guardianangel.data.DegradationLevel
import com.guardianangel.data.Grant
import com.guardianangel.data.GuardianConfig
import com.guardianangel.data.GuardianState
import com.guardianangel.data.Mood
import com.guardianangel.data.ProofPrompt
import com.guardianangel.data.ProofPrompts
import com.guardianangel.data.ProofReason
import com.guardianangel.data.ProofRequest
import com.guardianangel.data.Questions
import com.guardianangel.data.RatingRecord
import com.guardianangel.data.RuleEnforcement
import com.guardianangel.data.SessionEnding
import com.guardianangel.data.SessionOutcome
import com.guardianangel.data.SessionRecord
import com.guardianangel.data.Sessions
import com.guardianangel.data.SiteVisit
import com.guardianangel.data.Sites
import com.guardianangel.data.Store
import com.guardianangel.data.Summons
import com.guardianangel.data.TaskKind
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import java.util.Calendar
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

enum class Failure(val merit: Int) {
    MISSED_PROOF(8),
    MISSED_TASK(8),
    TASK_FAILED(5),
    WRONG_ANSWERS(5),
    LEFT_SITE(5),
    RUIN_FAILED(5),
    SWITCHED_OFF(10),
    TAMPERED(10),
}

sealed interface AskResult {
    val line: String
    data class Granted(override val line: String) : AskResult
    data class ProofDemanded(override val line: String, val request: ProofRequest) : AskResult
    data class Denied(override val line: String, val until: Long) : AskResult
}

sealed interface AnswerResult {
    val line: String
    data class Done(override val line: String) : AnswerResult
    data class Wrong(override val line: String, val triesLeft: Int) : AnswerResult
    data class Failed(override val line: String) : AnswerResult
}

sealed interface ReleaseResult {
    val line: String
    data class Released(override val line: String) : ReleaseResult
    data class Denied(override val line: String, val timeAdded: Boolean) : ReleaseResult
}

/** The angel herself: every action that changes config or state goes through here. */
object Guardian {
    private lateinit var appContext: Context
    lateinit var config: Store<GuardianConfig>
        private set
    lateinit var state: Store<GuardianState>
        private set

    private val random = Random.Default

    fun init(context: Context) {
        if (::appContext.isInitialized) return
        appContext = context.applicationContext
        config = Store(appContext, "config", GuardianConfig.serializer(), GuardianConfig())
        state = Store(appContext, "state", GuardianState.serializer(), GuardianState())
        Notifier.createChannel(appContext)
        migrate()
    }

    /** Small fix-ups for data saved by older versions. */
    private fun migrate() {
        val questions = config.value.questions
        val upgraded = Questions.upgrade(questions)
        if (upgraded != questions) config.update { it.copy(questions = upgraded) }
    }

    fun now(): Long = System.currentTimeMillis()

    fun minuteOfDay(): Int {
        val c = Calendar.getInstance()
        return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
    }

    // ---- Voice -------------------------------------------------------------------------------

    /** Picks a line in her current mood (your edited lines if you have any) and remembers it. */
    fun say(line: Line): String =
        state.update { st ->
            val next = withMood(st)
            next.copy(lastLine = Voice.pick(line, next.currentMood, random, config.value.lineOverrides))
        }.lastLine

    /** Like [say], but doesn't replace what she said last. For lines shown only while nothing else was said. */
    fun line(line: Line): String = Voice.pick(line, withMood(state.value).currentMood, random, config.value.lineOverrides)

    /** Her mood right now (Sweet or Strict). Only dialogue and begging use it. */
    fun mood(): Mood = state.update { withMood(it) }.currentMood

    private fun withMood(st: GuardianState): GuardianState {
        val c = config.value
        val t = now()
        val (mood, until) = when (c.mood) {
            Mood.SWEET, Mood.STRICT -> c.mood to 0L
            Mood.SWITCHING ->
                if (st.moodUntil > t && st.currentMood != Mood.SWITCHING) {
                    st.currentMood to st.moodUntil
                } else {
                    val next = if (random.nextBoolean()) Mood.SWEET else Mood.STRICT
                    next to t + random.nextLong(30, 121) * MINUTE
                }
        }
        return st.copy(currentMood = mood, moodUntil = until)
    }

    // ---- Master controls ---------------------------------------------------------------------

    fun setEnabled(on: Boolean) {
        if (on) {
            config.update { it.copy(enabled = true) }
            Scheduler.scheduleAll(appContext)
            WallpaperController.applyIfWanted(appContext)
            say(Line.GREETING)
        } else {
            val proofs = state.value.proofs
            config.update { it.copy(enabled = false) }
            state.update {
                it.copy(proofs = emptyList(), grants = emptyList(), checkInPending = false, task = null, summons = null, visit = null)
            }
            Scheduler.cancelAll(appContext, proofs)
            Notifier.cancelAll(appContext)
            say(Line.OFF)
        }
    }

    /** Ends every lock, timer and restriction immediately and switches her off. Never penalized. */
    fun quitForNow() {
        val proofs = state.value.proofs
        config.update { it.copy(enabled = false) }
        state.update {
            it.copy(
                chastity = null,
                proofs = emptyList(),
                grants = emptyList(),
                punishmentUntil = 0,
                lockoutUntil = 0,
                askCooldownUntil = emptyMap(),
                checkInPending = false,
                lastBegAt = 0,
                task = null,
                summons = null,
                visit = null,
            )
        }
        Scheduler.cancelAll(appContext, proofs)
        Notifier.cancelAll(appContext)
        say(Line.QUIT)
    }

    // ---- Lock guard (rounds 41 and 43) -------------------------------------------------------

    fun locked(): Boolean = LockGuard.locked(config.value, state.value, now())

    /** Lock guard on and she's on. No lock needed (round 43). */
    fun guarding(): Boolean = LockGuard.guarding(config.value)

    /** Switching her off now needs the slow way. */
    fun offNeedsWait(): Boolean = guarding()

    /** Debug mode (round 56): only while she's off. */
    fun setDebugMode(on: Boolean) {
        if (!LockGuard.canSetDebug(config.value)) return
        config.update { it.copy(debugMode = on) }
    }

    /** The slow switch off is done: it counts as a failure, then she's off. */
    fun slowOff() {
        fail(Failure.SWITCHED_OFF)
        setEnabled(false)
    }

    /**
     * A settings change that loosens her control while guarding, waiting for the slow way.
     * The screen that shows it lives in MainActivity. Not saved: leaving the app drops it.
     */
    val pendingLoosen = MutableStateFlow<((GuardianConfig) -> GuardianConfig)?>(null)

    /** Her refusal when a change is frozen (round 53: her block or bedtime is running). Shown in MainActivity. */
    val refusal = MutableStateFlow<String?>(null)

    /** The slow way is done: apply the change, on top of whatever changed meanwhile. */
    fun applyLoosen() {
        val change = pendingLoosen.value ?: return
        pendingLoosen.value = null
        // A block or bedtime may have started during the 30 minutes: then it waits until that ends.
        val before = config.value
        LockGuard.frozen(before, change(before), state.value, now(), minuteOfDay())?.let {
            refusal.value = it
            return
        }
        applyConfig(change)
    }

    fun cancelLoosen() {
        pendingLoosen.value = null
    }

    /** Her watch (the accessibility service) started. A start during a lock that isn't an update is tampering. */
    fun onWatchStarted(version: Int) {
        val st = state.value
        val duringLock = config.value.lockGuard && !config.value.debugMode && locked()
        val tamper = LockGuard.tamperOnStart(duringLock, st.guardVersion, version, st.tamperOffNoticed)
        state.update { it.copy(guardVersion = version, tamperOffNoticed = false) }
        if (tamper) tampered()
    }

    /** Checked when you open the app and at check-ins: her watch switched off while guarding. */
    fun checkWatch() {
        if (!::appContext.isInitialized) return
        val watchOn = Permissions.accessibility(appContext)
        if (!LockGuard.tamperWhenOff(guarding(), watchOn, state.value.tamperOffNoticed)) return
        state.update { it.copy(tamperOffNoticed = true) }
        tampered()
    }

    private fun tampered() {
        fail(Failure.TAMPERED)
        Notifier.message(appContext, say(Line.TAMPERED))
    }

    /**
     * For every setting except the on/off switch, which uses [setEnabled]. With Lock guard on, a
     * change that loosens her control waits in [pendingLoosen] for the slow way instead.
     */
    fun updateConfig(transform: (GuardianConfig) -> GuardianConfig) {
        val before = config.value
        if (guarding()) {
            LockGuard.frozen(before, transform(before), state.value, now(), minuteOfDay())?.let {
                refusal.value = it
                return
            }
        }
        if (guarding() && LockGuard.loosens(before, transform(before))) {
            pendingLoosen.value = transform
            return
        }
        applyConfig(transform)
    }

    private fun applyConfig(transform: (GuardianConfig) -> GuardianConfig) {
        val before = config.value
        // Debug mode only changes through setDebugMode, while she's off.
        val after = config.update { transform(it).copy(enabled = it.enabled, debugMode = it.debugMode) }
        if (before.chastity.on && !after.chastity.on) endChastity()
        if (before.tasksOn && !after.tasksOn) clearTasks()
        if (before.showsUpOn && !after.showsUpOn && state.value.summons != null) clearSummons()
        if (before.sitesOn && !after.sitesOn) clearVisit()
        if (before.lockouts.on && !after.lockouts.on) state.update { it.copy(lockoutUntil = 0) }
        if (!after.enabled) return
        if (after.checkInMinutes != before.checkInMinutes) Scheduler.scheduleNextCheckIn(appContext)
        val wallpaperChanged = after.wallpaper.on &&
            (!before.wallpaper.on || after.wallpaper.mode != before.wallpaper.mode)
        if (wallpaperChanged) WallpaperController.applyIfWanted(appContext)
    }

    // ---- Merit and failure -------------------------------------------------------------------

    fun addMerit(points: Int) {
        if (!config.value.meritOn) return
        state.update { it.copy(merit = (it.merit + points).coerceAtLeast(0)) }
    }

    fun fail(failure: Failure): String {
        val c = config.value
        val t = now()
        addMerit(-failure.merit)
        state.update { it.copy(linesFloor = (it.linesFloor + 1).coerceAtMost(2)) } // failures make the next lines harder
        val line = say(
            when {
                !c.degradation.on -> Line.FAIL_NEUTRAL
                c.degradation.level == DegradationLevel.HARSH -> Line.DEGRADE_HARSH
                else -> Line.DEGRADE_MILD
            },
        )
        if (c.chastity.on) addChastityTime(announce = false)
        if (c.punishment.on) {
            state.update {
                it.copy(punishmentUntil = max(it.punishmentUntil, t) + c.punishment.length.minutes * MINUTE)
            }
        }
        Notifier.message(appContext, line)
        return line
    }

    // ---- App access --------------------------------------------------------------------------

    fun decide(pkg: String, protectedPackages: Set<String> = emptySet()): Decision =
        Rules.decide(pkg, config.value, state.value, now(), minuteOfDay(), protectedPackages)

    /** Whether her bedtime screen should cover this app or the home screen right now (round 54). */
    fun bedtimeScreen(decision: Decision, launcher: Boolean): Boolean =
        Rules.bedtimeScreen(config.value, minuteOfDay(), decision, launcher)

    fun grant(pkg: String, minutes: Int, bought: Boolean = false) {
        val t = now()
        state.update { st ->
            st.copy(grants = st.grants.filter { it.until > t && it.packageName != pkg } + Grant(pkg, t + minutes * MINUTE, bought))
        }
    }

    /** Spend merit for a few minutes in an app during her timed block (round 53). Returns her line, or null if you can't. */
    fun buyTime(pkg: String): String? {
        if (!Rules.canBuyTime(config.value, state.value)) return null
        state.update { it.copy(merit = it.merit - Rules.BUY_MERIT) }
        grant(pkg, Rules.BUY_MINUTES, bought = true)
        return say(Line.BOUGHT_TIME)
    }

    /** At a check-in: she may start a timed app block (round 53). */
    private fun maybeStartBlock(roll: Double) {
        val c = config.value
        val t = now()
        if (!Rules.startsBlock(c, state.value, t, minuteOfDay(), roll)) return
        val minutes = Rules.blockMinutes(c, random)
        state.update { it.copy(lockoutUntil = t + minutes * MINUTE) }
        Notifier.message(appContext, say(Line.BLOCK_START))
    }

    fun ask(pkg: String): AskResult {
        val t = now()
        val cooldown = state.value.askCooldownUntil[pkg] ?: 0
        if (cooldown > t) return AskResult.Denied(say(Line.DENY), cooldown)
        return when (Rules.askOutcome(random.nextDouble())) {
            AskOutcome.GRANT -> {
                grant(pkg, Rules.GRANT_MINUTES)
                AskResult.Granted(say(Line.GRANT))
            }
            AskOutcome.PROOF -> {
                val request = requestProof(ProofReason.PERMISSION, 10, pkg)
                AskResult.ProofDemanded(say(Line.DEMAND_PROOF), request)
            }
            AskOutcome.DENY -> {
                val until = t + Rules.ASK_COOLDOWN_MINUTES * MINUTE
                state.update { st ->
                    st.copy(askCooldownUntil = st.askCooldownUntil.filterValues { it > t } + (pkg to until))
                }
                AskResult.Denied(say(Line.DENY), until)
            }
        }
    }

    // ---- Photo proof -------------------------------------------------------------------------

    fun requestProof(
        reason: ProofReason,
        dueMinutes: Int,
        pkg: String? = null,
        notify: Boolean = false,
        custom: ProofPrompt? = null,
    ): ProofRequest {
        val t = now()
        val prompt = if (custom != null) {
            custom
        } else if (reason.usesPromptList) {
            val prompts = config.value.proofPrompts.filter { !reason.everydayOnly || !it.explicit }
            prompts.randomOrNull(random) ?: ProofPrompts.FALLBACK
        } else {
            null
        }
        val request = ProofRequest(
            id = t,
            reason = reason,
            createdAt = t,
            dueAt = t + dueMinutes * MINUTE,
            packageName = pkg,
            prompt = prompt?.text.orEmpty(),
            explicit = prompt?.explicit == true,
        )
        state.update { st ->
            st.copy(proofs = st.proofs.filterNot { pkg != null && it.packageName == pkg } + request)
        }
        if (reason.penalized) Scheduler.scheduleProofDeadline(appContext, request)
        if (notify) Notifier.proof(appContext, say(Line.DEMAND_PROOF))
        return request
    }

    /** A photo failed her checks. Returns how many times this request has failed. */
    fun recordFailedCheck(id: Long): Int =
        state.update { st ->
            st.copy(proofs = st.proofs.map { if (it.id == id) it.copy(failedChecks = it.failedChecks + 1) else it })
        }.proofs.firstOrNull { it.id == id }?.failedChecks ?: 0

    /**
     * Saves the photo privately and settles the request. Returns her reaction.
     * [verified] is false when sent anyway after repeated failed checks: accepted, but no merit.
     */
    fun completeProof(id: Long, photo: File, verified: Boolean = true): String {
        val t = now()
        val request = state.value.proofs.firstOrNull { it.id == id }
        val dir = File(appContext.filesDir, ProofFiles.DIR).apply { mkdirs() }
        val name = request?.reason?.name?.lowercase() ?: "extra"
        photo.copyTo(File(dir, "proof_${t}_$name.jpg"), overwrite = true)
        photo.delete()
        if (request == null) return say(Line.PRAISE)

        state.update { st ->
            st.copy(
                proofs = st.proofs.filterNot { it.id == id },
                chastity = if (request.reason == ProofReason.CHASTITY_LOCK) st.chastity?.copy(proofReceived = true) else st.chastity,
            )
        }
        Scheduler.cancelProofDeadline(appContext, request)
        Notifier.cancel(appContext, Notifier.ID_PROOF)
        if (verified && request.reason.penalized && t <= request.dueAt) addMerit(5)
        request.packageName?.let { grant(it, Rules.GRANT_MINUTES) }
        return say(if (verified) Line.PRAISE else Line.WARNING)
    }

    fun onProofDeadline(id: Long) {
        if (state.value.proofs.none { it.id == id }) return
        state.update { st -> st.copy(proofs = st.proofs.filterNot { it.id == id }) }
        if (config.value.enabled) fail(Failure.MISSED_PROOF)
    }

    // ---- Chastity ----------------------------------------------------------------------------

    fun startChastity(): String {
        val c = config.value
        val t = now()
        val minutes = Rules.lockMinutes(c.chastity.minLockMinutes, c.chastity.maxLockMinutes, c.chastity.maxHours, random)
        val lock = ChastityLock(startedAt = t, endsAt = t + minutes * MINUTE)
        state.update { it.copy(chastity = lock, lastBegAt = 0) }
        Scheduler.scheduleChastityEnd(appContext, lock.endsAt)
        requestProof(ProofReason.CHASTITY_LOCK, 15)
        return say(Line.CHASTITY_START)
    }

    /** Adds the user-set amount, capped by the longest-lock setting. */
    fun addChastityTime(announce: Boolean = true): Boolean {
        val settings = config.value.chastity
        val lock = state.value.chastity ?: return false
        if (!settings.canAddTime) return false
        val cap = lock.startedAt + settings.maxHours * HOUR
        val newEnd = min(lock.endsAt + settings.addMinutes * MINUTE, max(cap, lock.endsAt))
        if (newEnd <= lock.endsAt) return false
        val added = ((newEnd - lock.endsAt) / MINUTE).toInt()
        state.update { st ->
            st.copy(chastity = st.chastity?.let { it.copy(endsAt = newEnd, addedMinutes = it.addedMinutes + added) })
        }
        Scheduler.scheduleChastityEnd(appContext, newEnd)
        if (announce) Notifier.message(appContext, say(Line.TIME_ADDED))
        return true
    }

    fun requestRelease(): ReleaseResult {
        val lock = state.value.chastity ?: return ReleaseResult.Released(say(Line.RELEASED))
        val t = now()
        if (t >= lock.endsAt) {
            val hours = ((lock.endsAt - lock.startedAt) / HOUR).toInt()
            addMerit(if (lock.proofReceived) 10 + hours else 2)
            endChastity()
            return ReleaseResult.Released(say(Line.RELEASED))
        }
        if (t < state.value.lastBegAt + Rules.BEG_COOLDOWN_MINUTES * MINUTE) {
            return ReleaseResult.Denied(say(Line.EARLY_DENIED), timeAdded = false)
        }
        val outcome = Rules.begOutcome(mood(), random.nextDouble(), random.nextDouble())
        if (outcome.released) {
            endChastity()
            return ReleaseResult.Released(say(Line.BEG_GRANTED))
        }
        state.update { it.copy(lastBegAt = t) }
        val added = outcome.addTime && addChastityTime(announce = false)
        return ReleaseResult.Denied(say(if (added) Line.BEG_TIME_ADDED else Line.EARLY_DENIED), added)
    }

    fun endChastity() {
        val st = state.value
        val chastityProofs = st.proofs.filter {
            it.reason == ProofReason.CHASTITY_LOCK || it.reason == ProofReason.CHASTITY_CHECK
        }
        state.update { s -> s.copy(chastity = null, lastBegAt = 0, proofs = s.proofs - chastityProofs.toSet()) }
        Scheduler.cancelChastityEnd(appContext)
        chastityProofs.forEach { Scheduler.cancelProofDeadline(appContext, it) }
    }

    fun onChastityEnd() {
        val lock = state.value.chastity ?: return
        if (config.value.enabled && now() >= lock.endsAt) Notifier.message(appContext, say(Line.TIMER_DONE))
    }

    // ---- Check-ins ---------------------------------------------------------------------------

    fun onCheckIn() {
        val c = config.value
        if (!c.enabled) return
        checkWatch()
        addMerit(2) // for keeping her enabled
        checkVisit()
        val st = state.value
        val lock = st.chastity
        val rolls = CheckInRolls(random.nextDouble(), random.nextDouble(), random.nextDouble(), random.nextDouble())
        val canOpenSites = Permissions.accessibility(appContext) && SiteOpener.browserPackage(appContext) != null
        val action = Rules.checkInAction(c, st, minuteOfDay(), Notifier.canNotify(appContext), rolls, canOpenSites)
        val handled = when (action) {
            CheckInAction.QUIET -> true // bedtime: let her pet sleep
            CheckInAction.SITE -> startVisit(asked = false)
            CheckInAction.TASK -> issueTask(fitQuiet = true)
            CheckInAction.SUMMONS -> summon()
            CheckInAction.PROOF -> {
                val reason = if (lock != null) ProofReason.CHASTITY_CHECK else ProofReason.CHECK_IN
                requestProof(reason, Rules.CHECK_IN_PROOF_MINUTES, notify = true)
                true
            }
            CheckInAction.PLAIN -> false
        }
        if (!handled) {
            state.update { it.copy(checkInPending = true) }
            Notifier.checkIn(appContext, say(Line.CHECK_IN))
        }
        if (action != CheckInAction.QUIET) maybeStartBlock(random.nextDouble())
        val whim = action != CheckInAction.QUIET && lock != null && c.chastity.canAddTime
        if (whim && random.nextDouble() < 0.1) addChastityTime()
        Scheduler.scheduleNextCheckIn(appContext)
    }

    fun acknowledgeCheckIn(): String {
        if (state.value.checkInPending) {
            state.update { it.copy(checkInPending = false) }
            addMerit(1)
            Notifier.cancel(appContext, Notifier.ID_CHECK_IN)
        }
        return say(Line.PRAISE)
    }

    // ---- Rules & Tasks -----------------------------------------------------------------------

    /**
     * Picks one from the list and issues it. Photo tasks become a proof request. One open task at a time.
     * [fitQuiet]: at check-ins she only picks tasks whose deadline ends before quiet hours. When you
     * ask her for one yourself, anything goes.
     */
    fun issueTask(fitQuiet: Boolean = false): Boolean {
        val c = config.value
        val st = state.value
        if (!c.enabled || !c.tasksOn || st.task != null || st.proofs.any { it.reason == ProofReason.TASK }) return false
        val choices = if (fitQuiet) Rules.tasksThatFit(c, minuteOfDay()) else c.taskList
        val template = choices.randomOrNull(random) ?: return false
        val t = now()
        if (template.kind == TaskKind.PHOTO) {
            requestProof(ProofReason.TASK, template.minutes.coerceAtLeast(5), custom = ProofPrompt(template.text, template.explicit))
            Notifier.proof(appContext, say(Line.TASK_ISSUED))
        } else {
            val task = when (template.kind) {
                TaskKind.RULE -> {
                    val until = t + template.minutes.coerceAtLeast(5) * MINUTE
                    val enforced = template.enforce != RuleEnforcement.NONE
                    ActiveTask(
                        id = t, text = template.text, kind = template.kind, issuedAt = t,
                        dueAt = if (enforced) until else until + Rules.RULE_REPORT_MINUTES * MINUTE,
                        ruleUntil = until, enforce = template.enforce,
                    )
                }
                TaskKind.STILLNESS -> ActiveTask(
                    id = t, text = template.text, kind = template.kind, issuedAt = t,
                    dueAt = t + Rules.TASK_DUE_MINUTES * MINUTE, minutes = template.minutes.coerceIn(1, 30),
                )
                else -> {
                    val difficulty = Lines.pickDifficulty(state.value.linesFloor, random)
                    ActiveTask(
                        id = t, text = template.text, kind = TaskKind.LINES, issuedAt = t,
                        dueAt = t + Rules.TASK_DUE_MINUTES * MINUTE, difficulty = difficulty,
                        sentence = Lines.pickSentence(difficulty, random), lines = Lines.COUNTS[difficulty],
                    )
                }
            }
            state.update { it.copy(task = task) }
            Scheduler.scheduleTask(appContext, nextTaskAlarm(task, t))
            Notifier.task(appContext, say(Line.TASK_ISSUED))
        }
        return true
    }

    /** Honor rules ring once when the rule ends (to report back) and again at the deadline. */
    fun nextTaskAlarm(task: ActiveTask, t: Long): Long =
        if (task.kind == TaskKind.RULE && !task.enforced && t < task.ruleUntil) task.ruleUntil else task.dueAt

    fun onTaskAlarm() {
        val task = state.value.task ?: return
        val t = now()
        if (t < task.dueAt - 1_000) {
            if (task.kind == TaskKind.RULE && t >= task.ruleUntil - 1_000) Notifier.task(appContext, say(Line.RULE_REPORT))
            Scheduler.scheduleTask(appContext, task.dueAt)
            return
        }
        if (task.enforced) {
            Notifier.message(appContext, finishTask())
            return
        }
        state.update { it.copy(task = null) }
        Notifier.cancel(appContext, Notifier.ID_TASK)
        if (config.value.enabled) fail(Failure.MISSED_TASK)
    }

    /** Done well: merit and praise. Finished lines reset the difficulty floor. */
    fun finishTask(): String {
        val task = state.value.task ?: return say(Line.PRAISE)
        state.update { it.copy(task = null, linesFloor = if (task.kind == TaskKind.LINES) 0 else it.linesFloor) }
        Scheduler.cancelTask(appContext)
        Notifier.cancel(appContext, Notifier.ID_TASK)
        addMerit(5)
        return say(Line.PRAISE)
    }

    /** Saves line-writing progress (0 after a typo). Finishing the last line goes through [finishTask]. */
    fun setLinesDone(done: Int) {
        state.update { st -> st.copy(task = st.task?.takeIf { it.kind == TaskKind.LINES }?.copy(linesDone = done) ?: st.task) }
    }

    /** Moved during stillness, or admitted breaking a rule. [reaction] is what she says on screen. */
    fun failTask(reaction: Line): String {
        if (state.value.task == null) return say(reaction)
        state.update { it.copy(task = null) }
        Scheduler.cancelTask(appContext)
        Notifier.cancel(appContext, Notifier.ID_TASK)
        val line = fail(Failure.TASK_FAILED)
        return if (reaction == Line.FAIL_NEUTRAL) line else say(reaction)
    }

    private fun clearTasks() {
        val photoTasks = state.value.proofs.filter { it.reason == ProofReason.TASK }
        state.update { st -> st.copy(task = null, proofs = st.proofs - photoTasks.toSet()) }
        Scheduler.cancelTask(appContext)
        Notifier.cancel(appContext, Notifier.ID_TASK)
        photoTasks.forEach { Scheduler.cancelProofDeadline(appContext, it) }
    }

    // ---- Shows up ----------------------------------------------------------------------------

    /** She wants you: a notification that opens her full screen. Ignore it and everything locks. */
    fun summon(): Boolean {
        val c = config.value
        if (!c.enabled || !c.showsUpOn || state.value.summons != null) return false
        val t = now()
        val question = c.questions.randomOrNull(random) ?: Questions.FALLBACK
        val summons = Summons(id = t, createdAt = t, lockAt = t + Rules.SUMMON_LOCK_MINUTES * MINUTE, question = question)
        state.update { it.copy(summons = summons) }
        Scheduler.scheduleSummons(appContext, summons.lockAt)
        Notifier.summon(appContext, say(Line.SUMMON))
        return true
    }

    /** Ten minutes ignored: remind you that everything is now locked. */
    fun onSummonsAlarm() {
        val summons = state.value.summons ?: return
        if (config.value.enabled && now() >= summons.lockAt - 1_000) Notifier.summon(appContext, say(Line.IGNORED))
    }

    /** Right: praise and merit. Wrong: she asks again, and the third wrong answer is a failure. */
    fun answer(given: String): AnswerResult {
        val summons = state.value.summons ?: return AnswerResult.Done(say(Line.PRAISE))
        if (Rules.isCorrect(summons.question, given)) {
            clearSummons()
            addMerit(3)
            return AnswerResult.Done(say(Line.PRAISE))
        }
        val wrong = summons.wrongAnswers + 1
        if (wrong >= Rules.MAX_WRONG_ANSWERS) {
            clearSummons()
            return AnswerResult.Failed(fail(Failure.WRONG_ANSWERS))
        }
        state.update { st -> st.copy(summons = st.summons?.copy(wrongAnswers = wrong)) }
        return AnswerResult.Wrong(say(Line.WRONG_ANSWER), Rules.MAX_WRONG_ANSWERS - wrong)
    }

    private fun clearSummons() {
        state.update { it.copy(summons = null) }
        Scheduler.cancelSummons(appContext)
        Notifier.cancel(appContext, Notifier.ID_SUMMON)
    }

    // ---- Open sites --------------------------------------------------------------------------

    /**
     * Picks one of your sites and shows her 10 second warning, or waits for the phone to be
     * unlocked (up to [Rules.SITE_SHOW_WITHIN_MINUTES]). [asked]: you pressed "Ask her", so it
     * shows straight away and quiet hours don't stop it.
     */
    fun startVisit(asked: Boolean): Boolean {
        val c = config.value
        if (!c.enabled || !c.sitesOn || state.value.visit != null) return false
        // Without the accessibility service she can't time your stay or see you leave.
        if (!Permissions.accessibility(appContext)) return false
        val url = c.siteList.randomOrNull(random) ?: return false
        val browser = SiteOpener.browserPackage(appContext) ?: return false
        val t = now()
        val visit = SiteVisit(
            id = t, url = url, browser = browser, createdAt = t,
            showBy = t + Rules.SITE_SHOW_WITHIN_MINUTES * MINUTE,
            stayMs = c.siteMinutes.coerceIn(Sites.MIN_MINUTES, Sites.MAX_MINUTES) * MINUTE,
            asked = asked,
        )
        state.update { it.copy(visit = visit) }
        if (asked) SiteOpener.showWarning(appContext) else showVisitIfReady()
        return true
    }

    /** A visit waiting for the phone: shown once it's unlocked, not in a call, and not running into quiet time. */
    fun showVisitIfReady(): Boolean {
        val visit = state.value.visit ?: return false
        if (!visit.pending || !config.value.enabled) return false
        if (Rules.visitExpired(visit, now()) || !Rules.canShowVisit(config.value, visit, minuteOfDay())) {
            clearVisit()
            return false
        }
        if (!SiteOpener.phoneReady(appContext)) return false
        SiteOpener.showWarning(appContext)
        return true
    }

    /** Her warning screen is up. The countdown runs from the first time it shows. */
    fun visitWarned() {
        state.update { st -> st.copy(visit = st.visit?.let { if (it.pending) it.copy(warnedAt = now()) else it }) }
    }

    /** Countdown over: open the page and start timing. A browser that won't open it just ends the visit. */
    fun openVisit() {
        val visit = state.value.visit?.takeIf { it.warning } ?: return
        val t = now()
        state.update { it.copy(visit = visit.copy(openedAt = t, onSiteSince = t)) }
        if (!SiteOpener.open(appContext, visit.url, visit.browser)) clearVisit()
    }

    /** The app in front changed during an open visit. Leaving is a failure; her app, calls and system screens pause. */
    fun onVisitMove(move: SiteMove) {
        val visit = state.value.visit?.takeIf { it.open } ?: return
        val t = now()
        when (move) {
            SiteMove.ON_SITE -> if (visit.onSiteSince == 0L) {
                state.update { it.copy(visit = visit.copy(onSiteSince = t)) }
            }
            SiteMove.PAUSE -> pauseVisit()
            SiteMove.LEFT -> {
                if (Rules.visitDone(visit, t)) {
                    finishVisit()
                } else {
                    clearVisit()
                    if (config.value.enabled) fail(Failure.LEFT_SITE)
                }
            }
            SiteMove.NONE -> Unit
        }
    }

    /** Screen off, or a pause from [onVisitMove]: time stops until you're back on the site. */
    fun pauseVisit() {
        val visit = state.value.visit?.takeIf { it.open && it.onSiteSince > 0 } ?: return
        val t = now()
        state.update { it.copy(visit = visit.copy(stayedMs = Rules.stayedMs(visit, t), onSiteSince = 0)) }
    }

    /** Called every second during a visit: finishes it when you've stayed long enough, drops stale ones. */
    fun checkVisit() {
        val visit = state.value.visit ?: return
        val t = now()
        when {
            !config.value.enabled -> clearVisit()
            Rules.visitDone(visit, t) -> finishVisit()
            Rules.visitExpired(visit, t) -> clearVisit()
        }
    }

    private fun finishVisit() {
        clearVisit()
        addMerit(3)
        Notifier.message(appContext, say(Line.SITE_DONE))
    }

    private fun clearVisit() {
        state.update { it.copy(visit = null) }
    }

    // ---- Rate me -----------------------------------------------------------------------------

    /**
     * She rates your measurements and what she saw in the photo. [signals] null means she rated on
     * your word alone. Only her verdict line uses mood; the score never does. No merit, no locks.
     */
    fun rate(lengthCm: Double, girthCm: Double, signals: PhotoSignals?): Pair<RatingRecord, String> {
        val record = Rating.rate(lengthCm, girthCm, config.value.rating.taste, signals, now())
        state.update { it.copy(ratings = (it.ratings + record).takeLast(Rating.HISTORY)) }
        val verdict = say(
            when (Rating.tier(record.score)) {
                RatingTier.TOP -> Line.RATE_TOP
                RatingTier.GOOD -> Line.RATE_GOOD
                RatingTier.MID -> Line.RATE_MID
                RatingTier.LOW -> Line.RATE_LOW
            },
        )
        return record to if (signals == null) "$verdict ${line(Line.RATE_UNSEEN)}" else verdict
    }

    fun clearRatings() {
        state.update { it.copy(ratings = emptyList()) }
    }

    // ---- Guided sessions ---------------------------------------------------------------------

    /** True when a chastity lock is running, so the session only uses cage-safe commands. */
    fun caged(): Boolean = state.value.chastity != null

    /**
     * A finished session (leaving early isn't recorded and costs nothing). Obeying to the end: +5 merit.
     * A ruin you couldn't hold is a failure with the usual failure settings. Returns her last line.
     * After a ruin during a lock she wants a photo of the cage back on: returns that request's id too.
     */
    fun finishSession(record: SessionRecord): Pair<String, Long?> {
        state.update { it.copy(sessions = (it.sessions + record).takeLast(Sessions.HISTORY)) }
        val line = if (record.outcome == SessionOutcome.RUIN_FAILED) {
            fail(Failure.RUIN_FAILED)
        } else {
            addMerit(5)
            say(if (record.outcome == SessionOutcome.RUINED) Line.SESSION_RUIN_DONE else Line.SESSION_END)
        }
        val relock = if (record.caged && record.ending == SessionEnding.RUINED && state.value.chastity != null) {
            requestProof(ProofReason.CHASTITY_CHECK, 15).id
        } else {
            null
        }
        return line to relock
    }

    fun clearSessions() {
        state.update { it.copy(sessions = emptyList()) }
    }
}
