package com.guardianangel.core

import kotlin.math.sqrt

enum class PhotoIssue(val message: String) {
    UNREADABLE("She couldn't read that photo."),
    TOO_DARK("Too dark. She can't see anything."),
    BLANK("There's nothing in that photo."),
    BLURRY("Too blurry. Hold still."),
    NOT_EXPLICIT("That's not what she asked for."),
}

/** Basic quality checks on a grayscale image (values 0 to 255). Thresholds are lenient on purpose. */
object PhotoQuality {
    const val DARK_MEAN = 20.0
    const val BLANK_STDDEV = 8.0
    const val BLUR_VARIANCE = 6.0

    fun check(gray: IntArray, width: Int, height: Int): PhotoIssue? {
        if (width < 3 || height < 3 || gray.size < width * height) return PhotoIssue.UNREADABLE
        val n = width * height
        var sum = 0.0
        var sumSq = 0.0
        for (i in 0 until n) {
            val v = gray[i].toDouble()
            sum += v
            sumSq += v * v
        }
        val mean = sum / n
        val stddev = sqrt((sumSq / n - mean * mean).coerceAtLeast(0.0))
        if (mean < DARK_MEAN) return PhotoIssue.TOO_DARK
        if (stddev < BLANK_STDDEV) return PhotoIssue.BLANK
        if (laplacianVariance(gray, width, height) < BLUR_VARIANCE) return PhotoIssue.BLURRY
        return null
    }

    /** Variance of the Laplacian: low means few edges, i.e. a blurry shot. */
    fun laplacianVariance(gray: IntArray, width: Int, height: Int): Double {
        var sum = 0.0
        var sumSq = 0.0
        var count = 0
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val i = y * width + x
                val lap = (4 * gray[i] - gray[i - 1] - gray[i + 1] - gray[i - width] - gray[i + width]).toDouble()
                sum += lap
                sumSq += lap * lap
                count++
            }
        }
        val mean = sum / count
        return sumSq / count - mean * mean
    }
}

/** Reads NudeNet output: 4 box rows, then one score row per class, each across every anchor. */
object ExplicitScore {
    const val THRESHOLD = 0.3f

    /** Classes that count as explicit, by NudeNet label index. */
    val EXPLICIT_CLASSES = mapOf(
        2 to "BUTTOCKS_EXPOSED",
        3 to "FEMALE_BREAST_EXPOSED",
        4 to "FEMALE_GENITALIA_EXPOSED",
        6 to "ANUS_EXPOSED",
        14 to "MALE_GENITALIA_EXPOSED",
    )

    fun maxScore(output: Array<FloatArray>): Float {
        var best = 0f
        for (cls in EXPLICIT_CLASSES.keys) {
            val row = output.getOrNull(4 + cls) ?: continue
            for (score in row) if (score > best) best = score
        }
        return best
    }

    fun isExplicit(output: Array<FloatArray>): Boolean = maxScore(output) >= THRESHOLD

    const val MALE_GENITALIA = 14

    /** The most confident box for [cls], in model input pixels (centre x, centre y, width, height), or null if none. */
    fun best(output: Array<FloatArray>, cls: Int): Detection? {
        val scores = output.getOrNull(4 + cls) ?: return null
        if (output.size < 4 || scores.isEmpty()) return null
        var bestIndex = 0
        for (i in scores.indices) if (scores[i] > scores[bestIndex]) bestIndex = i
        return Detection(
            score = scores[bestIndex],
            cx = output[0].getOrElse(bestIndex) { 0f },
            cy = output[1].getOrElse(bestIndex) { 0f },
            w = output[2].getOrElse(bestIndex) { 0f },
            h = output[3].getOrElse(bestIndex) { 0f },
        )
    }
}

/** One detector box, in model input pixels. */
data class Detection(val score: Float, val cx: Float, val cy: Float, val w: Float, val h: Float)

/** Brightness (mean, 0 to 255) and sharpness (variance of the Laplacian) of a grayscale image. */
data class PhotoStats(val brightness: Double, val sharpness: Double) {
    companion object {
        fun of(gray: IntArray, width: Int, height: Int): PhotoStats =
            PhotoStats(gray.take(width * height).average(), PhotoQuality.laplacianVariance(gray, width, height))
    }
}
