package com.guardianangel.core

import com.guardianangel.data.MotionSensitivity
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * One camera frame compared with the one before: [energy] is how much changed (0 until 1), and
 * [x], [y] is where (0 until 1, NaN when nothing changed enough). Frames themselves are never kept.
 */
data class MotionSample(val timeMs: Long, val energy: Float, val x: Float, val y: Float)

/**
 * What she sees right now. [live]: frames are arriving (if not, she never judges).
 * [level]: recent movement. [moving]: above her threshold. [rate]: your rhythm per minute, or null.
 */
data class MotionReading(val live: Boolean, val level: Float, val moving: Boolean, val rate: Int?)

/** Camera motion checks for guided sessions (9.9 part 2). Pure Kotlin, tested in MotionTest. */
object Motion {
    const val GRID_W = 48
    const val GRID_H = 36

    /** A cell counts as changed when its brightness moved this much (0 until 1). */
    const val CELL_CHANGE = 0.06f

    /** Rhythm is judged over this much time. */
    const val RATE_WINDOW_MS = 8_000L
    const val MIN_RATE = 20
    const val MAX_RATE = 220

    /** Within this share of her beat counts as on beat. */
    const val BEAT_TOLERANCE = 0.3

    /** Time to find her beat after a new command before she judges it. */
    const val BEAT_GRACE_MS = 5_000L

    /** Off beat (or stopped) this long in a row and she catches you. */
    const val OFF_BEAT_MS = 8_000L

    /** Time to get your hands off after "stop". */
    const val STILL_GRACE_MS = 2_000L

    /** Moving this long in a row during a hands-off command and she catches you. */
    const val MOVED_MS = 1_500L

    private const val RESAMPLE_HZ = 30

    /** Shrinks one RGBA frame to a small brightness grid (0 until 1). Each cell averages 4 x 4 points, which evens out camera noise. */
    fun grid(buffer: ByteBuffer, width: Int, height: Int, rowStride: Int, pixelStride: Int): FloatArray {
        val out = FloatArray(GRID_W * GRID_H)
        for (gy in 0 until GRID_H) {
            for (gx in 0 until GRID_W) {
                var sum = 0
                for (sy in 0 until 4) {
                    val py = ((gy * 4 + sy) * 2 + 1) * height / (GRID_H * 8)
                    for (sx in 0 until 4) {
                        val px = ((gx * 4 + sx) * 2 + 1) * width / (GRID_W * 8)
                        val i = py * rowStride + px * pixelStride
                        val r = buffer.get(i).toInt() and 0xFF
                        val g = buffer.get(i + 1).toInt() and 0xFF
                        val b = buffer.get(i + 2).toInt() and 0xFF
                        sum += r * 299 + g * 587 + b * 114
                    }
                }
                out[gy * GRID_W + gx] = sum / (16 * 255_000f)
            }
        }
        return out
    }

    /**
     * Compares two grids. Each frame's average brightness is taken out first, so the camera
     * adjusting its exposure doesn't count as movement.
     */
    fun compare(previous: FloatArray, next: FloatArray, timeMs: Long): MotionSample {
        val ma = previous.average().toFloat()
        val mb = next.average().toFloat()
        var total = 0f
        var weight = 0f
        var sx = 0f
        var sy = 0f
        for (i in next.indices) {
            val d = abs((next[i] - mb) - (previous[i] - ma))
            total += d
            if (d > CELL_CHANGE) {
                weight += d
                sx += d * ((i % GRID_W) + 0.5f) / GRID_W
                sy += d * ((i / GRID_W) + 0.5f) / GRID_H
            }
        }
        val energy = total / next.size
        return if (weight > 0f) MotionSample(timeMs, energy, sx / weight, sy / weight) else MotionSample(timeMs, energy, Float.NaN, Float.NaN)
    }

    /**
     * Your rhythm in strokes per minute, from where the movement is over time, or null if there's
     * no clear rhythm. Uses the axis that moves most, so it works with the phone either way round.
     */
    fun rate(samples: List<MotionSample>): Int? {
        if (samples.size < 20) return null
        val start = samples.first().timeMs
        val span = samples.last().timeMs - start
        if (span < 3_000) return null
        val n = (span * RESAMPLE_HZ / 1_000).toInt()
        val xs = resample(samples, start, n) { it.x } ?: return null
        val ys = resample(samples, start, n) { it.y } ?: return null
        val signal = if (variance(xs) >= variance(ys)) xs else ys
        if (sqrt(variance(signal)) < 0.015) return null
        val mean = signal.average()
        val s = DoubleArray(n) { signal[it] - mean }

        val minLag = (60.0 * RESAMPLE_HZ / MAX_RATE).toInt().coerceAtLeast(2)
        val maxLag = minOf((60.0 * RESAMPLE_HZ / MIN_RATE).toInt(), n / 2)
        if (maxLag <= minLag + 1) return null
        val r = DoubleArray(maxLag + 2) { lag -> if (lag in minLag - 1..maxLag + 1) correlation(s, lag) else 0.0 }
        var best = 0.0
        for (lag in minLag..maxLag) best = maxOf(best, r[lag])
        if (best < 0.3) return null
        // The first strong peak is the stroke; later peaks are whole multiples of it.
        for (lag in minLag..maxLag) {
            if (r[lag] >= 0.85 * best && r[lag] >= r[lag - 1] && r[lag] >= r[lag + 1]) {
                val a = r[lag - 1]
                val b = r[lag]
                val c = r[lag + 1]
                val denom = a - 2 * b + c
                val shift = if (denom != 0.0) (0.5 * (a - c) / denom).coerceIn(-0.5, 0.5) else 0.0
                return (60.0 * RESAMPLE_HZ / (lag + shift)).roundToInt()
            }
        }
        return null
    }

