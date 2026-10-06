package com.guardianangel.core

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.PowerManager
import com.guardianangel.ui.SiteActivity

/** The Android side of Open sites: which browser, whether the phone is free, and opening the page. */
object SiteOpener {
    const val CHROME = "com.android.chrome"

    /** Chrome when it's installed and enabled, otherwise the default browser. Null when there's no browser at all. */
    fun browserPackage(context: Context): String? {
        val pm = context.packageManager
        val chrome = runCatching { pm.getApplicationInfo(CHROME, 0).enabled }.getOrDefault(false)
        if (chrome) return CHROME
        val view = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")).addCategory(Intent.CATEGORY_BROWSABLE)
        // With no default set this resolves to the system "Open with" chooser, which isn't a browser.
        val default = pm.resolveActivity(view, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
        if (default != null && default != "android") return default
        @Suppress("DEPRECATION")
        return pm.queryIntentActivities(view, 0).firstOrNull()?.activityInfo?.packageName
    }

    /** Never on the lock screen, with the screen off, or during a call (round 18). */
    fun phoneReady(context: Context): Boolean {
        val interactive = context.getSystemService(PowerManager::class.java)?.isInteractive ?: false
        val locked = context.getSystemService(KeyguardManager::class.java)?.isKeyguardLocked ?: true
        val mode = context.getSystemService(AudioManager::class.java)?.mode ?: AudioManager.MODE_NORMAL
        val inCall = mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_IN_COMMUNICATION ||
            mode == AudioManager.MODE_RINGTONE
        return interactive && !locked && !inCall
    }

    /** Her full-screen warning with the 10 second countdown. */
    fun showWarning(context: Context) {
        runCatching { context.startActivity(SiteActivity.intent(context)) }
    }

    /** Opens the page in her browser. False if the browser couldn't take it. */
    fun open(context: Context, url: String, browser: String): Boolean = runCatching {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .setPackage(browser)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }.isSuccess

    /** Back to the page you were on (the browser's open tab), without loading it again. */
    fun bringBack(context: Context, browser: String) {
        runCatching {
            val intent = context.packageManager.getLaunchIntentForPackage(browser) ?: return
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
