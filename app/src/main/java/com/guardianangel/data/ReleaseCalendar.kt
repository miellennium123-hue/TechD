package com.guardianangel.data

import kotlinx.serialization.Serializable

/**
 * Her release calendar (round 104): she picks the one day you may cum, at random between [minDays]
 * and [maxDays] from your last one. Nothing moves it once set. On release day you spin her wheel:
 * permission (a release session ending on her command, filmed), a ruin, or another week.
 */
@Serializable
data class ReleaseSettings(
    val on: Boolean = false,
    val minDays: Int = 3,
    val maxDays: Int = 14,
    /** You see the day (and a countdown). Off: "?" until the morning of release day. */
    val showDate: Boolean = true,
    /** Her wheel, in percent: a ruin, and another week. The rest is permission. */
    val ruinChance: Int = 25,
    val weekChance: Int = 15,
    /** Before you spin, she plays this week's ruin clips. */
    val reel: Boolean = true,
    /** Her backgrounds show the days left (only when the date is shown). */
    val countdownWallpaper: Boolean = true,
)

/** What her wheel lands on. */
enum class WheelResult(val label: String) {
    PERMISSION("Permission"),
    RUIN("A ruin"),
    WEEK("Another week"),
}

/** One day in her release log. */
enum class ReleaseEntry(val label: String) {
    /** You came with her permission (release day). */
    RELEASED("Released"),
    /** Her wheel said ruin, and you ruined. */
    RUINED("Ruined on release day"),
    /** Her wheel said another week. */
    WEEK("Another week"),
    /** Release day and the day after passed unused. */
    MISSED("Release day missed"),
    /** You came without her permission and told her. */
    CONFESSED("Confessed a release"),
}

/** [day]: the local day (days since 1970). */
@Serializable
data class ReleaseLog(val day: Long, val at: Long, val kind: ReleaseEntry)

/**
 * The calendar while it's on. Days are local days since 1970. [nextDay]: her release day (you have it
 * and the day after). [lastReleaseDay]: your last release, -1 for none since [startDay]. [spin]: what
 * her wheel said this release day. Quit for now keeps all of it.
 */
@Serializable
data class ReleaseState(
    val nextDay: Long,
    val startDay: Long,
    val lastReleaseDay: Long = -1,
    val bestStreak: Int = 0,
    val spin: WheelResult? = null,
    val reelWatched: Boolean = false,
    val notifiedDay: Long = -1,
)
