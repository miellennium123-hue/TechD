package com.guardianangel.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.net.Uri
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

    /** Images bundled in assets/wallpapers, in name order. Part of her cycle (round 59). */
    fun wallpaperNames(context: Context): List<String> =
        context.assets.list("wallpapers")?.filter(::isImage)?.sorted().orEmpty()

    fun wallpaper(context: Context, name: String): Bitmap? = decode(context, "wallpapers/$name")

    private fun decode(context: Context, path: String): Bitmap? =
        runCatching { context.assets.open(path).use { BitmapFactory.decodeStream(it) } }.getOrNull()
}

/**
 * Proof photos and her peeks live in private app storage only: no gallery, no backups. Since round 77
 * session videos have their own place (SessionClips), so Photos only shows photos.
 */
object ProofFiles {
    const val DIR = "proof"

    fun list(context: Context): List<File> =
        File(context.filesDir, DIR).listFiles()
            ?.filter { it.extension == "jpg" }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()

    fun deleteAll(context: Context) {
        File(context.filesDir, DIR).listFiles()?.filter { it.extension == "jpg" }?.forEach { it.delete() }
    }

    fun isVideo(file: File): Boolean = file.extension == "mp4"

    /** A still for the gallery: the photo itself, or a frame from a clip. */
    fun thumbnail(file: File, maxDim: Int): Bitmap? =
        if (!isVideo(file)) {
            load(file, maxDim)
        } else {
            runCatching {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(file.path)
                    retriever.getFrameAtTime(0)?.let { frame ->
                        val scale = maxDim.toFloat() / maxOf(frame.width, frame.height)
                        if (scale >= 1f) frame else Bitmap.createScaledBitmap(frame, (frame.width * scale).toInt(), (frame.height * scale).toInt(), true)
                    }
                } finally {
                    retriever.release()
                }
            }.getOrNull()
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

/**
 * Her videos (round 77): ruins, edges and CBT she filmed during sessions, with sound. Private app
 * storage only, apart from proof photos. She keeps the newest Clips.KEEP. Rules in core/Clips.
 */
object SessionClips {
    fun dir(context: Context): File = File(context.filesDir, Clips.DIR).apply { mkdirs() }

    /** A new file for her to film into. */
    fun newFile(context: Context, kind: ClipKind): File = File(dir(context), Clips.name(kind, System.currentTimeMillis()))

    fun file(context: Context, name: String): File = File(dir(context), name)

    /** Her clips, newest first. */
    fun infos(context: Context): List<ClipInfo> =
        Clips.sorted(dir(context).listFiles()?.mapNotNull { f -> Clips.parse(f.name)?.takeIf { f.length() > 0 } }.orEmpty())

    fun list(context: Context): List<File> = infos(context).map { file(context, it.name) }

    /** Round 79: only her videos (no edge photos), for the clips she makes you watch. */
    fun videos(context: Context): List<ClipInfo> = infos(context).filter { it.kind.video }

    /** After a new clip: deletes the oldest past her limit. */
    fun prune(context: Context) {
        Clips.overflow(infos(context)).forEach { file(context, it.name).delete() }
    }

    fun deleteAll(context: Context) {
        dir(context).listFiles()?.forEach { it.delete() }
    }

    /** Ruin clips from before round 77 sat with the proof photos. Moves them here, once. */
    fun migrate(context: Context) {
        File(context.filesDir, ProofFiles.DIR).listFiles()?.forEach { f ->
            val name = Clips.fromLegacy(f.name) ?: return@forEach
            val target = file(context, name)
            if (!f.renameTo(target)) runCatching { f.copyTo(target, overwrite = true); f.delete() }
        }
    }
}

/** Your own backgrounds (round 59): copied into private app storage, downscaled and turned upright. */
object CustomBackgrounds {
    const val DIR = "backgrounds"
    private const val MAX_DIM = 2560

    fun list(context: Context): List<File> =
        File(context.filesDir, DIR).listFiles()
            ?.filter { it.extension == "jpg" }
            ?.sortedBy { it.name }
            .orEmpty()

    fun file(context: Context, name: String): File = File(File(context.filesDir, DIR), name)

    /** Copies one picked image in. Returns false if it couldn't be read. Call off the main thread. */
    fun add(context: Context, uri: Uri): Boolean = runCatching {
        val temp = File.createTempFile("pick", ".img", context.cacheDir)
        try {
            context.contentResolver.openInputStream(uri)?.use { input -> temp.outputStream().use { input.copyTo(it) } }
                ?: return false
            val bitmap = ProofFiles.load(temp, MAX_DIM) ?: return false
            val dir = File(context.filesDir, DIR).apply { mkdirs() }
            val out = File(dir, "bg_${System.currentTimeMillis()}_${(1000..9999).random()}.jpg")
            out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            true
        } finally {
            temp.delete()
        }
    }.getOrDefault(false)
}
