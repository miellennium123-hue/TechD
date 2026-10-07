package com.guardianangel.service

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.net.Uri
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat
import com.guardianangel.core.Decision
import com.guardianangel.core.Guardian
import com.guardianangel.core.Line
import com.guardianangel.core.LockGuard
import com.guardianangel.core.MarkOverlay
import com.guardianangel.core.PornBlock
import com.guardianangel.core.PornScanner
import com.guardianangel.core.ProtectedApps
import com.guardianangel.core.Rules
import com.guardianangel.core.ScreenPeek
import com.guardianangel.core.SiteOpener
import com.guardianangel.core.Usage
import com.guardianangel.core.WallpaperController
import com.guardianangel.data.AppLists
import com.guardianangel.ui.BedtimeActivity
import com.guardianangel.ui.BlockActivity
import com.guardianangel.ui.CaughtActivity
import com.guardianangel.ui.MainActivity
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Watches which app is in front and sends her block screen when it's off limits.
 * It reads the package name of the foreground window. With Lock guard on, it also
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
    private val scope = MainScope()
    private var mark: MarkOverlay? = null
    private var peek: ScreenPeek? = null
    private var porn: PornScanner? = null
    private var browsers: Set<String> = emptySet()

    /** Daily report (round 68): the app whose time is being counted, and since when (0: not counting). */
    private var usagePkg: String? = null
    private var usageSince = 0L

    /** Re-checks every 30 seconds so expiring grants and starting bedtimes take effect mid-app. */
    private val tick = object : Runnable {
        override fun run() {
            refreshPackages()
            currentPackage?.let { evaluate(it) }
            startVisitTick() // in case a check-in started a visit since the last event
            WallpaperController.enforce(this@GuardianAccessibilityService)
            updateGuardEvents()
            updateMark()
            peekIfDue()
            meter(screenUnlocked())
            Guardian.checkReport()
            handler.postDelayed(this, 30_000)
        }
    }

    /** Porn block (round 73): a look every few seconds while a browser or social app is in front. */
    private val pornTick = object : Runnable {
        override fun run() {
            scanForPorn()
            handler.postDelayed(this, PornBlock.SCAN_SECONDS * 1_000L)
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
            // Daily report: count the unlock, and only count app time while the phone is unlocked.
            when (intent.action) {
                Intent.ACTION_USER_PRESENT -> {
                    Guardian.countUnlock()
                    meter(true)
                }
                Intent.ACTION_SCREEN_OFF -> meter(false)
            }
            // Unlocking during bedtime brings her bedtime screen back over whatever was in front.
            if (intent.action == Intent.ACTION_USER_PRESENT) currentPackage?.let { evaluate(it) }
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
        handler.postDelayed(pornTick, PornBlock.SCAN_SECONDS * 1_000L)
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(this, screen, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        startVisitTick()
        meter(screenUnlocked())
        // Her mark follows every change to her settings and state right away (blocks starting,
        // Quit for now); the 30 second tick catches blocks and bedtimes that start or end on time.
        mark = MarkOverlay(this)
        peek = ScreenPeek(this)
        porn = PornScanner(this)
        scope.launch { Guardian.config.flow.combine(Guardian.state.flow) { _, _ -> }.collect { updateMark() } }
    }

    /** Her mark: the collar badge, and the dark tint during her blocks except over her own screens. */
    private fun updateMark() {
        mark?.update(ownApp = currentPackage == packageName)
    }

    /** She peeks (round 60): about every 5 minutes, at whatever you're in. Rules in core/Peek. */
    private fun peekIfDue() {
        val pkg = currentPackage
        peek?.maybePeek(pkg, pkg != null && pkg in launchers, browsers, protectedPackages)
    }

    /** Porn block (round 73): rules in core/PornBlock. Cheap when it's off: no screenshot is taken. */
    private fun scanForPorn() {
        val pkg = currentPackage
        porn?.maybeScan(pkg, pkg != null && pkg in launchers, browsers, protectedPackages) { onCaught() }
    }

    /**
     * She caught you: out of the app, her caught screen up, and the screen locked if you set that.
     * Unlocking the phone shows her caught screen, not what she caught.
     */
    private fun onCaught() {
        performGlobalAction(GLOBAL_ACTION_HOME)
        startActivity(CaughtActivity.intent(this))
        updateMark()
        if (Guardian.config.value.pornBlock.lockScreen && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            handler.postDelayed({ performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN) }, 600)
        }
    }

    /**
     * Daily report (round 68): adds the time since the last call to the app that was in front, then
     * keeps counting the app in front now if [running]. Her own screens, home and the phone don't count.
     */
    private fun meter(running: Boolean) {
        val t = Guardian.now()
        val pkg = usagePkg
        if (usageSince > 0 && pkg != null && Usage.countsApp(pkg, pkg == packageName, pkg in launchers, protectedPackages)) {
            Guardian.countTime(pkg, t - usageSince)
        }
        usagePkg = currentPackage
        usageSince = if (running) t else 0
    }

    private fun screenUnlocked(): Boolean {
        val power = getSystemService(Context.POWER_SERVICE) as PowerManager
        val keyguard = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        return power.isInteractive && !keyguard.isKeyguardLocked
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
        browsers = runCatching {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")), 0)
                .map { it.activityInfo.packageName }.toSet()
        }.getOrDefault(emptySet())
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
     * Lock guard: a Settings or uninstall screen showing her name while Lock guard is on. She sends you
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
            meter(screenUnlocked())
            onVisitApp(pkg)
        }
        evaluate(pkg)
        updateMark()
        WallpaperController.enforce(this)
    }

    /**
     * The keyboard, the notification shade, volume and power dialogs also send window events.
     * Those come from system packages and aren't activities, so they mustn't replace the app
     * you're actually in, or the 30 second re-check would stop watching it.
     */
    private fun isForegroundApp(pkg: String, className: CharSequence?): Boolean {
        if (pkg in launchers) return true
        // Her own mark's overlay windows aren't one of her screens.
        if (pkg == packageName) return isActivity(pkg, className)
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
        // Bedtime screen (round 54): covers the home screen and bedtime's blocked apps.
        val bedtime = Guardian.bedtimeScreen(decision, pkg in launchers)
        // Caught screen (round 73): the same, while her porn block lock runs.
        val caught = !bedtime && Guardian.caughtScreen(decision, pkg in launchers)
        if (decision !is Decision.Block && !bedtime && !caught) return
        val t = Guardian.now()
        if (pkg == lastBlockedPackage && t - lastBlockAt < 1_500) return
        lastBlockedPackage = pkg
        lastBlockAt = t
        startActivity(
            when {
                bedtime -> BedtimeActivity.intent(this)
                caught -> CaughtActivity.intent(this)
                else -> BlockActivity.intent(this, pkg)
            },
        )
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        handler.removeCallbacks(pornTick)
        handler.removeCallbacks(visitTick)
        scope.cancel()
        mark?.hide()
        runCatching { unregisterReceiver(screen) }
        super.onDestroy()
    }
}
