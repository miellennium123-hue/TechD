package com.guardianangel.core

import android.content.Context
import com.guardianangel.data.ChastityLock
import com.guardianangel.data.DegradationLevel
import com.guardianangel.data.Grant
import com.guardianangel.data.GuardianConfig
import com.guardianangel.data.GuardianState
import com.guardianangel.data.Mood
import com.guardianangel.data.ProofFrequency
import com.guardianangel.data.ProofPrompts
import com.guardianangel.data.ProofReason
import com.guardianangel.data.ProofRequest
import com.guardianangel.data.Store
import java.io.File
import java.util.Calendar
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

enum class Failure(val merit: Int) {
    MISSED_PROOF(8),
}

sealed interface AskResult {
    val line: String
    data class Granted(override val line: String) : AskResult
    data class ProofDemanded(override val line: String, val request: ProofRequest) : AskResult
    data class Denied(override val line: String, val until: Long) : AskResult
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
            state.update { it.copy(proofs = emptyList(), grants = emptyList(), checkInPending = false) }
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
    ): ProofRequest {
        val t = now()
        val prompt = if (reason.usesPromptList) {
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
        val proofChance = when {
            c.photoProof.on && c.photoProof.frequency == ProofFrequency.FREQUENT -> 0.6
            c.photoProof.on -> 0.25
            lock != null -> 0.25 // chastity always includes random check-ins
            else -> 0.0
        }
        val hasPending = st.proofs.any { it.reason.penalized }
        if (!hasPending && random.nextDouble() < proofChance) {
            requestProof(if (lock != null) ProofReason.CHASTITY_CHECK else ProofReason.CHECK_IN, 30, notify = true)
        } else {
            state.update { it.copy(checkInPending = true) }
            Notifier.checkIn(appContext, say(Line.CHECK_IN))
        }
        if (lock != null && c.chastity.canAddTime && random.nextDouble() < 0.1) addChastityTime()
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
}
