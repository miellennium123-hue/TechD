package com.guardianangel.core

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.os.PowerManager
import android.view.Display
import androidx.annotation.RequiresApi
import kotlin.concurrent.thread
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Porn block (round 71): her watch screenshots the screen in browsers and social apps (Accessibility
 * screenshots, Android 11 or later) and runs the NudeNet detector on it, all on the phone. The
 * screenshot is only held in memory and never saved. Rules in core/PornBlock.
 */
class PornScanner(private val service: AccessibilityService) {
    @Volatile private var busy = false
    private var blindScans = 0

    /** What one scan saw: the best explicit score (null if the detector couldn't run), and a black screen. */
    private class Scan(val score: Float?, val blind: Boolean)

    /** Called every few seconds from her watch. [onCaught] runs on the main thread after she catches you. */
    fun maybeScan(pkg: String?, launcher: Boolean, browsers: Set<String>, protectedPackages: Set<String>, onCaught: () -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || busy) return
        val info = pkg?.let { runCatching { service.packageManager.getApplicationInfo(it, 0) }.getOrNull() }
        val browser = pkg != null && pkg in browsers
        val watched = pkg != null && !launcher &&
            PornBlock.watches(pkg, info?.category ?: ApplicationInfo.CATEGORY_UNDEFINED, browser)
        val power = service.getSystemService(Context.POWER_SERVICE) as PowerManager
        val keyguard = service.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        val may = PornBlock.mayScan(
            pkg,
            Guardian.config.value,
            Guardian.state.value,
            Guardian.now(),
            ownApp = pkg == service.packageName,
            screenOn = power.isInteractive,
            screenLocked = keyguard.isKeyguardLocked,
            watched = watched,
            protectedPackages = protectedPackages,
        )
        if (!may || pkg == null) {
            blindScans = 0
            return
        }
        val app = info?.loadLabel(service.packageManager)?.toString() ?: pkg
        busy = true
        capture { scan ->
            busy = false
            if (scan == null) return@capture
            val config = Guardian.config.value
            blindScans = PornBlock.nextBlind(blindScans, scan.blind, browser, config)
            val porn = scan.score != null && PornBlock.porn(scan.score)
            if (!porn && !PornBlock.hiding(blindScans)) return@capture
            blindScans = 0
            Guardian.caught(app, hiding = !porn)
            // She may have been switched off while she looked.
            if (PornBlock.locked(Guardian.config.value, Guardian.state.value, Guardian.now())) onCaught()
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun capture(done: (Scan?) -> Unit) {
        service.takeScreenshot(
            Display.DEFAULT_DISPLAY,
            service.mainExecutor,
            object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                    thread(name = "porn-block") {
                        val scan = runCatching { analyze(result) }.getOrNull()
                        service.mainExecutor.execute { done(scan) }
                    }
                }

                override fun onFailure(errorCode: Int) = done(null)
            },
        )
    }

    /** Copies the screenshot out of graphics memory, shrinks it, checks for a black screen, then each tile. */
    @RequiresApi(Build.VERSION_CODES.R)
    private fun analyze(result: AccessibilityService.ScreenshotResult): Scan? {
        val buffer = result.hardwareBuffer
        val full = try {
            Bitmap.wrapHardwareBuffer(buffer, result.colorSpace)?.copy(Bitmap.Config.ARGB_8888, false)
        } finally {
            buffer.close()
        } ?: return null
        // The detector works at 320 pixels, so a 640 pixel wide copy loses nothing it would see.
        val scale = SHORT_SIDE.toFloat() / minOf(full.width, full.height)
        val screen = if (scale < 1f) {
            Bitmap.createScaledBitmap(full, (full.width * scale).roundToInt().coerceAtLeast(1), (full.height * scale).roundToInt().coerceAtLeast(1), true)
                .also { full.recycle() }
        } else {
            full
        }
        try {
            // A private tab: nothing to run the detector on.
            if (blind(screen)) return Scan(score = 0f, blind = true)
            var best: Float? = null
            for (tile in PornBlock.tiles(screen.width, screen.height)) {
                val piece = Bitmap.createBitmap(screen, tile.x, tile.y, tile.size, tile.size)
                val output = ExplicitDetector.output(service, piece)
                if (piece !== screen) piece.recycle()
                val score = output?.let { ExplicitScore.maxScore(it) } ?: continue
                best = max(best ?: 0f, score)
                if (PornBlock.porn(score)) break
            }
            return Scan(best, blind = false)
        } finally {
            screen.recycle()
        }
    }

    /** A small grayscale copy for the black screen check. */
    private fun blind(bitmap: Bitmap): Boolean {
        val w = 48
        val h = (w * bitmap.height.toFloat() / bitmap.width).roundToInt().coerceAtLeast(4)
        val small = Bitmap.createScaledBitmap(bitmap, w, h, false)
        val pixels = IntArray(w * h)
        small.getPixels(pixels, 0, w, 0, 0, w, h)
        if (small !== bitmap) small.recycle()
        val gray = IntArray(pixels.size) { i ->
            val c = pixels[i]
            (0.299 * Color.red(c) + 0.587 * Color.green(c) + 0.114 * Color.blue(c)).toInt()
        }
        return PornBlock.blind(gray, w, h)
    }

    companion object {
        private const val SHORT_SIDE = 640

        /** Accessibility screenshots arrived in Android 11. */
        fun supported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
    }
}
