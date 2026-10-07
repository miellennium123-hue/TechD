package com.guardianangel.core

import com.guardianangel.data.AppLists
import com.guardianangel.data.CatchRecord
import com.guardianangel.data.GuardianConfig
import com.guardianangel.data.GuardianState
import com.guardianangel.data.PornBlockSettings
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** How she caught you. Each has its own lines. */
enum class CatchKind(val line: Line) {
    PORN(Line.CAUGHT_PORN),
    HIDING(Line.CAUGHT_HIDING),
    /** Round 74: you opened one of your adult apps. */
    ADULT_APP(Line.CAUGHT_APP),
}

/** One square piece of the screen, in screenshot pixels. The detector looks at each one on its own. */
data class Tile(val x: Int, val y: Int, val size: Int)

/**
 * Porn block (round 73): every few seconds in a browser or social app she screenshots the screen and
 * runs the on-device nudity detector on it. Porn locks the phone. Pure Kotlin, tested in PornBlockTest.
 * The screenshot itself is in core/PornScanner. Nothing she scans is saved or leaves the phone.
 */
object PornBlock {
    /** How often she can check, in seconds (round 74). 5 is the default. */
    val SCAN_STEPS: List<Int> = listOf(3, 5, 10, 15, 30, 60)

    /** Higher than photo proof's 0.3, so an ordinary screen rarely sets her off. */
    const val THRESHOLD = 0.45f

    /** A browser screen she can't see for about this long is a private tab. */
    const val BLIND_SECONDS = 15

    /** She keeps this many catches. */
    const val KEEP = 50

    /** Lock length steps, in minutes. */
    const val MIN_LOCK_MINUTES = 15
    const val MAX_LOCK_MINUTES = 24 * 60

    /** Social apps on top of the lockout list. Apps Android tags as social are watched too. */
    val SOCIAL_APPS: Set<String> = AppLists.SOCIAL_MEDIA + setOf(
        "com.tumblr",
        "xyz.blueskyweb.app",
        "com.pinterest",
        "com.instagram.barcelona",
        "org.joinmastodon.android",
    )

    /** Checked unless you take it off her list: browsers, social apps, and anything Android tags as social. [category] is the app's Android category, or -1. */
    fun watchedByDefault(pkg: String, category: Int, browser: Boolean): Boolean =
        browser || pkg in SOCIAL_APPS || category == Peek.CATEGORY_SOCIAL

    /** Round 74: her list as you edited it. Apps you took off are skipped, apps you added are checked. */
    fun watches(pkg: String, category: Int, browser: Boolean, settings: PornBlockSettings): Boolean =
        pkg !in settings.unwatched && (pkg in settings.watched || watchedByDefault(pkg, category, browser))

    /** Ticks or unticks an app in her list, keeping only what differs from her defaults. */
    fun setWatched(settings: PornBlockSettings, pkg: String, byDefault: Boolean, on: Boolean): PornBlockSettings =
        settings.copy(
            watched = if (on && !byDefault) settings.watched + pkg else settings.watched - pkg,
            unwatched = if (!on && byDefault) settings.unwatched + pkg else settings.unwatched - pkg,
        )

    /** How often she checks: your setting, or the nearest step if it's ever off the list. */
    fun scanSeconds(settings: PornBlockSettings): Int = SCAN_STEPS.minBy { abs(it - settings.scanSeconds) }

    /** The next step up (less often) or down (more often). */
    fun stepScan(current: Int, up: Boolean): Int {
        val i = SCAN_STEPS.indexOf(SCAN_STEPS.minBy { abs(it - current) })
        return SCAN_STEPS[(if (up) i + 1 else i - 1).coerceIn(0, SCAN_STEPS.lastIndex)]
    }

    /**
     * Round 74: porn is off limits right now. She and Porn block are on, and with your hours on, it's
     * inside them. Outside your hours she doesn't look and adult apps open. A lock already running stays.
     */
    fun active(config: GuardianConfig, minuteOfDay: Int): Boolean {
        val p = config.pornBlock
        return config.enabled && p.on && (!p.hoursOn || Rules.isInWindow(p.startMinute, p.endMinute, minuteOfDay))
    }

    /**
     * Round 74: opening one of your adult apps is a catch, while porn is off limits and no lock is
     * running yet. Always-allowed doesn't protect them; only the phone and system screens are never caught.
     */
    fun adultApp(
        pkg: String,
        config: GuardianConfig,
        state: GuardianState,
        now: Long,
        minuteOfDay: Int,
        ownApp: Boolean,
        protectedPackages: Set<String>,
    ): Boolean =
        !ownApp && pkg in config.pornBlock.adultApps && pkg !in protectedPackages && pkg !in AppLists.NEVER_BLOCK &&
            active(config, minuteOfDay) && !locked(config, state, now)

