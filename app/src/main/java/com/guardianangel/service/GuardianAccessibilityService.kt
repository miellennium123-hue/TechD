package com.guardianangel.service

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat
import com.guardianangel.core.Decision
import com.guardianangel.core.Guardian
import com.guardianangel.core.Line
import com.guardianangel.core.LockGuard
import com.guardianangel.core.ProtectedApps
import com.guardianangel.core.Rules
import com.guardianangel.core.SiteOpener
import com.guardianangel.core.WallpaperController
import com.guardianangel.data.AppLists
import com.guardianangel.ui.BlockActivity
import com.guardianangel.ui.MainActivity

/**
 * Watches which app is in front and sends her block screen when it's off limits.
 * It reads the package name of the foreground window. With Lock guard on during a lock, it also
 * looks for her name on Settings and uninstall screens, and nowhere else.
 */
class GuardianAccessibilityService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private var currentPackage: String? = null
    private var protectedPackages: Set<String> = emptySet()
    private var launchers: Set<String> = emptySet()
    private val activityCache = HashMap<String, Boolean>()
    private var lastBlockedPackage: String? = null
    private var lastBlockAt = 0L
    private var lastWarningAt = 0L
    private var lastGuardAt = 0L
    private var lastGuardCheckAt = 0L
    private var guardEvents = false
    private val myName: String by lazy { applicationInfo.loadLabel(packageManager).toString() }

    /** Re-checks every 30 seconds so expiring grants and starting bedtimes take effect mid-app. */
    private val tick = object : Runnable {
        override fun run() {
            refreshPackages()
            currentPackage?.let { evaluate(it) }
            startVisitTick() // in case a check-in started a visit since the last event
            WallpaperController.enforce(this@GuardianAccessibilityService)
            updateGuardEvents()
            handler.postDelayed(this, 30_000)
        }
    }

    /**
     * Open sites: runs every second while a visit exists. Finishes it when you've stayed long
     * enough, shows a waiting one once the phone is free, and keeps her warning in front.
     */
    private val visitTick = object : Runnable {
        override fun run() {
            Guardian.checkVisit()
            val visit = Guardian.state.value.visit ?: return
            when {
                visit.pending -> showWarningAgain { Guardian.showVisitIfReady() }
                visit.warning -> currentPackage?.let { if (leavesWarning(it)) showWarningAgain { SiteOpener.showWarning(this@GuardianAccessibilityService) } }
            }
            handler.postDelayed(this, 1_000)
        }
    }

    /** Screen off pauses a visit. Unlocking brings you back to the page, or shows a visit that was waiting. */
    private val screen = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val visit = Guardian.state.value.visit ?: return
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> Guardian.pauseVisit()
                Intent.ACTION_USER_PRESENT -> {
                    if (visit.open) SiteOpener.bringBack(context, visit.browser) else Guardian.showVisitIfReady()
                    startVisitTick()
                }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Guardian.init(this)
        val version = runCatching {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, 0).versionCode
        }.getOrDefault(0)
        Guardian.onWatchStarted(version)
        refreshPackages()
        updateGuardEvents()
        handler.post(tick)
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(this, screen, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        startVisitTick()
    }

    private fun startVisitTick() {
        handler.removeCallbacks(visitTick)
        if (Guardian.state.value.visit != null) handler.post(visitTick)
    }

    /** During the 10 second warning, going home or to another app brings her warning back. */
    private fun leavesWarning(pkg: String): Boolean =
        pkg != packageName && (pkg in launchers || !(pkg in protectedPackages || pkg in AppLists.NEVER_BLOCK))

    private inline fun showWarningAgain(show: () -> Unit) {
        val t = Guardian.now()
        if (t - lastWarningAt < 3_000) return
        lastWarningAt = t
        show()
    }

    private fun onVisitApp(pkg: String) {
        val visit = Guardian.state.value.visit ?: return
        val move = Rules.siteMove(
            visit, pkg, Guardian.now(),
            ownApp = pkg == packageName,
            launcher = pkg in launchers,
            exempt = pkg in protectedPackages || pkg in AppLists.NEVER_BLOCK,
        )
        Guardian.onVisitMove(move)
        startVisitTick()
    }

    private fun refreshPackages() {
        protectedPackages = ProtectedApps.discover(this)
        launchers = ProtectedApps.launchers(this)
    }

    /** Screen content changes are only needed while Lock guard is guarding, so they're off otherwise. */
    private fun updateGuardEvents() {
        val want = Guardian.guarding()
        if (want == guardEvents) return
        val info = serviceInfo ?: return
        info.eventTypes = if (want) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        } else {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        }
        serviceInfo = info
        guardEvents = want
    }

    /**
     * Lock guard: a Settings or uninstall screen showing her name during a lock. She sends you
     * home and back to her. Never a failure; Quit for now is on her screen.
     */
    private fun guard(event: AccessibilityEvent, pkg: String): Boolean {
        if (!LockGuard.guardedPackage(pkg) || !Guardian.guarding()) return false
        val t = Guardian.now()
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED && t - lastGuardCheckAt < 400) return false
        lastGuardCheckAt = t
        val shown = event.text.any { it?.contains(myName, ignoreCase = true) == true } ||
            runCatching { rootInActiveWindow?.findAccessibilityNodeInfosByText(myName)?.isNotEmpty() == true }.getOrDefault(false)
        if (!shown) return false
        if (t - lastGuardAt < 1_500) return true
        lastGuardAt = t
        Guardian.say(Line.GUARDED)
        performGlobalAction(GLOBAL_ACTION_HOME)
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString() ?: return
        updateGuardEvents()
        if (guard(event, pkg)) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        if (isForegroundApp(pkg, event.className)) {
            currentPackage = pkg
            onVisitApp(pkg)
        }
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
        handler.removeCallbacks(visitTick)
        runCatching { unregisterReceiver(screen) }
        super.onDestroy()
    }
}
