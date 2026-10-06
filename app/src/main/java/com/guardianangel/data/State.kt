package com.guardianangel.data

import kotlinx.serialization.Serializable

/** Runtime state: everything she is currently doing. "Quit for now" clears all of it. */
@Serializable
data class GuardianState(
    val merit: Int = 0,
    val chastity: ChastityLock? = null,
    val proofs: List<ProofRequest> = emptyList(),
    val grants: List<Grant> = emptyList(),
    val punishmentUntil: Long = 0,
    val lastPenaltyAt: Long = 0,
    val askCooldownUntil: Map<String, Long> = emptyMap(),
    val currentMood: Mood = Mood.SWEET,
    val moodUntil: Long = 0,
    val lastLine: String = "",
    val wallpaperId: Int = -1,
    val nextCheckInAt: Long = 0,
    val checkInPending: Boolean = false,
    val releaseRequestedAt: Long = 0,
)

@Serializable
data class ChastityLock(
    val startedAt: Long,
    val endsAt: Long,
    val addedMinutes: Int = 0,
    val proofReceived: Boolean = false,
)

@Serializable
data class ProofRequest(
    val id: Long,
    val reason: ProofReason,
    val createdAt: Long,
    val dueAt: Long,
    /** Set when the proof unlocks an app. */
    val packageName: String? = null,
    val grantLevel: Intensity? = null,
)

/** Temporary permission to open an app. Only covers restrictions up to [level]. */
@Serializable
data class Grant(
    val packageName: String,
    val until: Long,
    val level: Intensity,
)

enum class ProofReason(val penalized: Boolean, val prompt: String) {
    CHASTITY_LOCK(true, "Show her you're locked in."),
    CHASTITY_CHECK(true, "Random check. Show her you're still locked."),
    CHECK_IN(true, "Show her what you're doing right now."),
    PERMISSION(false, "Earn it. Send her a photo."),
}
