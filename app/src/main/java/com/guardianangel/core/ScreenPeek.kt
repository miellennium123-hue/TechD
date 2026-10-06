package com.guardianangel.core

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.os.Build
import android.os.PowerManager
import android.view.Display
import android.view.accessibility.AccessibilityWindowInfo
import androidx.annotation.RequiresApi
import java.io.File
import kotlin.concurrent.thread

/**
 * She peeks (round 60): her watch captures the screen (Accessibility screenshots, Android 11 or
 * later, no extra permission) into her private gallery, then she comments. Rules in core/Peek.
 * Screenshots stay in private app storage and never leave the phone.
 */
class ScreenPeek(private val service: AccessibilityService) {
    @Volatile private var busy = false

    /** Called from her watch's ticks. Does nothing unless a peek is due and she may look. */
    fun maybePeek(pkg: String?, launcher: Boolean, browsers: Set<String>, protectedPackages: Set<String>) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || busy || !Guardian.peekDue()) return
        val power = service.getSystemService(Context.POWER_SERVICE) as PowerManager
        val keyguard = service.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        val keyboardUp = runCatching { service.windows.any { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD } }.getOrDefault(true)
        val may = Peek.mayLook(
            pkg,
            Guardian.config.value,
            ownApp = pkg == service.packageName,
            screenOn = power.isInteractive,
            locked = keyguard.isKeyguardLocked,
            keyboardUp = keyboardUp,
            launcher = launcher,
            protectedPackages = protectedPackages,
        )
        if (!may || pkg == null) return
        val info = runCatching { service.packageManager.getApplicationInfo(pkg, 0) }.getOrNull()
        val app = if (launcher) "your home screen" else info?.loadLabel(service.packageManager)?.toString() ?: pkg
        val kind = Peek.kind(pkg, info?.category ?: ApplicationInfo.CATEGORY_UNDEFINED, launcher, pkg in browsers)
        busy = true
        Guardian.peekStarted()
        capture(app, kind)
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun capture(app: String, kind: PeekKind) {
        service.takeScreenshot(
            Display.DEFAULT_DISPLAY,
            service.mainExecutor,
            object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                    thread(name = "peek") {
                        val name = runCatching { save(result) }.getOrNull()
                        service.mainExecutor.execute {
                            busy = false
                            if (name != null) Guardian.peeked(name, app, kind)
                        }
                    }
                }

                override fun onFailure(errorCode: Int) {
                    busy = false
                }
            },
        )
    }

    /** Copies the screenshot out of graphics memory, downscales it and saves a JPEG. */
    @RequiresApi(Build.VERSION_CODES.R)
    private fun save(result: AccessibilityService.ScreenshotResult): String? {
        val buffer = result.hardwareBuffer
        val bitmap = try {
            Bitmap.wrapHardwareBuffer(buffer, result.colorSpace)?.copy(Bitmap.Config.ARGB_8888, false)
        } finally {
            buffer.close()
        } ?: return null
        val scale = MAX_DIM.toFloat() / maxOf(bitmap.width, bitmap.height)
        val out = if (scale < 1f) Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true) else bitmap
        val dir = File(service.filesDir, ProofFiles.DIR).apply { mkdirs() }
        val file = File(dir, "peek_${System.currentTimeMillis()}.jpg")
        file.outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 80, it) }
        return file.name
    }

    companion object {
        private const val MAX_DIM = 1280

        /** Accessibility screenshots arrived in Android 11. */
        fun supported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
    }
}
