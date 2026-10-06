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
    /** Choice questions need at least this many wrong answers, or 3 strikes can never happen. */
    const val MIN_WRONG = 3

    val DEFAULTS: List<Question> = listOf(
        Question(
            "Who do you belong to?", QuestionKind.CHOICE, "My angel",
            listOf("Myself", "Nobody", "Whoever's nearest", "My phone"),
        ),
        Question(
            "What are you?", QuestionKind.CHOICE, "Your pet",
            listOf("In charge", "Free to do what I want", "Your equal", "Too good for rules"),
        ),
        Question(
            "Who decides when you're unlocked?", QuestionKind.CHOICE, "You do",
            listOf("I do", "The timer", "Nobody", "Whoever asks nicely"),
        ),
        Question(
            "What does a good pet do when I call?", QuestionKind.CHOICE, "Comes straight away",
            listOf("Makes you wait", "Ignores you", "Finishes scrolling first", "Asks why"),
        ),
        Question(
            "Who gets the last word?", QuestionKind.CHOICE, "You, always",
            listOf("Me", "Whoever's loudest", "We take turns", "Nobody"),
        ),
        Question("Tell me what you are.", QuestionKind.PHRASE, "I am your good pet."),
        Question("Tell me you came when called.", QuestionKind.PHRASE, "I came as soon as you called."),
        Question("Thank me properly.", QuestionKind.PHRASE, "Thank you for watching over me."),
    )

    /** Used if the list is ever emptied. */
    val FALLBACK = DEFAULTS.first()

    /**
     * Brings saved starter questions up to date (round 16 added more wrong answers).
     * Only touches a question that still matches a starter and whose wrong answers are all
     * starter ones, so anything you wrote or changed yourself is left alone.
     */
    fun upgrade(saved: List<Question>): List<Question> = saved.map { q ->
        val newer = DEFAULTS.firstOrNull { it.text == q.text && it.kind == q.kind && it.answer == q.answer }
        if (newer != null && newer.wrong.size > q.wrong.size && newer.wrong.containsAll(q.wrong)) newer else q
    }
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
