package com.guardianangel.data

import kotlinx.serialization.Serializable

/** What kind of thing she asks for, and how the phone checks it. */
enum class TaskKind(val label: String) {
    /** Obey for [TaskTemplate.minutes]. App-enforced when [TaskTemplate.enforce] isn't NONE. */
    RULE("Rule"),

    /** Do it once before the deadline, then prove it with a photo of the task. */
    PHOTO("Photo task"),

    /** Hold the phone still for [TaskTemplate.minutes]. The motion sensor checks. */
    STILLNESS("Stillness"),

    /** Type her sentence over and over. Difficulty is picked when she issues it. */
    LINES("Lines"),
}

/** Which apps an app-enforced rule locks while it runs. */
enum class RuleEnforcement(val label: String) {
    NONE("Honor"),
    SOCIAL_MEDIA("Locks social media"),
    EVERYTHING("Locks everything"),
}

/** One entry in the editable Rules & Tasks list. */
@Serializable
data class TaskTemplate(
    val text: String,
    val kind: TaskKind,
    /** Rule length, stillness length, or photo-task deadline. Unused for lines. */
    val minutes: Int = 30,
    val enforce: RuleEnforcement = RuleEnforcement.NONE,
    /** Photo tasks only: check the photo with the on-device nudity detector. */
    val explicit: Boolean = false,
)

object TaskTemplates {
    val DEFAULTS: List<TaskTemplate> = listOf(
        TaskTemplate("No social media for 2 hours.", TaskKind.RULE, 120, RuleEnforcement.SOCIAL_MEDIA),
        TaskTemplate("No social media for an hour. Think about me instead.", TaskKind.RULE, 60, RuleEnforcement.SOCIAL_MEDIA),
        TaskTemplate("Phone down for 30 minutes. Essentials only.", TaskKind.RULE, 30, RuleEnforcement.EVERYTHING),
        TaskTemplate("No sitting on furniture for an hour. Floor only.", TaskKind.RULE, 60),
        TaskTemplate("No snacks for 2 hours. Water only.", TaskKind.RULE, 120),
        TaskTemplate("Make your bed, then show me.", TaskKind.PHOTO, 30),
        TaskTemplate("Tidy the room you're in, then show me.", TaskKind.PHOTO, 60),
        TaskTemplate("Write \"I belong to my angel\" on paper and show me.", TaskKind.PHOTO, 60),
        TaskTemplate("Kneel for 5 minutes holding your phone still.", TaskKind.STILLNESS, 5),
        TaskTemplate("Hold your phone out at arm's length for 3 minutes. Don't move.", TaskKind.STILLNESS, 3),
        TaskTemplate("Kneel with your phone flat on your palms for 10 minutes.", TaskKind.STILLNESS, 10),
        TaskTemplate("Write lines for me.", TaskKind.LINES),
    )
}

/** The one task she has issued right now. Photo tasks live in the proof list instead. */
@Serializable
data class ActiveTask(
    val id: Long,
    val text: String,
    val kind: TaskKind,
    val issuedAt: Long,
    /** Done by this time, or it's a failure. Rules: the time to report back by. */
    val dueAt: Long,
    /** Rules: obey until this time. */
    val ruleUntil: Long = 0,
    val enforce: RuleEnforcement = RuleEnforcement.NONE,
    /** Stillness: how long to hold still. */
    val minutes: Int = 0,
    /** Lines: 0 easy, 1 medium, 2 hard. */
    val difficulty: Int = 0,
    val sentence: String = "",
    val lines: Int = 0,
    /** Lines typed correctly so far. Kept here so leaving the screen doesn't lose progress. */
    val linesDone: Int = 0,
) {
    val enforced: Boolean get() = kind == TaskKind.RULE && enforce != RuleEnforcement.NONE
}
