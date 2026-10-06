package com.guardianangel.core

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.guardianangel.data.WallpaperMode
import kotlin.concurrent.thread

/**
 * Sets her wallpaper. "Set and lock" re-applies it whenever it changes
 * (a true lock would need Device Owner mode).
 */
object WallpaperController {
    @Volatile private var applying = false
    private var lastCheck = 0L

    fun applyIfWanted(context: Context) {
        val c = Guardian.config.value
        if (c.enabled && c.wallpaper.on) applyAsync(context)
    }

    fun applyAsync(context: Context) {
        if (applying) return
        applying = true
        thread(name = "wallpaper") {
            try {
                apply(context)
            } finally {
                applying = false
            }
        }
    }

    /** Called often from the accessibility service; checks at most every 20 seconds. */
    fun enforce(context: Context) {
        val t = Guardian.now()
        if (t - lastCheck < 20_000) return
        lastCheck = t
        val c = Guardian.config.value
        if (!c.enabled || !c.wallpaper.on || c.wallpaper.mode != WallpaperMode.SET_AND_LOCK) return
        val id = runCatching {
            WallpaperManager.getInstance(context).getWallpaperId(WallpaperManager.FLAG_SYSTEM)
        }.getOrNull() ?: return
        if (id != Guardian.state.value.wallpaperId) applyAsync(context)
    }

    private fun apply(context: Context) {
        runCatching {
            val wm = WallpaperManager.getInstance(context)
            val metrics = context.resources.displayMetrics
            val bitmap = AssetImages.randomWallpaper(context)
                ?: placeholder(metrics.widthPixels.coerceAtLeast(720), metrics.heightPixels.coerceAtLeast(1280))
            wm.setBitmap(bitmap, null, true, WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK)
            val id = wm.getWallpaperId(WallpaperManager.FLAG_SYSTEM)
            Guardian.state.update { it.copy(wallpaperId = id) }
        }
    }

    /** Placeholder until real art is dropped into assets/wallpapers. */
    private fun placeholder(w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(
            0f, 0f, 0f, h.toFloat(),
            intArrayOf(0xFF140B1A.toInt(), 0xFF4A1442.toInt(), 0xFF8E2459.toInt()),
            null,
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        paint.shader = null

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = w * 0.018f
        paint.color = 0xFFF6D58E.toInt()
        val top = h * 0.24f
        canvas.drawOval(RectF(w * 0.3f, top, w * 0.7f, top + w * 0.11f), paint)

        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD_ITALIC)
        paint.color = 0xE6FFFFFF.toInt()
        paint.textSize = w * 0.075f
        canvas.drawText("Be good, pet.", w / 2f, h * 0.6f, paint)
        paint.typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC)
        paint.color = 0x99FFFFFF.toInt()
        paint.textSize = w * 0.038f
        canvas.drawText("She is watching over you", w / 2f, h * 0.6f + w * 0.09f, paint)
        return bmp
    }
}
