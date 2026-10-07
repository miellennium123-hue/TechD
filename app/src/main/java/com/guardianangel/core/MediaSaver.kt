package com.guardianangel.core

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import java.io.File

/**
 * Save to phone (round 81): copies a proof photo, peek or session clip out of her private storage
 * into your phone's own gallery, only when you tap Save. Photos go to Pictures/Guardian Angel,
 * videos to Movies/Guardian Angel. The copy is yours: other apps and backups can see it, and
 * deleting it in her app doesn't delete the copy.
 */
object MediaSaver {
    const val FOLDER = "Guardian Angel"

    /** Android 9 and older need the storage permission to write to the shared gallery. */
    fun needsPermission(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q

    /** Where saved copies show up, for her message. */
    fun where(files: List<File>): String = when {
        files.all { ProofFiles.isVideo(it) } -> "Movies/$FOLDER"
        files.none { ProofFiles.isVideo(it) } -> "Pictures/$FOLDER"
        else -> "Pictures/$FOLDER and Movies/$FOLDER"
    }

    /** Saves each file. Returns how many were saved. Call off the main thread. */
    fun saveAll(context: Context, files: List<File>): Int = files.count { save(context, it) }

    fun save(context: Context, file: File): Boolean = runCatching {
        if (!file.exists() || file.length() == 0L) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) saveScoped(context, file) else saveLegacy(context, file)
    }.getOrDefault(false)

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveScoped(context: Context, file: File): Boolean {
        val video = ProofFiles.isVideo(file)
        val collection = if (video) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
            put(MediaStore.MediaColumns.MIME_TYPE, if (video) "video/mp4" else "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${if (video) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES}/$FOLDER")
            put(MediaStore.MediaColumns.DATE_TAKEN, file.lastModified())
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(collection, values) ?: return false
        val copied = runCatching {
            resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } } != null
        }.getOrDefault(false)
        if (!copied) {
            resolver.delete(uri, null, null)
            return false
        }
        resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        return true
    }

    @Suppress("DEPRECATION")
    private fun saveLegacy(context: Context, file: File): Boolean {
        val base = Environment.getExternalStoragePublicDirectory(
            if (ProofFiles.isVideo(file)) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES,
        )
        val dir = File(base, FOLDER).apply { mkdirs() }
        val target = File(dir, file.name)
        file.copyTo(target, overwrite = true)
        MediaScannerConnection.scanFile(context, arrayOf(target.path), null, null)
        return true
    }
}
