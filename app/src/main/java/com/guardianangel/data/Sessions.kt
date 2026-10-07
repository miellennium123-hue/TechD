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
    /** Round 79: she speaks every command aloud and counts CBT out loud (the phone's own voice). */
    val voice: Boolean = true,
    /** Round 79: from 22:00 to 06:00 her voice drops to a slow whisper. */
    val whisper: Boolean = true,
    /** Round 79: her words big enough to read from across the room. */
    val bigText: Boolean = false,
    /** Round 84: the theme you picked last time, kept for the next session. */
    val theme: SessionTheme = SessionTheme.YOURS,
    /** Round 84: she picks the length (5 to 45 minutes) and doesn't tell you. */
    val herLength: Boolean = false,
    /** Round 84: her training program. Sessions get longer and harsher every week since you started. */
    val training: Boolean = false,
    /** Round 84: after any failure, your next session is a punishment session (CBT-heavy, always ruined). */
    val punishmentSessions: Boolean = true,
    /** Round 84: when Porn block catches you, you owe her a ruined session within a day, or it's a failure. */
    val ruinAfterCatch: Boolean = true,
    /** Round 84: she books your next session for a time she picks. Miss it and it's a failure. */
    val booked: Boolean = false,
)

/**
 * Session themes (round 84). Each one uses only kinks you've switched on, and sets her endings and the
 * length for that session. See core/SessionPlan.
 */
enum class SessionTheme(val label: String, val note: String) {
    YOURS("Your settings", "Your kink menu, endings and length, as set below"),
    TEASE_NIGHT("Tease night", "Slow teasing, stops and countdowns. Almost always denied"),
    EDGE_MARATHON("Edge marathon", "Edge after edge for half an hour. Rarely a full release"),
    PUNISHMENT("Punishment", "CBT, humiliation and edges. Always ruined"),
    REWARD("Reward", "Sweet and teasing, and she usually lets you cum"),
    RUIN_TRAINING("Ruin training", "Short and fast. Always ruined"),
    CBT_DISCIPLINE("CBT discipline", "CBT counts, countdowns and humiliation. Never a full release"),
}

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
    /** Round 79. */
    POST_ORGASM("Keep going", "After you finish or ruin, keep stroking right through it", cageSafe = false),
    /** Round 84. */
    CLAMPS("Clamps and pins", "Clamps or clothes pins on, then off, on her timer (3 minutes at most)", cageSafe = true),
    /** Round 84: uses your Rate me result. */
    SMALL_SIZE("Small-size humiliation", "Remarks about your size, using your Rate me result", cageSafe = true),
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
    /** Round 79: edges you did, clips she filmed, your fastest edge in seconds (0: none), and her deal. */
    val edges: Int = 0,
    val filmed: Int = 0,
    val fastestEdge: Int = 0,
    val deal: String = "",
    /** Round 84: the theme it ran with, and whether it paid off a punishment. */
    val theme: SessionTheme = SessionTheme.YOURS,
)

enum class SessionOutcome {
    FINISHED,
    RUINED,
    RUIN_FAILED,
    DENIED,
    /** Round 79: you came too early or too late for her command. A failure. */
    MISSED_COMMAND,
}

object Sessions {
    const val MIN_MINUTES = 5
    const val MAX_MINUTES = 30
    /** Round 84: her own lengths, themes and training can run longer than the stepper. */
    const val HER_MAX_MINUTES = 45
    const val DEFAULT_MINUTES = 10
    const val HISTORY = 30

    val DEFAULT_KINKS: Set<Kink> = setOf(Kink.EDGING, Kink.STOP_AND_GO, Kink.SPEED, Kink.TEASING, Kink.COUNTDOWNS, Kink.PRAISE)
}
