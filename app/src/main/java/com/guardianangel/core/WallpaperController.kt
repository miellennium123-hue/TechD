package com.guardianangel.core

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.guardianangel.data.BackgroundRef
import com.guardianangel.data.Backgrounds
import com.guardianangel.data.BuiltInBackground
import com.guardianangel.data.Motif
import com.guardianangel.data.WallpaperMode
import kotlin.concurrent.thread

/**
 * Sets her wallpaper. "Set and lock" re-applies it whenever it changes
 * (a true lock would need Device Owner mode). Since round 59 she cycles through her
 * backgrounds every few minutes: her built-in designs, bundled images and yours.
 */
object WallpaperController {
    @Volatile private var applying = false
    private var lastCheck = 0L

    fun applyIfWanted(context: Context) {
        val c = Guardian.config.value
        if (c.enabled && c.wallpaper.on) applyAsync(context)
    }

    /** Sets the current background, or the next one with [advance]. */
    fun applyAsync(context: Context, advance: Boolean = false) {
        if (applying) return
        applying = true
        thread(name = "wallpaper") {
            try {
                apply(context, advance)
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
        if (!c.enabled || !c.wallpaper.on) return
        if (c.wallpaper.cycle && Backgrounds.due(t, Guardian.state.value.wallpaperChangedAt, c.wallpaper.cycleMinutes)) {
            applyAsync(context, advance = true)
            return
        }
        if (c.wallpaper.mode != WallpaperMode.SET_AND_LOCK) return
        val id = runCatching {
            WallpaperManager.getInstance(context).getWallpaperId(WallpaperManager.FLAG_SYSTEM)
        }.getOrNull() ?: return
        if (id != Guardian.state.value.wallpaperId) applyAsync(context)
    }

    /** Everything she cycles through right now. */
    fun pool(context: Context): List<BackgroundRef> {
        val w = Guardian.config.value.wallpaper
        return Backgrounds.pool(
            w.builtIns,
            w.hiddenBuiltIns,
            AssetImages.wallpaperNames(context),
            CustomBackgrounds.list(context).map { it.name },
        )
    }

    private fun apply(context: Context, advance: Boolean) {
        runCatching {
            val wm = WallpaperManager.getInstance(context)
            val metrics = context.resources.displayMetrics
            val w = metrics.widthPixels.coerceAtLeast(720)
            val h = metrics.heightPixels.coerceAtLeast(1280)
            val pool = pool(context)
            val last = Guardian.state.value.wallpaperIndex
            val index = if (advance || last < 0) Backgrounds.next(last, pool.size) else Backgrounds.current(last, pool.size)
            val bitmap = render(context, pool[index], w, h) ?: draw(Backgrounds.BUILT_IN.first(), w, h)
            wm.setBitmap(bitmap, null, true, WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK)
            val id = wm.getWallpaperId(WallpaperManager.FLAG_SYSTEM)
            Guardian.state.update { it.copy(wallpaperId = id, wallpaperIndex = index, wallpaperChangedAt = Guardian.now()) }
        }
    }

    private fun render(context: Context, ref: BackgroundRef, w: Int, h: Int): Bitmap? = when (ref) {
        is BackgroundRef.BuiltIn -> draw(Backgrounds.builtIn(ref.id), w, h)
        is BackgroundRef.Asset -> AssetImages.wallpaper(context, ref.name)
        is BackgroundRef.Custom -> ProofFiles.load(CustomBackgrounds.file(context, ref.fileName), maxOf(w, h))
    }

    /** One of her built-in designs: a gradient, a soft glow, her emblem, and her words. Also used for previews. */
    fun draw(bg: BuiltInBackground, w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val p = bg.palette
        val accent = p.accent.toInt()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        paint.shader = LinearGradient(
            0f, 0f, 0f, h.toFloat(),
            intArrayOf(p.top.toInt(), p.mid.toInt(), p.bottom.toInt()),
            null,
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)

        // A soft glow behind her emblem.
        val cx = w / 2f
        val cy = h * 0.32f
        paint.shader = RadialGradient(cx, cy, w * 0.55f, (accent and 0x00FFFFFF) or 0x33000000, 0x00000000, Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, w * 0.55f, paint)
        paint.shader = null

        drawMotif(canvas, bg.motif, cx, cy, w * 0.26f, accent, p.mid.toInt())

        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD_ITALIC)
        paint.color = 0xF2FFFFFF.toInt()
        fitText(paint, bg.headline, w * 0.84f, w * 0.12f)
        val headY = h * 0.6f
        canvas.drawText(bg.headline, cx, headY, paint)

        paint.color = accent
        paint.strokeWidth = w * 0.004f
        canvas.drawLine(cx - w * 0.12f, headY + w * 0.05f, cx + w * 0.12f, headY + w * 0.05f, paint)

        paint.typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC)
        paint.color = 0xB3FFFFFF.toInt()
        fitText(paint, bg.subline, w * 0.84f, w * 0.045f)
        canvas.drawText(bg.subline, cx, headY + w * 0.13f, paint)
        return bmp
    }

