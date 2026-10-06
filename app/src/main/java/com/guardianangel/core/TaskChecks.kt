package com.guardianangel.core

import kotlin.math.sqrt
import kotlin.random.Random

/** Line writing: her sentences, line counts per difficulty, and the typing check. Pure Kotlin. */
object Lines {
    val LABELS = listOf("Easy", "Medium", "Hard")
    val COUNTS = listOf(5, 15, 30)

    /** Longer sentences at higher difficulty. */
    val SENTENCES: List<List<String>> = listOf(
        listOf(
            "I belong to my angel.",
            "I obey my angel.",
            "Good pets do as they're told.",
        ),
        listOf(
            "I will obey my angel without question.",
            "My phone belongs to my angel, and so do I.",
            "I am a good pet and I do as I am told.",
        ),
        listOf(
            "I am my angel's pet, and I will obey every rule she gives me.",
            "I will not touch my phone without my angel's permission ever again.",
            "My angel decides what I do, and I am grateful that she does.",
        ),
    )

    /** Random difficulty, never below [floor] (failures raise the floor). */
    fun pickDifficulty(floor: Int, random: Random): Int = random.nextInt(floor.coerceIn(0, 2), 3)

    fun pickSentence(difficulty: Int, random: Random): String =
        SENTENCES[difficulty.coerceIn(0, 2)].let { it[random.nextInt(it.size)] }

    enum class Input {
        /** Still on track. */
        OK,

        /** The line is finished. */
        LINE_DONE,

        /** Wrong character: restart from line 1. */
        TYPO,

        /** More than one character arrived at once (paste or suggestion): ignored. */
        PASTE,
    }

    /** Judges one change to the text field. Case and smart quotes don't count as typos. */
    fun check(previous: String, next: String, sentence: String): Input {
        if (next.length - previous.length > 1) return Input.PASTE
        val typed = normalize(next)
        val target = normalize(sentence)
        return when {
            typed == target -> Input.LINE_DONE
            target.startsWith(typed) -> Input.OK
            else -> Input.TYPO
        }
    }

    private fun normalize(text: String): String =
        text.lowercase().replace('’', '\'').replace('‘', '\'')
}

/**
 * Decides whether the phone moved during a stillness task, from accelerometer readings.
 * The first readings set the resting position. Moving means the smoothed reading drifts
 * more than [threshold] m/s² from it for longer than [sustainMs], so a single bump doesn't fail you.
 * 2.5 m/s² is roughly tilting the phone 15 degrees, or a real shift of position.
 */
class StillnessJudge(
    private val threshold: Float = 2.5f,
    private val sustainMs: Long = 500,
    private val calibrationSamples: Int = 10,
) {
    private var count = 0
    private val baseline = FloatArray(3)
    private val smooth = FloatArray(3)
    private var driftSince = -1L

    var moved = false
        private set

    /** Feed one reading. Returns true once the phone has moved (and stays true). */
    fun add(x: Float, y: Float, z: Float, timeMs: Long): Boolean {
        if (moved) return true
        val v = floatArrayOf(x, y, z)
        if (count < calibrationSamples) {
            for (i in 0..2) baseline[i] = (baseline[i] * count + v[i]) / (count + 1)
            v.copyInto(smooth)
            count++
            return false
        }
        for (i in 0..2) smooth[i] = smooth[i] * 0.8f + v[i] * 0.2f
        val dx = smooth[0] - baseline[0]
        val dy = smooth[1] - baseline[1]
        val dz = smooth[2] - baseline[2]
        if (sqrt(dx * dx + dy * dy + dz * dz) > threshold) {
            if (driftSince < 0) driftSince = timeMs
            if (timeMs - driftSince >= sustainMs) moved = true
        } else {
            driftSince = -1
        }
        return moved
    }
}
