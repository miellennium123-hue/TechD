package com.guardianangel.core

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import com.guardianangel.data.MarkCorner
import com.guardianangel.data.Motif

/**
 * Her mark (round 60): her collar badge in a corner over every app, and a dark tint over the
 * screen during her blocks. Both are Accessibility overlays (no extra permission) drawn by her
 * watch, and neither ever takes a touch or focus, so everything under them, Quit for now
 * included, works as usual. Rules decide when they show (Rules.markShown, Rules.markTinted).
 */
class MarkOverlay(private val service: AccessibilityService) {
    private val windows = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val density = service.resources.displayMetrics.density
    private var badge: View? = null
    private var badgeCorner: MarkCorner? = null
    private var tint: View? = null

    /** [ownApp]: one of her own screens is in front, so neither shows over it. */
    fun update(ownApp: Boolean) {
        val config = Guardian.config.value
        val showBadge = Rules.markShown(config, ownApp)
        val showTint = Rules.markTinted(config, Guardian.state.value, Guardian.now(), Guardian.minuteOfDay(), ownApp)
        // The tint goes under the badge, so it's added first.
        if (showTint) showTint() else hideTint()
        if (showBadge) showBadge(config.mark.corner) else hideBadge()
    }

    fun hide() {
        hideTint()
        hideBadge()
    }

    private fun params(width: Int, height: Int) = WindowManager.LayoutParams(
        width,
        height,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    )

    private fun showTint() {
        if (tint != null) return
        val view = View(service).apply { setBackgroundColor(TINT_COLOR) }
        if (runCatching { windows.addView(view, params(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)) }.isSuccess) {
            tint = view
            // Re-add the badge so it stays on top of the tint.
            badge?.let { badgeCorner?.let { corner -> hideBadge(); showBadge(corner) } }
        }
    }

    private fun hideTint() {
        tint?.let { runCatching { windows.removeView(it) } }
        tint = null
    }

    private fun showBadge(corner: MarkCorner) {
        if (badge != null && badgeCorner == corner) return
        hideBadge()
        val size = (BADGE_DP * density).toInt()
        val p = params(size, size).apply {
            gravity = (if (corner.top) Gravity.TOP else Gravity.BOTTOM) or (if (corner.left) Gravity.START else Gravity.END)
            x = (EDGE_DP * density).toInt()
            // Clear of the status bar at the top and the navigation bar at the bottom.
            y = ((if (corner.top) TOP_DP else BOTTOM_DP) * density).toInt()
        }
        val view = CollarBadge(service)
        if (runCatching { windows.addView(view, p) }.isSuccess) {
            badge = view
            badgeCorner = corner
        }
    }

    private fun hideBadge() {
        badge?.let { runCatching { windows.removeView(it) } }
        badge = null
        badgeCorner = null
    }

    /** A black disc with a gold ring and her gold collar, slightly see-through. */
    private class CollarBadge(context: Context) : View(context) {
        private val disc = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xD9000000.toInt() }
        private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = GOLD }

        override fun onDraw(canvas: Canvas) {
            val r = width / 2f
            ring.strokeWidth = r * 0.08f
            canvas.drawCircle(r, r, r * 0.95f, disc)
            canvas.drawCircle(r, r, r * 0.9f, ring)
            WallpaperController.drawMotif(canvas, Motif.COLLAR, r, r * 0.82f, r * 0.62f, GOLD, 0xFF000000.toInt())
        }
    }

    companion object {
        private const val BADGE_DP = 30
        private const val EDGE_DP = 6
        private const val TOP_DP = 36
        private const val BOTTOM_DP = 64
        private const val GOLD = 0xFFD4AF37.toInt()

        /** About 45% black: dark enough to feel, light enough to read (and to dial) through. */
        private const val TINT_COLOR = 0x73000000
    }
}