    /** Largest text size up to [max] that fits [width]. */
    private fun fitText(paint: Paint, text: String, width: Float, max: Float) {
        paint.textSize = max
        while (paint.measureText(text) > width && paint.textSize > max * 0.4f) paint.textSize *= 0.94f
    }

    internal fun drawMotif(canvas: Canvas, motif: Motif, cx: Float, cy: Float, s: Float, accent: Int, cutout: Int) {
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = s * 0.07f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = accent
        }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = accent }
        val hole = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = cutout }
        when (motif) {
            Motif.HALO -> canvas.drawOval(RectF(cx - s * 0.8f, cy - s * 0.22f, cx + s * 0.8f, cy + s * 0.22f), stroke)
            Motif.LOCK -> {
                canvas.drawArc(RectF(cx - s * 0.32f, cy - s * 0.75f, cx + s * 0.32f, cy - s * 0.05f), 180f, 180f, false, stroke)
                canvas.drawLine(cx - s * 0.32f, cy - s * 0.4f, cx - s * 0.32f, cy, stroke)
                canvas.drawLine(cx + s * 0.32f, cy - s * 0.4f, cx + s * 0.32f, cy, stroke)
                canvas.drawRoundRect(RectF(cx - s * 0.5f, cy - s * 0.05f, cx + s * 0.5f, cy + s * 0.65f), s * 0.1f, s * 0.1f, fill)
                canvas.drawCircle(cx, cy + s * 0.22f, s * 0.09f, hole)
                canvas.drawRect(cx - s * 0.035f, cy + s * 0.22f, cx + s * 0.035f, cy + s * 0.45f, hole)
            }
            Motif.CROWN -> {
                val path = Path().apply {
                    moveTo(cx - s * 0.7f, cy + s * 0.4f)
                    lineTo(cx - s * 0.75f, cy - s * 0.35f)
                    lineTo(cx - s * 0.35f, cy + s * 0.05f)
                    lineTo(cx, cy - s * 0.55f)
                    lineTo(cx + s * 0.35f, cy + s * 0.05f)
                    lineTo(cx + s * 0.75f, cy - s * 0.35f)
                    lineTo(cx + s * 0.7f, cy + s * 0.4f)
                    close()
                }
                canvas.drawPath(path, fill)
                listOf(-0.75f, 0f, 0.75f).forEach { dx ->
                    canvas.drawCircle(cx + s * dx, cy + (if (dx == 0f) -s * 0.62f else -s * 0.42f), s * 0.08f, fill)
                }
                canvas.drawRect(cx - s * 0.7f, cy + s * 0.48f, cx + s * 0.7f, cy + s * 0.6f, fill)
            }
            Motif.HEART -> {
                val path = Path().apply {
                    moveTo(cx, cy + s * 0.6f)
                    cubicTo(cx - s * 1.1f, cy - s * 0.1f, cx - s * 0.55f, cy - s * 0.85f, cx, cy - s * 0.3f)
                    cubicTo(cx + s * 0.55f, cy - s * 0.85f, cx + s * 1.1f, cy - s * 0.1f, cx, cy + s * 0.6f)
                    close()
                }
                canvas.drawPath(path, stroke)
                // A tiny padlock on her heart.
                canvas.drawArc(RectF(cx - s * 0.12f, cy - s * 0.12f, cx + s * 0.12f, cy + s * 0.12f), 180f, 180f, false, stroke)
                canvas.drawRoundRect(RectF(cx - s * 0.19f, cy, cx + s * 0.19f, cy + s * 0.27f), s * 0.04f, s * 0.04f, fill)
            }
            Motif.KEY -> {
                canvas.drawCircle(cx - s * 0.45f, cy, s * 0.25f, stroke)
                canvas.drawLine(cx - s * 0.2f, cy, cx + s * 0.75f, cy, stroke)
                canvas.drawLine(cx + s * 0.5f, cy, cx + s * 0.5f, cy + s * 0.22f, stroke)
                canvas.drawLine(cx + s * 0.7f, cy, cx + s * 0.7f, cy + s * 0.3f, stroke)
            }
            Motif.COLLAR -> {
                canvas.drawOval(RectF(cx - s * 0.75f, cy - s * 0.45f, cx + s * 0.75f, cy + s * 0.35f), stroke.apply { strokeWidth = s * 0.14f })
                stroke.strokeWidth = s * 0.06f
                canvas.drawCircle(cx, cy + s * 0.5f, s * 0.13f, stroke)
                canvas.drawCircle(cx, cy + s * 0.78f, s * 0.13f, fill)
            }
        }
    }
}
