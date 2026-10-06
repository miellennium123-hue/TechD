package com.guardianangel.core

import com.guardianangel.data.RatingRecord
import com.guardianangel.data.RatingTaste
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * What she can really tell from a rating photo. [confidence] is null when the detector couldn't run.
 * [coverage] is the share of the frame her box covers, [offCentre] 0 (centred) to 1 (in a corner).
 */
data class PhotoSignals(
    val confidence: Float?,
    val coverage: Double,
    val offCentre: Double,
    val sharpness: Double,
    val brightness: Double,
)

enum class RatingTier { LOW, MID, GOOD, TOP }

/**
 * Rate me (round 27). Pure Kotlin, tested in RatingTest.
 *
 * She can't judge the body itself, so the score is built from what's real: your measurements
 * against published population data, and what the phone can measure in the photo. Mood never
 * changes the score; it only picks her sweet or strict verdict line.
 */
object Rating {
    // Veale et al. 2015, "Am I normal?", BJU International 115:978-986. Erect length n = 692, erect girth n = 381.
    const val LENGTH_MEAN_CM = 13.12
    const val LENGTH_SD_CM = 1.66
    const val GIRTH_MEAN_CM = 11.66
    const val GIRTH_SD_CM = 1.10

    const val CM_PER_INCH = 2.54
    const val MIN_CM = 2.0
    const val MAX_CM = 40.0

    /** Weights: size is most of the score, presentation the rest. Length counts more than girth. */
    const val SIZE_WEIGHT = 0.8
    const val LENGTH_SHARE = 0.6

    /** After this many rejected photos she offers to rate on your word alone (presentation 0). */
    const val MAX_FAILED_PHOTOS = 3

    /** Keeps this many ratings in the history. */
    const val HISTORY = 30

    fun toCm(value: Double, inches: Boolean): Double = if (inches) value * CM_PER_INCH else value

    fun fromCm(cm: Double, inches: Boolean): Double = if (inches) cm / CM_PER_INCH else cm

    /** Reads a typed number; accepts a decimal comma. */
    fun parse(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }

    fun isValid(cm: Double?): Boolean = cm != null && cm in MIN_CM..MAX_CM

    /** Where a measurement sits among the study's men, 1 to 99. */
    fun percentile(value: Double, mean: Double, sd: Double): Int =
        (normalCdf((value - mean) / sd) * 100).roundToInt().coerceIn(1, 99)

    fun lengthPercentile(cm: Double): Int = percentile(cm, LENGTH_MEAN_CM, LENGTH_SD_CM)

    fun girthPercentile(cm: Double): Int = percentile(cm, GIRTH_MEAN_CM, GIRTH_SD_CM)

    /** 0 to 100, in the direction of her taste. */
    fun sizeScore(lengthPercentile: Int, girthPercentile: Int, taste: RatingTaste): Double {
        val combined = LENGTH_SHARE * lengthPercentile + (1 - LENGTH_SHARE) * girthPercentile
        return if (taste == RatingTaste.SMALLER) 100 - combined else combined
    }

    /**
     * Turns a detector box ([inputSize] square model pixels, image padded bottom-right to a square)
     * into frame coverage and centring for a [width] x [height] photo.
     */
    fun signals(detection: Detection?, width: Int, height: Int, stats: PhotoStats, inputSize: Int = 320): PhotoSignals {
        if (detection == null || width <= 0 || height <= 0) {
            return PhotoSignals(null, 0.0, 0.0, stats.sharpness, stats.brightness)
        }
        val scale = maxOf(width, height).toDouble() / inputSize
        val boxW = detection.w * scale
        val boxH = detection.h * scale
        val coverage = (boxW * boxH / (width.toDouble() * height)).coerceIn(0.0, 1.0)
        val dx = detection.cx * scale / width - 0.5
        val dy = detection.cy * scale / height - 0.5
        val offCentre = (sqrt(dx * dx + dy * dy) / sqrt(0.5)).coerceIn(0.0, 1.0)
        return PhotoSignals(detection.score, coverage, offCentre, stats.sharpness, stats.brightness)
    }

