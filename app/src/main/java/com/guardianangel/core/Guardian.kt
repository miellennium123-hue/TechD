package com.guardianangel.core

import android.content.Context
import android.widget.Toast
import com.guardianangel.data.ActiveTask
import com.guardianangel.data.CatchRecord
import com.guardianangel.data.FailureRecord
import com.guardianangel.data.WheelResult
import com.guardianangel.data.ReleaseState
import com.guardianangel.data.ReleaseLog
import com.guardianangel.data.ReleaseEntry
import com.guardianangel.data.ChastityLock
import com.guardianangel.data.DegradationLevel
import com.guardianangel.data.Grade
import com.guardianangel.data.Grant
import com.guardianangel.data.GuardianConfig
import com.guardianangel.data.GuardianState
import com.guardianangel.data.Mood
import com.guardianangel.data.PeekRecord
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
import com.guardianangel.data.UsageDay
import com.guardianangel.data.WatchRequest
import com.guardianangel.data.ClipSource
import com.guardianangel.data.ForcedClip
import com.guardianangel.data.BookedSession
import com.guardianangel.data.SessionTheme
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/** Every way to fail her. [label] is what her record shows (round 94). */
enum class Failure(val merit: Int, val label: String) {
    MISSED_PROOF(8, "Missed a photo proof deadline"),
    MISSED_TASK(8, "Missed a task deadline"),
    TASK_FAILED(5, "Failed a task"),
    WRONG_ANSWERS(5, "Wrong answers to her question"),
    LEFT_SITE(5, "Left her site early"),
    RUIN_FAILED(5, "Couldn't stop at the ruin"),
    SWITCHED_OFF(10, "Switched her off"),
    TAMPERED(10, "Tampering: her watch was off or restarted during a lock"),
    BAD_DAY(5, "An F on your daily report"),
    CAUGHT_PORN(10, "Caught by Porn block"),
    MISSED_CLIP(5, "Didn't open her clip within a minute"),
    MISSED_COMMAND(5, "Missed her command to cum"),
    MISSED_RUIN(8, "Missed the ruin you owed her"),
    MISSED_SESSION(5, "Missed a booked session"),
    /** Round 104: you came without her permission and told her (release calendar). */
    CONFESSED_RELEASE(10, "Came without her permission (confessed)"),
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
        runCatching { SessionClips.migrate(appContext) }
        releaseTick()
    }

    /** Small fix-ups for data saved by older versions. */
    private fun migrate() {
        val questions = config.value.questions
        val upgraded = Questions.upgrade(questions)
        if (upgraded != questions) config.update { it.copy(questions = upgraded) }
        capPunishment()
        // Round 104: the punishment session left owed by the old owed-ruin bug.
        val st = state.value
        if (SessionPlan.leftoverFromCatch(st.owedPunishment, st.ruinOwedBy, st.failures.lastOrNull()?.kind)) {
            state.update { it.copy(owedPunishment = false) }
        }
    }

    /** Round 94: a punishment lockout longer than her cap (from before the cap, or a lowered cap) is cut to it. */
    private fun capPunishment() {
        val t = now()
        state.update { it.copy(punishmentUntil = Rules.capPunishment(it.punishmentUntil, t, config.value.punishment.capMinutes)) }
    }

    /** Round 94: her record keeps this many failures. */
    const val FAILURE_RECORD_KEEP = 100

    fun clearRecord() {
        state.update { it.copy(failures = emptyList()) }
    }

    fun now(): Long = System.currentTimeMillis()

    /** Round 104: the local day (days since 1970), for her release calendar. */
    fun today(): Long = dayOf(now())

    fun dayOf(at: Long): Long =
        java.time.Instant.ofEpochMilli(at).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toEpochDay()

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
            if (config.value.session.booked && state.value.booked == null) bookNext()
        } else {
            val proofs = state.value.proofs
            config.update { it.copy(enabled = false) }
            state.update {
                it.copy(
                    proofs = emptyList(), grants = emptyList(), checkInPending = false, task = null, summons = null, visit = null, watch = null,
                    booked = null, ruinOwedBy = 0, owedPunishment = false, forcedClip = null,
                )
            }
            Scheduler.cancelAll(appContext, proofs)
            Notifier.cancelAll(appContext)
            say(Line.OFF)
        }
    }

    /** Ends every lock, timer and restriction immediately and switches her off. Never penalized. */
    /** Round 96: her slow exit screen is in front. While it is, she holds still. */
    fun quittingBeat() {
        state.update { it.copy(quittingAt = now()) }
    }

    /** Round 96: her slow exit screen closed or went to the background. */
    fun quittingDone() {
        if (state.value.quittingAt != 0L) state.update { it.copy(quittingAt = 0) }
    }

    /** Round 96: you're on her slow exit screen right now. Nothing of hers may take the screen. */
    fun quitting(): Boolean = LockGuard.quitting(state.value.quittingAt, now())

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
                caughtUntil = 0,
                askCooldownUntil = emptyMap(),
                checkInPending = false,
                lastBegAt = 0,
                task = null,
                summons = null,
                visit = null,
                watch = null,
                booked = null,
                ruinOwedBy = 0,
                owedPunishment = false,
                forcedClip = null,
                quittingAt = 0,
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
        val duringLock = config.value.lockGuard && locked()
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
        if (before.pornBlock.on && !after.pornBlock.on) state.update { it.copy(caughtUntil = 0) }
        if (after.punishment.capMinutes < before.punishment.capMinutes) capPunishment()
        // Round 84: training starts the day you switch it on; booked sessions and owed ruins follow their switches.
        if (!before.session.training && after.session.training) state.update { it.copy(trainingStart = now()) }
        // Round 104: her release calendar picks your first day when it's switched on, and stops when off.
        if (!before.release.on && after.release.on) {
            state.update { it.copy(release = Release.start(today(), after.release, random.nextDouble())) }
        }
        if (before.release.on && !after.release.on) state.update { it.copy(release = null) }
        if (before.session.training && !after.session.training) state.update { it.copy(trainingStart = 0) }
        if (before.session.booked && !after.session.booked) cancelBooking()
        if (before.session.ruinAfterCatch && !after.session.ruinAfterCatch) clearRuinOwed()
        if (before.session.punishmentSessions && !after.session.punishmentSessions) state.update { it.copy(owedPunishment = false) }
        if (!after.enabled) return
        if (!before.session.booked && after.session.booked) bookNext()
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
        val chastityBefore = state.value.chastity?.endsAt ?: 0L
        if (c.chastity.on) addChastityTime(announce = false)
        val chastityAdded = ((state.value.chastity?.endsAt ?: 0L) - chastityBefore).coerceAtLeast(0) / MINUTE
        var punishmentAdded = 0L
        if (c.punishment.on) {
            // Round 94: it adds up, but never past her cap.
            state.update {
                val before = max(it.punishmentUntil, t)
                val after = Rules.punishmentUntil(it.punishmentUntil, t, c.punishment.length.minutes, c.punishment.capMinutes)
                punishmentAdded = ((after - before) / MINUTE).coerceAtLeast(0)
                it.copy(punishmentUntil = after)
            }
        }
        // Round 94: her record.
        val record = FailureRecord(
            at = t,
            kind = failure.name,
            label = failure.label,
            merit = if (c.meritOn) failure.merit else 0,
            punishmentMinutes = punishmentAdded.toInt(),
            chastityMinutes = chastityAdded.toInt(),
        )
        state.update { it.copy(failures = (it.failures + record).takeLast(FAILURE_RECORD_KEEP)) }
        // Round 84: your next session will be a punishment session.
        if (c.session.on && c.session.punishmentSessions) state.update { it.copy(owedPunishment = true) }
        Notifier.message(appContext, line)
        return line
    }

    // ---- App access --------------------------------------------------------------------------

    fun decide(pkg: String, protectedPackages: Set<String> = emptySet()): Decision =
        Rules.decide(pkg, config.value, state.value, now(), minuteOfDay(), protectedPackages)

    /** Whether her bedtime screen should cover this app or the home screen right now (round 54). */
    fun bedtimeScreen(decision: Decision, launcher: Boolean): Boolean =
        Rules.bedtimeScreen(config.value, minuteOfDay(), decision, launcher)

    /** Whether her caught screen should cover this app or the home screen right now (round 73). */
    fun caughtScreen(decision: Decision, launcher: Boolean): Boolean =
        Rules.caughtScreen(config.value, state.value, now(), decision, launcher)

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
        releaseTick()
        val st = state.value
        val lock = st.chastity
        val rolls = CheckInRolls(random.nextDouble(), random.nextDouble(), random.nextDouble(), random.nextDouble(), random.nextDouble())
        val canOpenSites = Permissions.accessibility(appContext) && SiteOpener.browserPackage(appContext) != null
        val clips = SessionClips.videos(appContext).size
        val action = Rules.checkInAction(c, st, minuteOfDay(), Notifier.canNotify(appContext), rolls, canOpenSites, clips, now())
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
            CheckInAction.WATCH -> startWatch()
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
        // Round 96: never over her slow exit screen. It waits until you're done or leave it.
        if (quitting()) return false
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

    // ---- She peeks ---------------------------------------------------------------------------

    /** She peeks (round 60): time for a look. Her watch also checks [Peek.mayLook] first. */
    fun peekDue(): Boolean = Peek.due(config.value, state.value.lastPeekAt, now())

    /** She's taking a look now, so the next one waits its 5 minutes whether or not this one works. */
    fun peekStarted() {
        state.update { it.copy(lastPeekAt = now()) }
    }

    /**
     * She peeked: the screenshot [file] is in her private gallery. She comments on [kind], keeps the
     * newest peeks and deletes the oldest screenshots. Her comment is a silent notification, never in
     * quiet time. Never a failure, no merit.
     */
    fun peeked(file: String, app: String, kind: PeekKind) {
        val line = say(kind.line)
        var dropped: List<PeekRecord> = emptyList()
        state.update { st ->
            val (kept, gone) = Peek.add(st.peeks, PeekRecord(now(), file, app, line))
            dropped = gone
            st.copy(peeks = kept)
        }
        dropped.forEach { File(File(appContext.filesDir, ProofFiles.DIR), it.file).delete() }
        val quiet = Rules.isQuiet(config.value, minuteOfDay())
        if (Peek.showsComment(config.value, quiet)) Toast.makeText(appContext, line, Toast.LENGTH_LONG).show()
        if (!quiet) Notifier.peek(appContext, "$app. $line")
    }

    /** The gallery deleted screenshots: forget their peeks too. */
    fun forgetPeeks(files: Set<String>?) {
        state.update { st -> st.copy(peeks = if (files == null) emptyList() else st.peeks.filter { it.file !in files }) }
    }

    // ---- Her videos (round 77) -------------------------------------------------------------

    /** A check-in makes you watch one of your clips: open it within a minute. False if there's nothing to watch. */
    private fun startWatch(): Boolean {
        val clip = Clips.pick(SessionClips.infos(appContext), random) ?: return false
        val t = now()
        val request = WatchRequest(clip.name, t, t + Clips.WATCH_DUE_SECONDS * 1_000L)
        state.update { it.copy(watch = request) }
        Notifier.watch(appContext, say(Line.WATCH_CLIP))
        Scheduler.scheduleWatch(appContext, request.dueAt)
        return true
    }

    /**
     * You opened it in time: the deadline is met. Watching to the end clears it. Round 92: from now
     * until it ends, the clip holds your phone.
     */
    fun watchStarted() {
        val w = state.value.watch ?: return
        if (state.value.forcedClip == null) forceClip(w.clip, ClipSource.CHECK_IN)
        if (w.started) return
        state.update { it.copy(watch = w.copy(started = true)) }
        Scheduler.cancelWatch(appContext)
        Notifier.cancel(appContext, Notifier.ID_WATCH)
    }

    /** Round 92: she plays [clip] full screen, and the phone is hers until it ends. */
    fun forceClip(clip: String, source: ClipSource) {
        state.update { it.copy(forcedClip = ForcedClip(clip, source, now())) }
    }

    /** Round 92: the screen went off, so the clip starts over from the beginning. */
    fun clipRestarted() {
        state.update { st -> st.copy(forcedClip = st.forcedClip?.copy(since = now())) }
    }

    /** Round 92: her 5 minute cooldown since the last full-screen clip is over. */
    fun clipCooldownOver(): Boolean = state.value.forcedClip == null && Clips.cooldownOver(state.value.lastClipAt, now())

    /** Round 92: her watch sends you back to her clip from [pkg]. */
    fun clipPullsBack(ownApp: Boolean, launcher: Boolean, exempt: Boolean): Boolean =
        Clips.pullsBack(state.value.forcedClip, now(), ownApp, launcher, exempt)

    /**
     * Round 92: her full-screen clip played to the end. Her cooldown starts; a check-in clip earns its
     * merit. Returns her line for a check-in clip, null otherwise.
     */
    fun clipEnded(): String? {
        val forced = state.value.forcedClip
        state.update { it.copy(forcedClip = null, lastClipAt = now()) }
        return if (forced?.source == ClipSource.CHECK_IN) watchFinished() else null
    }

    /** You watched it to the end. Returns her line. */
    fun watchFinished(): String {
        state.update { it.copy(watch = null) }
        Scheduler.cancelWatch(appContext)
        Notifier.cancel(appContext, Notifier.ID_WATCH)
        addMerit(2)
        return say(Line.WATCH_DONE)
    }

    /** A minute went by and you never opened it: a failure. */
    fun onWatchDeadline() {
        val w = state.value.watch ?: return
        if (w.started) return
        state.update { it.copy(watch = null) }
        Notifier.cancel(appContext, Notifier.ID_WATCH)
        if (config.value.enabled) fail(Failure.MISSED_CLIP)
    }

    // ---- Porn block ---------------------------------------------------------------------------

    /**
     * Porn block (round 73): she saw porn in [app], a private tab hid it from her, or (round 74) you opened one of your adult apps. The
     * phone locks for your lock length (everything but Always-allowed and the phone), it's a failure if
     * you set that, and she keeps the catch (never a screenshot). Her watch sends you home, shows her
     * caught screen and locks the screen. Returns her line.
     */
    fun caught(app: String, kind: CatchKind): String {
        val c = config.value
        if (!c.enabled || !c.pornBlock.on) return ""
        if (c.pornBlock.failure) fail(Failure.CAUGHT_PORN)
        val t = now()
        val line = say(kind.line)
        state.update {
            it.copy(
                caughtUntil = PornBlock.lockUntil(it.caughtUntil, t, c.pornBlock.lockMinutes),
                catches = PornBlock.add(
                    it.catches,
                    CatchRecord(t, app, hiding = kind == CatchKind.HIDING, line = line, adultApp = kind == CatchKind.ADULT_APP),
                ),
            )
        }
        Notifier.message(appContext, line)
        oweRuin()
        return line
    }

    fun clearCatches() {
        state.update { it.copy(catches = emptyList()) }
    }

    // ---- Daily report ------------------------------------------------------------------------

    /** Her day number right now. Her day ends at your report time (round 68). */
    private fun usageDay(t: Long): Long = Usage.dayKey(t + TimeZone.getDefault().getOffset(t), config.value.report.reportMinute)

    /** You unlocked the phone. Counted only while she's on and the daily report is on. */
    fun countUnlock() {
        if (!Usage.counting(config.value)) return
        checkReport()
        state.update { it.copy(usage = Usage.addUnlock(it.usage, usageDay(now()))) }
    }

    /** Time in an app, from her watch. Her watch already left out her own screens, home and the phone. */
    fun countTime(pkg: String, ms: Long) {
        if (!Usage.counting(config.value) || ms <= 0) return
        checkReport()
        state.update { it.copy(usage = Usage.addTime(it.usage, usageDay(now()), pkg, ms)) }
    }

    /** Her day ended at your report time: she grades it. Also checked on her watch's 30 second tick. */
    fun checkReport() {
        if (!Usage.counting(config.value)) return
        val day = usageDay(now())
        if (state.value.usage.day == day) return
        var done: UsageDay? = null
        state.update { st ->
            val (next, finished) = Usage.rollover(st.usage, day)
            done = finished
            st.copy(usage = next)
        }
        done?.let { sendReport(it) }
    }

    /**
     * Her nightly report: the grade, merit (or a failure for an F, if you set that), her line, and a
     * notification (silent in quiet time). Tapping it opens her reports.
     */
    private fun sendReport(day: UsageDay) {
        val c = config.value
        val grade = Usage.grade(day.unlocks, day.screenMs, c.report)
        if (grade == Grade.F && c.report.failOnF) fail(Failure.BAD_DAY) else addMerit(grade.merit)
        val line = say(Usage.line(grade))
        val report = Usage.report(day, c.report, now(), line) { appName(it) }
        state.update { it.copy(reports = Usage.add(it.reports, report)) }
        Notifier.report(appContext, "Grade ${grade.name}. $line", silent = Rules.isQuiet(c, minuteOfDay()))
    }

    private fun appName(pkg: String): String = runCatching {
        val pm = appContext.packageManager
        pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString()
    }.getOrDefault(pkg)

    fun clearReports() {
        state.update { it.copy(reports = emptyList()) }
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
     * A ruin you couldn't hold, or missing her exact command to cum (round 79), is a failure with the
     * usual failure settings. Returns her last line.
     * After a ruin during a lock she wants a photo of the cage back on: returns that request's id too.
     */
    fun finishSession(record: SessionRecord): Pair<String, Long?> {
        state.update { it.copy(sessions = (it.sessions + record).takeLast(Sessions.HISTORY)) }
        // Round 84: a punishment session pays off the punishment; a ruin pays off the ruin you owed.
        // Round 104: the owed ruin is her punishment for the catch, so it pays the punishment session too.
        val paid = SessionPlan.paysOff(record.theme, record.outcome, ruinOwed = state.value.ruinOwedBy > 0)
        if (paid.punishment) state.update { it.copy(owedPunishment = false) }
        if (paid.ruin) clearRuinOwed()
        val line = if (record.outcome == SessionOutcome.RUIN_FAILED) {
            fail(Failure.RUIN_FAILED)
        } else if (record.outcome == SessionOutcome.MISSED_COMMAND) {
            fail(Failure.MISSED_COMMAND)
        } else {
            addMerit(5)
            say(if (record.outcome == SessionOutcome.RUINED) Line.SESSION_RUIN_DONE else Line.SESSION_END)
        }
        // Round 104: her release session settles release day.
        if (record.release) releaseFinished(record.outcome)
        // A ruin, or a release (round 104), during a lock: she wants the cage back on.
        val relock = if (record.caged && record.ending != SessionEnding.DENIED && state.value.chastity != null) {
            requestProof(ProofReason.CHASTITY_CHECK, 15).id
        } else {
            null
        }
        return line to relock
    }

    // ---- Release calendar (round 104) --------------------------------------------------------

    /** The calendar while it's on and she's on, or null. */
    fun release(): ReleaseState? = state.value.release.takeIf { config.value.enabled && config.value.release.on }

    private fun logRelease(entry: ReleaseLog) {
        state.update { it.copy(releaseLog = (it.releaseLog + entry).takeLast(Release.LOG_KEEP)) }
    }

    /**
     * Keeps her calendar up to date: a missed day (release day and the day after unused) is logged and
     * she picks a new one; her morning notice on release day. At check-ins, app start and the calendar.
     */
    fun releaseTick() {
        if (!::appContext.isInitialized) return
        val c = config.value
        if (!c.enabled || !c.release.on) return
        val today = today()
        val r = state.value.release ?: Release.start(today, c.release, random.nextDouble()).also { started ->
            state.update { it.copy(release = started) }
        }
        Release.rollOver(r, today, c.release, random.nextDouble(), now())?.let { (next, log) ->
            state.update { it.copy(release = next) }
            logRelease(log)
            Notifier.message(appContext, say(Line.RELEASE_MISSED))
        }
        val current = state.value.release ?: return
        if (Release.notifyDue(current, today, Rules.isQuiet(c, minuteOfDay()))) {
            state.update { st -> st.copy(release = st.release?.copy(notifiedDay = today)) }
            Notifier.message(appContext, say(Line.RELEASE_DAY))
        }
    }

    /** You watched her reel (or there was nothing to watch). */
    fun releaseReelWatched() {
        state.update { st -> st.copy(release = st.release?.copy(reelWatched = true)) }
    }

    /**
     * Her wheel, once per release day. Another week settles it at once (7 days from today, logged).
     * Returns what it landed on and her line, or null when it isn't release day.
     */
    fun spinWheel(): Pair<WheelResult, String>? {
        val c = config.value
        val r = release() ?: return null
        val today = today()
        if (!Release.isReleaseDay(r, today)) return null
        val result = r.spin ?: Release.spin(c.release, random.nextDouble())
        if (result == WheelResult.WEEK) {
            state.update { it.copy(release = Release.used(r, today, ReleaseEntry.WEEK, c.release, random.nextDouble())) }
            logRelease(ReleaseLog(today, now(), ReleaseEntry.WEEK))
        } else {
            state.update { st -> st.copy(release = st.release?.copy(spin = result)) }
        }
        val line = when (result) {
            WheelResult.PERMISSION -> Line.RELEASE_PERMISSION
            WheelResult.RUIN -> Line.RELEASE_RUIN
            WheelResult.WEEK -> Line.RELEASE_WEEK
        }
        return result to say(line)
    }

    /** Her release session finished: release day is used, logged, and she picks your next day. */
    private fun releaseFinished(outcome: SessionOutcome) {
        val c = config.value
        val r = state.value.release ?: return
        val today = today()
        val entry = Release.entryFor(outcome)
        state.update { it.copy(release = Release.used(r, today, entry, c.release, random.nextDouble())) }
        logRelease(ReleaseLog(today, now(), entry))
    }

    /** You came without her permission and told her: a failure, and your streak starts over. Her day stays. */
    fun confessRelease(): String {
        val r = release()
        val today = today()
        if (r != null) state.update { it.copy(release = Release.confess(r, today)) }
        logRelease(ReleaseLog(today, now(), ReleaseEntry.CONFESSED))
        fail(Failure.CONFESSED_RELEASE)
        return say(Line.RELEASE_CONFESSED)
    }

    // ---- Session plans (round 84) -----------------------------------------------------------

    /** The session you're about to start, with what you owe her. */
    fun planSession(chosen: SessionTheme): SessionPlanned =
        SessionPlan.plan(config.value, state.value, chosen, now(), random.nextDouble())

    /** You tapped Start: a session started inside her booked window keeps the booking. */
    fun sessionStarted() {
        val b = state.value.booked ?: return
        if (b.kept || !SessionPlan.keepsBooking(b.at, now())) return
        state.update { it.copy(booked = b.copy(kept = true)) }
        Scheduler.cancelBooking(appContext)
        Notifier.cancel(appContext, Notifier.ID_SESSION)
        addMerit(3)
        bookNext()
    }

    /** She books your next session, if you let her and she can tell you about it. */
    private fun bookNext() {
        val c = config.value
        if (!c.enabled || !c.session.on || !c.session.booked || !Notifier.canNotify(appContext)) {
            state.update { it.copy(booked = null) }
            return
        }
        val at = SessionPlan.nextBooking(c, now(), minuteOfDay(), random.nextDouble(), random.nextDouble()) ?: return
        state.update { it.copy(booked = BookedSession(at)) }
        Scheduler.scheduleBooking(appContext, at)
        val time = java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(at))
        val day = java.text.SimpleDateFormat("EEEE", java.util.Locale.getDefault()).format(java.util.Date(at))
        Notifier.session(appContext, "${say(Line.SESSION_BOOKED)} $day, $time.", silent = true)
    }

    private fun cancelBooking() {
        state.update { it.copy(booked = null) }
        Scheduler.cancelBooking(appContext)
        Notifier.cancel(appContext, Notifier.ID_SESSION)
    }

    /** 15 minutes before her booked session. */
    fun onBookRemind() {
        val b = state.value.booked ?: return
        if (b.kept || !config.value.enabled) return
        val time = java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(b.at))
        Notifier.session(appContext, "${say(Line.SESSION_BOOKED)} $time.")
    }

    /** Her booked session is now. */
    fun onBookNow() {
        val b = state.value.booked ?: return
        if (b.kept || !config.value.enabled) return
        Notifier.session(appContext, say(Line.SESSION_BOOK_NOW))
    }

    /** 15 minutes after: you never started. A failure, and she books the next one. */
    fun onBookMiss() {
        val b = state.value.booked ?: return
        if (b.kept || !config.value.enabled) return
        state.update { it.copy(booked = null) }
        Notifier.cancel(appContext, Notifier.ID_SESSION)
        fail(Failure.MISSED_SESSION)
        bookNext()
    }

    /** Porn block caught you: a ruined session within a day (only if she can tell you, and sessions are on). */
    private fun oweRuin() {
        val c = config.value
        if (!c.session.on || !c.session.ruinAfterCatch || !Notifier.canNotify(appContext)) return
        val t = now()
        val due = state.value.ruinOwedBy.takeIf { it > t } ?: (t + SessionPlan.RUIN_OWED_HOURS * 60 * MINUTE)
        state.update { it.copy(ruinOwedBy = due) }
        Scheduler.scheduleRuinDue(appContext, due)
        Notifier.session(appContext, say(Line.SESSION_RUIN_OWED))
    }

    private fun clearRuinOwed() {
        state.update { it.copy(ruinOwedBy = 0) }
        Scheduler.cancelRuinDue(appContext)
    }

    /** The day is up and you never ruined for her: a failure. */
    fun onRuinDue() {
        val due = state.value.ruinOwedBy
        if (due <= 0 || now() < due) return
        clearRuinOwed()
        if (config.value.enabled) fail(Failure.MISSED_RUIN)
    }

    fun clearSessions() {
        state.update { it.copy(sessions = emptyList()) }
    }

    /** Round 79: her caption for a clip or edge photo she just saved. Captions of deleted clips are dropped. */
    fun captionClip(name: String, caption: String) {
        val names = SessionClips.infos(appContext).map { it.name }.toSet()
        state.update { st -> st.copy(clipCaptions = Clips.keepCaptions(st.clipCaptions + (name to caption), names)) }
    }
}
