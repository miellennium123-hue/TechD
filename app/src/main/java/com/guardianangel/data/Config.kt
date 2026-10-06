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
    /** Quiet hours: she lets you sleep. Doesn't lock anything; that's what Bedtime is for. */
    val quietHours: QuietHoursSettings = QuietHoursSettings(),
    val wallpaper: WallpaperSettings = WallpaperSettings(),
    val chastity: ChastitySettings = ChastitySettings(),
    val photoProof: PhotoProofSettings = PhotoProofSettings(),
    /** What she can ask you to photograph when it isn't about chastity. */
    val proofPrompts: List<ProofPrompt> = ProofPrompts.DEFAULTS,
    /** Rules & Tasks: at about 1 in 3 check-ins she issues one from [taskList]. */
    val tasksOn: Boolean = false,
    val taskList: List<TaskTemplate> = TaskTemplates.DEFAULTS,
    /** Shows up: at some check-ins she wants you, and asks a question from [questions]. */
    val showsUpOn: Boolean = false,
    val questions: List<Question> = Questions.DEFAULTS,
    /**
     * Open sites (round 19): at almost every check-in she opens one of [siteList] in Chrome after a
     * 10 second warning, and you stay [siteMinutes]. The list is yours; nothing is bundled.
     */
    val sitesOn: Boolean = false,
    val siteList: List<String> = emptyList(),
    val siteMinutes: Int = Sites.DEFAULT_MINUTES,
    val checkInMinutes: Int = 120,
    val degradation: DegradationSettings = DegradationSettings(),
    val punishment: PunishmentSettings = PunishmentSettings(),
    val discreetNotifications: Boolean = true,
    val mood: Mood = Mood.SWITCHING,
    val meritOn: Boolean = true,
    /**
     * Her lines (round 21): your versions of the situations you edited, keyed by situation name
     * (`Line.name`). Situations you never touched aren't stored, so they keep getting her newest
     * built-in lines. See Voice.
     */
    val lineOverrides: Map<String, LineSet> = emptyMap(),
    /** Rate me (round 27): she scores your measurements and a photo. See core/Rating. */
    val rating: RatingSettings = RatingSettings(),
)

@Serializable
data class RatingSettings(
    val on: Boolean = false,
    /** Which way her score runs. The percentiles you see are the same either way. */
    val taste: RatingTaste = RatingTaste.BIGGER,
    val inches: Boolean = false,
)

enum class RatingTaste(val label: String) {
    BIGGER("She likes bigger"),
    SMALLER("She likes smaller"),
}

/** One situation's lines, as you edited them. An empty mood falls back to her built-in lines. */
@Serializable
data class LineSet(
    val sweet: List<String> = emptyList(),
    val strict: List<String> = emptyList(),
)

@Serializable
data class LockoutSettings(
    val on: Boolean = false,
    val scope: LockoutScope = LockoutScope.SOCIAL_MEDIA,
)

@Serializable
data class AskPermissionSettings(
    val on: Boolean = false,
)

/**
 * Inside this window check-ins are silent, and she never sets anything due inside it.
 * On by default (round 16): nobody should fail a deadline in their sleep.
 */
@Serializable
data class QuietHoursSettings(
    val on: Boolean = true,
    val startMinute: Int = 23 * 60,
    val endMinute: Int = 7 * 60,
)

@Serializable
data class BedtimeSettings(
    val on: Boolean = false,
    val startMinute: Int = 23 * 60,
    val endMinute: Int = 7 * 60,
)

@Serializable
data class WallpaperSettings(
    val on: Boolean = false,
    val mode: WallpaperMode = WallpaperMode.SET_AND_LOCK,
)

@Serializable
data class ChastitySettings(
    val on: Boolean = false,
    /** She picks a lock length between these two. */
    val minLockMinutes: Int = 60,
    val maxLockMinutes: Int = 240,
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

    /** Used if the list is ever emptied, or has no everyday prompts. */
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

/**
 * Mood changes her dialogue. The one exception: begging for early chastity release,
 * where strict mood denies and adds time more often (see Rules.begOutcome).
 */
enum class Mood(val label: String) {
    SWEET("Sweet"),
    STRICT("Strict"),
    SWITCHING("Switching"),
}
