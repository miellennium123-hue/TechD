package com.guardianangel.data

import kotlinx.serialization.Serializable

/**
 * Guided sessions (9.9, round 33): she runs a timed session to her beat and decides how it ends.
 * Only when you tap Start. During a chastity lock only cage-safe kinks run, and it never ends in permission.
 */
@Serializable
data class SessionSettings(
    val on: Boolean = false,
    val minutes: Int = Sessions.DEFAULT_MINUTES,
    val kinks: Set<Kink> = Sessions.DEFAULT_KINKS,
    val cbt: CbtLevel = CbtLevel.SOFT,
    /** How often each ending comes up. Relative weights 0 to 100; all 0 means denied. */
    val permissionWeight: Int = 20,
    val ruinWeight: Int = 40,
    val denialWeight: Int = 40,
    /** A tick sound on every beat, as well as the on-screen pulse. */
    val beatSound: Boolean = true,
    /**
     * Round 77: the camera is on for every session, so you see yourself. Front by default, back if
     * you'd rather (switch on the session screen). Motion checks and "out of view" were removed.
     */
    val backCamera: Boolean = false,
    /** Round 77: she films your edges and CBT, with sound. Ruins are always filmed. Clips go to Her videos. */
    val filmTasks: Boolean = true,
    /** Round 77: she makes you watch one of your clips: mid-session, at check-ins, on her lock screens. */
    val watchInSessions: Boolean = true,
    val watchAtCheckIns: Boolean = true,
    val watchOnLockScreens: Boolean = true,
)

/** One kink in the kink menu. [cageSafe] runs during a lock; [uncaged] runs without one. */
enum class Kink(val label: String, val note: String, val cageSafe: Boolean, val uncaged: Boolean = true) {
    EDGING("Edging", "Edge for me, then hands off while it fades", cageSafe = false),
    STOP_AND_GO("Stop and go", "Random hands-off pauses", cageSafe = true),
    SPEED("Speed changes", "Faster, slower, sudden bursts", cageSafe = false),
    TEASING("Teasing", "Fingertips only, slow and light", cageSafe = false),
    HOLDS("Holds and stillness", "Freeze and stay still until she says", cageSafe = true),
    NIPPLES("Nipple play", "Pinch, rub, hands on your chest", cageSafe = true),
    CAGE_TEASE("Cage tease", "Tap, rub or squeeze the cage. Only while locked", cageSafe = true, uncaged = false),
    COUNTDOWNS("Countdowns", "She counts you down to her next command", cageSafe = true),
    PRAISE("Praise", "Sweet encouragement now and then", cageSafe = true),
    HUMILIATION("Humiliation", "Degrading remarks now and then", cageSafe = true),
    TOYS("Toys", "Vibrator or plug commands", cageSafe = true),
    CBT("CBT", "Counted ball play to her beat. Soft or hard", cageSafe = true),
    SOUNDING("Sounding", "Sound in, hold, out. Slow and never rushed", cageSafe = false),
}

enum class CbtLevel(val label: String) {
    SOFT("Soft"),
    HARD("Hard"),
}

enum class SessionEnding(val label: String) {
    PERMISSION("Permission"),
    RUINED("Ruined"),
    DENIED("Denied"),
}

/** One finished session, for the history. [outcome] is what happened at the end. */
@Serializable
data class SessionRecord(
    val at: Long,
    val minutes: Int,
    val ending: SessionEnding,
    val caged: Boolean,
    /** Times she caught you out of view or off beat. Always 0 since round 77 (no more catching). */
    val caught: Int = 0,
    val skipped: Int,
    val outcome: SessionOutcome,
    /** A quickshot (round 49). */
    val quick: Boolean = false,
)

enum class SessionOutcome {
    FINISHED,
    RUINED,
    RUIN_FAILED,
    DENIED,
}

object Sessions {
    const val MIN_MINUTES = 5
    const val MAX_MINUTES = 30
    const val DEFAULT_MINUTES = 10
    const val HISTORY = 30

    val DEFAULT_KINKS: Set<Kink> = setOf(Kink.EDGING, Kink.STOP_AND_GO, Kink.SPEED, Kink.TEASING, Kink.COUNTDOWNS, Kink.PRAISE)
}
