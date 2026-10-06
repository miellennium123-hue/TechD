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
    val askCooldownUntil: Map<String, Long> = emptyMap(),
    val currentMood: Mood = Mood.SWEET,
    val moodUntil: Long = 0,
    val lastLine: String = "",
    val wallpaperId: Int = -1,
    val nextCheckInAt: Long = 0,
    val checkInPending: Boolean = false,
    /** When she last denied a beg for release. Begging again waits for the cooldown. */
    val lastBegAt: Long = 0,
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
    /** What to photograph. Blank means the reason's built-in prompt. */
    val prompt: String = "",
    /** Explicit requests must pass the on-device nudity detector. */
    val explicit: Boolean = false,
    val failedChecks: Int = 0,
) {
    val subject: String get() = prompt.ifBlank { reason.prompt }
}

/** Temporary permission to open an app. Covers everything except a punishment lockout. */
@Serializable
data class Grant(
    val packageName: String,
    val until: Long,
)

/** [everydayOnly] requests only pick non-explicit prompts. */
enum class ProofReason(
    val penalized: Boolean,
    val prompt: String,
    val usesPromptList: Boolean,
    val everydayOnly: Boolean = false,
) {
    CHASTITY_LOCK(true, "Show her you're locked in your cage.", false),
    CHASTITY_CHECK(true, "Random check. Show her you're still locked in your cage.", false),
    CHECK_IN(true, "Where you are right now.", true),
    PERMISSION(false, "Where you are right now.", true, everydayOnly = true),
}
