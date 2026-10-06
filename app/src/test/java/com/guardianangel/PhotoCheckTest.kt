package com.guardianangel

import com.guardianangel.core.ExplicitScore
import com.guardianangel.core.PhotoIssue
import com.guardianangel.core.PhotoQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class PhotoCheckTest {
    private val w = 64
    private val h = 64

    private fun image(f: (x: Int, y: Int) -> Int) = IntArray(w * h) { i -> f(i % w, i / w).coerceIn(0, 255) }

    @Test
    fun darkPhotoRejected() {
        assertEquals(PhotoIssue.TOO_DARK, PhotoQuality.check(image { _, _ -> 5 }, w, h))
    }

    @Test
    fun blankPhotoRejected() {
        assertEquals(PhotoIssue.BLANK, PhotoQuality.check(image { _, _ -> 128 }, w, h))
    }

    @Test
    fun smoothBlurRejected() {
        // A wide gradient has contrast but no edges at all.
        assertEquals(PhotoIssue.BLURRY, PhotoQuality.check(image { x, _ -> 40 + x * 2 }, w, h))
    }

    @Test
    fun detailedPhotoPasses() {
        val random = Random(1)
        assertNull(PhotoQuality.check(image { x, y -> 60 + (x + y) % 2 * 60 + random.nextInt(40) }, w, h))
    }

    @Test
    fun tooSmallIsUnreadable() {
        assertEquals(PhotoIssue.UNREADABLE, PhotoQuality.check(IntArray(4), 2, 2))
    }

    private fun output(cls: Int, score: Float): Array<FloatArray> =
        Array(22) { row -> FloatArray(2100).also { if (row == 4 + cls) it[1234] = score } }

    @Test
    fun explicitClassAboveThresholdCounts() {
        assertTrue(ExplicitScore.isExplicit(output(14, 0.8f))) // MALE_GENITALIA_EXPOSED
        assertTrue(ExplicitScore.isExplicit(output(4, 0.31f))) // FEMALE_GENITALIA_EXPOSED
    }

    @Test
    fun nonExplicitClassesAndLowScoresDoNot() {
        assertFalse(ExplicitScore.isExplicit(output(14, 0.1f)))
        assertFalse(ExplicitScore.isExplicit(output(12, 0.99f))) // FACE_MALE
        assertFalse(ExplicitScore.isExplicit(output(0, 0.99f))) // FEMALE_GENITALIA_COVERED
    }
}
