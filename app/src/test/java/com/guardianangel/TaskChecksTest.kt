package com.guardianangel

import com.guardianangel.core.Lines
import com.guardianangel.core.StillnessJudge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class TaskChecksTest {
    private val sentence = "I obey my angel."

    @Test
    fun typingOnTrack() {
        assertEquals(Lines.Input.OK, Lines.check("", "i", sentence))
        assertEquals(Lines.Input.OK, Lines.check("I ob", "I obe", sentence))
        // Backspace is fine.
        assertEquals(Lines.Input.OK, Lines.check("I obe", "I ob", sentence))
    }

    @Test
    fun finishedLine() {
        assertEquals(Lines.Input.LINE_DONE, Lines.check("I obey my angel", "I obey my angel.", sentence))
        assertEquals(Lines.Input.LINE_DONE, Lines.check("i obey my angel", "i obey my angel.", sentence))
    }

    @Test
    fun typoRestarts() {
        assertEquals(Lines.Input.TYPO, Lines.check("I ob", "I obr", sentence))
        assertEquals(Lines.Input.TYPO, Lines.check("I obey my angel.", "I obey my angel..", sentence))
    }

    @Test
    fun pasteIsBlocked() {
        assertEquals(Lines.Input.PASTE, Lines.check("", sentence, sentence))
        assertEquals(Lines.Input.PASTE, Lines.check("I ", "I obey", sentence))
    }

    @Test
    fun smartApostrophesCount() {
        val s = "Good pets do as they're told."
        assertEquals(Lines.Input.OK, Lines.check("Good pets do as they", "Good pets do as they’", s))
    }

    @Test
    fun difficultyNeverBelowFloorAndSentencesGetLonger() {
        val random = Random(1)
        repeat(200) { assertTrue(Lines.pickDifficulty(2, random) == 2) }
        repeat(200) { assertTrue(Lines.pickDifficulty(1, random) in 1..2) }
        assertEquals(listOf(5, 15, 30), Lines.COUNTS)
        val avg = Lines.SENTENCES.map { list -> list.map { it.length }.average() }
        assertTrue(avg[0] < avg[1] && avg[1] < avg[2])
    }

    @Test
    fun stillPhonePasses() {
        val judge = StillnessJudge()
        val random = Random(3)
        for (t in 0 until 3000 step 20) {
            // Small hand tremor around resting gravity.
            val moved = judge.add(0.2f + random.nextFloat() * 0.4f, 0.1f, 9.8f + random.nextFloat() * 0.4f - 0.2f, t.toLong())
            assertFalse(moved)
        }
    }

    @Test
    fun singleBumpPasses() {
        val judge = StillnessJudge()
        for (t in 0 until 1000 step 20) judge.add(0f, 0f, 9.8f, t.toLong())
        judge.add(15f, 0f, 9.8f, 1000)
        for (t in 1020 until 3000 step 20) judge.add(0f, 0f, 9.8f, t.toLong())
        assertFalse(judge.moved)
    }

    @Test
    fun tiltingFails() {
        val judge = StillnessJudge()
        for (t in 0 until 1000 step 20) judge.add(0f, 0f, 9.8f, t.toLong())
        // Phone tipped about 30 degrees and held there.
        for (t in 1000 until 2500 step 20) judge.add(4.9f, 0f, 8.5f, t.toLong())
        assertTrue(judge.moved)
    }
}
