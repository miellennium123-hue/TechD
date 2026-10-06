package com.guardianangel.data

import kotlinx.serialization.Serializable

/**
 * Every user-facing setting lives in this one serializable object.
 * A future partner-remote sync only has to read and write a [GuardianConfig];
 * it must never be able to remove "Quit for now", which is not a setting at all.
 */
@Serializable
data class GuardianConfig(
    val enabled: Boolean = false,
    val lockouts: LockoutSettings = LockoutSettings(),
    val alwaysAllowed: Set<String> = AppLists.DEFAULT_ALLOWED,
    val askPermission: AskPermissionSettings = AskPermissionSettings(),
    val bedtime: BedtimeSettings = BedtimeSettings(),
    val wallpaper: WallpaperSettings = WallpaperSettings(),
    val chastity: ChastitySettings = ChastitySettings(),
    val photoProof: PhotoProofSettings = PhotoProofSettings(),
    /** What she can ask you to photograph when it isn't about chastity. */
    val proofPrompts: List<ProofPrompt> = ProofPrompts.DEFAULTS,
    val checkInMinutes: Int = 120,
    val degradation: DegradationSettings = DegradationSettings(),
    val punishment: PunishmentSettings = PunishmentSettings(),
    val discreetNotifications: Boolean = true,
    val mood: Mood = Mood.SWITCHING,
    val meritOn: Boolean = true,
)

@Serializable
data class LockoutSettings(
    val on: Boolean = false,
    val scope: LockoutScope = LockoutScope.SOCIAL_MEDIA,
    val intensity: Intensity = Intensity.FIRM,
)

@Serializable
data class AskPermissionSettings(
    val on: Boolean = false,
    val intensity: Intensity = Intensity.FIRM,
)

@Serializable
data class BedtimeSettings(
    val on: Boolean = false,
    val startMinute: Int = 23 * 60,
    val endMinute: Int = 7 * 60,
    val intensity: Intensity = Intensity.FIRM,
)

@Serializable
data class WallpaperSettings(
    val on: Boolean = false,
    val mode: WallpaperMode = WallpaperMode.SET_AND_LOCK,
)

@Serializable
data class ChastitySettings(
    val on: Boolean = false,
    val intensity: Intensity = Intensity.FIRM,
    val canAddTime: Boolean = false,
    val addMinutes: Int = 60,
    /** Hard cap on a lock's total length, including any time she adds. */
    val maxHours: Int = 24,
)

@Serializable
data class PhotoProofSettings(
    val on: Boolean = false,
    val frequency: ProofFrequency = ProofFrequency.OCCASIONAL,
)

/** One thing she can demand a photo of. Explicit prompts are checked with the on-device nudity detector. */
@Serializable
data class ProofPrompt(
    val text: String,
    val explicit: Boolean = false,
)

object ProofPrompts {
    val DEFAULTS: List<ProofPrompt> = listOf(
        ProofPrompt("Where you are right now."),
        ProofPrompt("You kneeling. Show me your knees on the floor."),
        ProofPrompt("Your hands, palms up, so I know they're behaving."),
        ProofPrompt("Your face, eyes down."),
        ProofPrompt("Today's date written on your hand."),
        ProofPrompt("Nothing covered below the waist. Show me.", explicit = true),
    )

    /** Used if the list is ever emptied. */
    val FALLBACK = ProofPrompt("Where you are right now.")
}

@Serializable
data class DegradationSettings(
    val on: Boolean = false,
    val level: DegradationLevel = DegradationLevel.MILD,
)

@Serializable
data class PunishmentSettings(
    val on: Boolean = false,
    val length: PunishmentLength = PunishmentLength.SHORT,
)

enum class Intensity(val label: String, val blurb: String) {
    GENTLE("Gentle", "Reminders and warnings only"),
    FIRM("Firm", "Bypass needs a delay or photo proof"),
    STRICT("Strict", "Blocked until the timer ends"),
    ABSOLUTE("Absolute", "Blocked, and every attempt counts as a failure"),
}

enum class LockoutScope(val label: String) {
    SOCIAL_MEDIA("Social media"),
    EVERYTHING("Everything"),
}

enum class WallpaperMode(val label: String) {
    SET_ONLY("Set only"),
    SET_AND_LOCK("Set and lock"),
}

enum class ProofFrequency(val label: String) {
    OCCASIONAL("Occasional"),
    FREQUENT("Frequent"),
}

enum class DegradationLevel(val label: String) {
    MILD("Mild"),
    HARSH("Harsh"),
}

enum class PunishmentLength(val label: String, val minutes: Int) {
    SHORT("Short (30 min)", 30),
    LONG("Long (3 hours)", 180),
}

/** Mood only changes her dialogue. It never touches rules, timers or punishments. */
enum class Mood(val label: String) {
    SWEET("Sweet"),
    STRICT("Strict"),
    SWITCHING("Switching"),
}