    /** Her lock after a catch is running. Switching Porn block off ends it. */
    fun locked(config: GuardianConfig, state: GuardianState, now: Long): Boolean =
        config.enabled && config.pornBlock.on && state.caughtUntil > now

    /**
     * Whether she scans right now: porn is off limits ([active]), no lock running yet, the screen is on and
     * unlocked, and a watched app is in front. Never her own screens, the phone, system screens or your
     * Always-allowed apps. Unlike her peeks, she still looks with the keyboard up: nothing is saved.
     */
    fun mayScan(
        pkg: String?,
        config: GuardianConfig,
        state: GuardianState,
        now: Long,
        minuteOfDay: Int,
        ownApp: Boolean,
        screenOn: Boolean,
        screenLocked: Boolean,
        watched: Boolean,
        protectedPackages: Set<String>,
    ): Boolean {
        if (pkg == null || ownApp || !screenOn || screenLocked || !watched) return false
        if (!active(config, minuteOfDay) || locked(config, state, now)) return false
        return !Rules.isExempt(pkg, config, protectedPackages)
    }

    /**
     * Squares along the screen's long side, overlapping, so pictures in a feed reach the detector big
     * enough. A tall phone screen gives 3.
     */
    fun tiles(width: Int, height: Int): List<Tile> {
        if (width <= 0 || height <= 0) return emptyList()
        val side = min(width, height)
        val long = max(width, height)
        val count = ceil(long.toDouble() / side).toInt().coerceAtLeast(1)
        return (0 until count).map { i ->
            val offset = if (count == 1) 0 else ((long - side).toLong() * i / (count - 1)).toInt()
            if (height >= width) Tile(0, offset, side) else Tile(offset, 0, side)
        }
    }

    /** The detector's best explicit score on any tile reached her line. */
    fun porn(score: Float): Boolean = score >= THRESHOLD

    /**
     * A screen she can't see: the middle of it (no status or navigation bar) is plain black. That's
     * what a private tab looks like to her, since browsers block screenshots there. [gray] is 0 to 255.
     */
    fun blind(gray: IntArray, width: Int, height: Int): Boolean {
        if (width < 1 || height < 4 || gray.size < width * height) return false
        val top = height * 15 / 100
        val bottom = height * 85 / 100
        var sum = 0.0
        var sumSq = 0.0
        var n = 0
        for (y in top until bottom) {
            for (x in 0 until width) {
                val v = gray[y * width + x].toDouble()
                sum += v
                sumSq += v * v
                n++
            }
        }
        if (n == 0) return false
        val mean = sum / n
        val stddev = sqrt((sumSq / n - mean * mean).coerceAtLeast(0.0))
        return mean < 6 && stddev < 3
    }

    /** Blind scans in a row. Only browsers count, and only with Private tabs on. */
    fun nextBlind(count: Int, blind: Boolean, browser: Boolean, config: GuardianConfig): Int =
        if (blind && browser && config.pornBlock.privateTabs) count + 1 else 0

    /** Blind scans in a row that make about [BLIND_SECONDS] at your check rate. At least 2, so one black frame never counts. */
    fun blindScansNeeded(scanSeconds: Int): Int = ceil(BLIND_SECONDS.toDouble() / scanSeconds.coerceAtLeast(1)).toInt().coerceAtLeast(2)

    fun hiding(blindScans: Int, scanSeconds: Int): Boolean = blindScans >= blindScansNeeded(scanSeconds)

    /** The phone stays locked [minutes] from now, or longer if a lock is already running. */
    fun lockUntil(caughtUntil: Long, now: Long, minutes: Int): Long = max(caughtUntil, now) + minutes * MINUTE

    /** Lock length steps: 15 minutes up to 2 hours, then an hour at a time up to a day. */
    fun stepLock(current: Int, up: Boolean): Int {
        val probe = if (up) current else current - 1
        val step = if (probe < 120) 15 else 60
        return (if (up) current + step else current - step).coerceIn(MIN_LOCK_MINUTES, MAX_LOCK_MINUTES)
    }

    /** Adds a catch, newest last, keeping the newest [KEEP]. */
    fun add(catches: List<CatchRecord>, record: CatchRecord): List<CatchRecord> = (catches + record).takeLast(KEEP)
}