    fun onBeat(bpm: Int, rate: Int): Boolean = abs(rate - bpm) <= bpm * BEAT_TOLERANCE

    private fun resample(samples: List<MotionSample>, start: Long, n: Int, value: (MotionSample) -> Float): DoubleArray? {
        val known = samples.filter { !value(it).isNaN() }
        if (known.size < 10) return null
        val out = DoubleArray(n)
        var j = 0
        for (i in 0 until n) {
            val t = start + i * 1_000L / RESAMPLE_HZ
            while (j < known.size - 2 && known[j + 1].timeMs <= t) j++
            val a = known[j]
            val b = known[minOf(j + 1, known.size - 1)]
            out[i] = when {
                t <= a.timeMs || b.timeMs == a.timeMs -> value(a).toDouble()
                t >= b.timeMs -> value(b).toDouble()
                else -> value(a) + (value(b) - value(a)) * (t - a.timeMs).toDouble() / (b.timeMs - a.timeMs)
            }
        }
        return out
    }

    private fun variance(v: DoubleArray): Double {
        val m = v.average()
        return v.sumOf { (it - m) * (it - m) } / v.size
    }

    private fun correlation(s: DoubleArray, lag: Int): Double {
        if (lag <= 0 || lag >= s.size) return 0.0
        var num = 0.0
        var ea = 0.0
        var eb = 0.0
        for (i in 0 until s.size - lag) {
            num += s[i] * s[i + lag]
            ea += s[i] * s[i]
            eb += s[i + lag] * s[i + lag]
        }
        return if (ea > 0 && eb > 0) num / sqrt(ea * eb) else 0.0
    }
}

/**
 * Collects motion samples from the camera thread and answers what she sees from the screen.
 * Keeps only numbers, never frames.
 */
class MotionTracker(private val keepMs: Long = 12_000) {
    private var previous: FloatArray? = null
    private val samples = ArrayDeque<MotionSample>()
    private var lastX = Float.NaN
    private var lastY = Float.NaN

    @Synchronized
    fun add(grid: FloatArray, timeMs: Long) {
        val prev = previous
        previous = grid
        if (prev == null || prev.size != grid.size) return
        val raw = Motion.compare(prev, grid, timeMs)
        // Where nothing changed, the movement is still where it was last seen.
        val sample = if (raw.x.isNaN()) raw.copy(x = lastX, y = lastY) else raw
        lastX = sample.x
        lastY = sample.y
        samples.addLast(sample)
        while (samples.isNotEmpty() && timeMs - samples.first().timeMs > keepMs) samples.removeFirst()
    }

    /** Forget everything, for a new camera: the jump between cameras isn't movement. */
    @Synchronized
    fun reset() {
        previous = null
        samples.clear()
        lastX = Float.NaN
        lastY = Float.NaN
    }

    @Synchronized
    fun reading(sinceMs: Long, nowMs: Long, sensitivity: MotionSensitivity): MotionReading {
        val recent = samples.filter { nowMs - it.timeMs <= 600 }
        if (recent.size < 3) return MotionReading(live = false, level = 0f, moving = false, rate = null)
        val level = recent.map { it.energy }.sorted()[recent.size / 2]
        val moving = level > sensitivity.threshold
        val from = maxOf(sinceMs, nowMs - Motion.RATE_WINDOW_MS)
        val rate = if (moving) Motion.rate(samples.filter { it.timeMs >= from }) else null
        return MotionReading(live = true, level = level, moving = moving, rate = rate)
    }
}

/** What she checks during a command. */
enum class MotionCheck { NONE, BEAT, STILL }

/**
 * Judges one command from what she sees. [update] returns true when she catches you.
 * She never judges without live frames, and a gap in frames starts the count over.
 */
class MotionJudge(private val check: MotionCheck, private val bpm: Int, private val startMs: Long) {
    private var badSince = -1L

    fun update(nowMs: Long, reading: MotionReading): Boolean {
        val grace = if (check == MotionCheck.BEAT) Motion.BEAT_GRACE_MS else Motion.STILL_GRACE_MS
        if (check == MotionCheck.NONE || nowMs - startMs < grace || !reading.live) {
            badSince = -1
            return false
        }
        val bad = when (check) {
            // Stopped, or a clear rhythm that isn't hers. No clear rhythm while moving gets the benefit of the doubt.
            MotionCheck.BEAT -> !reading.moving || (reading.rate != null && !Motion.onBeat(bpm, reading.rate))
            MotionCheck.STILL -> reading.moving
            MotionCheck.NONE -> false
        }
        if (!bad) {
            badSince = -1
            return false
        }
        if (badSince < 0) badSince = nowMs
        val limit = if (check == MotionCheck.BEAT) Motion.OFF_BEAT_MS else Motion.MOVED_MS
        return nowMs - badSince >= limit
    }
}
