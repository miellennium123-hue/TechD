package com.guardianangel

import com.guardianangel.core.Detection
import com.guardianangel.core.ExplicitScore
import com.guardianangel.core.PhotoSignals
import com.guardianangel.core.PhotoStats
import com.guardianangel.core.Rating
import com.guardianangel.core.RatingTier
import com.guardianangel.data.RatingTaste
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RatingTest {
    private val sharp = PhotoStats(brightness = 128.0, sharpness = 400.0)
    private val ideal = PhotoSignals(confidence = 0.9f, coverage = 0.35, offCentre = 0.0, sharpness = 400.0, brightness = 128.0)

    @Test
    fun normalCdfMatchesTables() {
        assertEquals(0.0, Rating.erf(0.0), 1e-7)
        assertEquals(0.5, Rating.normalCdf(0.0), 1e-6)
        assertEquals(0.8413, Rating.normalCdf(1.0), 1e-4)
        assertEquals(0.975, Rating.normalCdf(1.96), 1e-4)
        assertEquals(0.0228, Rating.normalCdf(-2.0), 1e-4)
    }

    @Test
    fun percentilesAgainstTheStudy() {
        assertEquals(50, Rating.lengthPercentile(Rating.LENGTH_MEAN_CM))
        assertEquals(84, Rating.lengthPercentile(Rating.LENGTH_MEAN_CM + Rating.LENGTH_SD_CM))
        assertEquals(16, Rating.girthPercentile(Rating.GIRTH_MEAN_CM - Rating.GIRTH_SD_CM))
        // Never 0th or 100th.
        assertEquals(99, Rating.lengthPercentile(30.0))
        assertEquals(1, Rating.girthPercentile(3.0))
    }

    @Test
    fun tasteFlipsTheScoreNotThePercentiles() {
        assertEquals(80.0, Rating.sizeScore(80, 80, RatingTaste.BIGGER), 1e-9)
        assertEquals(20.0, Rating.sizeScore(80, 80, RatingTaste.SMALLER), 1e-9)
        // Length counts 60%, girth 40%.
        assertEquals(60.0, Rating.sizeScore(100, 0, RatingTaste.BIGGER), 1e-9)
    }

    @Test
    fun goodPhotoPresentsWell() {
        assertTrue(Rating.presentation(ideal) >= 95)
    }

    @Test
    fun badPhotoPresentsBadly() {
        val poor = PhotoSignals(confidence = 0.35f, coverage = 0.03, offCentre = 0.9, sharpness = 8.0, brightness = 30.0)
        assertTrue(Rating.presentation(poor) < 20)
    }

    @Test
    fun eachSignalCounts() {
        val base = Rating.presentation(ideal)
        assertTrue(Rating.presentation(ideal.copy(confidence = 0.4f)) < base)
        assertTrue(Rating.presentation(ideal.copy(coverage = 0.05)) < base)
        assertTrue(Rating.presentation(ideal.copy(offCentre = 0.8)) < base)
        assertTrue(Rating.presentation(ideal.copy(sharpness = 10.0)) < base)
        assertTrue(Rating.presentation(ideal.copy(brightness = 240.0)) < base)
    }

    @Test
    fun detectorThatCouldNotRunIsNeutral() {
        val unknown = ideal.copy(confidence = null)
        assertFalse(Rating.unseen(unknown))
        assertTrue(Rating.presentation(unknown) in 50..80)
    }

    @Test
    fun unseenOnlyWhenTheDetectorRanAndMissed() {
        assertTrue(Rating.unseen(ideal.copy(confidence = 0.1f)))
        assertFalse(Rating.unseen(ideal))
    }

    @Test
    fun coverageSweetSpot() {
        assertEquals(0.0, Rating.coverageScore(0.01), 1e-9)
        assertEquals(1.0, Rating.coverageScore(0.15), 1e-9)
        assertEquals(1.0, Rating.coverageScore(0.6), 1e-9)
        assertEquals(0.5, Rating.coverageScore(1.0), 1e-9)
    }

    @Test
    fun signalsFromASquarePhoto() {
        // 320 x 320 photo: model pixels are photo pixels. Box centred, half by half.
        val s = Rating.signals(Detection(0.9f, 160f, 160f, 160f, 160f), 320, 320, sharp)
        assertEquals(0.25, s.coverage, 1e-9)
        assertEquals(0.0, s.offCentre, 1e-9)
        assertEquals(0.9f, s.confidence)
    }

    @Test
    fun signalsUndoThePadding() {
        // 640 x 320 photo is padded to 640 x 640, so model pixels are 2 photo pixels.
        // Model centre (160, 80) is photo (320, 160): the middle of the photo.
        val s = Rating.signals(Detection(0.9f, 160f, 80f, 80f, 80f), 640, 320, sharp)
        assertEquals(0.0, s.offCentre, 1e-9)
        assertEquals(160.0 * 160.0 / (640.0 * 320.0), s.coverage, 1e-9)
        // A box in the corner is far off centre.
        assertEquals(1.0, Rating.signals(Detection(0.9f, 0f, 0f, 10f, 10f), 640, 320, sharp).offCentre, 1e-9)
    }

    @Test
    fun noDetectorMeansNoConfidence() {
        assertNull(Rating.signals(null, 320, 320, sharp).confidence)
    }

    @Test
    fun scoreOutOfTen() {
        assertEquals(10, Rating.score(100.0, 100))
        assertEquals(1, Rating.score(0.0, null))
        assertEquals(5, Rating.score(50.0, 50))
        // Seeing it matters: presentation is 20%.
        assertEquals(8, Rating.score(100.0, null))
    }

    @Test
    fun tiers() {
        assertEquals(RatingTier.LOW, Rating.tier(1))
        assertEquals(RatingTier.LOW, Rating.tier(3))
        assertEquals(RatingTier.MID, Rating.tier(4))
        assertEquals(RatingTier.MID, Rating.tier(5))
        assertEquals(RatingTier.GOOD, Rating.tier(6))
        assertEquals(RatingTier.GOOD, Rating.tier(7))
        assertEquals(RatingTier.TOP, Rating.tier(8))
        assertEquals(RatingTier.TOP, Rating.tier(10))
    }

    @Test
    fun averageMeasurementsWithAGoodPhotoScoreAboutAverage() {
        val r = Rating.rate(Rating.LENGTH_MEAN_CM, Rating.GIRTH_MEAN_CM, RatingTaste.BIGGER, ideal, at = 5)
        assertEquals(50, r.lengthPercentile)
        assertEquals(50, r.girthPercentile)
        assertEquals(6, r.score)
        assertEquals(5, r.at)
    }

    @Test
    fun ratedOnYourWordAlone() {
        val r = Rating.rate(16.0, 13.0, RatingTaste.BIGGER, signals = null, at = 0)
        assertEquals(-1, r.presentation)
        assertTrue(r.score <= 8)
    }

    @Test
    fun smallerTasteRewardsSmaller() {
        val small = Rating.rate(10.0, 10.0, RatingTaste.SMALLER, ideal, 0)
        val big = Rating.rate(17.0, 14.0, RatingTaste.SMALLER, ideal, 0)
        assertTrue(small.score > big.score)
    }

    @Test
    fun unitsAndInput() {
        assertEquals(15.24, Rating.toCm(6.0, inches = true), 1e-9)
        assertEquals(6.0, Rating.fromCm(15.24, inches = true), 1e-9)
        assertEquals(13.1, Rating.toCm(13.1, inches = false), 1e-9)
        assertEquals(13.5, Rating.parse(" 13,5 "))
        assertNull(Rating.parse("abc"))
        assertNull(Rating.parse("NaN"))
        assertTrue(Rating.isValid(13.0))
        assertFalse(Rating.isValid(1.0))
        assertFalse(Rating.isValid(null))
    }

    @Test
    fun ordinals() {
        assertEquals("1st", Rating.ordinal(1))
        assertEquals("2nd", Rating.ordinal(2))
        assertEquals("3rd", Rating.ordinal(3))
        assertEquals("11th", Rating.ordinal(11))
        assertEquals("12th", Rating.ordinal(12))
        assertEquals("22nd", Rating.ordinal(22))
        assertEquals("84th", Rating.ordinal(84))
    }

    @Test
    fun detectorPicksTheMostConfidentBox() {
        val anchors = 3
        val output = Array(4 + 18) { FloatArray(anchors) }
        output[0] = floatArrayOf(10f, 20f, 30f)
        output[1] = floatArrayOf(11f, 21f, 31f)
        output[2] = floatArrayOf(12f, 22f, 32f)
        output[3] = floatArrayOf(13f, 23f, 33f)
        output[4 + ExplicitScore.MALE_GENITALIA] = floatArrayOf(0.2f, 0.7f, 0.4f)
        val best = ExplicitScore.best(output, ExplicitScore.MALE_GENITALIA)
        assertNotNull(best)
        assertEquals(Detection(0.7f, 20f, 21f, 22f, 23f), best)
        assertNull(ExplicitScore.best(Array(4) { FloatArray(anchors) }, ExplicitScore.MALE_GENITALIA))
    }

    @Test
    fun photoStats() {
        val gray = IntArray(16) { if (it % 2 == 0) 0 else 200 }
        val stats = PhotoStats.of(gray, 4, 4)
        assertEquals(100.0, stats.brightness, 1e-9)
        assertTrue(stats.sharpness > 0)
    }
}
