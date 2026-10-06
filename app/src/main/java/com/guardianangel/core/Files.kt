package com.guardianangel.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.File

/** Custom art: drop angel.png into assets/ and any images into assets/wallpapers/. */
object AssetImages {
    private val EXTENSIONS = listOf(".png", ".jpg", ".jpeg", ".webp")

    private fun isImage(name: String) = EXTENSIONS.any { name.endsWith(it, ignoreCase = true) }

    fun angel(context: Context): Bitmap? =
        context.assets.list("")
            ?.firstOrNull { it.startsWith("angel.") && isImage(it) }
            ?.let { decode(context, it) }

    fun randomWallpaper(context: Context): Bitmap? =
        context.assets.list("wallpapers")
            ?.filter(::isImage)
            ?.randomOrNull()
            ?.let { decode(context, "wallpapers/$it") }

    private fun decode(context: Context, path: String): Bitmap? =
        runCatching { context.assets.open(path).use { BitmapFactory.decodeStream(it) } }.getOrNull()
}

/** Proof photos live in private app storage only: no gallery, no backups. */
object ProofFiles {
    const val DIR = "proof"

    fun list(context: Context): List<File> =
        File(context.filesDir, DIR).listFiles()
            ?.filter { it.extension == "jpg" }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()

    fun deleteAll(context: Context) {
        File(context.filesDir, DIR).listFiles()?.forEach { it.delete() }
    }

    /** Decodes a downscaled, correctly rotated bitmap. */
    fun load(file: File, maxDim: Int): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxDim && bounds.outHeight / (sample * 2) >= maxDim) sample *= 2
        val bitmap = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return null
        val rotation = ExifInterface(file.path).rotationDegrees
        if (rotation == 0) {
            bitmap
        } else {
            val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }
    }.getOrNull()
}