    /** True when the detector ran and didn't see it. A detector that couldn't run never blocks you. */
    fun unseen(signals: PhotoSignals): Boolean = signals.confidence != null && signals.confidence < ExplicitScore.THRESHOLD

    /** How well you presented it, 0 to 100: how clearly she sees it, framing, centring, focus and light. */
    fun presentation(s: PhotoSignals): Int {
        val seen = s.confidence?.let { ramp(it.toDouble(), ExplicitScore.THRESHOLD.toDouble(), 0.8) } ?: 0.5
        val framing = if (s.confidence == null) 0.5 else coverageScore(s.coverage)
        val centred = if (s.confidence == null) 0.5 else 1 - s.offCentre
        val focus = ramp(ln(s.sharpness.coerceAtLeast(1.0)), ln(PhotoQuality.BLUR_VARIANCE), ln(150.0))
        val light = when {
            s.brightness < 80 -> ramp(s.brightness, PhotoQuality.DARK_MEAN, 80.0)
            s.brightness <= 180 -> 1.0
            else -> 1 - ramp(s.brightness, 180.0, 250.0)
        }
        val total = 0.30 * seen + 0.25 * framing + 0.15 * centred + 0.15 * focus + 0.15 * light
        return (total * 100).roundToInt().coerceIn(0, 100)
    }

    /** Best when it fills 15% to 60% of the frame. Too small loses everything, too close loses half. */
    fun coverageScore(coverage: Double): Double = when {
        coverage < 0.15 -> ramp(coverage, 0.02, 0.15)
        coverage <= 0.60 -> 1.0
        else -> 1 - 0.5 * ramp(coverage, 0.60, 1.0)
    }

    /** Her score out of 10. [presentation] null means she rated without seeing it, which counts as 0. */
    fun score(sizeScore: Double, presentation: Int?): Int {
        val total = SIZE_WEIGHT * sizeScore + (1 - SIZE_WEIGHT) * (presentation ?: 0)
        return (total / 10).roundToInt().coerceIn(1, 10)
    }

    fun tier(score: Int): RatingTier = when {
        score <= 3 -> RatingTier.LOW
        score <= 5 -> RatingTier.MID
        score <= 7 -> RatingTier.GOOD
        else -> RatingTier.TOP
    }

    /** The whole rating. [signals] null means she rated on your word alone. */
    fun rate(lengthCm: Double, girthCm: Double, taste: RatingTaste, signals: PhotoSignals?, at: Long): RatingRecord {
        val length = lengthPercentile(lengthCm)
        val girth = girthPercentile(girthCm)
        val presentation = signals?.let { presentation(it) }
        return RatingRecord(
            at = at,
            lengthCm = lengthCm,
            girthCm = girthCm,
            lengthPercentile = length,
            girthPercentile = girth,
            presentation = presentation ?: -1,
            score = score(sizeScore(length, girth, taste), presentation),
        )
    }

    /** 1st, 2nd, 3rd, 11th, 22nd... */
    fun ordinal(n: Int): String {
        val suffix = if (n % 100 in 11..13) "th" else when (n % 10) {
            1 -> "st"
            2 -> "nd"
            3 -> "rd"
            else -> "th"
        }
        return "$n$suffix"
    }

    fun normalCdf(z: Double): Double = 0.5 * (1 + erf(z / sqrt(2.0)))

    /** Abramowitz and Stegun 7.1.26, accurate to about 1.5e-7. */
    fun erf(x: Double): Double {
        val t = 1 / (1 + 0.3275911 * abs(x))
        val poly = t * (0.254829592 + t * (-0.284496736 + t * (1.421413741 + t * (-1.453152027 + t * 1.061405429))))
        val y = 1 - poly * exp(-x * x)
        return if (x >= 0) y else -y
    }

    /** 0 at [from], 1 at [to], straight line between. */
    private fun ramp(value: Double, from: Double, to: Double): Double = ((value - from) / (to - from)).coerceIn(0.0, 1.0)
}
