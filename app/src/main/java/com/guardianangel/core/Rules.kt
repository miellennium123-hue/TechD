package com.guardianangel.core

import com.guardianangel.data.AppLists
import com.guardianangel.data.BedtimeSettings
import com.guardianangel.data.GuardianConfig
import com.guardianangel.data.GuardianState
import com.guardianangel.data.Intensity
import com.guardianangel.data.LockoutScope
import kotlin.math.min
import kotlin.random.Random

const val MINUTE = 60_000L
const val HOUR = 60 * MINUTE

enum class RestrictionKind { LOCKOUT, PERMISSION, BEDTIME, PUNISHMENT }

data class Restriction(val kind: RestrictionKind, val intensity: Intensity, val askAllowed: Boolean)

sealed interface Decision {
    data object Allow : Decision

    data class Block(
        val kind: RestrictionKind,
        val intensity: Intensity,
        val askAllowed: Boolean,
        val until: Long = 0,
    ) : Decision {
        /** At Absolute, trying to open a blocked app is itself a failure. */
        val countsAsFailure: Boolean
            get() = intensity == Intensity.ABSOLUTE && kind != RestrictionKind.PERMISSION

        /** Gentle and Firm lockouts or bedtime can be bypassed without asking her. */
        val selfBypass: Boolean
            get() = (kind == RestrictionKind.LOCKOUT || kind == RestrictionKind.BEDTIME) &&
                intensity <= Intensity.FIRM
    }
}

enum class AskOutcome { GRANT, PROOF, DENY }

data class MeritLevel(val level: Int, val title: String, val floor: Int, val next: Int?) {
    fun progress(points: Int): Float =
        if (next == null) 1f else ((points - floor).toFloat() / (next - floor)).coerceIn(0f, 1f)
}

/** Pure decision logic. Mood is deliberately not an input to anything here. */
object Rules {
    const val BYPASS_MINUTES = 10
    const val GRANT_MINUTES = 15
    const val FIRM_DELAY_SECONDS = 60
    const val ASK_COOLDOWN_MINUTES = 5
    const val PENALTY_COOLDOWN_MINUTES = 15
    const val EARLY_RELEASE_WAIT_MINUTES = 10

    fun isInWindow(startMinute: Int, endMinute: Int, minuteOfDay: Int): Boolean = when {
        startMinute == endMinute -> false
        startMinute < endMinute -> minuteOfDay in startMinute until endMinute
        else -> minuteOfDay >= startMinute || minuteOfDay < endMinute
    }

    fun isBedtime(bedtime: BedtimeSettings, minuteOfDay: Int): Boolean =
        bedtime.on && isInWindow(bedtime.startMinute, bedtime.endMinute, minuteOfDay)

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
        val result = mutableListOf<Restriction>()
        val guarded = inScope(pkg, config.lockouts.scope)
        val ask = config.askPermission.on

        if (guarded && config.lockouts.on) {
            result += Restriction(RestrictionKind.LOCKOUT, config.lockouts.intensity, ask)
        } else if (guarded && ask) {
            result += Restriction(RestrictionKind.PERMISSION, config.askPermission.intensity, true)
        }
        if (isBedtime(config.bedtime, minuteOfDay)) {
            result += Restriction(RestrictionKind.BEDTIME, config.bedtime.intensity, ask)
        }
        if (state.punishmentUntil > now) {
            val everything = config.lockouts.on && config.lockouts.scope == LockoutScope.EVERYTHING
            if (everything || pkg in AppLists.SOCIAL_MEDIA) {
                result += Restriction(RestrictionKind.PUNISHMENT, Intensity.STRICT, false)
            }
        }
        return result
    }

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

        val punished = list.any { it.kind == RestrictionKind.PUNISHMENT }
        val top = list.maxWith(compareBy<Restriction> { it.intensity }.thenBy { it.kind == RestrictionKind.PUNISHMENT })
        val grant = state.grants.firstOrNull { it.packageName == pkg && it.until > now }
        if (!punished && grant != null && grant.level >= top.intensity) return Decision.Allow

        return Decision.Block(
            kind = if (punished) RestrictionKind.PUNISHMENT else top.kind,
            intensity = top.intensity,
            askAllowed = !punished && list.all { it.askAllowed },
            until = if (punished) state.punishmentUntil else 0,
        )
    }

    /** How she answers "may I?". Depends only on the Ask-permission intensity, never on mood. */
    fun askOutcome(intensity: Intensity, roll: Double): AskOutcome = when (intensity) {
        Intensity.GENTLE -> AskOutcome.GRANT
        Intensity.FIRM -> if (roll < 0.5) AskOutcome.GRANT else AskOutcome.PROOF
        Intensity.STRICT -> when {
            roll < 0.3 -> AskOutcome.GRANT
            roll < 0.7 -> AskOutcome.PROOF
            else -> AskOutcome.DENY
        }
        Intensity.ABSOLUTE -> when {
            roll < 0.1 -> AskOutcome.GRANT
            roll < 0.5 -> AskOutcome.PROOF
            else -> AskOutcome.DENY
        }
    }

    fun lockRangeMinutes(intensity: Intensity): IntRange = when (intensity) {
        Intensity.GENTLE -> 60..240
        Intensity.FIRM -> 240..720
        Intensity.STRICT -> 720..2880
        Intensity.ABSOLUTE -> 1440..4320
    }

    /** The lock length she picks, rounded to 15 minutes and never above the user's cap. */
    fun lockMinutes(intensity: Intensity, maxHours: Int, random: Random): Int {
        val cap = (maxHours * 60).coerceAtLeast(15)
        val range = lockRangeMinutes(intensity)
        val hi = min(range.last, cap)
        val lo = min(range.first, hi)
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
