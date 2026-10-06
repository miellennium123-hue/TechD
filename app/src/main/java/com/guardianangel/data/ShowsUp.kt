package com.guardianangel.data

import kotlinx.serialization.Serializable

enum class QuestionKind(val label: String) {
    CHOICE("Multiple choice"),
    PHRASE("Type a phrase"),
}

/**
 * One thing she can ask when she shows up. Choice questions show [answer] mixed in with [wrong].
 * Phrase questions show [answer] and you type it exactly.
 */
@Serializable
data class Question(
    val text: String,
    val kind: QuestionKind,
    val answer: String,
    val wrong: List<String> = emptyList(),
)

object Questions {
    val DEFAULTS: List<Question> = listOf(
        Question("Who do you belong to?", QuestionKind.CHOICE, "My angel", listOf("Myself", "Nobody")),
        Question("What are you?", QuestionKind.CHOICE, "Your pet", listOf("In charge", "Free to do what I want")),
        Question("Who decides when you're unlocked?", QuestionKind.CHOICE, "You do", listOf("I do", "The timer")),
        Question("What does a good pet do when I call?", QuestionKind.CHOICE, "Comes straight away", listOf("Makes you wait", "Ignores you")),
        Question("Who gets the last word?", QuestionKind.CHOICE, "You, always", listOf("Me", "Whoever's loudest")),
        Question("Tell me what you are.", QuestionKind.PHRASE, "I am your good pet."),
        Question("Tell me you came when called.", QuestionKind.PHRASE, "I came as soon as you called."),
        Question("Thank me properly.", QuestionKind.PHRASE, "Thank you for watching over me."),
    )

    /** Used if the list is ever emptied. */
    val FALLBACK = Question("Who do you belong to?", QuestionKind.CHOICE, "My angel", listOf("Myself", "Nobody"))
}

/** She has shown up and wants an answer. Everything locks at [lockAt] until you answer. */
@Serializable
data class Summons(
    val id: Long,
    val createdAt: Long,
    val lockAt: Long,
    val question: Question,
    val wrongAnswers: Int = 0,
)
