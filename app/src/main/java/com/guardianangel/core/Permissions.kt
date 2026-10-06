package com.guardianangel.core

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.telecom.TelecomManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.guardianangel.service.GuardianAccessibilityService

object Permissions {
    fun accessibility(context: Context): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        val me = ComponentName(context, GuardianAccessibilityService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
    }

    fun notifications(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun exactAlarms(context: Context): Boolean = Scheduler.canScheduleExact(context)

    fun camera(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    fun coreReady(context: Context): Boolean =
        accessibility(context) && notifications(context) && exactAlarms(context)
}

/** Apps that must never be blocked, discovered on the device itself. */
object ProtectedApps {
    fun discover(context: Context): Set<String> {
        val pm = context.packageManager
        val result = mutableSetOf(context.packageName)
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        @Suppress("DEPRECATION")
        pm.queryIntentActivities(home, 0).forEach { result += it.activityInfo.packageName }
        runCatching {
            context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage?.let { result += it }
        }
        runCatching {
            context.getSystemService(InputMethodManager::class.java)?.inputMethodList?.forEach { result += it.packageName }
        }
        return result
    }
}

data class AppEntry(val packageName: String, val label: String)

object InstalledApps {
    fun launchable(context: Context): List<AppEntry> {
        val pm = context.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        return pm.queryIntentActivities(launcher, 0)
            .map { AppEntry(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
            .filter { it.packageName != context.packageName }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    fun label(context: Context, pkg: String): String = runCatching {
        val pm = context.packageManager
        pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString()
    }.getOrDefault(pkg)
}
