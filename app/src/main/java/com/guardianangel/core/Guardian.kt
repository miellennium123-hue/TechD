package com.guardianangel.core

import android.content.Context
import com.guardianangel.data.ChastityLock
import com.guardianangel.data.DegradationLevel
import com.guardianangel.data.Grant
import com.guardianangel.data.GuardianConfig
import com.guardianangel.data.GuardianState
import com.guardianangel.data.Intensity
import com.guardianangel.data.Mood
import com.guardianangel.data.ProofFrequency
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
    FORBIDDEN_ATTEMPT(5),
    EARLY_RELEASE_ATTEMPT(5),
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
    data class Wait(override val line: String, val until: Long) : ReleaseResult
    data class Denied(override val line: String) : ReleaseResult
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

    /** Picks a line in her current mood and remembers it. Mood never feeds back into rules. */
    fun say(line: Line): String {
        val c = config.value
        val t = now()
        return state.update { st ->
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
            st.copy(currentMood = mood, moodUntil = until, lastLine = Voice.pick(line, mood, random))
        }.lastLine
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
                releaseRequestedAt = 0,
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

    /** Absolute-level attempts count as failures, at most once per cooldown so it can't spiral. */
    fun penalizeAttempt(failure: Failure = Failure.FORBIDDEN_ATTEMPT): String? {
        val t = now()
        if (t - state.value.lastPenaltyAt < Rules.PENALTY_COOLDOWN_MINUTES * MINUTE) return null
        state.update { it.copy(lastPenaltyAt = t) }
        return fail(failure)
    }

    // ---- App access --------------------------------------------------------------------------

    fun decide(pkg: String, protectedPackages: Set<String> = emptySet()): Decision =
        Rules.decide(pkg, config.value, state.value, now(), minuteOfDay(), protectedPackages)

    fun grant(pkg: String, minutes: Int, level: Intensity) {
        val t = now()
        state.update { st ->
            st.copy(grants = st.grants.filter { it.until > t && it.packageName != pkg } + Grant(pkg, t + minutes * MINUTE, level))
        }
    }

    fun ask(pkg: String): AskResult {
        val t = now()
        val cooldown = state.value.askCooldownUntil[pkg] ?: 0
        if (cooldown > t) return AskResult.Denied(say(Line.DENY), cooldown)
        return when (Rules.askOutcome(config.value.askPermission.intensity, random.nextDouble())) {
            AskOutcome.GRANT -> {
                grant(pkg, Rules.GRANT_MINUTES, Intensity.ABSOLUTE)
                AskResult.Granted(say(Line.GRANT))
            }
            AskOutcome.PROOF -> {
                val request = requestProof(ProofReason.PERMISSION, 10, pkg, Intensity.ABSOLUTE)
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
        grantLevel: Intensity? = null,
        notify: Boolean = false,
    ): ProofRequest {
        val t = now()
        val request = ProofRequest(t, reason, t, t + dueMinutes * MINUTE, pkg, grantLevel)
        state.update { st ->
            st.copy(proofs = st.proofs.filterNot { pkg != null && it.packageName == pkg } + request)
        }
        if (reason.penalized) Scheduler.scheduleProofDeadline(appContext, request)
        if (notify) Notifier.proof(appContext, say(Line.DEMAND_PROOF))
        return request
    }

    /** Saves the photo privately and settles the request. Returns her reaction. */
    fun completeProof(id: Long, photo: File): String {
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
        if (request.reason.penalized && t <= request.dueAt) addMerit(5)
        request.packageName?.let { grant(it, Rules.GRANT_MINUTES, request.grantLevel ?: Intensity.FIRM) }
        return say(Line.PRAISE)
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
        val minutes = Rules.lockMinutes(c.chastity.intensity, c.chastity.maxHours, random)
        val lock = ChastityLock(startedAt = t, endsAt = t + minutes * MINUTE)
        state.update { it.copy(chastity = lock, releaseRequestedAt = 0) }
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
        return when (config.value.chastity.intensity) {
            Intensity.GENTLE -> {
                endChastity()
                ReleaseResult.Released(say(Line.WARNING))
            }
            Intensity.FIRM -> {
                val requested = state.value.releaseRequestedAt
                val waitMs = Rules.EARLY_RELEASE_WAIT_MINUTES * MINUTE
                when {
                    requested == 0L -> {
                        state.update { it.copy(releaseRequestedAt = t) }
                        ReleaseResult.Wait(say(Line.WAIT), t + waitMs)
                    }
                    t >= requested + waitMs -> {
                        endChastity()
                        ReleaseResult.Released(say(Line.WARNING))
                    }
                    else -> ReleaseResult.Wait(say(Line.WAIT), requested + waitMs)
                }
            }
            Intensity.STRICT -> ReleaseResult.Denied(say(Line.EARLY_DENIED))
            Intensity.ABSOLUTE -> ReleaseResult.Denied(
                penalizeAttempt(Failure.EARLY_RELEASE_ATTEMPT) ?: say(Line.EARLY_DENIED),
            )
        }
    }

    fun endChastity() {
        val st = state.value
        val chastityProofs = st.proofs.filter {
            it.reason == ProofReason.CHASTITY_LOCK || it.reason == ProofReason.CHASTITY_CHECK
        }
        state.update { s -> s.copy(chastity = null, releaseRequestedAt = 0, proofs = s.proofs - chastityProofs.toSet()) }
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
        val whim = lock != null && c.chastity.canAddTime && c.chastity.intensity >= Intensity.STRICT
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
}
