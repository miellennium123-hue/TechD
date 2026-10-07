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
    /** Her timed app block (round 53) runs until this time. 0 or past means no block. */
    val lockoutUntil: Long = 0,
    val askCooldownUntil: Map<String, Long> = emptyMap(),
    val currentMood: Mood = Mood.SWEET,
    val moodUntil: Long = 0,
    val lastLine: String = "",
    val wallpaperId: Int = -1,
    /** Which background in her cycle is up (round 59), and when she set it. */
    val wallpaperIndex: Int = -1,
    val wallpaperChangedAt: Long = 0,
    val nextCheckInAt: Long = 0,
    val checkInPending: Boolean = false,
    /** When she last denied a beg for release. Begging again waits for the cooldown. */
    val lastBegAt: Long = 0,
    /** The rule or task she has issued (photo tasks are in [proofs]). */
    val task: ActiveTask? = null,
    /** Lowest lines difficulty for the next lines task. Failures raise it, finished lines reset it. */
    val linesFloor: Int = 0,
    /** She has shown up and is waiting for an answer. */
    val summons: Summons? = null,
    /** She is opening one of your sites, or has opened it and is timing your stay. */
    val visit: SiteVisit? = null,
    /** Rate me history, newest last. Numbers only, never the photo. Quit for now keeps it. */
    val ratings: List<RatingRecord> = emptyList(),
    /** Finished guided sessions, newest last. Quit for now keeps them. */
    val sessions: List<SessionRecord> = emptyList(),
    /** She peeks (round 60): her peeks, newest last, and when she last looked. Quit for now keeps them. */
    val peeks: List<PeekRecord> = emptyList(),
    val lastPeekAt: Long = 0,
    /** Daily report (round 68): today's counts so far, and her reports, newest last. Quit for now keeps them. */
    val usage: UsageDay = UsageDay(),
    val reports: List<DayReport> = emptyList(),
    /** Porn block (round 73): she caught you, so the phone is locked until this time. Quit for now clears it. */
    val caughtUntil: Long = 0,
    /** Her catches, newest last. No screenshots, just when and where. Quit for now keeps them. */
    val catches: List<CatchRecord> = emptyList(),
    /** Round 79: her caption on each clip, by file name. Quit for now keeps them. */
    val clipCaptions: Map<String, String> = emptyMap(),
    /** Round 77: a check-in wants you to watch one of your clips. Quit for now clears it. */
    val watch: WatchRequest? = null,
    /** Lock guard: the app version when her watch last started, to tell a restart from an update. */
    val guardVersion: Int = 0,
    /** Lock guard: she already punished her watch being off; cleared when it starts again. */
    val tamperOffNoticed: Boolean = false,
)

/** One of her peeks: the screenshot's file name in her private gallery, the app, and what she said. */
@Serializable
data class PeekRecord(
    val at: Long,
    val file: String,
    val app: String,
    val line: String,
)

/**
 * A check-in made you watch [clip] (round 77). Open it by [dueAt] or it's a failure. Once [started],
 * the deadline is met; watching to the end clears it.
 */
@Serializable
data class WatchRequest(
    val clip: String,
    val createdAt: Long,
    val dueAt: Long,
    val started: Boolean = false,
)

/**
 * One of her porn block catches (round 73). [hiding]: a private tab hid the screen from her.
 * [adultApp] (round 74): you opened one of your adult apps.
 */
@Serializable
data class CatchRecord(
    val at: Long,
    val app: String,
    val hiding: Boolean,
    val line: String,
    val adultApp: Boolean = false,
)

/**
 * Her count for one day (round 68). [day] is her day number (see Usage.dayKey), -1 before she starts.
 * [appMs] is time in each app by package name. Only counts, never what was on screen.
 */
@Serializable
data class UsageDay(
    val day: Long = -1,
    val unlocks: Int = 0,
    val appMs: Map<String, Long> = emptyMap(),
) {
    val screenMs: Long get() = appMs.values.sum()
}

/** One app's time in a report, by its name. */
@Serializable
data class AppTime(
    val app: String,
    val ms: Long,
)

/** Her nightly report on one day (round 68). */
@Serializable
data class DayReport(
    val at: Long,
    val unlocks: Int,
    val screenMs: Long,
    /** Your most used apps that day, most first. */
    val top: List<AppTime>,
    val grade: Grade,
    val line: String,
    val unlockGoal: Int,
    val screenGoalMinutes: Int,
)

/** Her grade for a day. Merit for A and B, a cost for D and F. */
enum class Grade(val merit: Int) {
    A(5),
    B(2),
    C(0),
    D(-3),
    F(-5),
}

/** One of her ratings. Sizes in cm. [presentation] is -1 when she rated without seeing it. */
@Serializable
data class RatingRecord(
    val at: Long,
    val lengthCm: Double,
    val girthCm: Double,
    val lengthPercentile: Int,
    val girthPercentile: Int,
    val presentation: Int,
    val score: Int,
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

/**
 * Temporary permission to open an app. Never covers bedtime, a punishment, her rules or a summons.
 * Only [bought] time (merit, round 53) gets through her timed block; asking her only covers Ask permission.
 */
@Serializable
data class Grant(
    val packageName: String,
    val until: Long,
    val bought: Boolean = false,
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
    TASK(true, "Show her you did it.", false),
}
