package com.guardianangel.service

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import com.guardianangel.core.Decision
import com.guardianangel.core.Guardian
import com.guardianangel.core.ProtectedApps
import com.guardianangel.core.WallpaperController
import com.guardianangel.data.AppLists
import com.guardianangel.ui.BlockActivity

/**
 * Watches which app is in front and sends her block screen when it's off limits.
 * It only reads the package name of the foreground window, never screen content.
 */
class GuardianAccessibilityService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private var currentPackage: String? = null
    private var protectedPackages: Set<String> = emptySet()
    private var launchers: Set<String> = emptySet()
    private val activityCache = HashMap<String, Boolean>()
    private var lastBlockedPackage: String? = null
    private var lastBlockAt = 0L

    /** Re-checks every 30 seconds so expiring grants and starting bedtimes take effect mid-app. */
    private val tick = object : Runnable {
        override fun run() {
            refreshPackages()
            currentPackage?.let { evaluate(it) }
            WallpaperController.enforce(this@GuardianAccessibilityService)
            handler.postDelayed(this, 30_000)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Guardian.init(this)
        refreshPackages()
        handler.post(tick)
    }

    private fun refreshPackages() {
        protectedPackages = ProtectedApps.discover(this)
        launchers = ProtectedApps.launchers(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (isForegroundApp(pkg, event.className)) currentPackage = pkg
        evaluate(pkg)
        WallpaperController.enforce(this)
    }

    /**
     * The keyboard, the notification shade, volume and power dialogs also send window events.
     * Those come from system packages and aren't activities, so they mustn't replace the app
     * you're actually in, or the 30 second re-check would stop watching it.
     */
    private fun isForegroundApp(pkg: String, className: CharSequence?): Boolean {
        if (pkg in launchers) return true
        val system = pkg in protectedPackages || pkg in AppLists.NEVER_BLOCK
        return !system || isActivity(pkg, className)
    }

    private fun isActivity(pkg: String, className: CharSequence?): Boolean {
        val cls = className?.toString() ?: return false
        return activityCache.getOrPut("$pkg/$cls") {
            @Suppress("DEPRECATION")
            runCatching { packageManager.getActivityInfo(ComponentName(pkg, cls), 0) }.isSuccess
        }
    }

    private fun evaluate(pkg: String) {
        if (pkg == packageName) return
        val decision = Guardian.decide(pkg, protectedPackages)
        if (decision !is Decision.Block) return
        val t = Guardian.now()
        if (pkg == lastBlockedPackage && t - lastBlockAt < 1_500) return
        lastBlockedPackage = pkg
        lastBlockAt = t
        startActivity(BlockActivity.intent(this, pkg))
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        super.onDestroy()
    }
}
