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
import com.guardianangel.data.RuleEnforcement
import com.guardianangel.data.Store
import com.guardianangel.data.Summons
import com.guardianangel.data.TaskKind
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
    }

    fun now(): Long = System.currentTimeMillis()

    fun minuteOfDay(): Int {
        val c = Calendar.getInstance()
        return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
    }

    // ---- Voice -------------------------------------------------------------------------------

    /** Picks a line in her current mood and remembers it. */
    fun say(line: Line): String =
        state.update { st ->
            val next = withMood(st)
            next.copy(lastLine = Voice.pick(line, next.currentMood, random))
        }.lastLine

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
            state.update { it.copy(proofs = emptyList(), grants = emptyList(), checkInPending = false, task = null, summons = null) }
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
                askCooldownUntil = emptyMap(),
                checkInPending = false,
                lastBegAt = 0,
                task = null,
                summons = null,
            )
        }
        Scheduler.cancelAll(appContext, proofs)
        Notifier.cancelAll(appContext)
        say(Line.QUIT)
    }

    /** For every setting except the on/off switch, which uses [setEnabled]. */
    fun updateConfig(transform: (GuardianConfig) -> GuardianConfig) {
        val before = config.value
        val after = config.update { transform(it).copy(enabled = it.enabled) }
        if (before.chastity.on && !after.chastity.on) endChastity()
        if (before.tasksOn && !after.tasksOn) clearTasks()
        if (before.showsUpOn && !after.showsUpOn && state.value.summons != null) clearSummons()
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

    fun grant(pkg: String, minutes: Int) {
        val t = now()
        state.update { st ->
            st.copy(grants = st.grants.filter { it.until > t && it.packageName != pkg } + Grant(pkg, t + minutes * MINUTE))
        }
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
        addMerit(2) // for keeping her enabled
        val st = state.value
        val lock = st.chastity
        val rolls = CheckInRolls(random.nextDouble(), random.nextDouble(), random.nextDouble())
        val action = Rules.checkInAction(c, st, minuteOfDay(), Notifier.canNotify(appContext), rolls)
        val handled = when (action) {
            CheckInAction.QUIET -> true // bedtime: let her pet sleep
            CheckInAction.TASK -> issueTask()
            CheckInAction.SUMMONS -> summon()
            CheckInAction.PROOF -> {
                requestProof(if (lock != null) ProofReason.CHASTITY_CHECK else ProofReason.CHECK_IN, 30, notify = true)
                true
            }
            CheckInAction.PLAIN -> false
        }
        if (!handled) {
            state.update { it.copy(checkInPending = true) }
            Notifier.checkIn(appContext, say(Line.CHECK_IN))
        }
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

    /** Picks one from the list and issues it. Photo tasks become a proof request. One open task at a time. */
    fun issueTask(): Boolean {
        val c = config.value
        val st = state.value
        if (!c.enabled || !c.tasksOn || st.task != null || st.proofs.any { it.reason == ProofReason.TASK }) return false
        val template = c.taskList.randomOrNull(random) ?: return false
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
}
